package com.example.xcpro.igc.usecase

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.data.IgcExportDiagnostic
import com.example.xcpro.igc.data.IgcExportDiagnosticsRepository
import com.example.xcpro.igc.data.IgcDownloadsRepository
import com.example.xcpro.igc.data.IgcLogEntry
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

enum class IgcFilesSort {
    DATE_DESC,
    NAME_ASC,
    SIZE_DESC,
    DURATION_DESC
}

enum class IgcShareMode {
    SHARE,
    EMAIL,
    UPLOAD
}

data class IgcShareRequest(
    val document: DocumentRef,
    val mime: String,
    val subject: String,
    val text: String,
    val chooserTitle: String
)

class IgcFilesUseCase @Inject constructor(
    private val downloadsRepository: IgcDownloadsRepository,
    private val exportDiagnosticsRepository: IgcExportDiagnosticsRepository
) {
    val entries: StateFlow<List<IgcLogEntry>> = downloadsRepository.entries
    val latestDiagnostic: StateFlow<IgcExportDiagnostic?> = exportDiagnosticsRepository.latest

    suspend fun refresh() = withContext(Dispatchers.IO) {
        downloadsRepository.refreshEntries()
    }

    fun applyFilterAndSort(
        allEntries: List<IgcLogEntry>,
        query: String,
        sort: IgcFilesSort
    ): List<IgcLogEntry> {
        val normalizedQuery = query.trim()
        val filtered = if (normalizedQuery.isBlank()) {
            allEntries
        } else {
            allEntries.filter { entry ->
                entry.displayName.contains(normalizedQuery, ignoreCase = true)
            }
        }
        return when (sort) {
            IgcFilesSort.DATE_DESC -> filtered.sortedByDescending { it.lastModifiedEpochMillis }
            IgcFilesSort.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            IgcFilesSort.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            IgcFilesSort.DURATION_DESC -> filtered.sortedByDescending { it.durationSeconds ?: -1L }
        }
    }

    fun buildShareRequest(entry: IgcLogEntry, mode: IgcShareMode): IgcShareRequest {
        val baseSubject = "IGC flight log: ${entry.displayName}"
        val baseText = "XCPro IGC file ${entry.displayName}"
        val chooserTitle = when (mode) {
            IgcShareMode.SHARE -> "Share IGC File"
            IgcShareMode.EMAIL -> "Email IGC File"
            IgcShareMode.UPLOAD -> "Upload IGC File"
        }
        return IgcShareRequest(
            document = entry.document,
            mime = "application/vnd.fai.igc",
            subject = baseSubject,
            text = baseText,
            chooserTitle = chooserTitle
        )
    }

    suspend fun copyToDestination(entry: IgcLogEntry, destinationUri: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            downloadsRepository.copyToDestination(
                source = entry.document,
                destinationUri = destinationUri
            )
        }
    }

    fun publishDiagnostic(diagnostic: IgcExportDiagnostic) {
        exportDiagnosticsRepository.publish(diagnostic)
    }

    fun clearLatestDiagnostic() {
        exportDiagnosticsRepository.clear()
    }

    fun buildMetadataText(entry: IgcLogEntry): String {
        val utcDate = entry.utcDate?.toString() ?: "unknown"
        val duration = entry.durationSeconds?.let { "${it}s" } ?: "unknown"
        return buildString {
            appendLine("IGC File Metadata")
            appendLine("Name: ${entry.displayName}")
            appendLine("UTC Date: $utcDate")
            appendLine("Size Bytes: ${entry.sizeBytes}")
            appendLine("Duration: $duration")
            appendLine("URI: ${entry.document.uri}")
        }.trim()
    }
}
