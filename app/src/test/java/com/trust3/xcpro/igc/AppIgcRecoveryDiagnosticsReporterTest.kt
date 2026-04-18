package com.trust3.xcpro.igc

import com.trust3.xcpro.core.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIgcRecoveryDiagnosticsReporterTest {

    @Test
    fun report_capturesEventWithWallTimeAndAttributes() {
        val clock = FakeClock(wallMs = 1234L)
        val reporter = AppIgcRecoveryDiagnosticsReporter(clock)

        reporter.report(
            event = "igc_recovery_terminal_failure",
            attributes = mapOf("code" to "STAGING_MISSING")
        )

        val events = reporter.events.value
        assertEquals(1, events.size)
        assertEquals(1234L, events.first().wallTimeMs)
        assertEquals("igc_recovery_terminal_failure", events.first().event)
        assertEquals("STAGING_MISSING", events.first().attributes["code"])
    }

    @Test
    fun report_keepsOnlyLatestEntriesAtCapacity() {
        val clock = FakeClock(wallMs = 0L)
        val reporter = AppIgcRecoveryDiagnosticsReporter(clock)
        val totalEvents = AppIgcRecoveryDiagnosticsReporter.MAX_EVENTS + 5

        repeat(totalEvents) { index ->
            clock.setWallMs(index.toLong())
            reporter.report(event = "evt_$index")
        }

        val events = reporter.events.value
        assertEquals(AppIgcRecoveryDiagnosticsReporter.MAX_EVENTS, events.size)
        assertEquals("evt_5", events.first().event)
        assertEquals("evt_${totalEvents - 1}", events.last().event)
        assertTrue(events.last().wallTimeMs > events.first().wallTimeMs)
    }
}
