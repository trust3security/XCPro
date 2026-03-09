package com.example.xcpro.igc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.igc.data.IgcLogEntry
import com.example.xcpro.igc.usecase.IgcFilesSort
import com.example.xcpro.igc.usecase.IgcFilesUseCase
import com.example.xcpro.igc.usecase.IgcReplayLauncher
import com.example.xcpro.igc.usecase.IgcShareMode
import com.example.xcpro.igc.usecase.IgcShareRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IgcFilesUiState(
    val allEntries: List<IgcLogEntry> = emptyList(),
    val visibleEntries: List<IgcLogEntry> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    val sort: IgcFilesSort = IgcFilesSort.DATE_DESC,
    val errorMessage: String? = null
)

sealed interface IgcFilesEvent {
    data class ShowMessage(val message: String) : IgcFilesEvent
    data class Share(val request: IgcShareRequest) : IgcFilesEvent
    data class LaunchCopyTo(val suggestedFileName: String) : IgcFilesEvent
    data class CopyMetadata(val text: String) : IgcFilesEvent
    object NavigateBackToMap : IgcFilesEvent
}

@HiltViewModel
class IgcFilesViewModel @Inject constructor(
    private val useCase: IgcFilesUseCase,
    private val replayLauncher: IgcReplayLauncher
) : ViewModel() {

    private val _uiState = MutableStateFlow(IgcFilesUiState())
    val uiState: StateFlow<IgcFilesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<IgcFilesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<IgcFilesEvent> = _events.asSharedFlow()

    private var pendingCopyEntry: IgcLogEntry? = null

    init {
        observeRepository()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                useCase.refresh()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Failed to load IGC files"
                    )
                }
            }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = useCase.applyFilterAndSort(
                allEntries = state.allEntries,
                query = query,
                sort = state.sort
            )
            state.copy(query = query, visibleEntries = filtered)
        }
    }

    fun onSortChanged(sort: IgcFilesSort) {
        _uiState.update { state ->
            val filtered = useCase.applyFilterAndSort(
                allEntries = state.allEntries,
                query = state.query,
                sort = sort
            )
            state.copy(sort = sort, visibleEntries = filtered)
        }
    }

    fun share(entry: IgcLogEntry, mode: IgcShareMode) {
        val request = useCase.buildShareRequest(entry, mode)
        _events.tryEmit(IgcFilesEvent.Share(request))
    }

    fun copyMetadata(entry: IgcLogEntry) {
        _events.tryEmit(IgcFilesEvent.CopyMetadata(useCase.buildMetadataText(entry)))
    }

    fun copyTo(entry: IgcLogEntry) {
        pendingCopyEntry = entry
        _events.tryEmit(IgcFilesEvent.LaunchCopyTo(entry.displayName))
    }

    fun onCopyDestinationSelected(destinationUri: String?) {
        val entry = pendingCopyEntry
        pendingCopyEntry = null
        if (destinationUri == null || entry == null) {
            return
        }
        viewModelScope.launch {
            val result = useCase.copyToDestination(entry, destinationUri)
            if (result.isSuccess) {
                _events.emit(IgcFilesEvent.ShowMessage("Copied ${entry.displayName}"))
            } else {
                _events.emit(
                    IgcFilesEvent.ShowMessage(
                        "Copy failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                    )
                )
            }
        }
    }

    fun replayOpen(entry: IgcLogEntry) {
        viewModelScope.launch {
            runCatching {
                replayLauncher.loadDocument(entry.document)
                replayLauncher.play()
            }.onSuccess {
                _events.emit(IgcFilesEvent.NavigateBackToMap)
            }.onFailure { throwable ->
                _events.emit(
                    IgcFilesEvent.ShowMessage(
                        throwable.message ?: "Replay open failed"
                    )
                )
            }
        }
    }

    private fun observeRepository() {
        viewModelScope.launch {
            useCase.entries.collectLatest { entries ->
                _uiState.update { state ->
                    val filtered = useCase.applyFilterAndSort(
                        allEntries = entries,
                        query = state.query,
                        sort = state.sort
                    )
                    state.copy(
                        isLoading = false,
                        allEntries = entries,
                        visibleEntries = filtered
                    )
                }
            }
        }
    }
}
