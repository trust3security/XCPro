package com.example.xcpro.map

import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.model.MapLocationUiModel
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
    fun fromFlightData_usesReplayTimeBase() {
        val liveData = RealTimeFlightData(
            latitude = -33.0,
            longitude = 151.0,
            groundSpeed = 25.0,
            track = 90.0,
            accuracy = 4.0,
            timestamp = 9_999L
        )
        val orientation = OrientationData(
            mode = MapOrientationMode.HEADING_UP,
            headingDeg = 270.0,
            headingValid = true
        )

        val envelope = adapter.fromFlightData(liveData, orientation)

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
