package com.example.xcpro.livefollow.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class XcAccountViewModel @Inject constructor(
    private val useCase: XcAccountUseCase
) : ViewModel() {
    private val editorState = MutableStateFlow(XcAccountEditorState())
    private val relationshipState = MutableStateFlow(XcAccountRelationshipState())
    private val operationState = MutableStateFlow(XcAccountOperationState())
    private val mutableUiState = MutableStateFlow(XcAccountUiState())
    private var syncedRemoteFingerprint: String? = null

    val uiState: StateFlow<XcAccountUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            useCase.state.collectLatest(::syncDraftsFromSnapshot)
        }
        viewModelScope.launch {
            combine(
                useCase.state,
                editorState,
                relationshipState,
                operationState
            ) { snapshot, editor, relationships, operation ->
                buildUiState(snapshot, editor, relationships, operation)
            }.collectLatest { state ->
                mutableUiState.value = state
            }
        }
    }

    fun onHandleChanged(value: String) {
        editorState.update { it.copy(handle = value) }
    }

    fun onDisplayNameChanged(value: String) {
        editorState.update { it.copy(displayName = value) }
    }

    fun onCompNumberChanged(value: String) {
        editorState.update { it.copy(compNumber = value) }
    }

    fun onDiscoverabilitySelected(value: XcDiscoverability) {
        editorState.update { it.copy(privacy = it.privacy.copy(discoverability = value)) }
    }

    fun onFollowPolicySelected(value: XcFollowPolicy) {
        editorState.update { it.copy(privacy = it.privacy.copy(followPolicy = value)) }
    }

    fun onDefaultLiveVisibilitySelected(value: XcDefaultLiveVisibility) {
        editorState.update { it.copy(privacy = it.privacy.copy(defaultLiveVisibility = value)) }
    }

    fun onConnectionListVisibilitySelected(value: XcConnectionListVisibility) {
        editorState.update { it.copy(privacy = it.privacy.copy(connectionListVisibility = value)) }
    }

    fun onSearchQueryChanged(value: String) {
        relationshipState.update { it.copy(searchQuery = value) }
    }

    fun dismissStatus() {
        operationState.update { it.copy(statusMessage = null) }
    }

    fun refresh() {
        if (!useCase.state.value.isSignedIn || operationState.value.isBusy) return
        viewModelScope.launch {
            operationState.update {
                it.copy(
                    isRefreshing = true,
                    statusMessage = null
                )
            }
            val result = useCase.refresh()
            operationState.update {
                it.copy(
                    isRefreshing = false,
                    statusMessage = result.failureMessage()
                )
            }
        }
    }

    fun signIn(method: XcAccountSignInMethod) {
        if (operationState.value.isBusy) return
        viewModelScope.launch {
            operationState.update {
                it.copy(
                    isSigningIn = true,
                    statusMessage = null
                )
            }
            val result = useCase.signIn(method)
            val successMessage = if (result is XcAccountActionResult.Success) {
                if (useCase.state.value.needsProfileCompletion) {
                    "Signed in. Finish your pilot profile to complete onboarding."
                } else {
                    "Signed in."
                }
            } else {
                result.failureMessage()
            }
            operationState.update {
                it.copy(
                    isSigningIn = false,
                    statusMessage = successMessage
                )
            }
        }
    }

    fun signInWithGoogleIdToken(googleIdToken: String) {
        if (operationState.value.isBusy) return
        viewModelScope.launch {
            operationState.update {
                it.copy(
                    isSigningIn = true,
                    statusMessage = null
                )
            }
            val result = useCase.signInWithGoogleIdToken(googleIdToken)
            val successMessage = if (result is XcAccountActionResult.Success) {
                if (useCase.state.value.needsProfileCompletion) {
                    "Signed in. Finish your pilot profile to complete onboarding."
                } else {
                    "Signed in."
                }
            } else {
                result.failureMessage()
            }
            operationState.update {
                it.copy(
                    isSigningIn = false,
                    statusMessage = successMessage
                )
            }
        }
    }

    fun showStatusMessage(message: String) {
        if (operationState.value.isBusy) return
        operationState.update {
            it.copy(statusMessage = message)
        }
    }

    fun signOut() {
        if (operationState.value.isBusy) return
        viewModelScope.launch {
            useCase.signOut()
            syncedRemoteFingerprint = null
            editorState.value = XcAccountEditorState()
            operationState.update {
                it.copy(
                    isRefreshing = false,
                    isSavingPrivacy = false,
                    isSavingProfile = false,
                    isSearchingUsers = false,
                    isUpdatingRelationships = false,
                    isSigningIn = false,
                    statusMessage = "Signed out."
                )
            }
        }
    }

    fun saveProfile() {
        if (operationState.value.isBusy) return
        viewModelScope.launch {
            operationState.update {
                it.copy(
                    isSavingProfile = true,
                    statusMessage = null
                )
            }
            val editor = editorState.value
            val result = useCase.saveProfile(
                handle = editor.handle,
                displayName = editor.displayName,
                compNumber = editor.compNumber
            )
            operationState.update {
                it.copy(
                    isSavingProfile = false,
                    statusMessage = if (result is XcAccountActionResult.Success) {
                        "Pilot profile saved."
                    } else {
                        result.failureMessage()
                    }
                )
            }
        }
    }

    fun savePrivacy() {
        if (operationState.value.isBusy) return
        viewModelScope.launch {
            operationState.update {
                it.copy(
                    isSavingPrivacy = true,
                    statusMessage = null
                )
            }
            val result = useCase.savePrivacy(
                XcPrivacyUpdateRequest(
                    discoverability = editorState.value.privacy.discoverability,
                    followPolicy = editorState.value.privacy.followPolicy,
                    defaultLiveVisibility = editorState.value.privacy.defaultLiveVisibility,
                    connectionListVisibility = editorState.value.privacy.connectionListVisibility
                )
            )
            operationState.update {
                it.copy(
                    isSavingPrivacy = false,
                    statusMessage = if (result is XcAccountActionResult.Success) {
                        "Privacy settings saved."
                    } else {
                        result.failureMessage()
                    }
                )
            }
        }
    }

    fun searchUsers() {
        val query = relationshipState.value.searchQuery.trim()
        if (query.length < 2 || operationState.value.isBusy) return
        viewModelScope.launch {
            operationState.update {
                it.copy(
                    isSearchingUsers = true,
                    statusMessage = null
                )
            }
            when (val result = useCase.searchUsers(query)) {
                is XcAccountValueResult.Success -> {
                    relationshipState.update {
                        it.copy(
                            searchResults = result.value,
                            hasSearchedUsers = true
                        )
                    }
                    operationState.update {
                        it.copy(
                            isSearchingUsers = false,
                            statusMessage = null
                        )
                    }
                }

                is XcAccountValueResult.Failure -> {
                    operationState.update {
                        it.copy(
                            isSearchingUsers = false,
                            statusMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun sendFollowRequest(targetUserId: String) {
        if (operationState.value.isBusy) return
        viewModelScope.launch {
            runRelationshipMutation(
                successMessage = "Follow request sent."
            ) {
                useCase.sendFollowRequest(targetUserId)
            }
        }
    }

    fun acceptFollowRequest(requestId: String) {
        if (operationState.value.isBusy) return
        viewModelScope.launch {
            runRelationshipMutation(
                successMessage = "Follow request accepted."
            ) {
                useCase.acceptFollowRequest(requestId)
            }
        }
    }

    fun declineFollowRequest(requestId: String) {
        if (operationState.value.isBusy) return
        viewModelScope.launch {
            runRelationshipMutation(
                successMessage = "Follow request declined."
            ) {
                useCase.declineFollowRequest(requestId)
            }
        }
    }

    private fun syncDraftsFromSnapshot(snapshot: XcAccountSnapshot) {
        val profile = snapshot.profile
        val privacy = snapshot.privacy
        if (profile == null || privacy == null) {
            if (!snapshot.isSignedIn && syncedRemoteFingerprint != null) {
                syncedRemoteFingerprint = null
                editorState.value = XcAccountEditorState()
                relationshipState.value = XcAccountRelationshipState()
            }
            return
        }
        val fingerprint = buildRemoteFingerprint(profile, privacy)
        if (fingerprint == syncedRemoteFingerprint) return

        syncedRemoteFingerprint = fingerprint
        editorState.value = XcAccountEditorState(
            handle = profile.handle.orEmpty(),
            displayName = profile.displayName.orEmpty(),
            compNumber = profile.compNumber.orEmpty(),
            privacy = privacy
        )
    }

    private fun buildUiState(
        snapshot: XcAccountSnapshot,
        editor: XcAccountEditorState,
        relationships: XcAccountRelationshipState,
        operation: XcAccountOperationState
    ): XcAccountUiState {
        val profile = snapshot.profile
        val privacy = snapshot.privacy
        val normalizedHandle = normalizeXcHandleCandidate(editor.handle)
        val normalizedDisplayName = normalizeXcDisplayNameCandidate(editor.displayName)
        val normalizedCompNumber = normalizeXcCompNumberCandidate(editor.compNumber)
        val profileDirty = profile == null ||
            profile.handle != normalizedHandle ||
            profile.displayName != normalizedDisplayName ||
            profile.compNumber != normalizedCompNumber
        val privacyDirty = privacy == null || privacy != editor.privacy
        return XcAccountUiState(
            isLoading = snapshot.isLoading || operation.isRefreshing,
            isSignedIn = snapshot.isSignedIn,
            needsProfileCompletion = snapshot.needsProfileCompletion,
            userId = profile?.userId,
            authMethodLabel = snapshot.session?.authMethod?.label,
            handle = editor.handle,
            displayName = editor.displayName,
            compNumber = editor.compNumber,
            privacy = editor.privacy,
            searchQuery = relationships.searchQuery,
            searchResults = relationships.searchResults,
            hasSearchedUsers = relationships.hasSearchedUsers,
            incomingFollowRequests = snapshot.incomingFollowRequests,
            outgoingFollowRequests = snapshot.outgoingFollowRequests,
            signInCapabilities = snapshot.signInCapabilities,
            isSigningIn = operation.isSigningIn,
            isSavingProfile = operation.isSavingProfile,
            isSavingPrivacy = operation.isSavingPrivacy,
            isSearchingUsers = operation.isSearchingUsers,
            isUpdatingRelationships = operation.isUpdatingRelationships,
            profileSaveEnabled = snapshot.isSignedIn &&
                !snapshot.isLoading &&
                !operation.isBusy &&
                normalizedHandle != null &&
                normalizedDisplayName != null &&
                profileDirty,
            privacySaveEnabled = snapshot.isSignedIn &&
                privacy != null &&
                !snapshot.isLoading &&
                !operation.isBusy &&
                privacyDirty,
            searchEnabled = snapshot.isSignedIn &&
                !snapshot.isLoading &&
                !operation.isBusy &&
                relationships.searchQuery.trim().length >= 2,
            canSendFollowRequests = snapshot.isSignedIn &&
                !snapshot.needsProfileCompletion &&
                !snapshot.isLoading &&
                !operation.isBusy,
            statusMessage = operation.statusMessage,
            errorMessage = snapshot.errorMessage
        )
    }

    private fun buildRemoteFingerprint(
        profile: XcPilotProfile,
        privacy: XcPrivacySettings
    ): String {
        return listOf(
            profile.userId,
            profile.handle.orEmpty(),
            profile.displayName.orEmpty(),
            profile.compNumber.orEmpty(),
            privacy.discoverability.wireValue,
            privacy.followPolicy.wireValue,
            privacy.defaultLiveVisibility.wireValue,
            privacy.connectionListVisibility.wireValue
        ).joinToString("|")
    }

    private suspend fun runRelationshipMutation(
        successMessage: String,
        action: suspend () -> XcAccountActionResult
    ) {
        operationState.update {
            it.copy(
                isUpdatingRelationships = true,
                statusMessage = null
            )
        }
        val result = action()
        if (result is XcAccountActionResult.Success) {
            refreshSearchResults()
        }
        operationState.update {
            it.copy(
                isUpdatingRelationships = false,
                statusMessage = if (result is XcAccountActionResult.Success) {
                    successMessage
                } else {
                    result.failureMessage()
                }
            )
        }
    }

    private suspend fun refreshSearchResults() {
        val query = relationshipState.value.searchQuery.trim()
        if (query.length < 2) {
            relationshipState.update {
                it.copy(
                    searchResults = emptyList(),
                    hasSearchedUsers = false
                )
            }
            return
        }
        when (val result = useCase.searchUsers(query)) {
            is XcAccountValueResult.Success -> {
                relationshipState.update {
                    it.copy(
                        searchResults = result.value,
                        hasSearchedUsers = true
                    )
                }
            }

            is XcAccountValueResult.Failure -> {
                operationState.update { it.copy(statusMessage = result.message) }
            }
        }
    }
}
