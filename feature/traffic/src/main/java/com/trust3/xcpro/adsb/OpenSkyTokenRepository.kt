package com.trust3.xcpro.adsb

import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.core.time.Clock
import com.google.gson.JsonParser
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface OpenSkyTokenAccessState {
    data class Available(val token: String) : OpenSkyTokenAccessState
    data object NoCredentials : OpenSkyTokenAccessState
    data class CredentialsRejected(val reason: String) : OpenSkyTokenAccessState
    data class TransientFailure(val reason: String) : OpenSkyTokenAccessState
}

interface OpenSkyTokenRepository {
    suspend fun getTokenAccessState(): OpenSkyTokenAccessState
    suspend fun getValidTokenOrNull(): String?
    fun hasCredentials(): Boolean
    fun invalidate()
}

@Singleton
class OpenSkyTokenRepositoryImpl @Inject constructor(
    private val credentialsRepository: OpenSkyCredentialsRepository,
    private val clock: Clock,
    private val httpClient: OkHttpClient,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : OpenSkyTokenRepository {

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var expiryMonoMs: Long = 0L

    @Volatile
    private var transientFailureUntilMonoMs: Long = 0L

    @Volatile
    private var transientFailureReason: String? = null

    private val lock = Any()
    private val tokenFetchMutex = Mutex()

    override suspend fun getTokenAccessState(): OpenSkyTokenAccessState = withContext(dispatcher) {
        fun cachedTokenIfFresh(nowMonoMs: Long): String? = synchronized(lock) {
            val token = cachedToken
            if (!token.isNullOrBlank() && nowMonoMs + TOKEN_REFRESH_BUFFER_MS < expiryMonoMs) {
                token
            } else {
                null
            }
        }
        cachedTokenIfFresh(clock.nowMonoMs())?.let { freshToken ->
            return@withContext OpenSkyTokenAccessState.Available(freshToken)
        }

        val credentials = credentialsRepository.loadCredentials()
            ?: return@withContext OpenSkyTokenAccessState.NoCredentials

        synchronized(lock) {
            val nowMonoMs = clock.nowMonoMs()
            if (nowMonoMs < transientFailureUntilMonoMs) {
                return@withContext OpenSkyTokenAccessState.TransientFailure(
                    transientFailureReason ?: "TransientFailureCooldown"
                )
            }
        }

        return@withContext tokenFetchMutex.withLock {
            cachedTokenIfFresh(clock.nowMonoMs())?.let { refreshedByAnotherRequest ->
                return@withLock OpenSkyTokenAccessState.Available(refreshedByAnotherRequest)
            }
            synchronized(lock) {
                val nowMonoMs = clock.nowMonoMs()
                if (nowMonoMs < transientFailureUntilMonoMs) {
                    return@withLock OpenSkyTokenAccessState.TransientFailure(
                        transientFailureReason ?: "TransientFailureCooldown"
                    )
                }
            }

            when (val result = fetchToken(credentials)) {
                is TokenFetchResult.Success -> {
                    synchronized(lock) {
                        cachedToken = result.token.accessToken
                        val expirySec = result.token.expiresInSec ?: DEFAULT_TOKEN_EXPIRY_SEC
                        expiryMonoMs = clock.nowMonoMs() + expirySec * 1_000L
                        transientFailureUntilMonoMs = 0L
                        transientFailureReason = null
                    }
                    OpenSkyTokenAccessState.Available(result.token.accessToken)
                }

                is TokenFetchResult.CredentialsRejected -> {
                    synchronized(lock) {
                        cachedToken = null
                        expiryMonoMs = 0L
                        transientFailureUntilMonoMs = 0L
                        transientFailureReason = null
                    }
                    OpenSkyTokenAccessState.CredentialsRejected(result.reason)
                }

                is TokenFetchResult.TransientFailure -> {
                    synchronized(lock) {
                        transientFailureUntilMonoMs =
                            clock.nowMonoMs() + TRANSIENT_FAILURE_COOLDOWN_MS
                        transientFailureReason = result.reason
                    }
                    OpenSkyTokenAccessState.TransientFailure(result.reason)
                }
            }
        }
    }

    override suspend fun getValidTokenOrNull(): String? =
        (getTokenAccessState() as? OpenSkyTokenAccessState.Available)?.token

    override fun hasCredentials(): Boolean = credentialsRepository.loadCredentials() != null

    override fun invalidate() {
        synchronized(lock) {
            cachedToken = null
            expiryMonoMs = 0L
            transientFailureUntilMonoMs = 0L
            transientFailureReason = null
        }
    }

    private suspend fun fetchToken(credentials: OpenSkyClientCredentials): TokenFetchResult {
        val formBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", credentials.clientId)
            .add("client_secret", credentials.clientSecret)
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        return try {
            httpClient.newCall(request).awaitResponse().use { response ->
                if (response.code in CREDENTIALS_REJECTED_CODES) {
                    return@use TokenFetchResult.CredentialsRejected(
                        reason = "HTTP ${response.code}"
                    )
                }
                if (!response.isSuccessful) {
                    return@use TokenFetchResult.TransientFailure(
                        reason = "HTTP ${response.code}"
                    )
                }
                val body = response.body?.string().orEmpty()
                val root = runCatching { JsonParser.parseString(body).asJsonObject }
                    .getOrElse {
                        return@use TokenFetchResult.TransientFailure(
                            reason = "MalformedTokenResponse"
                        )
                    }
                val token = root.get("access_token")?.takeIf { it.isJsonPrimitive }?.asString
                val expiresIn = root.get("expires_in")?.takeIf { it.isJsonPrimitive }?.asLong
                if (token.isNullOrBlank()) {
                    TokenFetchResult.TransientFailure(reason = "MissingAccessToken")
                } else {
                    TokenFetchResult.Success(
                        token = OpenSkyTokenResponse(
                            accessToken = token,
                            expiresInSec = expiresIn
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            TokenFetchResult.TransientFailure(reason = e::class.java.simpleName.ifBlank { "NetworkError" })
        } catch (_: Exception) {
            TokenFetchResult.TransientFailure(reason = "TokenFetchFailure")
        }
    }

    private sealed interface TokenFetchResult {
        data class Success(val token: OpenSkyTokenResponse) : TokenFetchResult
        data class CredentialsRejected(val reason: String) : TokenFetchResult
        data class TransientFailure(val reason: String) : TokenFetchResult
    }

    private companion object {
        private const val TOKEN_ENDPOINT =
            "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token"
        private const val DEFAULT_TOKEN_EXPIRY_SEC = 1_800L
        private const val TOKEN_REFRESH_BUFFER_MS = 5L * 60L * 1_000L
        private const val TRANSIENT_FAILURE_COOLDOWN_MS = 30_000L
        private val CREDENTIALS_REJECTED_CODES = setOf(400, 401, 403)
    }
}
