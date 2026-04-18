package com.trust3.xcpro.livefollow.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LiveFollowWatchTelemetryTest {

    @Test
    fun buildLiveFollowWatchTelemetryFields_usesExplicitSpectatorLabels() {
        val fields = buildLiveFollowWatchTelemetryFields(
            LiveFollowWatchUiState(
                panelStatusLabel = "Active",
                panelSpeedLabel = "13 m/s",
                panelAltitudeLabel = "510 m MSL",
                panelAglLabel = "45 m",
                panelHeadingLabel = "185 deg",
                panelFreshnessLabel = "Updated 12 s ago"
            )
        )

        assertEquals(
            listOf("Status", "Speed", "Altitude MSL", "AGL", "Heading", "Last update"),
            fields.map(LiveFollowWatchTelemetryField::label)
        )
        assertEquals(
            listOf("Active", "13 m/s", "510 m MSL", "45 m", "185 deg", "Updated 12 s ago"),
            fields.map(LiveFollowWatchTelemetryField::value)
        )
    }

    @Test
    fun buildLiveFollowWatchTelemetryFields_doesNotInventAglWhenUnavailable() {
        val fields = buildLiveFollowWatchTelemetryFields(
            LiveFollowWatchUiState(
                panelStatusLabel = "Active",
                panelAltitudeLabel = "510 m MSL"
            )
        )

        assertFalse(fields.any { it.label == "AGL" })
    }
}
