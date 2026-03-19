package com.example.xcpro.livefollow.pilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.livefollow.data.session.LiveFollowCommandResult
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
class LiveFollowPilotViewModel @Inject constructor(
    private val useCase: LiveFollowPilotUseCase
) : ViewModel() {
    private val actionState = MutableStateFlow(LiveFollowPilotActionState())
    private val mutableUiState = MutableStateFlow(LiveFollowPilotUiState())

    val uiState: StateFlow<LiveFollowPilotUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                useCase.sessionState,
                useCase.ownshipSnapshot,
                actionState
            ) { session, ownshipSnapshot, currentActionState ->
                buildLiveFollowPilotUiState(
                    session = session,
                    ownshipSnapshot = ownshipSnapshot,
                    actionState = currentActionState
                )
            }.collectLatest { state ->
                mutableUiState.value = state
            }
        }
    }

    fun startSharing() {
        if (actionState.value.isBusy) return
        viewModelScope.launch {
            runCommand(useCase::startSharing)
        }
    }

    fun stopSharing() {
        if (actionState.value.isBusy) return
        viewModelScope.launch {
            runCommand(useCase::stopSharing)
        }
    }

    private suspend fun runCommand(
        command: suspend () -> LiveFollowCommandResult
    ) {
        actionState.update { state ->
            state.copy(isBusy = true, commandMessage = null)
        }
        val result = command()
        actionState.update { state ->
            state.copy(
                isBusy = false,
                commandMessage = liveFollowPilotCommandMessage(result)
            )
        }
    }
}
