package com.example.xcpro.sensors.domain

import com.example.xcpro.common.flight.FlightMode

import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightCalculationHelpers
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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

    @Test
    fun owner_path_marks_glide_metrics_valid_when_runtime_has_real_values() {
        val useCase = newUseCaseForMetricValidity(
            currentLd = 32f,
            polarLdCurrentSpeed = 37.0,
            polarBestLd = 44.0
        )

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            deltaTimeSeconds = 1.0,
            varioMs = 1.0,
            altitude = 1_000.0,
            externalAirspeedSample = airspeedSample(
                trueMs = 27.0,
                indicatedMs = 25.0,
                clockMillis = 1_000L
            )
        )

        assertTrue(result.currentLDValid)
        assertTrue(result.polarLdCurrentSpeedValid)
        assertTrue(result.polarBestLdValid)
        assertEquals(32f, result.calculatedLD, 1e-6f)
        assertEquals(37f, result.polarLdCurrentSpeed, 1e-6f)
        assertEquals(44f, result.polarBestLd, 1e-6f)
    }

    @Test
    fun owner_path_marks_glide_metrics_invalid_when_runtime_has_no_authoritative_value() {
        val useCase = newUseCaseForMetricValidity(
            currentLd = 0f,
            polarLdCurrentSpeed = null,
            polarBestLd = null
        )

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            deltaTimeSeconds = 1.0,
            varioMs = 1.0,
            altitude = 1_000.0
        )

        assertFalse(result.currentLDValid)
        assertFalse(result.polarLdCurrentSpeedValid)
        assertFalse(result.polarBestLdValid)
        assertEquals(0f, result.calculatedLD, 1e-6f)
        assertEquals(0f, result.polarLdCurrentSpeed, 1e-6f)
        assertEquals(0f, result.polarBestLd, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_invalid_when_runtime_value_is_non_finite() {
        val useCase = newUseCaseForMetricValidity(
            currentLd = Float.POSITIVE_INFINITY,
            polarLdCurrentSpeed = null,
            polarBestLd = null
        )

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            deltaTimeSeconds = 1.0,
            varioMs = 1.0,
            altitude = 1_000.0
        )

        assertFalse(result.currentLDValid)
        assertTrue(result.calculatedLD.isInfinite())
    }

    @Test
    fun owner_path_computes_current_ld_air_from_non_gps_true_airspeed_and_te_vario() {
        val useCase = newUseCase()

        executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0
        )
        val result = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            bearing = 0.0
        )

        assertEquals("SENSOR", result.airspeedSourceLabel)
        assertTrue(result.currentLDAirValid)
        assertEquals(13f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_computes_current_ld_air_from_tas_only_external_sample_and_te_vario() {
        val useCase = newUseCase()

        executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0,
            externalAirspeedSample = airspeedSample(
                trueMs = 26.0,
                indicatedMs = Double.NaN,
                clockMillis = 900L
            )
        )
        val result = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            bearing = 0.0,
            externalAirspeedSample = airspeedSample(
                trueMs = 26.0,
                indicatedMs = Double.NaN,
                clockMillis = 1_900L
            )
        )

        assertEquals("SENSOR", result.airspeedSourceLabel)
        assertTrue(result.currentLDAirValid)
        assertEquals(13f, result.currentLDAir, 1e-6f)
        assertTrue(result.indicatedAirspeedMs.isNaN())
    }

    @Test
    fun owner_path_warms_up_te_vario_on_first_eligible_sample_then_enables_ld_vario() {
        val useCase = newUseCase()

        val first = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0
        )
        val second = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            bearing = 0.0
        )

        assertEquals("SENSOR", first.airspeedSourceLabel)
        assertEquals(null, first.teVario)
        assertFalse(first.currentLDAirValid)
        assertEquals(0f, first.currentLDAir, 1e-6f)

        assertEquals("SENSOR", second.airspeedSourceLabel)
        assertEquals(-2.0, second.teVario!!, 1e-6)
        assertTrue(second.currentLDAirValid)
        assertEquals(13f, second.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_air_invalid_on_gps_fallback_airspeed() {
        val useCase = newUseCase()

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            deltaTimeSeconds = 1.0,
            varioMs = -2.0,
            altitude = 1_000.0,
            speedMs = 20.0
        )

        assertEquals("GPS", result.airspeedSourceLabel)
        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_keeps_ld_curr_valid_when_ld_vario_is_invalid_on_same_frame() {
        val useCase = newUseCaseForMetricValidity(
            currentLd = 32f,
            polarLdCurrentSpeed = null,
            polarBestLd = null
        )

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            deltaTimeSeconds = 1.0,
            varioMs = -2.0,
            altitude = 1_000.0,
            speedMs = 20.0
        )

        assertTrue(result.currentLDValid)
        assertEquals(32f, result.calculatedLD, 1e-6f)
        assertEquals("GPS", result.airspeedSourceLabel)
        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_air_invalid_when_tas_is_not_authoritative() {
        val useCase = newUseCase()

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            deltaTimeSeconds = 1.0,
            varioMs = -2.0,
            altitude = 1_000.0,
            speedMs = 0.0
        )

        assertFalse(result.tasValid)
        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_air_invalid_when_te_vario_is_non_finite() {
        val useCase = newUseCase { Double.NaN }

        executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0
        )
        val result = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            bearing = 0.0
        )

        assertTrue(result.teVario!!.isNaN())
        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_air_invalid_when_true_airspeed_is_too_low() {
        val useCase = newUseCase()

        executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0,
            externalAirspeedSample = airspeedSample(trueMs = 5.0, indicatedMs = 5.0, clockMillis = 900L)
        )
        val result = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            bearing = 0.0,
            externalAirspeedSample = airspeedSample(trueMs = 5.0, indicatedMs = 5.0, clockMillis = 1_900L)
        )

        assertEquals("SENSOR", result.airspeedSourceLabel)
        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_air_invalid_when_te_sink_is_too_small() {
        val useCase = newUseCase()

        executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0,
            varioMs = -0.15
        )
        val result = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            bearing = 0.0,
            varioMs = -0.15
        )

        assertEquals(-0.15, result.teVario!!, 1e-6)
        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_air_invalid_while_turning() {
        val useCase = newUseCase()

        executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0
        )
        val result = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            bearing = 20.0
        )

        assertFalse(result.isCircling)
        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_air_invalid_while_circling() {
        val useCase = newUseCase()
        var result = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0
        )
        var time = 1_000L
        var bearing = 0.0

        repeat(18) {
            time += 1_000L
            bearing = (bearing + 12.0) % 360.0
            result = executeAirLdRequest(
                useCase = useCase,
                currentTimeMillis = time,
                bearing = bearing
            )
        }

        assertTrue(result.isCircling)
        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_air_invalid_when_not_flying() {
        val useCase = newUseCase()

        executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0,
            isFlying = false
        )
        val result = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            bearing = 0.0,
            isFlying = false
        )

        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    @Test
    fun owner_path_marks_current_ld_air_invalid_when_te_is_disabled() {
        val useCase = newUseCase()

        executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            bearing = 0.0,
            teCompensationEnabled = false
        )
        val result = executeAirLdRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            bearing = 0.0,
            teCompensationEnabled = false
        )

        assertEquals("SENSOR", result.airspeedSourceLabel)
        assertEquals(null, result.teVario)
        assertFalse(result.currentLDAirValid)
        assertEquals(0f, result.currentLDAir, 1e-6f)
    }

    private fun newUseCaseForMetricValidity(
        currentLd: Float,
        polarLdCurrentSpeed: Double?,
        polarBestLd: Double?
    ): CalculateFlightMetricsUseCase {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = 0.0
            override fun iasBoundsMs(): SpeedBoundsMs? = null
            override fun ldAtSpeed(airspeedMs: Double): Double? = polarLdCurrentSpeed
            override fun bestLd(): Double? = polarBestLd
        }
        val helpers = mock<FlightCalculationHelpers>()
        whenever(helpers.calculateNetto(any(), anyOrNull(), any(), any())).thenReturn(
            FlightCalculationHelpers.NettoComputation(0.0, true)
        )
        whenever(helpers.calculateTotalEnergy(any(), any(), any(), any())).thenAnswer { invocation ->
            invocation.getArgument<Double>(0)
        }
        whenever(helpers.calculateCurrentLD(any(), any(), any())).thenReturn(currentLd)
        whenever(helpers.updateThermalState(any(), any(), any(), any(), any())).thenAnswer { }
        whenever(helpers.updateAGL(any(), any(), any())).thenAnswer { }
        whenever(helpers.recordLocationSample(any(), any())).thenAnswer { }
        whenever(helpers.thermalAverageCurrent).thenReturn(0f)
        whenever(helpers.thermalAverageTotal).thenReturn(0f)
        whenever(helpers.thermalGainCurrent).thenReturn(0.0)
        whenever(helpers.thermalGainValid).thenReturn(false)
        whenever(helpers.currentThermalLiftRate).thenReturn(0.0)
        whenever(helpers.currentThermalValid).thenReturn(false)

        return CalculateFlightMetricsUseCase(
            flightHelpers = helpers,
            sinkProvider = sinkProvider,
            windEstimator = WindEstimator()
        )
    }

    private fun executeAirLdRequest(
        useCase: CalculateFlightMetricsUseCase,
        currentTimeMillis: Long,
        bearing: Double,
        varioMs: Double = -2.0,
        altitude: Double = 1_000.0,
        externalAirspeedSample: com.example.xcpro.weather.wind.model.AirspeedSample = airspeedSample(
            trueMs = 26.0,
            indicatedMs = 24.0,
            clockMillis = currentTimeMillis - 100L
        ),
        isFlying: Boolean = true,
        teCompensationEnabled: Boolean = true
    ): FlightMetricsResult {
        return useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(timeMs = currentTimeMillis, speedMs = 20.0).copy(bearing = bearing),
                currentTimeMillis = currentTimeMillis,
                wallTimeMillis = currentTimeMillis,
                gpsTimestampMillis = currentTimeMillis,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(varioMs, altitude),
                varioGpsValue = varioMs,
                baroResult = null,
                windState = null,
                externalAirspeedSample = externalAirspeedSample,
                varioValidUntil = currentTimeMillis + 1_000L,
                isFlying = isFlying,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                teCompensationEnabled = teCompensationEnabled,
                flightMode = FlightMode.CRUISE
            )
        )
    }
}
