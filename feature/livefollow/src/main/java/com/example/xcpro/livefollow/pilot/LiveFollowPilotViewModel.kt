package com.example.xcpro.livefollow.pilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.data.session.LiveFollowSessionVisibility
import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LiveFollowPilotViewModel @Inject constructor(
    private val useCase: LiveFollowPilotUseCase
) : ViewModel() {
    private val actionState = MutableStateFlow(LiveFollowPilotActionState())
    private val mutableUiState = MutableStateFlow(LiveFollowPilotUiState())
    private val mutableEvents = MutableSharedFlow<LiveFollowPilotEvent>(extraBufferCapacity = 1)
    private var autoStartPending = false

    val uiState: StateFlow<LiveFollowPilotUiState> = mutableUiState.asStateFlow()
    val events: SharedFlow<LiveFollowPilotEvent> = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                useCase.sessionState,
                useCase.ownshipSnapshot,
                useCase.accountState,
                actionState
            ) { session, ownshipSnapshot, accountSnapshot, currentActionState ->
                LiveFollowPilotRenderFrame(
                    state = buildLiveFollowPilotUiState(
                        session = session,
                        ownshipSnapshot = ownshipSnapshot,
                        accountSnapshot = accountSnapshot,
                        actionState = currentActionState
                    ),
                    session = session,
                    ownshipSnapshot = ownshipSnapshot,
                    isSignedIn = accountSnapshot.isSignedIn,
                    actionState = currentActionState
                )
            }.collectLatest { frame ->
                mutableUiState.value = frame.state
                maybeRunAutoStart(
                    session = frame.session,
                    ownshipSnapshot = frame.ownshipSnapshot,
                    isSignedIn = frame.isSignedIn,
                    currentActionState = frame.actionState
                )
            }
        }
    }

    fun startSharing() {
        if (actionState.value.isBusy) return
        viewModelScope.launch {
            runCommand(
                pendingCommand = LiveFollowPilotPendingCommand.START_SHARING,
                command = {
                    useCase.startSharing(
                        visibility = mutableUiState.value.selectedVisibility
                    )
                }
            )
        }
    }

    fun selectVisibility(visibility: LiveFollowSessionVisibility) {
        if (!visibilitySelectionEnabled(visibility, mutableUiState.value)) return
        actionState.update { state ->
            state.copy(
                manualVisibilitySelection = visibility,
                commandMessage = null,
                lastShareCommandFailed = false
            )
        }
    }

    fun updateVisibility() {
        if (actionState.value.isBusy || !mutableUiState.value.canUpdateVisibility) return
        viewModelScope.launch {
            runCommand(
                pendingCommand = LiveFollowPilotPendingCommand.UPDATE_VISIBILITY,
                command = {
                    useCase.updateSharingVisibility(
                        visibility = mutableUiState.value.selectedVisibility
                    )
                }
            )
        }
    }

    fun stopSharing() {
        if (actionState.value.isBusy) return
        viewModelScope.launch {
            runCommand(
                pendingCommand = LiveFollowPilotPendingCommand.STOP_SHARING,
                command = useCase::stopSharing
            )
        }
    }

    fun autoStartSharingWhenReady() {
        if (actionState.value.isBusy) return
        val session = useCase.sessionState.value
        if (!canAutoStartFrom(session)) return
        val selectedVisibility = mutableUiState.value.selectedVisibility
        if (!useCase.accountState.value.isSignedIn &&
            selectedVisibility != LiveFollowSessionVisibility.PUBLIC
        ) {
            return
        }
        if (useCase.ownshipSnapshot.value == null) {
            autoStartPending = true
            return
        }
        viewModelScope.launch {
            runCommand(
                pendingCommand = LiveFollowPilotPendingCommand.START_SHARING,
                command = {
                    useCase.startSharing(
                        visibility = mutableUiState.value.selectedVisibility
                    )
                }
            )
        }
    }

    fun copyShareCode() {
        val shareCode = mutableUiState.value.shareCode ?: return
        actionState.update { state ->
            state.copy(
                commandMessage = "Share code copied.",
                lastShareCommandFailed = false
            )
        }
        mutableEvents.tryEmit(LiveFollowPilotEvent.CopyShareCode(shareCode))
    }

    private fun maybeRunAutoStart(
        session: LiveFollowSessionSnapshot,
        ownshipSnapshot: LiveOwnshipSnapshot?,
        isSignedIn: Boolean,
        currentActionState: LiveFollowPilotActionState
    ) {
        if (!autoStartPending || currentActionState.isBusy) return
        if (!canAutoStartFrom(session)) {
            autoStartPending = false
            return
        }
        if (ownshipSnapshot == null) return
        val selectedVisibility = currentActionState.manualVisibilitySelection
            ?: mutableUiState.value.selectedVisibility
        if (!isSignedIn && selectedVisibility != LiveFollowSessionVisibility.PUBLIC) {
            autoStartPending = false
            return
        }
        autoStartPending = false
        viewModelScope.launch {
            runCommand(
                pendingCommand = LiveFollowPilotPendingCommand.START_SHARING,
                command = {
                    useCase.startSharing(
                        visibility = mutableUiState.value.selectedVisibility
                    )
                }
            )
        }
    }

    private suspend fun runCommand(
        pendingCommand: LiveFollowPilotPendingCommand,
        command: suspend () -> LiveFollowCommandResult
    ) {
        actionState.update { state ->
            state.copy(
                isBusy = true,
                commandMessage = null,
                pendingCommand = pendingCommand,
                lastShareCommandFailed = false
            )
        }
        val result = command()
        actionState.update { state ->
            state.copy(
                isBusy = false,
                commandMessage = liveFollowPilotCommandMessage(result),
                pendingCommand = null,
                lastShareCommandFailed = result is LiveFollowCommandResult.Failure ||
                    result is LiveFollowCommandResult.Rejected
            )
        }
    }

    private fun canAutoStartFrom(
        session: LiveFollowSessionSnapshot
    ): Boolean {
        return session.sideEffectsAllowed &&
            session.transportAvailability.isAvailable &&
            session.role == LiveFollowSessionRole.NONE &&
            session.lifecycle == LiveFollowSessionLifecycle.IDLE
    }
}

private data class LiveFollowPilotRenderFrame(
    val state: LiveFollowPilotUiState,
    val session: LiveFollowSessionSnapshot,
    val ownshipSnapshot: LiveOwnshipSnapshot?,
    val isSignedIn: Boolean,
    val actionState: LiveFollowPilotActionState
)

sealed interface LiveFollowPilotEvent {
    data class CopyShareCode(
        val shareCode: String
    ) : LiveFollowPilotEvent
}

private fun visibilitySelectionEnabled(
    visibility: LiveFollowSessionVisibility,
    uiState: LiveFollowPilotUiState
): Boolean {
    return when (visibility) {
        LiveFollowSessionVisibility.PUBLIC -> true
        LiveFollowSessionVisibility.FOLLOWERS,
        LiveFollowSessionVisibility.OFF -> uiState.canUsePrivateVisibility
    }
}
