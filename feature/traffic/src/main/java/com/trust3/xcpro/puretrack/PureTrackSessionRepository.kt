package com.trust3.xcpro.puretrack

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

interface PureTrackSessionRepository {
    val sessionState: StateFlow<PureTrackSessionState>
    suspend fun loadStoredSessionState(): PureTrackSessionState
    suspend fun login(email: String, password: String): PureTrackSessionState
    suspend fun logout()
    suspend fun markTokenInvalid()
    suspend fun getBearerTokenOrNull(): String?
}

class DefaultPureTrackSessionRepository(
    private val providerClient: PureTrackProviderClient,
    private val tokenStore: PureTrackTokenStore
) : PureTrackSessionRepository {
    private val mutableSessionState =
        MutableStateFlow<PureTrackSessionState>(PureTrackSessionState.LoggedOut)
    private val sessionMutex = Mutex()
    private var authIntentVersion = 0L
    private var localTokenAccessBlocked = false

    override val sessionState: StateFlow<PureTrackSessionState> =
        mutableSessionState.asStateFlow()

    override suspend fun loadStoredSessionState(): PureTrackSessionState =
        sessionMutex.withLock {
            if (localTokenAccessBlocked) {
                val state = persistenceUnavailableState()
                mutableSessionState.value = state
                return@withLock state
            }
            val state = try {
                tokenStore.readSession()?.toSessionState()
                    ?: PureTrackSessionState.LoggedOut
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                localTokenAccessBlocked = true
                persistenceUnavailableState()
            }
            mutableSessionState.value = state
            state
        }

    override suspend fun login(email: String, password: String): PureTrackSessionState {
        val loginIntentVersion = sessionMutex.withLock {
            authIntentVersion += 1
            authIntentVersion
        }
        val result = providerClient.login(email = email, password = password)
        return sessionMutex.withLock {
            if (loginIntentVersion != authIntentVersion) {
                return@withLock mutableSessionState.value
            }
            val state = result.toSessionState()
            mutableSessionState.value = state
            state
        }
    }

    override suspend fun logout() {
        sessionMutex.withLock {
            authIntentVersion += 1
            val state = clearLocalSessionOrPersistenceUnavailable(
                clearedState = PureTrackSessionState.LoggedOut
            )
            mutableSessionState.value = state
        }
    }

    override suspend fun markTokenInvalid() {
        sessionMutex.withLock {
            authIntentVersion += 1
            val state = clearLocalSessionOrPersistenceUnavailable(
                clearedState = PureTrackSessionState.TokenInvalid
            )
            mutableSessionState.value = state
        }
    }

    override suspend fun getBearerTokenOrNull(): String? = sessionMutex.withLock {
        if (localTokenAccessBlocked) {
            return@withLock null
        }
        if (mutableSessionState.value !is PureTrackSessionState.LoggedInPro) {
            return@withLock null
        }
        val storedSession = try {
            tokenStore.readSession()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            localTokenAccessBlocked = true
            mutableSessionState.value = persistenceUnavailableState()
            return@withLock null
        } ?: return@withLock null
        storedSession.accessToken
            .takeIf { storedSession.pro }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun PureTrackProviderResult<PureTrackLoginSession>.toSessionState(): PureTrackSessionState {
        return when (this) {
            is PureTrackProviderResult.Success -> {
                val token = value.accessToken.trim()
                if (token.isBlank()) {
                    PureTrackSessionState.Error(
                        kind = PureTrackSessionErrorKind.MISSING_TOKEN,
                        reason = "MissingAccessToken"
                    )
                } else {
                    val session = PureTrackStoredSession(
                        accessToken = token,
                        pro = value.pro
                    )
                    try {
                        tokenStore.saveSession(session)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        localTokenAccessBlocked = true
                        return persistenceUnavailableState()
                    }
                    localTokenAccessBlocked = false
                    session.toSessionState()
                }
            }

            PureTrackProviderResult.MissingAppKey ->
                PureTrackSessionState.Error(
                    kind = PureTrackSessionErrorKind.MISSING_APP_KEY,
                    reason = "MissingAppKey"
                )

            PureTrackProviderResult.MissingBearerToken ->
                PureTrackSessionState.Error(
                    kind = PureTrackSessionErrorKind.MISSING_TOKEN,
                    reason = "MissingBearerToken"
                )

            is PureTrackProviderResult.HttpError -> {
                val kind = if (code in CREDENTIALS_REJECTED_CODES) {
                    PureTrackSessionErrorKind.CREDENTIALS_REJECTED
                } else {
                    PureTrackSessionErrorKind.TRANSIENT_FAILURE
                }
                PureTrackSessionState.Error(kind = kind, reason = "HTTP $code")
            }

            is PureTrackProviderResult.NetworkError -> {
                val errorKind = if (kind == PureTrackNetworkFailureKind.MALFORMED_RESPONSE) {
                    PureTrackSessionErrorKind.MALFORMED_RESPONSE
                } else {
                    PureTrackSessionErrorKind.TRANSIENT_FAILURE
                }
                PureTrackSessionState.Error(kind = errorKind, reason = message)
            }

            is PureTrackProviderResult.RateLimited ->
                PureTrackSessionState.Error(
                    kind = PureTrackSessionErrorKind.TRANSIENT_FAILURE,
                    reason = "RateLimited"
                )
        }
    }

    private fun PureTrackStoredSession.toSessionState(): PureTrackSessionState =
        if (pro) {
            PureTrackSessionState.LoggedInPro(tokenAvailable = accessToken.isNotBlank())
        } else {
            PureTrackSessionState.LoggedInNotPro(tokenAvailable = accessToken.isNotBlank())
        }

    private suspend fun clearLocalSessionOrPersistenceUnavailable(
        clearedState: PureTrackSessionState
    ): PureTrackSessionState {
        localTokenAccessBlocked = true
        return try {
            tokenStore.clearSession()
            localTokenAccessBlocked = false
            clearedState
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            persistenceUnavailableState()
        }
    }

    private fun persistenceUnavailableState(): PureTrackSessionState.Error =
        PureTrackSessionState.Error(
            kind = PureTrackSessionErrorKind.PERSISTENCE_UNAVAILABLE,
            reason = "TokenPersistenceUnavailable"
        )

    private companion object {
        private val CREDENTIALS_REJECTED_CODES = setOf(400, 401, 403)
    }
}
