package com.example.xcpro.map.trail

import com.example.xcpro.map.trail.domain.TrailProcessor
import com.example.xcpro.map.trail.domain.TrailTimeBase
import com.example.xcpro.map.trail.domain.TrailUpdateInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrailProcessorTest {

    @Test
    fun live_uses_monotonic_time_when_available() {
        val gps = defaultGps(monotonicTimestampMillis = 42_000L, timestampMillis = 1_000L)
        val data = buildCompleteFlightData(gps = gps, timestampMillis = 9_999L)
        val processor = TrailProcessor()

        val result = processor.update(
            TrailUpdateInput(
                data = data,
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )

        assertNotNull(result)
        val render = result!!.renderState
        assertEquals(TrailTimeBase.LIVE_MONOTONIC, render.timeBase)
        assertEquals(42_000L, render.currentTimeMillis)
    }

    @Test
    fun live_falls_back_to_wall_time_when_monotonic_missing() {
        val gps = defaultGps(monotonicTimestampMillis = 0L, timestampMillis = 2_000L)
        val data = buildCompleteFlightData(gps = gps, timestampMillis = 8_888L)
        val processor = TrailProcessor()

        val result = processor.update(
            TrailUpdateInput(
                data = data,
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )

        assertNotNull(result)
        val render = result!!.renderState
        assertEquals(TrailTimeBase.LIVE_WALL, render.timeBase)
        assertEquals(8_888L, render.currentTimeMillis)
    }

    @Test
    fun replay_uses_igc_time_base() {
        val data = buildCompleteFlightData(timestampMillis = 7_777L)
        val processor = TrailProcessor()

        val result = processor.update(
            TrailUpdateInput(
                data = data,
                windState = null,
                isFlying = true,
                isReplay = true
            )
        )

        assertNotNull(result)
        val render = result!!.renderState
        assertEquals(TrailTimeBase.REPLAY_IGC, render.timeBase)
        assertEquals(7_777L, render.currentTimeMillis)
    }

    @Test
    fun missingGps_returnsNullUpdate() {
        val data = buildCompleteFlightData(gps = null, timestampMillis = 9_000L)
        val processor = TrailProcessor()

        val result = processor.update(
            TrailUpdateInput(
                data = data,
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )

        assertEquals(null, result)
    }

    @Test
    fun live_timeBaseSwitch_resetsStore_and_keepsSampleFlow() {
        val processor = TrailProcessor()

        val first = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(
                        latitude = 46.0000,
                        longitude = 7.0000,
                        monotonicTimestampMillis = 10_000L,
                        timestampMillis = 1_000L
                    ),
                    timestampMillis = 1_500L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )
        assertNotNull(first)
        assertEquals(TrailTimeBase.LIVE_MONOTONIC, first!!.renderState.timeBase)
        assertTrue(first.sampleAdded)
        assertEquals(1, first.renderState.points.size)

        val switchedToWall = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(
                        latitude = 46.0002,
                        longitude = 7.0002,
                        monotonicTimestampMillis = 0L,
                        timestampMillis = 2_000L
                    ),
                    timestampMillis = 1_700_000_000_000L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )
        assertNotNull(switchedToWall)
        assertEquals(TrailTimeBase.LIVE_WALL, switchedToWall!!.renderState.timeBase)
        assertTrue(switchedToWall.storeReset)
        assertTrue(switchedToWall.sampleAdded)
        assertEquals(1, switchedToWall.renderState.points.size)

        val switchedBackToMonotonic = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(
                        latitude = 46.0004,
                        longitude = 7.0004,
                        monotonicTimestampMillis = 12_500L,
                        timestampMillis = 3_000L
                    ),
                    timestampMillis = 1_700_000_001_000L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )
        assertNotNull(switchedBackToMonotonic)
        assertEquals(TrailTimeBase.LIVE_MONOTONIC, switchedBackToMonotonic!!.renderState.timeBase)
        assertTrue(switchedBackToMonotonic.storeReset)
        assertTrue(switchedBackToMonotonic.sampleAdded)
        assertEquals(1, switchedBackToMonotonic.renderState.points.size)
    }
}

