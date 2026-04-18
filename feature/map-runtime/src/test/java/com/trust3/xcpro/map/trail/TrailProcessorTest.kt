package com.trust3.xcpro.map.trail

import com.trust3.xcpro.map.trail.domain.TrailProcessor
import com.trust3.xcpro.map.trail.domain.TrailReplayRetentionMode
import com.trust3.xcpro.map.trail.domain.TrailRenderInvalidationReason
import com.trust3.xcpro.map.trail.domain.TrailTimeBase
import com.trust3.xcpro.map.trail.domain.TrailUpdateInput
import com.trust3.xcpro.weather.wind.model.WindSource
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun live_sampling_keepsCruiseCadenceAtTwoSeconds() {
        val processor = TrailProcessor()

        val first = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 10_000L, timestampMillis = 1_000L),
                    isCircling = false,
                    timestampMillis = 1_000L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )
        val second = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 11_000L, timestampMillis = 2_000L),
                    isCircling = false,
                    timestampMillis = 2_000L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(true, first!!.sampleAdded)
        assertEquals(false, second!!.sampleAdded)
        assertEquals(null, second.invalidationReason)
    }

    @Test
    fun live_sampling_usesDenserCadenceWhileCircling() {
        val processor = TrailProcessor()

        val first = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 10_000L, timestampMillis = 1_000L),
                    isCircling = true,
                    timestampMillis = 1_000L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )
        val blocked = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 10_400L, timestampMillis = 1_400L),
                    isCircling = true,
                    timestampMillis = 1_400L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )
        val accepted = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 10_500L, timestampMillis = 1_500L),
                    isCircling = true,
                    timestampMillis = 1_500L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )

        assertNotNull(first)
        assertNotNull(blocked)
        assertNotNull(accepted)
        assertEquals(true, first!!.sampleAdded)
        assertEquals(false, blocked!!.sampleAdded)
        assertEquals(true, accepted!!.sampleAdded)
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

    @Test
    fun live_wind_is_zero_when_airspeed_source_is_not_wind() {
        val processor = TrailProcessor()
        val wind = WindState(
            vector = WindVector(east = 4.0, north = 3.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        val result = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 5_000L, timestampMillis = 5_000L),
                    airspeedSource = "GPS",
                    timestampMillis = 5_000L
                ),
                windState = wind,
                isFlying = true,
                isReplay = false
            )
        )

        assertNotNull(result)
        assertEquals(0.0, result!!.renderState.windSpeedMs, 1e-6)
    }

    @Test
    fun live_wind_is_used_when_airspeed_source_is_wind() {
        val processor = TrailProcessor()
        val wind = WindState(
            vector = WindVector(east = 4.0, north = 3.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        val first = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 6_000L, timestampMillis = 6_000L),
                    airspeedSource = "WIND",
                    timestampMillis = 6_000L
                ),
                windState = wind,
                isFlying = true,
                isReplay = false
            )
        )
        val second = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 7_000L, timestampMillis = 7_000L),
                    airspeedSource = "WIND",
                    timestampMillis = 7_000L
                ),
                windState = wind,
                isFlying = true,
                isReplay = false
            )
        )

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(0.0, first!!.renderState.windSpeedMs, 1e-6)
        assertTrue(second!!.renderState.windSpeedMs > 0.0)
    }

    @Test
    fun circling_transition_requiresFullRender_evenWithoutNewSample() {
        val processor = TrailProcessor()

        val first = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 20_000L, timestampMillis = 20_000L),
                    isCircling = false,
                    timestampMillis = 20_000L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )
        val second = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 20_100L, timestampMillis = 20_100L),
                    isCircling = true,
                    timestampMillis = 20_100L
                ),
                windState = null,
                isFlying = true,
                isReplay = false
            )
        )

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(false, second!!.sampleAdded)
        assertEquals(true, second.requiresFullRender)
        assertEquals(TrailRenderInvalidationReason.CIRCLING_CHANGED, second.invalidationReason)
    }

    @Test
    fun live_wind_change_requiresFullRender_whenDriftIsActive() {
        val processor = TrailProcessor()
        val initialWind = WindState(
            vector = WindVector(east = 0.0, north = 4.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )
        val strongerWind = initialWind.copy(
            vector = WindVector(east = 0.0, north = 10.0)
        )

        val first = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 30_000L, timestampMillis = 30_000L),
                    airspeedSource = "WIND",
                    isCircling = true,
                    timestampMillis = 30_000L
                ),
                windState = initialWind,
                isFlying = true,
                isReplay = false,
                windDriftEnabled = true
            )
        )
        val second = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 30_300L, timestampMillis = 30_300L),
                    airspeedSource = "WIND",
                    isCircling = true,
                    timestampMillis = 30_300L
                ),
                windState = strongerWind,
                isFlying = true,
                isReplay = false,
                windDriftEnabled = true
            )
        )

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(false, second!!.sampleAdded)
        assertEquals(true, second.requiresFullRender)
        assertEquals(TrailRenderInvalidationReason.WIND_CHANGED, second.invalidationReason)
    }

    @Test
    fun live_wind_isSmoothed_beforeRenderState() {
        val processor = TrailProcessor()
        val wind = WindState(
            vector = WindVector(east = 0.0, north = 10.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        val first = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 40_000L, timestampMillis = 40_000L),
                    airspeedSource = "WIND",
                    timestampMillis = 40_000L
                ),
                windState = wind,
                isFlying = true,
                isReplay = false
            )
        )
        val second = processor.update(
            TrailUpdateInput(
                data = buildCompleteFlightData(
                    gps = defaultGps(monotonicTimestampMillis = 41_000L, timestampMillis = 41_000L),
                    airspeedSource = "WIND",
                    timestampMillis = 41_000L
                ),
                windState = wind,
                isFlying = true,
                isReplay = false
            )
        )

        assertNotNull(first)
        assertNotNull(second)
        assertTrue(second!!.renderState.windSpeedMs > 0.0)
        assertTrue(second.renderState.windSpeedMs < 10.0)
    }

    @Test
    fun syntheticReplayValidation_retainsFullThermalHistory() {
        val defaultReplayCount = replayPointCountForRetentionMode(TrailReplayRetentionMode.DEFAULT)
        val syntheticReplayCount = replayPointCountForRetentionMode(TrailReplayRetentionMode.SYNTHETIC_VALIDATION)

        assertTrue(defaultReplayCount < syntheticReplayCount)
        assertEquals(1_201, syntheticReplayCount)
    }

    private fun replayPointCountForRetentionMode(mode: TrailReplayRetentionMode): Int {
        val processor = TrailProcessor()
        var lastCount = 0
        repeat(301) { index ->
            val timestampMillis = 1_000L + (index * 1_000L)
            val result = processor.update(
                TrailUpdateInput(
                    data = buildCompleteFlightData(
                        gps = defaultGps(
                            latitude = 46.0,
                            longitude = 7.0 + (index * 0.00001),
                            monotonicTimestampMillis = 0L,
                            timestampMillis = timestampMillis
                        ),
                        timestampMillis = timestampMillis
                    ),
                    windState = null,
                    isFlying = true,
                    isReplay = true,
                    replayRetentionMode = mode
                )
            )
            lastCount = result!!.renderState.points.size
        }
        return lastCount
    }
}

