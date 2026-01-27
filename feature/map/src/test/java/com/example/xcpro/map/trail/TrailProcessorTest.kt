package com.example.xcpro.map.trail

import com.example.xcpro.map.trail.domain.TrailProcessor
import com.example.xcpro.map.trail.domain.TrailTimeBase
import com.example.xcpro.map.trail.domain.TrailUpdateInput
import org.junit.Assert.assertEquals
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
}

