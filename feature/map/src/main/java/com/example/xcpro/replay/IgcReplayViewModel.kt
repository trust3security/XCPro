package com.example.xcpro.replay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.common.documents.DocumentRef
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
    private val replayUseCase: IgcReplayUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(IgcReplayUiState())
    val uiState: StateFlow<IgcReplayUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<IgcReplayUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<IgcReplayUiEvent> = _events.asSharedFlow()

    init {
        observeSession()
        observeReplayEvents()
    }

    fun onFileSelected(document: DocumentRef?) {
        if (document == null) {
            _uiState.update {
                it.copy(
                    selectedDocument = null,
                    selectedFileName = null,
                    errorMessage = null,
                    statusMessage = "Select an IGC file to begin replay.",
                    isReplayLoaded = false,
                    elapsedMillis = 0L,
                    durationMillis = 0L,
                    progressFraction = 0f
                )
            }
            return
        }
        val name = document.fileName()
        _uiState.update {
            it.copy(
                selectedDocument = document,
                selectedFileName = name,
                errorMessage = null,
                statusMessage = "Loading replay...",
                isReplayLoaded = false,
                progressFraction = 0f,
                elapsedMillis = 0L,
                durationMillis = 0L
            )
        }
        viewModelScope.launch {
            runCatching {
                replayUseCase.loadDocument(document)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        statusMessage = "Replay loaded. Ready to scrub or start.",
                        errorMessage = null,
                        isReplayLoaded = true
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        errorMessage = t.message ?: "Replay load failed",
                        statusMessage = "Replay failed.",
                        isReplayLoaded = false
                    )
                }
            }
        }
    }

    fun setSpeed(multiplier: Double) {
        replayUseCase.setSpeed(multiplier)
    }

    fun startReplay() {
        val document = _uiState.value.selectedDocument ?: run {
            _uiState.update { it.copy(errorMessage = "Please select an IGC file first.") }
            return
        }
        val name = _uiState.value.selectedFileName
        viewModelScope.launch {
            try {
                // If already loaded the same file, skip reload
                val alreadyLoaded = replayUseCase.session.value.selection?.document?.uri == document.uri
                if (!alreadyLoaded) {
                    _uiState.update { it.copy(statusMessage = "Loading replay...", errorMessage = null) }
                    replayUseCase.loadDocument(document)
                }
                replayUseCase.play()
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
        replayUseCase.stop()
    }

    fun seekTo(fraction: Float) {
        val selection = replayUseCase.session.value.selection
        val document = _uiState.value.selectedDocument
        if (selection != null) {
            replayUseCase.seekTo(fraction)
        } else if (document != null) {
            viewModelScope.launch {
                runCatching { replayUseCase.loadDocument(document) }
                    .onSuccess { replayUseCase.seekTo(fraction) }
            }
        }
    }

    private fun observeSession() {
        viewModelScope.launch {
            replayUseCase.session.collect { session ->
                val selection = session.selection
                _uiState.update { current ->
                    val base = current.copy(
                        isPlaying = session.status == SessionStatus.PLAYING,
                        speedMultiplier = session.speedMultiplier,
                        isReplayLoaded = selection != null, elapsedMillis = session.elapsedMillis, durationMillis = session.durationMillis, progressFraction = session.progressFraction,
                        statusMessage = when {
                            selection == null -> "Select an IGC file to begin replay."
                            session.status == SessionStatus.PLAYING ->
                                "Replaying use the map controls to pause or scrub."
                            else -> "Replay loaded. Control playback from the map overlay."
                        },
                        errorMessage = current.errorMessage
                    )
                    if (selection != null) {
                        base.copy(
                            selectedDocument = selection.document,
                            selectedFileName = selection.document.fileName()
                        )
                    } else base
                }
            }
        }
    }

    private fun observeReplayEvents() {
        viewModelScope.launch {
            replayUseCase.events.collect { event ->
                when (event) {
                    is ReplayEvent.Completed -> _uiState.update {
                        it.copy(
                            statusMessage = "Replay finished (${event.samples} samples).",
                            errorMessage = null,
                            isPlaying = false
                        )
                    }
                    is ReplayEvent.Failed -> _uiState.update {
                        it.copy(
                            statusMessage = "Replay stopped due to error.",
                            errorMessage = event.throwable.message ?: "Replay failed",
                            isPlaying = false
                        )
                    }
                    ReplayEvent.Cancelled -> _uiState.update {
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
