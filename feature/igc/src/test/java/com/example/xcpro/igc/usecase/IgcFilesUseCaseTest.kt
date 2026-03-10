package com.example.xcpro.igc.usecase

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.data.IgcDocumentReadResult
import com.example.xcpro.igc.data.IgcDownloadsRepository
import com.example.xcpro.igc.data.InMemoryIgcExportDiagnosticsRepository
import com.example.xcpro.igc.data.IgcLogEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcFilesUseCaseTest {

    @Test
    fun applyFilterAndSort_filtersByNameAndSortsBySize() {
        val repository = FakeDownloadsRepository()
        val useCase = IgcFilesUseCase(repository, InMemoryIgcExportDiagnosticsRepository())
        val entries = listOf(
            entry(name = "2025-03-10-XCP-000001-01.IGC", size = 10L, modified = 3L),
            entry(name = "2025-03-09-XCP-000002-01.IGC", size = 40L, modified = 2L),
            entry(name = "local_test.igc", size = 20L, modified = 1L)
        )

        val filtered = useCase.applyFilterAndSort(
            allEntries = entries,
            query = "2025",
            sort = IgcFilesSort.SIZE_DESC
        )

        assertEquals(2, filtered.size)
        assertEquals("2025-03-09-XCP-000002-01.IGC", filtered.first().displayName)
    }

    @Test
    fun buildShareRequest_usesExpectedChooserTitleForEmail() {
        val useCase = IgcFilesUseCase(
            FakeDownloadsRepository(),
            InMemoryIgcExportDiagnosticsRepository()
        )
        val request = useCase.buildShareRequest(
            entry = entry(name = "flight.IGC", size = 12L, modified = 1L),
            mode = IgcShareMode.EMAIL
        )
        assertEquals("Email IGC File", request.chooserTitle)
        assertEquals("application/vnd.fai.igc", request.mime)
    }

    @Test
    fun copyToDestination_delegatesToRepository() = runTest {
        val repository = FakeDownloadsRepository()
        val useCase = IgcFilesUseCase(repository, InMemoryIgcExportDiagnosticsRepository())
        val entry = entry(name = "flight.IGC", size = 12L, modified = 1L)

        val result = useCase.copyToDestination(entry, "content://dest/igc")

        assertTrue(result.isSuccess)
        assertEquals("content://dest/igc", repository.lastDestinationUri)
    }

    private fun entry(
        name: String,
        size: Long,
        modified: Long
    ): IgcLogEntry {
        return IgcLogEntry(
            document = DocumentRef(uri = "content://igc/$name", displayName = name),
            displayName = name,
            sizeBytes = size,
            lastModifiedEpochMillis = modified,
            utcDate = LocalDate.of(2025, 3, 10),
            durationSeconds = 60L
        )
    }

    private class FakeDownloadsRepository : IgcDownloadsRepository {
        private val flow = MutableStateFlow<List<IgcLogEntry>>(emptyList())
        override val entries: StateFlow<List<IgcLogEntry>> = flow
        var lastDestinationUri: String? = null

        override fun refreshEntries() = Unit

        override fun listExistingNamesForUtcDate(utcDate: LocalDate): Set<String> = emptySet()

        override fun copyToDestination(source: DocumentRef, destinationUri: String): Result<Unit> {
            lastDestinationUri = destinationUri
            return Result.success(Unit)
        }

        override fun readDocumentBytes(document: DocumentRef): IgcDocumentReadResult {
            return IgcDocumentReadResult.Failure(
                code = IgcDocumentReadResult.ErrorCode.OPEN_FAILED,
                message = "not used in test"
            )
        }
    }
}
