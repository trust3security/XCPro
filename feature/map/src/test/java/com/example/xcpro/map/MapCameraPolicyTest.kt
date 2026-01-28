package com.example.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.domain.MapShiftBiasCalculator
import com.example.xcpro.map.domain.MapShiftBiasMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraPolicyTest {

    @Test
    fun computeBasePadding_averagesOffsets() {
        val averager = TestOffsetAverager()
        val policy = MapCameraPolicy(averager, MapShiftBiasCalculator())

        val first = policy.computeBasePadding(intArrayOf(0, 10, 0, 30))
        val second = policy.computeBasePadding(intArrayOf(0, 20, 0, 50))

        assertArrayEquals(intArrayOf(0, 10, 0, 30), first)
        assertArrayEquals(intArrayOf(0, 15, 0, 40), second)
    }

    @Test
    fun computeBiasOffset_returns_zero_when_mode_none() {
        val policy = MapCameraPolicy(TestOffsetAverager(), MapShiftBiasCalculator())

        val offset = policy.computeBiasOffset(
            baseBiasInput(biasMode = MapShiftBiasMode.NONE)
        )

        assertEquals(0.0, offset.dxPx, 0.0)
        assertEquals(0.0, offset.dyPx, 0.0)
    }

    @Test
    fun computeBiasOffset_returns_zero_when_not_north_up() {
        val policy = MapCameraPolicy(TestOffsetAverager(), MapShiftBiasCalculator())

        val offset = policy.computeBiasOffset(
            baseBiasInput(orientationMode = MapOrientationMode.TRACK_UP)
        )

        assertEquals(0.0, offset.dxPx, 0.0)
        assertEquals(0.0, offset.dyPx, 0.0)
    }

    @Test
    fun computeBiasOffset_returns_zero_when_thermal() {
        val policy = MapCameraPolicy(TestOffsetAverager(), MapShiftBiasCalculator())

        val offset = policy.computeBiasOffset(
            baseBiasInput(flightMode = FlightModeSelection.THERMAL)
        )

        assertEquals(0.0, offset.dxPx, 0.0)
        assertEquals(0.0, offset.dyPx, 0.0)
    }

    @Test
    fun computeBiasOffset_returns_non_zero_for_valid_inputs() {
        val policy = MapCameraPolicy(TestOffsetAverager(), MapShiftBiasCalculator())

        val offset = policy.computeBiasOffset(
            baseBiasInput(
                biasMode = MapShiftBiasMode.TRACK,
                trackDeg = 90.0,
                mapBearing = 0.0,
                speedMs = 12.0,
                screenWidthPx = 1000,
                screenHeightPx = 1000
            )
        )

        assertTrue(offset.dxPx != 0.0 || offset.dyPx != 0.0)
    }

    @Test
    fun shouldUpdateCamera_skips_gate_when_not_due_or_bearing() {
        val policy = MapCameraPolicy(TestOffsetAverager(), MapShiftBiasCalculator())
        var called = 0
        val input = MapCameraPolicy.CameraUpdateInput(
            timeBase = null,
            useRenderFrameSync = false,
            cameraBearing = 0.0,
            targetBearing = 1.0,
            nowMs = 100,
            lastCameraUpdateMs = 90,
            minUpdateIntervalMs = 80,
            bearingEpsDeg = 2.0
        )

        val result = policy.shouldUpdateCamera(input) {
            called++
            true
        }

        assertFalse(result)
        assertEquals(0, called)
    }

    @Test
    fun shouldUpdateCamera_returns_true_on_replay_frame_sync() {
        val policy = MapCameraPolicy(TestOffsetAverager(), MapShiftBiasCalculator())
        val input = MapCameraPolicy.CameraUpdateInput(
            timeBase = DisplayClock.TimeBase.REPLAY,
            useRenderFrameSync = true,
            cameraBearing = 0.0,
            targetBearing = 0.0,
            nowMs = 100,
            lastCameraUpdateMs = 0,
            minUpdateIntervalMs = 80,
            bearingEpsDeg = 2.0
        )

        val result = policy.shouldUpdateCamera(input) { false }

        assertTrue(result)
    }

    @Test
    fun shouldUpdateCamera_requires_position_when_time_due() {
        val policy = MapCameraPolicy(TestOffsetAverager(), MapShiftBiasCalculator())
        val input = MapCameraPolicy.CameraUpdateInput(
            timeBase = null,
            useRenderFrameSync = false,
            cameraBearing = 0.0,
            targetBearing = 0.0,
            nowMs = 200,
            lastCameraUpdateMs = 0,
            minUpdateIntervalMs = 80,
            bearingEpsDeg = 2.0
        )

        val rejected = policy.shouldUpdateCamera(input) { false }
        val accepted = policy.shouldUpdateCamera(input) { true }

        assertFalse(rejected)
        assertTrue(accepted)
    }

    private fun baseBiasInput(
        biasMode: MapShiftBiasMode = MapShiftBiasMode.TRACK,
        orientationMode: MapOrientationMode = MapOrientationMode.NORTH_UP,
        flightMode: FlightModeSelection = FlightModeSelection.CRUISE,
        trackDeg: Double = 45.0,
        mapBearing: Double = 10.0,
        speedMs: Double = 10.0,
        screenWidthPx: Int = 800,
        screenHeightPx: Int = 600
    ): MapCameraPolicy.BiasInput {
        return MapCameraPolicy.BiasInput(
            trackDeg = trackDeg,
            targetBearingDeg = null,
            mapBearing = mapBearing,
            speedMs = speedMs,
            orientationMode = orientationMode,
            flightMode = flightMode,
            biasMode = biasMode,
            biasStrength = 1.0,
            minSpeedMs = 2.0,
            historySize = 1,
            maxOffsetFraction = 0.35,
            holdOnInvalid = true,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            gliderScreenPercent = 35
        )
    }

    private class TestOffsetAverager : MapCameraPolicy.OffsetAverager {
        private val samples = mutableListOf<Pair<Float, Float>>()

        override fun remember(topPx: Float, bottomPx: Float) {
            samples.add(topPx to bottomPx)
        }

        override fun averaged(): MapCameraPolicy.AveragedOffset {
            if (samples.isEmpty()) {
                return MapCameraPolicy.AveragedOffset(0f, 0f)
            }
            var sumTop = 0.0
            var sumBottom = 0.0
            samples.forEach { (top, bottom) ->
                sumTop += top
                sumBottom += bottom
            }
            val count = samples.size.toDouble()
            return MapCameraPolicy.AveragedOffset(
                topPx = (sumTop / count).toFloat(),
                bottomPx = (sumBottom / count).toFloat()
            )
        }
    }
}
