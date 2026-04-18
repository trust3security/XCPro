package com.trust3.xcpro.igc.ui

import android.content.ActivityNotFoundException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.igc.data.IgcExportDiagnostic
import com.trust3.xcpro.igc.data.IgcExportDiagnosticCode
import com.trust3.xcpro.igc.data.IgcExportDiagnosticSource
import com.trust3.xcpro.igc.data.IgcLogEntry
import com.trust3.xcpro.igc.usecase.IgcFilesSort
import com.trust3.xcpro.igc.usecase.IgcFilesUseCase
import com.trust3.xcpro.igc.usecase.IgcReplayLauncher
import com.trust3.xcpro.igc.usecase.IgcShareMode
import com.trust3.xcpro.igc.usecase.IgcShareRequest
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
    val errorMessage: String? = null,
    val latestDiagnostic: IgcExportDiagnostic? = null
)

sealed interface IgcFilesEvent {
    data class ShowMessage(val message: String) : IgcFilesEvent
    data class Share(val request: IgcShareRequest, val displayName: String) : IgcFilesEvent
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
        observeDiagnostics()
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
        useCase.clearLatestDiagnostic()
        val request = useCase.buildShareRequest(entry, mode)
        _events.tryEmit(IgcFilesEvent.Share(request, entry.displayName))
    }

    fun copyMetadata(entry: IgcLogEntry) {
        _events.tryEmit(IgcFilesEvent.CopyMetadata(useCase.buildMetadataText(entry)))
    }

    fun copyTo(entry: IgcLogEntry) {
        useCase.clearLatestDiagnostic()
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
                useCase.clearLatestDiagnostic()
                _events.emit(IgcFilesEvent.ShowMessage("Copied ${entry.displayName}"))
            } else {
                val diagnostic = IgcExportDiagnostic(
                    source = IgcExportDiagnosticSource.COPY_TO,
                    code = IgcExportDiagnosticCode.COPY_FAILED,
                    message = "Copy failed: ${result.exceptionOrNull()?.message ?: "unknown error"}",
                    fileName = entry.displayName
                )
                publishDiagnostic(diagnostic)
            }
        }
    }

    fun replayOpen(entry: IgcLogEntry) {
        useCase.clearLatestDiagnostic()
        viewModelScope.launch {
            runCatching {
                replayLauncher.loadDocument(entry.document)
                replayLauncher.play()
            }.onSuccess {
                useCase.clearLatestDiagnostic()
                _events.emit(IgcFilesEvent.NavigateBackToMap)
            }.onFailure { throwable ->
                publishDiagnostic(
                    IgcExportDiagnostic(
                        source = IgcExportDiagnosticSource.REPLAY_OPEN,
                        code = IgcExportDiagnosticCode.REPLAY_OPEN_FAILED,
                        message = throwable.message ?: "Replay open failed",
                        fileName = entry.displayName
                    )
                )
            }
        }
    }

    fun onShareLaunchFailed(displayName: String, error: Throwable?) {
        publishDiagnostic(
            when (error) {
                is ActivityNotFoundException -> IgcExportDiagnostic(
                    source = IgcExportDiagnosticSource.SHARE,
                    code = IgcExportDiagnosticCode.SHARE_FAILED,
                    message = "No app available to share this IGC file",
                    fileName = displayName
                )
                is SecurityException -> IgcExportDiagnostic(
                    source = IgcExportDiagnosticSource.SHARE,
                    code = IgcExportDiagnosticCode.SHARE_FAILED,
                    message = "Permission denied while sharing IGC file",
                    fileName = displayName
                )
                else -> IgcExportDiagnostic(
                    source = IgcExportDiagnosticSource.SHARE,
                    code = IgcExportDiagnosticCode.SHARE_FAILED,
                    message = "Unable to share IGC file",
                    fileName = displayName
                )
            }
        )
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

    private fun observeDiagnostics() {
        viewModelScope.launch {
            useCase.latestDiagnostic.collectLatest { diagnostic ->
                _uiState.update { state ->
                    state.copy(latestDiagnostic = diagnostic)
                }
            }
        }
    }

    private fun publishDiagnostic(diagnostic: IgcExportDiagnostic) {
        useCase.publishDiagnostic(diagnostic)
        _events.tryEmit(IgcFilesEvent.ShowMessage(diagnostic.message))
    }
}
