package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProfileDiagnosticsReporterTest {

    @Test
    fun report_capturesEventWithWallTimeAndAttributes() {
        val clock = FakeClock(wallMs = 1234L)
        val reporter = AppProfileDiagnosticsReporter(clock)

        reporter.report(
            event = "profile_bundle_export_success",
            attributes = mapOf("bytes" to "42")
        )

        val events = reporter.events.value
        assertEquals(1, events.size)
        assertEquals(1234L, events.first().wallTimeMs)
        assertEquals("profile_bundle_export_success", events.first().event)
        assertEquals("42", events.first().attributes["bytes"])
    }

    @Test
    fun report_keepsOnlyLatestEntriesAtCapacity() {
        val clock = FakeClock(wallMs = 0L)
        val reporter = AppProfileDiagnosticsReporter(clock)
        val totalEvents = AppProfileDiagnosticsReporter.MAX_EVENTS + 5

        repeat(totalEvents) { index ->
            clock.setWallMs(index.toLong())
            reporter.report(event = "evt_$index")
        }

        val events = reporter.events.value
        assertEquals(AppProfileDiagnosticsReporter.MAX_EVENTS, events.size)
        assertEquals("evt_5", events.first().event)
        assertEquals("evt_${totalEvents - 1}", events.last().event)
        assertTrue(events.last().wallTimeMs > events.first().wallTimeMs)
    }
}
