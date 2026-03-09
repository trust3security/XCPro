package com.example.xcpro.igc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.data.IgcDownloadsRepository
import com.example.xcpro.igc.data.IgcLogEntry
import com.example.xcpro.igc.ui.IgcFilesEvent
import com.example.xcpro.igc.ui.IgcFilesViewModel
import com.example.xcpro.igc.usecase.IgcFilesUseCase
import com.example.xcpro.igc.usecase.IgcReplayLauncher
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IgcFilesReplayOpenInstrumentedTest {

    @Test
    fun replayOpen_success_emitsNavigateBackEvent() = runBlocking {
        val replayLauncher = FakeReplayLauncher(failOnLoad = false)
        val viewModel = IgcFilesViewModel(
            useCase = IgcFilesUseCase(FakeDownloadsRepository()),
            replayLauncher = replayLauncher
        )
        val entry = entry("content://downloads/public_downloads/77")
        val eventAwaiter = async { withTimeout(2_000L) { viewModel.events.first() } }

        viewModel.replayOpen(entry)
        val event = eventAwaiter.await()

        assertTrue(event is IgcFilesEvent.NavigateBackToMap)
        assertEquals(entry.document.uri, replayLauncher.lastLoadedUri)
        assertEquals(1, replayLauncher.playCalls)
    }

    @Test
    fun replayOpen_failure_emitsActionableMessage() = runBlocking {
        val replayLauncher = FakeReplayLauncher(failOnLoad = true)
        val viewModel = IgcFilesViewModel(
            useCase = IgcFilesUseCase(FakeDownloadsRepository()),
            replayLauncher = replayLauncher
        )
        val entry = entry("content://downloads/public_downloads/78")
        val eventAwaiter = async { withTimeout(2_000L) { viewModel.events.first() } }

        viewModel.replayOpen(entry)
        val event = eventAwaiter.await()

        assertTrue(event is IgcFilesEvent.ShowMessage)
        event as IgcFilesEvent.ShowMessage
        assertTrue(event.message.contains("Replay open failed", ignoreCase = true))
    }

    private fun entry(uri: String): IgcLogEntry {
        return IgcLogEntry(
            document = DocumentRef(uri = uri, displayName = "flight.igc"),
            displayName = "flight.igc",
            sizeBytes = 10L,
            lastModifiedEpochMillis = 10L,
            utcDate = LocalDate.of(2025, 3, 9),
            durationSeconds = 15L
        )
    }

    private class FakeReplayLauncher(
        private val failOnLoad: Boolean
    ) : IgcReplayLauncher {
        var lastLoadedUri: String? = null
        var playCalls: Int = 0

        override suspend fun loadDocument(document: DocumentRef) {
            if (failOnLoad) {
                throw IllegalStateException("Replay open failed")
            }
            lastLoadedUri = document.uri
        }

        override fun play() {
            playCalls += 1
        }
    }

    private class FakeDownloadsRepository : IgcDownloadsRepository {
        private val flow = MutableStateFlow<List<IgcLogEntry>>(emptyList())
        override val entries: StateFlow<List<IgcLogEntry>> = flow
        override fun refreshEntries() = Unit
        override fun listExistingNamesForUtcDate(utcDate: LocalDate): Set<String> = emptySet()
        override fun copyToDestination(source: DocumentRef, destinationUri: String): Result<Unit> {
            return Result.success(Unit)
        }
    }
}
