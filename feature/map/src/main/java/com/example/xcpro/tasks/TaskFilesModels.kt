package com.example.xcpro.tasks

import com.example.xcpro.common.documents.DocumentRef

sealed interface TaskImportResult {
    data class Cup(val displayName: String, val success: Boolean) : TaskImportResult
    data class Json(val displayName: String, val json: String) : TaskImportResult
    data class Failure(val message: String) : TaskImportResult
}

data class TaskExportResult(
    val savedNames: List<String>,
    val errorMessage: String? = null
)

data class ShareRequest(
    val document: DocumentRef,
    val mime: String,
    val subject: String? = null,
    val text: String? = null,
    val chooserTitle: String = "Share Task File"
)

data class TaskFilesUiState(
    val files: List<CupDownloadEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface TaskFilesEvent {
    data class ShowMessage(val message: String) : TaskFilesEvent
    data class ApplyJson(val json: String, val displayName: String) : TaskFilesEvent
    data class Share(val request: ShareRequest) : TaskFilesEvent
}
