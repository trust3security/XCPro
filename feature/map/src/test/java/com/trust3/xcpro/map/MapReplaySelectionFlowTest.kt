package com.trust3.xcpro.map

import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.replay.Selection
import com.trust3.xcpro.replay.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapReplaySelectionFlowTest {

    @Test
    fun mapReplaySelectionActive_emitsOnlyWhenSelectionPresenceChanges() = runTest {
        val source = MutableSharedFlow<SessionState>(extraBufferCapacity = 16)
        val emitted = mutableListOf<Boolean>()
        val selectedA = Selection(DocumentRef(uri = "content://replay/a.igc", displayName = "a.igc"))
        val selectedB = Selection(DocumentRef(uri = "content://replay/b.igc", displayName = "b.igc"))

        val job = launch {
            source.mapReplaySelectionActive().collect { emitted += it }
        }
        runCurrent()

        source.emit(SessionState(status = com.trust3.xcpro.replay.SessionStatus.IDLE))
        source.emit(
            SessionState(
                status = com.trust3.xcpro.replay.SessionStatus.PLAYING,
                currentTimestampMillis = 10_000L
            )
        )
        source.emit(
            SessionState(
                selection = selectedA,
                status = com.trust3.xcpro.replay.SessionStatus.PAUSED,
                currentTimestampMillis = 20_000L
            )
        )
        source.emit(
            SessionState(
                selection = selectedA,
                status = com.trust3.xcpro.replay.SessionStatus.PLAYING,
                currentTimestampMillis = 30_000L
            )
        )
        source.emit(
            SessionState(
                selection = selectedB,
                status = com.trust3.xcpro.replay.SessionStatus.PAUSED,
                currentTimestampMillis = 40_000L
            )
        )
        source.emit(
            SessionState(
                selection = null,
                status = com.trust3.xcpro.replay.SessionStatus.IDLE,
                currentTimestampMillis = 50_000L
            )
        )
        runCurrent()

        assertEquals(listOf(false, true, false), emitted)
        job.cancel()
    }
}
