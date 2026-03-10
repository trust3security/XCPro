package com.example.xcpro.igc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.data.IgcDocumentReadResult
import com.example.xcpro.igc.data.IgcDownloadsRepository
import com.example.xcpro.igc.data.IgcLogEntry
import com.example.xcpro.igc.data.NoOpIgcExportDiagnosticsRepository
import com.example.xcpro.igc.usecase.IgcFilesUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IgcFilesCopyToInstrumentedTest {

    @Test
    fun copyToDestination_delegatesToDownloadsRepository() = runBlocking {
        val repository = FakeDownloadsRepository()
        val useCase = IgcFilesUseCase(repository, NoOpIgcExportDiagnosticsRepository)
        val entry = IgcLogEntry(
            document = DocumentRef(
                uri = "content://downloads/public_downloads/500",
                displayName = "2025-03-09-XCP-000500-01.IGC"
            ),
            displayName = "2025-03-09-XCP-000500-01.IGC",
            sizeBytes = 500L,
            lastModifiedEpochMillis = 1_742_000_500_000L,
            utcDate = LocalDate.of(2025, 3, 9),
            durationSeconds = 90L
        )

        val result = useCase.copyToDestination(
            entry = entry,
            destinationUri = "content://external/document/copy.igc"
        )

        assertTrue(result.isSuccess)
        assertEquals("content://external/document/copy.igc", repository.lastDestinationUri)
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
                message = "Not implemented in test fake"
            )
        }
    }
}
