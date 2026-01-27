package com.example.xcpro.orientation

import com.example.xcpro.MapOrientationSettings
import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.HeadingSolution
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationSensorData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationEngineTest {
    private val engine = OrientationEngine()
    private val minSpeedMs = 2.0

    @Test
    fun northUpAlwaysValid() {
        val sensorData = OrientationSensorData(
            track = 123.0,
            groundSpeed = 12.0,
            isGPSValid = true,
            headingSolution = HeadingSolution(bearingDeg = 45.0, source = BearingSource.COMPASS, isValid = true)
        )

        val output = engine.reduce(
            state = OrientationEngine.State(),
            sensorData = sensorData,
            settings = settingsFor(MapOrientationMode.NORTH_UP),
            nowMonoMs = 1_000L,
            nowWallMs = 2_000L
        )

        assertEquals(0.0, output.orientation.bearing, 1e-6)
        assertTrue(output.orientation.isValid)
        assertEquals(BearingSource.NONE, output.orientation.bearingSource)
    }

    @Test
    fun trackUpUsesGpsTrackWhenValid() {
        val sensorData = OrientationSensorData(
            track = 90.0,
            groundSpeed = 5.0,
            isGPSValid = true
        )

        val output = engine.reduce(
            state = OrientationEngine.State(),
            sensorData = sensorData,
            settings = settingsFor(MapOrientationMode.TRACK_UP),
            nowMonoMs = 1_000L,
            nowWallMs = 2_000L
        )

        assertEquals(90.0, output.orientation.bearing, 1e-6)
        assertTrue(output.orientation.isValid)
        assertEquals(BearingSource.TRACK, output.orientation.bearingSource)
    }

    @Test
    fun trackUpFallsBackToLastKnownBeforeStale() {
        val validData = OrientationSensorData(
            track = 120.0,
            groundSpeed = 6.0,
            isGPSValid = true
        )
        val first = engine.reduce(
            state = OrientationEngine.State(),
            sensorData = validData,
            settings = settingsFor(MapOrientationMode.TRACK_UP),
            nowMonoMs = 1_000L,
            nowWallMs = 2_000L
        )

        val slowData = OrientationSensorData(
            track = 200.0,
            groundSpeed = 0.5,
            isGPSValid = true
        )
        val second = engine.reduce(
            state = first.state,
            sensorData = slowData,
            settings = settingsFor(MapOrientationMode.TRACK_UP),
            nowMonoMs = 2_000L,
            nowWallMs = 3_000L
        )

        assertEquals(120.0, second.orientation.bearing, 1e-6)
        assertTrue(second.orientation.isValid)
        assertEquals(BearingSource.LAST_KNOWN, second.orientation.bearingSource)
    }

    @Test
    fun trackUpStaleResetsBearing() {
        val validData = OrientationSensorData(
            track = 275.0,
            groundSpeed = 4.0,
            isGPSValid = true
        )
        val first = engine.reduce(
            state = OrientationEngine.State(),
            sensorData = validData,
            settings = settingsFor(MapOrientationMode.TRACK_UP),
            nowMonoMs = 1_000L,
            nowWallMs = 2_000L
        )

        val staleData = OrientationSensorData(
            track = 80.0,
            groundSpeed = 0.0,
            isGPSValid = true
        )
        val second = engine.reduce(
            state = first.state,
            sensorData = staleData,
            settings = settingsFor(MapOrientationMode.TRACK_UP),
            nowMonoMs = 12_000L,
            nowWallMs = 13_000L
        )

        assertEquals(0.0, second.orientation.bearing, 1e-6)
        assertFalse(second.orientation.isValid)
        assertEquals(BearingSource.NONE, second.orientation.bearingSource)
    }

    @Test
    fun headingUpUsesLastKnownBeforeStale() {
        val validHeading = OrientationSensorData(
            headingSolution = HeadingSolution(
                bearingDeg = 45.0,
                source = BearingSource.COMPASS,
                isValid = true
            )
        )
        val first = engine.reduce(
            state = OrientationEngine.State(),
            sensorData = validHeading,
            settings = settingsFor(MapOrientationMode.HEADING_UP),
            nowMonoMs = 1_000L,
            nowWallMs = 2_000L
        )

        val invalidHeading = OrientationSensorData(
            headingSolution = HeadingSolution(
                bearingDeg = 10.0,
                source = BearingSource.COMPASS,
                isValid = false
            )
        )
        val second = engine.reduce(
            state = first.state,
            sensorData = invalidHeading,
            settings = settingsFor(MapOrientationMode.HEADING_UP),
            nowMonoMs = 2_000L,
            nowWallMs = 3_000L
        )

        assertEquals(45.0, second.orientation.bearing, 1e-6)
        assertFalse(second.orientation.isValid)
        assertEquals(BearingSource.LAST_KNOWN, second.orientation.bearingSource)
    }

    @Test
    fun headingUpStaleResetsBearing() {
        val validHeading = OrientationSensorData(
            headingSolution = HeadingSolution(
                bearingDeg = 300.0,
                source = BearingSource.COMPASS,
                isValid = true
            )
        )
        val first = engine.reduce(
            state = OrientationEngine.State(),
            sensorData = validHeading,
            settings = settingsFor(MapOrientationMode.HEADING_UP),
            nowMonoMs = 1_000L,
            nowWallMs = 2_000L
        )

        val invalidHeading = OrientationSensorData(
            headingSolution = HeadingSolution(
                bearingDeg = 12.0,
                source = BearingSource.COMPASS,
                isValid = false
            )
        )
        val second = engine.reduce(
            state = first.state,
            sensorData = invalidHeading,
            settings = settingsFor(MapOrientationMode.HEADING_UP),
            nowMonoMs = 7_000L,
            nowWallMs = 8_000L
        )

        assertEquals(0.0, second.orientation.bearing, 1e-6)
        assertFalse(second.orientation.isValid)
        assertEquals(BearingSource.NONE, second.orientation.bearingSource)
    }

    @Test
    fun userOverrideSuppressesUpdatesUntilTimeout() {
        val initialState = engine.onUserInteraction(OrientationEngine.State(), nowMonoMs = 1_000L)
        val sensorData = OrientationSensorData(
            track = 100.0,
            groundSpeed = 6.0,
            isGPSValid = true
        )

        val suppressed = engine.reduce(
            state = initialState,
            sensorData = sensorData,
            settings = settingsFor(MapOrientationMode.TRACK_UP),
            nowMonoMs = 2_000L,
            nowWallMs = 3_000L
        )

        assertFalse(suppressed.didUpdate)
        assertEquals(initialState.lastOrientation, suppressed.orientation)

        val resumed = engine.reduce(
            state = suppressed.state,
            sensorData = sensorData,
            settings = settingsFor(MapOrientationMode.TRACK_UP),
            nowMonoMs = 12_500L,
            nowWallMs = 13_500L
        )

        assertTrue(resumed.didUpdate)
        assertEquals(100.0, resumed.orientation.bearing, 1e-6)
    }

    private fun settingsFor(mode: MapOrientationMode): MapOrientationSettings {
        return MapOrientationSettings(
            cruiseMode = mode,
            circlingMode = mode,
            minSpeedThresholdMs = minSpeedMs
        )
    }
}