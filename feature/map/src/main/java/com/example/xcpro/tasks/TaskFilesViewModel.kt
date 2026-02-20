package com.example.xcpro.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
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
            val result = try {
                useCase.importTaskFile(document)
            } catch (throwable: Throwable) {
                _events.emit(TaskFilesEvent.ShowMessage("Import failed: ${throwable.message ?: "unknown error"}"))
                return@launch
            }
            when (result) {
                is TaskImportResult.Json -> {
                    _events.emit(TaskFilesEvent.ApplyJson(result.json, result.displayName))
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
            val request = runCatching { useCase.buildShareRequest(document, displayName) }.getOrNull()
            if (request != null) {
                _events.emit(TaskFilesEvent.Share(request))
            } else {
                _events.emit(TaskFilesEvent.ShowMessage("Unable to share $displayName"))
            }
        }
    }

    fun shareDownload(displayName: String) {
        viewModelScope.launch {
            val request = try {
                useCase.shareExistingDownload(displayName)
            } catch (_: Throwable) {
                null
            }
            if (request != null) {
                _events.emit(TaskFilesEvent.Share(request))
            } else {
                _events.emit(TaskFilesEvent.ShowMessage("File not found: $displayName"))
            }
        }
    }

    fun exportTaskToDownloads(task: Task) {
        viewModelScope.launch {
            runExport { useCase.exportTaskToDownloads(task) }
        }
    }

    fun exportTaskToDownloads(
        task: Task,
        taskType: TaskType,
        targets: List<TaskTargetSnapshot>
    ) {
        viewModelScope.launch {
            runExport {
                useCase.exportTaskToDownloads(
                    task = task,
                    taskType = taskType,
                    targets = targets
                )
            }
        }
    }

    private suspend fun runExport(block: suspend () -> TaskExportResult) {
        val result = try {
            block()
        } catch (throwable: Throwable) {
            _events.emit(TaskFilesEvent.ShowMessage("Export failed: ${throwable.message ?: "unknown error"}"))
            return
        }
        if (result.errorMessage != null) {
            _events.emit(TaskFilesEvent.ShowMessage(result.errorMessage))
        } else if (result.savedNames.isNotEmpty()) {
            _events.emit(TaskFilesEvent.ShowMessage("Exported ${result.savedNames.joinToString(", ")}"))
        }
    }

    fun shareTask(task: Task) {
        viewModelScope.launch {
            runShare { useCase.shareTask(task) }
        }
    }

    fun shareTask(
        task: Task,
        taskType: TaskType,
        targets: List<TaskTargetSnapshot>
    ) {
        viewModelScope.launch {
            runShare {
                useCase.shareTask(
                    task = task,
                    taskType = taskType,
                    targets = targets
                )
            }
        }
    }

    private suspend fun runShare(block: suspend () -> ShareRequest?) {
        val request = try {
            block()
        } catch (throwable: Throwable) {
            _events.emit(TaskFilesEvent.ShowMessage("Share failed: ${throwable.message ?: "unknown error"}"))
            return
        }
        if (request == null) {
            _events.emit(TaskFilesEvent.ShowMessage("Share failed"))
        } else {
            _events.emit(TaskFilesEvent.Share(request))
        }
    }
}
