package com.example.xcpro.map

import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdsbIconTelemetryTrackerTest {

    @Test
    fun unknownRenderCount_incrementsForUnknownClassifiedTargets() {
        val tracker = AdsbIconTelemetryTracker()

        tracker.onRenderedTargets(
            targets = listOf(
                target(icao24 = "abc123", category = 0),
                target(icao24 = "def456", category = null)
            ),
            nowMonoMs = 1_000L
        )

        val snapshot = tracker.snapshot()
        assertEquals(2L, snapshot.unknownRenderCount)
        assertEquals(0L, snapshot.legacyUnknownRenderCount)
        assertEquals(0L, snapshot.resolveLatencySampleCount)
        assertNull(snapshot.resolveLatencyLastMs)
    }

    @Test
    fun resolveLatency_recordedOnFirstUnknownToResolvedTransition() {
        val tracker = AdsbIconTelemetryTracker()
        tracker.onRenderedTargets(
            targets = listOf(target(icao24 = "abc123", category = 0)),
            nowMonoMs = 1_000L
        )

        tracker.onRenderedTargets(
            targets = listOf(
                target(
                    icao24 = "abc123",
                    category = 0,
                    metadataTypecode = "B738"
                )
            ),
            nowMonoMs = 1_450L
        )
        tracker.onRenderedTargets(
            targets = listOf(
                target(
                    icao24 = "abc123",
                    category = 0,
                    metadataTypecode = "B738"
                )
            ),
            nowMonoMs = 1_900L
        )

        val snapshot = tracker.snapshot()
        assertEquals(1L, snapshot.resolveLatencySampleCount)
        assertEquals(450L, snapshot.resolveLatencyLastMs)
        assertEquals(450L, snapshot.resolveLatencyMaxMs)
        assertEquals(450L, snapshot.resolveLatencyAverageMs)
    }

    @Test
    fun removedTargetBeforeResolution_doesNotEmitResolveLatencySample() {
        val tracker = AdsbIconTelemetryTracker()
        tracker.onRenderedTargets(
            targets = listOf(target(icao24 = "abc123", category = 0)),
            nowMonoMs = 2_000L
        )

        tracker.onRenderedTargets(
            targets = emptyList(),
            nowMonoMs = 2_400L
        )

        val snapshot = tracker.snapshot()
        assertEquals(0L, snapshot.resolveLatencySampleCount)
        assertNull(snapshot.resolveLatencyLastMs)
    }

    @Test
    fun nonMonotonicRenderTime_doesNotRecordNegativeResolveLatency() {
        val tracker = AdsbIconTelemetryTracker()
        tracker.onRenderedTargets(
            targets = listOf(target(icao24 = "abc123", category = 0)),
            nowMonoMs = 2_000L
        )
        tracker.onRenderedTargets(
            targets = listOf(target(icao24 = "abc123", category = 0, metadataTypecode = "B738")),
            nowMonoMs = 1_900L
        )

        val snapshot = tracker.snapshot()
        assertEquals(0L, snapshot.resolveLatencySampleCount)
        assertNull(snapshot.resolveLatencyLastMs)
    }

    @Test
    fun unknownRenderCount_usesProjectedIconOverrides() {
        val tracker = AdsbIconTelemetryTracker()
        tracker.onRenderedTargets(
            targets = listOf(target(icao24 = "abc123", category = 0)),
            nowMonoMs = 3_000L,
            iconStyleIdOverrides = mapOf("abc123" to "adsb_icon_plane_medium")
        )

        val snapshot = tracker.snapshot()
        assertEquals(0L, snapshot.unknownRenderCount)
        assertEquals(0L, snapshot.legacyUnknownRenderCount)
        assertEquals(1L, snapshot.resolveLatencySampleCount)
    }

    @Test
    fun unknownRenderCount_tracksLegacyUnknownOverrides() {
        val tracker = AdsbIconTelemetryTracker()
        tracker.onRenderedTargets(
            targets = listOf(target(icao24 = "abc123", category = 0)),
            nowMonoMs = 4_000L,
            iconStyleIdOverrides = mapOf("abc123" to ADSB_ICON_STYLE_UNKNOWN_LEGACY)
        )

        val snapshot = tracker.snapshot()
        assertEquals(1L, snapshot.unknownRenderCount)
        assertEquals(1L, snapshot.legacyUnknownRenderCount)
        assertEquals(0L, snapshot.resolveLatencySampleCount)
    }

    private fun target(
        icao24: String,
        category: Int?,
        metadataTypecode: String? = null
    ): AdsbTrafficUiModel {
        return AdsbTrafficUiModel(
            id = Icao24.from(icao24) ?: error("invalid ICAO24"),
            callsign = "TEST",
            lat = -33.0,
            lon = 151.0,
            altitudeM = 900.0,
            speedMps = 45.0,
            trackDeg = 180.0,
            climbMps = 0.1,
            ageSec = 1,
            isStale = false,
            distanceMeters = 1_000.0,
            bearingDegFromUser = 180.0,
            positionSource = 0,
            category = category,
            lastContactEpochSec = null,
            metadataTypecode = metadataTypecode,
            metadataIcaoAircraftType = null
        )
    }
}
