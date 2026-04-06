package com.example.xcpro.livefollow.account

import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class XcAccountRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_withoutStoredSession_emitsSignedOutSnapshot() = runTest {
        val repository = XcAccountRepository(
            sessionStore = FakeSessionStore(),
            authProvider = FakeAuthProvider(),
            googleAuthGateway = FakeGoogleAuthGateway(),
            remoteDataSource = mock(),
            scope = testScope(testScheduler)
        )

        advanceUntilIdle()

        assertFalse(repository.state.value.isSignedIn)
        assertFalse(repository.state.value.isLoading)
        assertNull(repository.state.value.profile)
    }

    @Test
    fun init_withUnauthorizedStoredSession_clearsSessionAndSignsOut() = runTest {
        val sessionStore = FakeSessionStore(
            XcAccountSession(
                accessToken = "stored-token",
                authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
            )
        )
        val remoteDataSource: CurrentApiXcAccountDataSource = mock()
        whenever(remoteDataSource.fetchMe("stored-token")).thenReturn(
            XcAccountRemoteResult.Failure(
                XcAccountApiError(
                    message = "invalid bearer token",
                    code = "unauthenticated",
                    httpCode = 401
                )
            )
        )

        val repository = XcAccountRepository(
            sessionStore = sessionStore,
            authProvider = FakeAuthProvider(),
            googleAuthGateway = FakeGoogleAuthGateway(),
            remoteDataSource = remoteDataSource,
            scope = testScope(testScheduler)
        )

        advanceUntilIdle()

        assertFalse(repository.state.value.isSignedIn)
        assertEquals(
            "Your XCPro session is no longer valid. Sign in again.",
            repository.state.value.errorMessage
        )
        assertNull(sessionStore.savedSession)
    }

    @Test
    fun signIn_persistsSessionAndLoadsAccountSnapshot() = runTest {
        val sessionStore = FakeSessionStore()
        val remoteDataSource: CurrentApiXcAccountDataSource = mock()
        whenever(remoteDataSource.fetchMe("dev-token")).thenReturn(
            XcAccountRemoteResult.Success(
                XcAccountMePayload(
                    profile = XcPilotProfile(
                        userId = "pilot-1",
                        handle = null,
                        displayName = "Pilot One",
                        compNumber = null
                    ),
                    privacy = XcPrivacySettings.DEFAULT
                )
            )
        )
        stubRelationshipLists(remoteDataSource, "dev-token")
        val repository = XcAccountRepository(
            sessionStore = sessionStore,
            authProvider = FakeAuthProvider(
                signInResult = XcAccountAuthResult.Success(
                    XcAccountSession(
                        accessToken = "dev-token",
                        authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
                    )
                )
            ),
            googleAuthGateway = FakeGoogleAuthGateway(),
            remoteDataSource = remoteDataSource,
            scope = testScope(testScheduler)
        )
        advanceUntilIdle()

        val result = repository.signIn(XcAccountSignInMethod.CONFIGURED_DEV_TOKEN)
        advanceUntilIdle()

        assertTrue(result is XcAccountActionResult.Success)
        assertEquals("dev-token", sessionStore.savedSession?.accessToken)
        assertTrue(repository.state.value.isSignedIn)
        assertEquals("pilot-1", repository.state.value.profile?.userId)
        assertTrue(repository.state.value.needsProfileCompletion)
    }

    @Test
    fun saveProfile_rejectsInvalidHandleBeforeNetworkCall() = runTest {
        val remoteDataSource: CurrentApiXcAccountDataSource = mock()
        whenever(remoteDataSource.fetchMe("stored-token")).thenReturn(
            XcAccountRemoteResult.Success(
                XcAccountMePayload(
                    profile = XcPilotProfile(
                        userId = "pilot-1",
                        handle = "pilot123",
                        displayName = "Pilot One",
                        compNumber = null
                    ),
                    privacy = XcPrivacySettings.DEFAULT
                )
            )
        )
        stubRelationshipLists(remoteDataSource, "stored-token")
        val repository = XcAccountRepository(
            sessionStore = FakeSessionStore(
                XcAccountSession(
                    accessToken = "stored-token",
                    authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
                )
            ),
            authProvider = FakeAuthProvider(),
            googleAuthGateway = FakeGoogleAuthGateway(),
            remoteDataSource = remoteDataSource,
            scope = testScope(testScheduler)
        )
        advanceUntilIdle()

        val result = repository.saveProfile(
            rawHandle = "Bad Handle",
            rawDisplayName = "Pilot One",
            rawCompNumber = ""
        )

        require(result is XcAccountActionResult.Failure)
        assertEquals(XCPRO_HANDLE_RULE_MESSAGE, result.message)
        verify(remoteDataSource, never()).patchProfile(any(), any())
    }

    @Test
    fun init_withStoredGoogleSession_refreshesAccessTokenBeforeFetch() = runTest {
        val sessionStore = FakeSessionStore(
            XcAccountSession(
                accessToken = "expired-google-token",
                authMethod = XcAccountAuthMethod.GOOGLE
            )
        )
        val remoteDataSource: CurrentApiXcAccountDataSource = mock()
        whenever(remoteDataSource.fetchMe("fresh-google-token")).thenReturn(
            XcAccountRemoteResult.Success(
                XcAccountMePayload(
                    profile = XcPilotProfile(
                        userId = "pilot-google",
                        handle = "pilot.google",
                        displayName = "Pilot Google",
                        compNumber = null
                    ),
                    privacy = XcPrivacySettings.DEFAULT
                )
            )
        )
        stubRelationshipLists(remoteDataSource, "fresh-google-token")

        val repository = XcAccountRepository(
            sessionStore = sessionStore,
            authProvider = FakeAuthProvider(),
            googleAuthGateway = FakeGoogleAuthGateway(
                restoreResult = XcAccountAuthResult.Success(
                    XcAccountSession(
                        accessToken = "fresh-google-token",
                        authMethod = XcAccountAuthMethod.GOOGLE
                    )
                )
            ),
            remoteDataSource = remoteDataSource,
            scope = testScope(testScheduler)
        )

        advanceUntilIdle()

        assertTrue(repository.state.value.isSignedIn)
        assertEquals("fresh-google-token", sessionStore.savedSession?.accessToken)
        assertEquals("pilot-google", repository.state.value.profile?.userId)
    }

    @Test
    fun sendFollowRequest_refreshesOutgoingRequestsInSnapshot() = runTest {
        val remoteDataSource: CurrentApiXcAccountDataSource = mock()
        whenever(remoteDataSource.fetchMe("stored-token")).thenReturn(
            XcAccountRemoteResult.Success(
                XcAccountMePayload(
                    profile = XcPilotProfile(
                        userId = "pilot-1",
                        handle = "pilot.one",
                        displayName = "Pilot One",
                        compNumber = null
                    ),
                    privacy = XcPrivacySettings.DEFAULT
                )
            )
        )
        whenever(remoteDataSource.fetchIncomingFollowRequests("stored-token")).thenReturn(
            XcAccountRemoteResult.Success(emptyList()),
            XcAccountRemoteResult.Success(emptyList())
        )
        whenever(remoteDataSource.fetchOutgoingFollowRequests("stored-token")).thenReturn(
            XcAccountRemoteResult.Success(emptyList()),
            XcAccountRemoteResult.Success(
                listOf(
                    XcFollowRequestItem(
                        requestId = "request-1",
                        status = XcFollowRequestStatus.PENDING,
                        direction = XcFollowRequestDirection.OUTGOING,
                        counterpart = XcPilotProfile(
                            userId = "pilot-2",
                            handle = "pilot.two",
                            displayName = "Pilot Two",
                            compNumber = null
                        ),
                        relationshipState = XcRelationshipState.OUTGOING_PENDING
                    )
                )
            )
        )
        whenever(remoteDataSource.createFollowRequest("stored-token", "pilot-2")).thenReturn(
            XcAccountRemoteResult.Success(
                XcFollowRequestItem(
                    requestId = "request-1",
                    status = XcFollowRequestStatus.PENDING,
                    direction = XcFollowRequestDirection.OUTGOING,
                    counterpart = XcPilotProfile(
                        userId = "pilot-2",
                        handle = "pilot.two",
                        displayName = "Pilot Two",
                        compNumber = null
                    ),
                    relationshipState = XcRelationshipState.OUTGOING_PENDING
                )
            )
        )

        val repository = XcAccountRepository(
            sessionStore = FakeSessionStore(
                XcAccountSession(
                    accessToken = "stored-token",
                    authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
                )
            ),
            authProvider = FakeAuthProvider(),
            googleAuthGateway = FakeGoogleAuthGateway(),
            remoteDataSource = remoteDataSource,
            scope = testScope(testScheduler)
        )
        advanceUntilIdle()

        val result = repository.sendFollowRequest("pilot-2")
        advanceUntilIdle()

        assertTrue(result is XcAccountActionResult.Success)
        assertEquals(1, repository.state.value.outgoingFollowRequests.size)
        assertEquals(
            "pilot.two",
            repository.state.value.outgoingFollowRequests.first().counterpart.handle
        )
    }

    @Test
    fun searchUsers_returnsParsedSearchResultsWithoutMutatingSnapshot() = runTest {
        val remoteDataSource: CurrentApiXcAccountDataSource = mock()
        whenever(remoteDataSource.fetchMe("stored-token")).thenReturn(
            XcAccountRemoteResult.Success(
                XcAccountMePayload(
                    profile = XcPilotProfile(
                        userId = "pilot-1",
                        handle = "pilot.one",
                        displayName = "Pilot One",
                        compNumber = null
                    ),
                    privacy = XcPrivacySettings.DEFAULT
                )
            )
        )
        stubRelationshipLists(remoteDataSource, "stored-token")
        whenever(remoteDataSource.searchUsers("stored-token", "pilot")).thenReturn(
            XcAccountRemoteResult.Success(
                listOf(
                    XcSearchPilot(
                        userId = "pilot-2",
                        handle = "pilot.two",
                        displayName = "Pilot Two",
                        compNumber = null,
                        relationshipState = XcRelationshipState.NONE
                    )
                )
            )
        )

        val repository = XcAccountRepository(
            sessionStore = FakeSessionStore(
                XcAccountSession(
                    accessToken = "stored-token",
                    authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
                )
            ),
            authProvider = FakeAuthProvider(),
            googleAuthGateway = FakeGoogleAuthGateway(),
            remoteDataSource = remoteDataSource,
            scope = testScope(testScheduler)
        )
        advanceUntilIdle()

        val result = repository.searchUsers("pilot")

        require(result is XcAccountValueResult.Success)
        assertEquals(1, result.value.size)
        assertEquals("pilot.two", result.value.first().handle)
        assertTrue(repository.state.value.outgoingFollowRequests.isEmpty())
    }

    private class FakeSessionStore(
        initialSession: XcAccountSession? = null
    ) : XcAccountSessionStore {
        var savedSession: XcAccountSession? = initialSession

        override suspend fun loadSession(): XcAccountSession? = savedSession

        override suspend fun saveSession(session: XcAccountSession) {
            savedSession = session
        }

        override suspend fun clearSession() {
            savedSession = null
        }
    }

    private class FakeAuthProvider(
        private val signInResult: XcAccountAuthResult = XcAccountAuthResult.Unavailable(
            "Unavailable"
        )
    ) : XcAccountAuthProvider {
        override fun signInCapabilities(): List<XcAccountSignInCapability> {
            return listOf(
                XcAccountSignInCapability(
                    method = XcAccountSignInMethod.CONFIGURED_DEV_TOKEN,
                    title = "Use configured dev account",
                    description = "Dev seam",
                    isAvailable = true
                )
            )
        }

        override suspend fun signIn(method: XcAccountSignInMethod): XcAccountAuthResult {
            return signInResult
        }
    }

    private class FakeGoogleAuthGateway(
        private val signInCapability: XcAccountSignInCapability = XcAccountSignInCapability(
            method = XcAccountSignInMethod.GOOGLE,
            title = "Continue with Google",
            description = "Preferred sign-in",
            isAvailable = true
        ),
        private val signInResult: XcAccountAuthResult = XcAccountAuthResult.Unavailable(
            "Google unavailable"
        ),
        private val restoreResult: XcAccountAuthResult = XcAccountAuthResult.Failure(
            "Google session unavailable"
        )
    ) : XcGoogleAuthGateway {
        override fun signInCapability(): XcAccountSignInCapability {
            return signInCapability
        }

        override suspend fun signInWithGoogleIdToken(
            googleIdToken: String
        ): XcAccountAuthResult = signInResult

        override suspend fun restoreSession(
            session: XcAccountSession
        ): XcAccountAuthResult = restoreResult

        override suspend fun signOut() = Unit
    }

    private suspend fun stubRelationshipLists(
        remoteDataSource: CurrentApiXcAccountDataSource,
        accessToken: String
    ) {
        whenever(remoteDataSource.fetchIncomingFollowRequests(accessToken)).thenReturn(
            XcAccountRemoteResult.Success(emptyList())
        )
        whenever(remoteDataSource.fetchOutgoingFollowRequests(accessToken)).thenReturn(
            XcAccountRemoteResult.Success(emptyList())
        )
    }

    private fun testScope(testScheduler: TestCoroutineScheduler): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
}
