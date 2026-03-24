package com.example.xcpro.livefollow.account

import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class XcAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun snapshot_syncsDrafts_andEditingEnablesProfileSave() = runTest {
        val useCase: XcAccountUseCase = mock()
        whenever(useCase.state).thenReturn(
            MutableStateFlow(
                XcAccountSnapshot(
                    isLoading = false,
                    session = XcAccountSession(
                        accessToken = "token",
                        authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
                    ),
                    profile = XcPilotProfile(
                        userId = "pilot-1",
                        handle = "pilot123",
                        displayName = "Pilot One",
                        compNumber = null
                    ),
                    privacy = XcPrivacySettings.DEFAULT,
                    signInCapabilities = emptyList()
                )
            )
        )

        val viewModel = XcAccountViewModel(useCase)
        advanceUntilIdle()

        assertEquals("pilot123", viewModel.uiState.value.handle)
        assertEquals("Pilot One", viewModel.uiState.value.displayName)
        assertEquals(false, viewModel.uiState.value.profileSaveEnabled)

        viewModel.onHandleChanged("pilot.next")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.profileSaveEnabled)
    }

    @Test
    fun signInFailure_surfacesStatusMessage() = runTest {
        val useCase: XcAccountUseCase = mock()
        whenever(useCase.state).thenReturn(
            MutableStateFlow(
                XcAccountSnapshot(
                    isLoading = false,
                    signInCapabilities = listOf(
                        XcAccountSignInCapability(
                            method = XcAccountSignInMethod.GOOGLE,
                            title = "Continue with Google",
                            description = "Preferred sign-in",
                            isAvailable = false,
                            availabilityNote = "Google sign-in is not configured in this build."
                        )
                    )
                )
            )
        )
        whenever(useCase.signIn(XcAccountSignInMethod.GOOGLE)).thenReturn(
            XcAccountActionResult.Failure("Google sign-in is not configured in this build.")
        )

        val viewModel = XcAccountViewModel(useCase)
        advanceUntilIdle()
        viewModel.signIn(XcAccountSignInMethod.GOOGLE)
        advanceUntilIdle()

        assertEquals(
            "Google sign-in is not configured in this build.",
            viewModel.uiState.value.statusMessage
        )
    }

    @Test
    fun googleIdTokenSignInFailure_surfacesStatusMessage() = runTest {
        val useCase: XcAccountUseCase = mock()
        whenever(useCase.state).thenReturn(
            MutableStateFlow(
                XcAccountSnapshot(
                    isLoading = false,
                    signInCapabilities = emptyList()
                )
            )
        )
        whenever(useCase.signInWithGoogleIdToken("google-id-token")).thenReturn(
            XcAccountActionResult.Failure("XCPro server did not return a usable bearer token.")
        )

        val viewModel = XcAccountViewModel(useCase)
        advanceUntilIdle()
        viewModel.signInWithGoogleIdToken("google-id-token")
        advanceUntilIdle()

        assertEquals(
            "XCPro server did not return a usable bearer token.",
            viewModel.uiState.value.statusMessage
        )
    }

    @Test
    fun searchUsers_updatesEphemeralResults() = runTest {
        val useCase: XcAccountUseCase = mock()
        whenever(useCase.state).thenReturn(
            MutableStateFlow(
                XcAccountSnapshot(
                    isLoading = false,
                    session = XcAccountSession(
                        accessToken = "token",
                        authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
                    ),
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
        whenever(useCase.searchUsers("pilot")).thenReturn(
            XcAccountValueResult.Success(
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

        val viewModel = XcAccountViewModel(useCase)
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("pilot")
        viewModel.searchUsers()
        advanceUntilIdle()

        assertEquals("pilot", viewModel.uiState.value.searchQuery)
        assertEquals(1, viewModel.uiState.value.searchResults.size)
        assertEquals("pilot.two", viewModel.uiState.value.searchResults.first().handle)
        assertEquals(true, viewModel.uiState.value.hasSearchedUsers)
    }

    @Test
    fun acceptFollowRequest_surfacesSuccessAndKeepsRequestListsFromSnapshot() = runTest {
        val useCase: XcAccountUseCase = mock()
        whenever(useCase.state).thenReturn(
            MutableStateFlow(
                XcAccountSnapshot(
                    isLoading = false,
                    session = XcAccountSession(
                        accessToken = "token",
                        authMethod = XcAccountAuthMethod.CONFIGURED_DEV_TOKEN
                    ),
                    profile = XcPilotProfile(
                        userId = "pilot-1",
                        handle = "pilot.one",
                        displayName = "Pilot One",
                        compNumber = null
                    ),
                    privacy = XcPrivacySettings.DEFAULT,
                    incomingFollowRequests = listOf(
                        XcFollowRequestItem(
                            requestId = "request-1",
                            status = XcFollowRequestStatus.PENDING,
                            direction = XcFollowRequestDirection.INCOMING,
                            counterpart = XcPilotProfile(
                                userId = "pilot-2",
                                handle = "pilot.two",
                                displayName = "Pilot Two",
                                compNumber = null
                            ),
                            relationshipState = XcRelationshipState.INCOMING_PENDING
                        )
                    )
                )
            )
        )
        whenever(useCase.acceptFollowRequest("request-1")).thenReturn(XcAccountActionResult.Success)
        whenever(useCase.searchUsers("pilot")).thenReturn(
            XcAccountValueResult.Success(
                listOf(
                    XcSearchPilot(
                        userId = "pilot-2",
                        handle = "pilot.two",
                        displayName = "Pilot Two",
                        compNumber = null,
                        relationshipState = XcRelationshipState.FOLLOWED_BY
                    )
                )
            )
        )

        val viewModel = XcAccountViewModel(useCase)
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("pilot")
        viewModel.acceptFollowRequest("request-1")
        advanceUntilIdle()

        assertEquals("Follow request accepted.", viewModel.uiState.value.statusMessage)
        assertEquals("pilot", viewModel.uiState.value.searchQuery)
        assertEquals(XcRelationshipState.FOLLOWED_BY, viewModel.uiState.value.searchResults.first().relationshipState)
        assertEquals(1, viewModel.uiState.value.incomingFollowRequests.size)
    }
}
