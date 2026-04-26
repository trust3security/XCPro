package com.trust3.xcpro.puretrack

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class PureTrackSessionRepositoryTest {

    @Test
    fun loadStoredSessionState_readsProToken() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "token-123", pro = true)
        )
        val repository = repository(tokenStore = tokenStore)

        val state = repository.loadStoredSessionState()

        assertEquals(PureTrackSessionState.LoggedInPro(), state)
        assertEquals(PureTrackSessionState.LoggedInPro(), repository.sessionState.value)
    }

    @Test
    fun login_proUserStoresTokenOnlyAndReturnsBearerToken() = runTest {
        val tokenStore = FakePureTrackTokenStore()
        val providerClient = FakePureTrackProviderClient(
            loginResult = PureTrackProviderResult.Success(
                value = PureTrackLoginSession(accessToken = " token-123 ", pro = true),
                httpCode = 200
            )
        )
        val repository = repository(
            providerClient = providerClient,
            tokenStore = tokenStore
        )

        val state = repository.login(
            email = "pilot@example.com",
            password = "do-not-store"
        )

        assertEquals(PureTrackSessionState.LoggedInPro(), state)
        assertEquals(PureTrackStoredSession(accessToken = "token-123", pro = true), tokenStore.session)
        assertEquals("token-123", repository.getBearerTokenOrNull())
        assertEquals(listOf("pilot@example.com" to "do-not-store"), providerClient.loginCalls)
    }

    @Test
    fun login_nonProUserStoresSessionButDoesNotExposeTrafficBearer() = runTest {
        val tokenStore = FakePureTrackTokenStore()
        val repository = repository(
            providerClient = FakePureTrackProviderClient(
                loginResult = PureTrackProviderResult.Success(
                    value = PureTrackLoginSession(accessToken = "token-123", pro = false),
                    httpCode = 200
                )
            ),
            tokenStore = tokenStore
        )

        val state = repository.login(email = "pilot@example.com", password = "password")

        assertEquals(PureTrackSessionState.LoggedInNotPro(), state)
        assertEquals(PureTrackStoredSession(accessToken = "token-123", pro = false), tokenStore.session)
        assertNull(repository.getBearerTokenOrNull())
    }

    @Test
    fun login_missingAppKeyDoesNotStoreToken() = runTest {
        val tokenStore = FakePureTrackTokenStore()
        val repository = repository(
            providerClient = FakePureTrackProviderClient(
                loginResult = PureTrackProviderResult.MissingAppKey
            ),
            tokenStore = tokenStore
        )

        val state = repository.login(email = "pilot@example.com", password = "password")

        assertEquals(
            PureTrackSessionState.Error(
                kind = PureTrackSessionErrorKind.MISSING_APP_KEY,
                reason = "MissingAppKey"
            ),
            state
        )
        assertNull(tokenStore.session)
    }

    @Test
    fun login_credentialsRejectedDoesNotStoreToken() = runTest {
        val tokenStore = FakePureTrackTokenStore()
        val repository = repository(
            providerClient = FakePureTrackProviderClient(
                loginResult = PureTrackProviderResult.HttpError(401, "Unauthorized")
            ),
            tokenStore = tokenStore
        )

        val state = repository.login(email = "pilot@example.com", password = "password")

        assertEquals(
            PureTrackSessionState.Error(
                kind = PureTrackSessionErrorKind.CREDENTIALS_REJECTED,
                reason = "HTTP 401"
            ),
            state
        )
        assertNull(tokenStore.session)
    }

    @Test
    fun login_malformedResponseDoesNotStoreToken() = runTest {
        val tokenStore = FakePureTrackTokenStore()
        val repository = repository(
            providerClient = FakePureTrackProviderClient(
                loginResult = PureTrackProviderResult.NetworkError(
                    kind = PureTrackNetworkFailureKind.MALFORMED_RESPONSE,
                    message = "Malformed PureTrack response"
                )
            ),
            tokenStore = tokenStore
        )

        val state = repository.login(email = "pilot@example.com", password = "password")

        assertEquals(
            PureTrackSessionState.Error(
                kind = PureTrackSessionErrorKind.MALFORMED_RESPONSE,
                reason = "Malformed PureTrack response"
            ),
            state
        )
        assertNull(tokenStore.session)
    }

    @Test
    fun login_saveFailureReturnsPersistenceUnavailableAndDoesNotReportLoggedIn() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            saveFailure = IllegalStateException("do-not-leak-token-123")
        )
        val repository = repository(
            providerClient = FakePureTrackProviderClient(
                loginResult = PureTrackProviderResult.Success(
                    value = PureTrackLoginSession(accessToken = "token-123", pro = true),
                    httpCode = 200
                )
            ),
            tokenStore = tokenStore
        )

        val state = repository.login(email = "pilot@example.com", password = "do-not-store")

        assertEquals(persistenceUnavailableState(), state)
        assertEquals(persistenceUnavailableState(), repository.sessionState.value)
        assertNull(tokenStore.session)
        assertNull(repository.getBearerTokenOrNull())
    }

    @Test
    fun loadStoredSessionState_readFailureReturnsPersistenceUnavailable() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "token-123", pro = true),
            readFailure = IllegalStateException("do-not-leak-token-123")
        )
        val repository = repository(tokenStore = tokenStore)

        val state = repository.loadStoredSessionState()

        assertEquals(persistenceUnavailableState(), state)
        assertEquals(persistenceUnavailableState(), repository.sessionState.value)
        assertNull(repository.getBearerTokenOrNull())
    }

    @Test
    fun loadStoredSessionState_readCancellationRethrows() = runTest {
        val repository = repository(
            tokenStore = FakePureTrackTokenStore(
                session = PureTrackStoredSession(accessToken = "token-123", pro = true),
                readFailure = CancellationException("cancelled")
            )
        )

        val failure = assertFailsWith<CancellationException> {
            repository.loadStoredSessionState()
        }

        assertEquals("cancelled", failure.message)
    }

    @Test
    fun login_saveCancellationRethrows() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            saveFailure = CancellationException("cancelled")
        )
        val repository = repository(
            providerClient = FakePureTrackProviderClient(
                loginResult = PureTrackProviderResult.Success(
                    value = PureTrackLoginSession(accessToken = "token-123", pro = true),
                    httpCode = 200
                )
            ),
            tokenStore = tokenStore
        )

        val failure = assertFailsWith<CancellationException> {
            repository.login(email = "pilot@example.com", password = "do-not-store")
        }

        assertEquals("cancelled", failure.message)
        assertNull(tokenStore.session)
    }

    @Test
    fun logoutClearsLocalTokenOnly() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "token-123", pro = true)
        )
        val providerClient = FakePureTrackProviderClient()
        val repository = repository(
            providerClient = providerClient,
            tokenStore = tokenStore
        )
        repository.loadStoredSessionState()

        repository.logout()

        assertNull(tokenStore.session)
        assertEquals(1, tokenStore.clearCount)
        assertEquals(PureTrackSessionState.LoggedOut, repository.sessionState.value)
        assertTrue(providerClient.loginCalls.isEmpty())
    }

    @Test
    fun logout_clearFailureSuppressesBearerAndReportsPersistenceUnavailable() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "token-123", pro = true),
            clearFailure = IllegalStateException("do-not-leak-token-123")
        )
        val repository = repository(tokenStore = tokenStore)
        repository.loadStoredSessionState()
        assertEquals("token-123", repository.getBearerTokenOrNull())

        repository.logout()

        assertEquals(persistenceUnavailableState(), repository.sessionState.value)
        assertEquals(PureTrackStoredSession(accessToken = "token-123", pro = true), tokenStore.session)
        assertNull(repository.getBearerTokenOrNull())
    }

    @Test
    fun logout_clearCancellationRethrows() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "token-123", pro = true),
            clearFailure = CancellationException("cancelled")
        )
        val repository = repository(tokenStore = tokenStore)
        repository.loadStoredSessionState()

        val failure = assertFailsWith<CancellationException> {
            repository.logout()
        }

        assertEquals("cancelled", failure.message)
    }

    @Test
    fun loginResultAfterLogoutDoesNotStoreStaleToken() = runTest {
        val tokenStore = FakePureTrackTokenStore()
        val loginStarted = CompletableDeferred<Unit>()
        val loginResult =
            CompletableDeferred<PureTrackProviderResult<PureTrackLoginSession>>()
        val repository = repository(
            providerClient = FakePureTrackProviderClient(
                loginHandler = { _, _ ->
                    loginStarted.complete(Unit)
                    loginResult.await()
                }
            ),
            tokenStore = tokenStore
        )
        val loginJob = async {
            repository.login(email = "pilot@example.com", password = "do-not-store")
        }
        loginStarted.await()

        repository.logout()
        loginResult.complete(
            PureTrackProviderResult.Success(
                value = PureTrackLoginSession(accessToken = "stale-token", pro = true),
                httpCode = 200
            )
        )

        assertEquals(PureTrackSessionState.LoggedOut, loginJob.await())
        assertNull(tokenStore.session)
        assertEquals(PureTrackSessionState.LoggedOut, repository.sessionState.value)
    }

    @Test
    fun loginResultAfterMarkTokenInvalidDoesNotStoreStaleToken() = runTest {
        val tokenStore = FakePureTrackTokenStore()
        val loginStarted = CompletableDeferred<Unit>()
        val loginResult =
            CompletableDeferred<PureTrackProviderResult<PureTrackLoginSession>>()
        val repository = repository(
            providerClient = FakePureTrackProviderClient(
                loginHandler = { _, _ ->
                    loginStarted.complete(Unit)
                    loginResult.await()
                }
            ),
            tokenStore = tokenStore
        )
        val loginJob = async {
            repository.login(email = "pilot@example.com", password = "do-not-store")
        }
        loginStarted.await()

        repository.markTokenInvalid()
        loginResult.complete(
            PureTrackProviderResult.Success(
                value = PureTrackLoginSession(accessToken = "stale-token", pro = true),
                httpCode = 200
            )
        )

        assertEquals(PureTrackSessionState.TokenInvalid, loginJob.await())
        assertNull(tokenStore.session)
        assertEquals(PureTrackSessionState.TokenInvalid, repository.sessionState.value)
    }

    @Test
    fun olderConcurrentLoginCannotOverwriteNewerLoginResult() = runTest {
        val tokenStore = FakePureTrackTokenStore()
        val firstLoginStarted = CompletableDeferred<Unit>()
        val secondLoginStarted = CompletableDeferred<Unit>()
        val firstLoginResult =
            CompletableDeferred<PureTrackProviderResult<PureTrackLoginSession>>()
        val secondLoginResult =
            CompletableDeferred<PureTrackProviderResult<PureTrackLoginSession>>()
        val loginCount = AtomicInteger(0)
        val repository = repository(
            providerClient = FakePureTrackProviderClient(
                loginHandler = { _, _ ->
                    when (loginCount.incrementAndGet()) {
                        1 -> {
                            firstLoginStarted.complete(Unit)
                            firstLoginResult.await()
                        }
                        2 -> {
                            secondLoginStarted.complete(Unit)
                            secondLoginResult.await()
                        }
                        else -> error("Unexpected login call")
                    }
                }
            ),
            tokenStore = tokenStore
        )
        val firstLoginJob = async {
            repository.login(email = "first@example.com", password = "do-not-store")
        }
        firstLoginStarted.await()
        val secondLoginJob = async {
            repository.login(email = "second@example.com", password = "do-not-store")
        }
        secondLoginStarted.await()

        secondLoginResult.complete(
            PureTrackProviderResult.Success(
                value = PureTrackLoginSession(accessToken = "new-token", pro = true),
                httpCode = 200
            )
        )
        assertEquals(PureTrackSessionState.LoggedInPro(), secondLoginJob.await())
        firstLoginResult.complete(
            PureTrackProviderResult.Success(
                value = PureTrackLoginSession(accessToken = "old-token", pro = true),
                httpCode = 200
            )
        )

        assertEquals(PureTrackSessionState.LoggedInPro(), firstLoginJob.await())
        assertEquals(
            PureTrackStoredSession(accessToken = "new-token", pro = true),
            tokenStore.session
        )
        assertEquals("new-token", repository.getBearerTokenOrNull())
        assertEquals(PureTrackSessionState.LoggedInPro(), repository.sessionState.value)
    }

    @Test
    fun markTokenInvalidClearsLocalToken() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "token-123", pro = true)
        )
        val repository = repository(tokenStore = tokenStore)

        repository.markTokenInvalid()

        assertNull(tokenStore.session)
        assertEquals(PureTrackSessionState.TokenInvalid, repository.sessionState.value)
    }

    @Test
    fun markTokenInvalid_clearFailureSuppressesBearerAndReportsPersistenceUnavailable() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "token-123", pro = true),
            clearFailure = IllegalStateException("do-not-leak-token-123")
        )
        val repository = repository(tokenStore = tokenStore)
        repository.loadStoredSessionState()
        assertEquals("token-123", repository.getBearerTokenOrNull())

        repository.markTokenInvalid()

        assertEquals(persistenceUnavailableState(), repository.sessionState.value)
        assertEquals(PureTrackStoredSession(accessToken = "token-123", pro = true), tokenStore.session)
        assertNull(repository.getBearerTokenOrNull())
    }

    @Test
    fun markTokenInvalid_clearCancellationRethrows() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "token-123", pro = true),
            clearFailure = CancellationException("cancelled")
        )
        val repository = repository(tokenStore = tokenStore)
        repository.loadStoredSessionState()

        val failure = assertFailsWith<CancellationException> {
            repository.markTokenInvalid()
        }

        assertEquals("cancelled", failure.message)
    }

    @Test
    fun getBearerTokenOrNullRejectsBlankOrNonProTokens() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "   ", pro = true)
        )
        val repository = repository(tokenStore = tokenStore)

        assertNull(repository.getBearerTokenOrNull())

        tokenStore.session = PureTrackStoredSession(accessToken = "token-123", pro = false)

        assertNull(repository.getBearerTokenOrNull())
    }

    @Test
    fun getBearerTokenOrNull_readCancellationRethrows() = runTest {
        val tokenStore = FakePureTrackTokenStore(
            session = PureTrackStoredSession(accessToken = "token-123", pro = true)
        )
        val repository = repository(tokenStore = tokenStore)
        repository.loadStoredSessionState()
        tokenStore.readFailure = CancellationException("cancelled")

        val failure = assertFailsWith<CancellationException> {
            repository.getBearerTokenOrNull()
        }

        assertEquals("cancelled", failure.message)
    }

    private fun persistenceUnavailableState(): PureTrackSessionState.Error =
        PureTrackSessionState.Error(
            kind = PureTrackSessionErrorKind.PERSISTENCE_UNAVAILABLE,
            reason = "TokenPersistenceUnavailable"
        )

    private fun repository(
        providerClient: PureTrackProviderClient = FakePureTrackProviderClient(),
        tokenStore: FakePureTrackTokenStore = FakePureTrackTokenStore()
    ): DefaultPureTrackSessionRepository =
        DefaultPureTrackSessionRepository(
            providerClient = providerClient,
            tokenStore = tokenStore
        )

    private class FakePureTrackTokenStore(
        var session: PureTrackStoredSession? = null,
        readFailure: RuntimeException? = null,
        saveFailure: RuntimeException? = null,
        clearFailure: RuntimeException? = null
    ) : PureTrackTokenStore {
        var clearCount = 0
        var readFailure: RuntimeException? = readFailure
        var saveFailure: RuntimeException? = saveFailure
        var clearFailure: RuntimeException? = clearFailure

        override suspend fun readSession(): PureTrackStoredSession? {
            readFailure?.let { throw it }
            return session
        }

        override suspend fun saveSession(session: PureTrackStoredSession) {
            saveFailure?.let { throw it }
            this.session = session
        }

        override suspend fun clearSession() {
            clearFailure?.let { throw it }
            clearCount += 1
            session = null
        }
    }

    private class FakePureTrackProviderClient(
        private val loginResult: PureTrackProviderResult<PureTrackLoginSession> =
            PureTrackProviderResult.HttpError(500, "Unset"),
        private val loginHandler:
            (suspend (String, String) -> PureTrackProviderResult<PureTrackLoginSession>)? = null
    ) : PureTrackProviderClient {
        val loginCalls = mutableListOf<Pair<String, String>>()

        override suspend fun login(
            email: String,
            password: String
        ): PureTrackProviderResult<PureTrackLoginSession> {
            loginCalls += email to password
            loginHandler?.let {
                return it(email, password)
            }
            return loginResult
        }

        override suspend fun fetchTraffic(
            request: PureTrackTrafficRequest,
            bearerToken: String
        ): PureTrackProviderResult<PureTrackTrafficResponse> =
            PureTrackProviderResult.HttpError(500, "Unset")
    }
}
