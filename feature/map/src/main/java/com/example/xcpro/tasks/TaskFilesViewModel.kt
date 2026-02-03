package com.example.xcpro.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.tasks.core.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TaskFilesViewModel @Inject constructor(
    private val useCase: TaskFilesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskFilesUiState(isLoading = true))
    val uiState: StateFlow<TaskFilesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TaskFilesEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TaskFilesEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = runCatching { useCase.loadDownloads() }
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(files = result.getOrDefault(emptyList()), isLoading = false, errorMessage = null)
                } else {
                    it.copy(files = emptyList(), isLoading = false, errorMessage = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    fun importTaskFile(document: DocumentRef) {
        viewModelScope.launch {
            when (val result = useCase.importTaskFile(document)) {
                is TaskImportResult.Json -> {
                    _events.emit(TaskFilesEvent.ApplyJson(result.json, result.displayName))
                    _events.emit(TaskFilesEvent.ShowMessage("Imported ${result.displayName}"))
                }
                is TaskImportResult.Cup -> {
                    val message = if (result.success) {
                        "Imported ${result.displayName}"
                    } else {
                        "Import failed for ${result.displayName}"
                    }
                    _events.emit(TaskFilesEvent.ShowMessage(message))
                }
                is TaskImportResult.Failure -> {
                    _events.emit(TaskFilesEvent.ShowMessage(result.message))
                }
            }
        }
    }

    fun shareFile(document: DocumentRef, displayName: String) {
        viewModelScope.launch {
            val request = useCase.buildShareRequest(document, displayName)
            if (request != null) {
                _events.emit(TaskFilesEvent.Share(request))
            } else {
                _events.emit(TaskFilesEvent.ShowMessage("Unable to share $displayName"))
            }
        }
    }

    fun shareDownload(displayName: String) {
        viewModelScope.launch {
            val request = useCase.shareExistingDownload(displayName)
            if (request != null) {
                _events.emit(TaskFilesEvent.Share(request))
            } else {
                _events.emit(TaskFilesEvent.ShowMessage("File not found: $displayName"))
            }
        }
    }

    fun exportTaskToDownloads(task: Task) {
        viewModelScope.launch {
            val result = useCase.exportTaskToDownloads(task)
            if (result.errorMessage != null) {
                _events.emit(TaskFilesEvent.ShowMessage(result.errorMessage))
            } else if (result.savedNames.isNotEmpty()) {
                _events.emit(TaskFilesEvent.ShowMessage("Exported ${result.savedNames.joinToString(", ")}"))
            }
        }
    }

    fun shareTask(task: Task) {
        viewModelScope.launch {
            val requests = useCase.shareTask(task)
            if (requests.isEmpty()) {
                _events.emit(TaskFilesEvent.ShowMessage("Share failed"))
            } else {
                requests.forEach { request ->
                    _events.emit(TaskFilesEvent.Share(request))
                }
            }
        }
    }
}
