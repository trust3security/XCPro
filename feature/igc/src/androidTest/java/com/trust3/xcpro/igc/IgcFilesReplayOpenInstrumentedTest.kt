package com.trust3.xcpro.igc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.igc.data.IgcDocumentReadResult
import com.trust3.xcpro.igc.data.IgcDownloadsRepository
import com.trust3.xcpro.igc.data.IgcLogEntry
import com.trust3.xcpro.igc.ui.IgcFilesEvent
import com.trust3.xcpro.igc.ui.IgcFilesViewModel
import com.trust3.xcpro.igc.data.NoOpIgcExportDiagnosticsRepository
import com.trust3.xcpro.igc.usecase.IgcFilesUseCase
import com.trust3.xcpro.replay.IgcReplayControllerPort
import com.trust3.xcpro.replay.IgcReplayUseCase
import com.trust3.xcpro.replay.ReplayEvent
import com.trust3.xcpro.replay.SessionState
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
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
        val replayController = FakeReplayController(failOnLoad = false)
        val viewModel = IgcFilesViewModel(
            useCase = IgcFilesUseCase(FakeDownloadsRepository(), NoOpIgcExportDiagnosticsRepository),
            replayUseCase = IgcReplayUseCase(replayController)
        )
        val entry = entry("content://downloads/public_downloads/77")
        val eventAwaiter = async { withTimeout(2_000L) { viewModel.events.first() } }

        viewModel.replayOpen(entry)
        val event = eventAwaiter.await()

        assertTrue(event is IgcFilesEvent.NavigateBackToMap)
        assertEquals(entry.document.uri, replayController.lastLoadedUri)
        assertEquals(1, replayController.playCalls)
    }

    @Test
    fun replayOpen_failure_emitsActionableMessage() = runBlocking {
        val replayController = FakeReplayController(failOnLoad = true)
        val viewModel = IgcFilesViewModel(
            useCase = IgcFilesUseCase(FakeDownloadsRepository(), NoOpIgcExportDiagnosticsRepository),
            replayUseCase = IgcReplayUseCase(replayController)
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

    private class FakeReplayController(
        private val failOnLoad: Boolean
    ) : IgcReplayControllerPort {
        var lastLoadedUri: String? = null
        var playCalls: Int = 0
        override val session: StateFlow<SessionState> = MutableStateFlow(SessionState())
        override val events = MutableSharedFlow<ReplayEvent>()

        override suspend fun loadDocument(document: DocumentRef) {
            if (failOnLoad) {
                throw IllegalStateException("Replay open failed")
            }
            lastLoadedUri = document.uri
        }

        override fun play() {
            playCalls += 1
        }

        override fun setSpeed(multiplier: Double) = Unit

        override fun stop(emitCancelledEvent: Boolean) = Unit

        override fun seekTo(fraction: Float) = Unit
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
