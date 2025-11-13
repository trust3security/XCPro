package com.example.xcpro.replay

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class IgcReplayViewModel @Inject constructor(
    private val replayController: IgcReplayController
) : ViewModel() {

    private val _uiState = MutableStateFlow(IgcReplayUiState())
    val uiState: StateFlow<IgcReplayUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<IgcReplayUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<IgcReplayUiEvent> = _events.asSharedFlow()

    init {
        observeSession()
        observeReplayEvents()
    }

    fun onFileSelected(uri: Uri?, displayName: String?) {
        _uiState.update {
            it.copy(
                selectedUri = uri,
                selectedFileName = displayName ?: uri?.lastPathSegment ?: it.selectedFileName,
                errorMessage = null,
                statusMessage = if (uri == null) "Select an IGC file to begin replay." else "Ready to load ${displayName ?: "selected file"}."
            )
        }
    }

    fun setSpeed(multiplier: Double) {
        replayController.setSpeed(multiplier)
    }

    fun startReplay() {
        val uri = _uiState.value.selectedUri ?: run {
            _uiState.update { it.copy(errorMessage = "Please select an IGC file first.") }
            return
        }
        val name = _uiState.value.selectedFileName
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(statusMessage = "Loading replay...", errorMessage = null) }
                replayController.loadFile(uri, name)
                replayController.play()
                _events.emit(IgcReplayUiEvent.NavigateBackToMap)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        errorMessage = t.message ?: "Replay failed",
                        statusMessage = "Replay failed."
                    )
                }
            }
        }
    }

    fun stopReplay() {
        replayController.stop()
    }

    private fun observeSession() {
        viewModelScope.launch {
            replayController.session.collect { session ->
                val selection = session.selection
                _uiState.update { current ->
                    val base = current.copy(
                        isPlaying = session.status == IgcReplayController.SessionStatus.PLAYING,
                        speedMultiplier = session.speedMultiplier,
                        isReplayLoaded = selection != null,
                        statusMessage = when {
                            selection == null -> "Select an IGC file to begin replay."
                            session.status == IgcReplayController.SessionStatus.PLAYING ->
                                "Replaying… use the map controls to pause or scrub."
                            else -> "Replay loaded. Control playback from the map overlay."
                        },
                        errorMessage = current.errorMessage
                    )
                    if (selection != null) {
                        base.copy(
                            selectedUri = selection.uri,
                            selectedFileName = selection.displayName ?: selection.uri.lastPathSegment ?: base.selectedFileName
                        )
                    } else base
                }
            }
        }
    }

    private fun observeReplayEvents() {
        viewModelScope.launch {
            replayController.events.collect { event ->
                when (event) {
                    is IgcReplayController.ReplayEvent.Completed -> _uiState.update {
                        it.copy(
                            statusMessage = "Replay finished (${event.samples} samples).",
                            errorMessage = null,
                            isPlaying = false
                        )
                    }
                    is IgcReplayController.ReplayEvent.Failed -> _uiState.update {
                        it.copy(
                            statusMessage = "Replay stopped due to error.",
                            errorMessage = event.throwable.message ?: "Replay failed",
                            isPlaying = false
                        )
                    }
                    IgcReplayController.ReplayEvent.Cancelled -> _uiState.update {
                        it.copy(
                            statusMessage = "Replay stopped.",
                            isPlaying = false
                        )
                    }
                }
            }
        }
    }

    sealed interface IgcReplayUiEvent {
        object NavigateBackToMap : IgcReplayUiEvent
    }
}
