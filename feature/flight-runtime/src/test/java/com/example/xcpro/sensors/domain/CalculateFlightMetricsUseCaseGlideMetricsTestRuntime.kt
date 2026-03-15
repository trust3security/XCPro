package com.example.xcpro.sensors.domain

import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateFlightMetricsUseCaseGlideMetricsTest {

    @Test
    fun reset_matches_fresh_use_case_for_levo_netto() {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = 1.0
            override fun iasBoundsMs(): SpeedBoundsMs? = SpeedBoundsMs(minMs = 20.0, maxMs = 50.0)
        }
        val useCase = newUseCaseWithGlideSupport(sinkProvider)
        val wind = WindState(
            vector = WindVector(east = 2.0, north = 0.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        glideRequest(useCase, 1_000L, 1_000.0, 2.0, 0.0, wind)
        glideRequest(useCase, 2_000L, 1_002.0, 2.0, 0.0, wind)

        useCase.reset()

        val afterReset = glideRequest(useCase, 3_000L, 1_004.0, 2.0, 0.0, wind)
        assertTrue(afterReset.levoNettoValid)

        val freshUseCase = newUseCaseWithGlideSupport(sinkProvider)
        val fresh = glideRequest(freshUseCase, 3_000L, 1_004.0, 2.0, 0.0, wind)
        assertTrue(fresh.levoNettoValid)

        assertEquals(fresh.levoNettoMs, afterReset.levoNettoMs, 1e-3)
    }

    @Test
    fun reset_matches_fresh_use_case_for_speed_to_fly() {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = 0.5 + 0.02 * airspeedMs
            override fun iasBoundsMs(): SpeedBoundsMs? = SpeedBoundsMs(minMs = 20.0, maxMs = 50.0)
        }
        val useCase = newUseCaseWithGlideSupport(sinkProvider)

        glideRequest(useCase, 1_000L, 1_000.0, 1.0, 0.0, null)
        glideRequest(useCase, 2_000L, 1_001.0, 1.0, 4.0, null)

        useCase.reset()

        val afterReset = glideRequest(useCase, 3_000L, 1_002.0, 1.0, 4.0, null)
        assertTrue(afterReset.speedToFlyValid)

        val freshUseCase = newUseCaseWithGlideSupport(sinkProvider)
        val fresh = glideRequest(freshUseCase, 3_000L, 1_002.0, 1.0, 4.0, null)
        assertTrue(fresh.speedToFlyValid)

        assertEquals(fresh.speedToFlyIasMs, afterReset.speedToFlyIasMs, 1e-3)
    }

    @Test
    fun reset_clears_auto_mc_history_at_use_case_boundary() {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = 0.5
            override fun iasBoundsMs(): SpeedBoundsMs? = SpeedBoundsMs(minMs = 20.0, maxMs = 50.0)
        }
        val thermalLift = doubleArrayOf(2.0)
        val thermalValid = booleanArrayOf(true)
        val useCase = newUseCaseWithDynamicThermal(
            sinkProvider = sinkProvider,
            thermalLiftProvider = { thermalLift[0] },
            thermalValidProvider = { thermalValid[0] }
        )

        val firstExit = runCirclingEpisode(useCase, 1_000L, 2.0)
        assertTrue(firstExit.autoMcValid)
        assertEquals(2.0, firstExit.autoMcMs, 0.05)

        useCase.reset()
        thermalLift[0] = 3.0

        val afterReset = runCirclingEpisode(useCase, 60_000L, 3.0)
        assertTrue(afterReset.autoMcValid)

        val freshUseCase = newUseCaseWithDynamicThermal(
            sinkProvider = sinkProvider,
            thermalLiftProvider = { 3.0 },
            thermalValidProvider = { true }
        )
        val fresh = runCirclingEpisode(freshUseCase, 60_000L, 3.0)
        assertTrue(fresh.autoMcValid)

        assertEquals(fresh.autoMcMs, afterReset.autoMcMs, 0.05)
    }
}
