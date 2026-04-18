package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.common.orientation.OrientationData
import com.trust3.xcpro.map.model.MapLocationUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationFeedAdapterTest {

    private val adapter = LocationFeedAdapter()

    @Test
    fun fromGps_usesMonotonicTimeBaseWhenAvailable() {
        val gps = buildLocation(monotonicMs = 2_000L, wallMs = 1_000L)
        val orientation = OrientationData(
            mode = MapOrientationMode.TRACK_UP,
            headingDeg = 123.0,
            headingValid = true
        )

        val envelope = adapter.fromGps(gps, orientation)

        assertEquals(DisplayClock.TimeBase.MONOTONIC, envelope.timeBase)
        assertEquals(2_000L, envelope.fix.timestampMs)
        assertEquals(123.0, envelope.fix.headingDeg, 1e-6)
        assertEquals(MapOrientationMode.TRACK_UP, envelope.fix.orientationMode)
    }

    @Test
    fun fromGps_fallsBackToWallTimeWhenMonotonicMissing() {
        val gps = buildLocation(monotonicMs = 0L, wallMs = 5_000L)
        val orientation = OrientationData(
            mode = MapOrientationMode.NORTH_UP,
            headingDeg = 10.0
        )

        val envelope = adapter.fromGps(gps, orientation)

        assertEquals(DisplayClock.TimeBase.WALL, envelope.timeBase)
        assertEquals(5_000L, envelope.fix.timestampMs)
    }

    @Test
    fun fromReplayFrame_usesReplayTimeBase() {
        val replayFrame = ReplayLocationFrame(
            latitude = -33.0,
            longitude = 151.0,
            groundSpeedMs = 25.0,
            trackDeg = 90.0,
            accuracyMeters = 4.0,
            gpsAltitudeMeters = 400.0,
            replayTimestampMs = 9_999L
        )
        val orientation = OrientationData(
            mode = MapOrientationMode.HEADING_UP,
            headingDeg = 270.0,
            headingValid = true
        )

        val envelope = adapter.fromReplayFrame(replayFrame, orientation)

        assertEquals(DisplayClock.TimeBase.REPLAY, envelope.timeBase)
        assertEquals(9_999L, envelope.fix.timestampMs)
        assertEquals(270.0, envelope.fix.headingDeg, 1e-6)
    }

    private fun buildLocation(monotonicMs: Long, wallMs: Long): MapLocationUiModel =
        MapLocationUiModel(
            latitude = -33.5,
            longitude = 151.2,
            speedMs = 12.5,
            bearingDeg = 45.0,
            accuracyMeters = 5.0,
            bearingAccuracyDeg = 3.0,
            speedAccuracyMs = 0.5,
            timestampMs = wallMs,
            monotonicTimestampMs = monotonicMs
        )
}
