package com.example.xcpro.igc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.data.IgcDocumentReadResult
import com.example.xcpro.igc.data.IgcDownloadsRepository
import com.example.xcpro.igc.data.IgcLogEntry
import com.example.xcpro.igc.usecase.IgcFilesSort
import com.example.xcpro.igc.data.NoOpIgcExportDiagnosticsRepository
import com.example.xcpro.igc.usecase.IgcFilesUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IgcFilesListInstrumentedTest {

    @Test
    fun applyFilterAndSort_filtersByQuery_andSortsByDateDescending() {
        val useCase = IgcFilesUseCase(FakeDownloadsRepository(), NoOpIgcExportDiagnosticsRepository)
        val entries = listOf(
            entry("2025-03-08-XCP-000001-01.IGC", modified = 100L),
            entry("2025-03-09-XCP-000002-01.IGC", modified = 300L),
            entry("practice.igc", modified = 200L)
        )

        val visible = useCase.applyFilterAndSort(
            allEntries = entries,
            query = "2025-03",
            sort = IgcFilesSort.DATE_DESC
        )

        assertEquals(2, visible.size)
        assertEquals("2025-03-09-XCP-000002-01.IGC", visible.first().displayName)
    }

    private fun entry(name: String, modified: Long): IgcLogEntry {
        return IgcLogEntry(
            document = DocumentRef(uri = "content://downloads/$name", displayName = name),
            displayName = name,
            sizeBytes = 123L,
            lastModifiedEpochMillis = modified,
            utcDate = LocalDate.of(2025, 3, 9),
            durationSeconds = 60L
        )
    }

    private class FakeDownloadsRepository : IgcDownloadsRepository {
        private val flow = MutableStateFlow<List<IgcLogEntry>>(emptyList())
        override val entries: StateFlow<List<IgcLogEntry>> = flow
        override fun refreshEntries() = Unit
        override fun listExistingNamesForUtcDate(utcDate: LocalDate): Set<String> = emptySet()
        override fun copyToDestination(source: DocumentRef, destinationUri: String): Result<Unit> {
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
