package com.example.xcpro.adsb

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

interface OpenSkyTokenRepository {
    suspend fun getValidTokenOrNull(): String?
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

    private val lock = Any()

    override suspend fun getValidTokenOrNull(): String? = withContext(dispatcher) {
        val now = clock.nowMonoMs()
        synchronized(lock) {
            val token = cachedToken
            if (!token.isNullOrBlank() && now + TOKEN_REFRESH_BUFFER_MS < expiryMonoMs) {
                return@withContext token
            }
        }

        val credentials = credentialsRepository.loadCredentials() ?: return@withContext null
        val fresh = fetchToken(credentials) ?: return@withContext null
        synchronized(lock) {
            cachedToken = fresh.accessToken
            val expirySec = fresh.expiresInSec ?: DEFAULT_TOKEN_EXPIRY_SEC
            expiryMonoMs = clock.nowMonoMs() + expirySec * 1_000L
        }
        fresh.accessToken
    }

    override fun invalidate() {
        synchronized(lock) {
            cachedToken = null
            expiryMonoMs = 0L
        }
    }

    private fun fetchToken(credentials: OpenSkyClientCredentials): OpenSkyTokenResponse? {
        val formBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", credentials.clientId)
            .add("client_secret", credentials.clientSecret)
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val root = JsonParser.parseString(body).asJsonObject
                val token = root.get("access_token")?.takeIf { it.isJsonPrimitive }?.asString
                val expiresIn = root.get("expires_in")?.takeIf { it.isJsonPrimitive }?.asLong
                if (token.isNullOrBlank()) null else OpenSkyTokenResponse(token, expiresIn)
            }
        }.getOrNull()
    }

    private companion object {
        private const val TOKEN_ENDPOINT =
            "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token"
        private const val DEFAULT_TOKEN_EXPIRY_SEC = 1_800L
        private const val TOKEN_REFRESH_BUFFER_MS = 5L * 60L * 1_000L
    }
}

