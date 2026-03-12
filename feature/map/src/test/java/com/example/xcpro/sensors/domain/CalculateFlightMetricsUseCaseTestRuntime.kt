package com.example.xcpro.sensors.domain

import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.dfcards.filters.ModernVarioResult
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightCalculationHelpers
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CalculateFlightMetricsUseCaseTest {

    @Test
    fun tc30s_stable_on_qnh_change() {
        val useCase = newUseCase()
        var time = 0L
        var altitude = 1000.0
        // feed 20 samples at 2 m/s climb
        repeat(20) {
            useCase.execute(
                FlightMetricsRequest(
                    gps = gpsSample(time),
                    currentTimeMillis = time,
                    wallTimeMillis = time,
                    gpsTimestampMillis = time,
                    deltaTimeSeconds = 1.0,
                    varioResult = varioSample(2.0, altitude),
                    varioGpsValue = 2.0,
                    baroResult = BarometricAltitudeData(
                        altitudeMeters = altitude,
                        qnh = 1013.25,
                        isCalibrated = true,
                        pressureHPa = 1013.25,
                        temperatureCompensated = true,
                        confidenceLevel = ConfidenceLevel.MEDIUM,
                        pressureAltitudeMeters = altitude,
                        gpsDeltaMeters = 0.0,
                        lastCalibrationTime = 0L
                    ),
                    windState = null,
                    varioValidUntil = time + 500,
                    isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
                )
            )
            time += 1000
            altitude += 2.0
        }
        // Now change QNH sharply
        val res = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time),
                currentTimeMillis = time,
                wallTimeMillis = time,
                gpsTimestampMillis = time,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(2.0, altitude),
                varioGpsValue = 2.0,
                    baroResult = BarometricAltitudeData(
                        altitudeMeters = altitude + 52.0, // simulated jump in displayed altitude
                        qnh = 1000.0, // big QNH change
                        isCalibrated = true,
                        pressureHPa = 1000.0,
                        temperatureCompensated = true,
                        confidenceLevel = ConfidenceLevel.MEDIUM,
                        pressureAltitudeMeters = altitude, // pressure delta continues 2 m/s climb
                        gpsDeltaMeters = 0.0,
                        lastCalibrationTime = time
                    ),
                    windState = null,
                    varioValidUntil = time + 500,
                    isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
                )
        )
        // TC30s should still reflect ~2 m/s average, not jump
        assertTrue(kotlin.math.abs(res.bruttoAverage30s - 2.0) < 1.0)
    }

    @Test
    fun qnhCalibrationAgeUsesWallTime() {
        val useCase = newUseCase()
        val currentTime = 1_000L
        val wallTime = 10_000L
        val calibrationTime = 7_000L
        val altitude = 1200.0

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(currentTime),
                currentTimeMillis = currentTime,
                wallTimeMillis = wallTime,
                gpsTimestampMillis = currentTime,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(0.5, altitude),
                varioGpsValue = 0.5,
                baroResult = BarometricAltitudeData(
                    altitudeMeters = altitude,
                    qnh = 1013.25,
                    isCalibrated = true,
                    pressureHPa = 1013.25,
                    temperatureCompensated = true,
                    confidenceLevel = ConfidenceLevel.MEDIUM,
                    pressureAltitudeMeters = altitude,
                    gpsDeltaMeters = 0.0,
                    lastCalibrationTime = calibrationTime
                ),
                windState = null,
                varioValidUntil = currentTime + 500,
                isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
            )
        )

        assertEquals(3L, result.qnhCalibrationAgeSeconds)
    }

    @Test
    fun te_vario_not_used_when_only_gps_speed() {
        val teValue = 3.21
        val useCase = newUseCase { teValue }
        val time = 0L
        val altitude = 500.0

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time),
                currentTimeMillis = time,
                wallTimeMillis = time,
                gpsTimestampMillis = time,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(0.5, altitude),
                varioGpsValue = 0.5,
                baroResult = BarometricAltitudeData(
                    altitudeMeters = altitude,
                    qnh = 1013.25,
                    isCalibrated = true,
                    pressureHPa = 1013.25,
                    temperatureCompensated = true,
                    confidenceLevel = ConfidenceLevel.MEDIUM,
                    pressureAltitudeMeters = altitude,
                    gpsDeltaMeters = 0.0,
                    lastCalibrationTime = 0L
                ),
                windState = null, // no wind vector -> forces GPS fallback
                varioValidUntil = time + 500,
                isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
            )
        )

        assertTrue(result.teVario == null || result.varioSource != "TE")
    }

    @Test
    fun replayRequest_disablesOnlineTerrainLookupUpdates() {
        val (useCase, helpers) = newUseCaseWithHelpers()
        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(1_000L),
                currentTimeMillis = 1_000L,
                wallTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = null,
                allowOnlineTerrainLookup = false,
                varioValidUntil = 2_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        assertTrue(result.verticalSpeed.isFinite())
        verify(helpers, times(0)).updateAGL(any(), any(), any())
    }

    @Test
    fun reset_clears_baseline_display_smoother_state() {
        val useCase = newUseCase()
        var time = 1_000L
        repeat(12) {
            useCase.execute(
                FlightMetricsRequest(
                    gps = gpsSample(time),
                    currentTimeMillis = time,
                    wallTimeMillis = time,
                    gpsTimestampMillis = time,
                    deltaTimeSeconds = 0.1,
                    varioResult = varioSample(4.0, 1_000.0 + it),
                    varioGpsValue = 4.0,
                    baroResult = null,
                    windState = null,
                    varioValidUntil = time + 1_000L,
                    isFlying = true,
                    macCreadySetting = 0.0,
                    autoMcEnabled = false,
                    flightMode = FlightMode.CRUISE
                )
            )
            time += 100L
        }

        val preReset = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time),
                currentTimeMillis = time,
                wallTimeMillis = time,
                gpsTimestampMillis = time,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(4.0, 1_100.0),
                varioGpsValue = 4.0,
                baroResult = null,
                windState = null,
                varioValidUntil = time + 1_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        assertTrue(preReset.displayBaselineVario > 0.5)

        useCase.reset()
        time += 100L

        val postReset = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time),
                currentTimeMillis = time,
                wallTimeMillis = time,
                gpsTimestampMillis = time,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.0, 1_100.0),
                varioGpsValue = 0.0,
                baroResult = null,
                windState = null,
                varioValidUntil = time + 1_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        assertTrue(kotlin.math.abs(postReset.displayBaselineVario) < 0.1)
    }

    @Test
    fun tc30s_ignores_single_spike() {
        val useCase = newUseCase()
        var time = 0L
        var altitude = 1000.0
        // normal stable samples
        repeat(10) {
            useCase.execute(
                FlightMetricsRequest(
                    gps = gpsSample(time),
                    currentTimeMillis = time,
                    wallTimeMillis = time,
                    gpsTimestampMillis = time,
                    deltaTimeSeconds = 1.0,
                    varioResult = varioSample(0.5, altitude),
                    varioGpsValue = 0.5,
                    baroResult = null,
                    windState = null,
                    varioValidUntil = time + 500,
                    isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
                )
            )
            time += 1000; altitude += 0.5
        }
        // spike
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time),
                currentTimeMillis = time,
                wallTimeMillis = time,
                gpsTimestampMillis = time,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(15.0, altitude + 15),
                varioGpsValue = 15.0,
                baroResult = null,
                windState = null,
                varioValidUntil = time + 500,
                isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
            )
        )
        val res = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time + 1000),
                currentTimeMillis = time + 1000,
                wallTimeMillis = time + 1000,
                gpsTimestampMillis = time + 1000,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(0.5, altitude + 15.5),
                varioGpsValue = 0.5,
                baroResult = null,
                windState = null,
                varioValidUntil = time + 1500,
                isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
            )
        )
        // average should remain close to steady 0.5 m/s despite spike
        assertTrue(res.bruttoAverage30s < 2.0)
    }

    @Test
    fun tc30s_tracks_constant_climb() {
        val useCase = newUseCase()
        var time = 0L
        var altitude = 0.0

        repeat(30) { // 30 seconds of 1 m/s climb
            useCase.execute(
                FlightMetricsRequest(
                    gps = gpsSample(time),
                    currentTimeMillis = time,
                    wallTimeMillis = time,
                    gpsTimestampMillis = time,
                    deltaTimeSeconds = 1.0,
                    varioResult = varioSample(1.0, altitude),
                    varioGpsValue = 1.0,
                    baroResult = null,
                    windState = null,
                    varioValidUntil = time + 500,
                    isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
                )
            )
            time += 1_000
            altitude += 1.0
        }

        val res = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time),
                currentTimeMillis = time,
                wallTimeMillis = time,
                gpsTimestampMillis = time,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(1.0, altitude),
                varioGpsValue = 1.0,
                baroResult = null,
                windState = null,
                varioValidUntil = time + 500,
                isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
            )
        )

        assertTrue(kotlin.math.abs(res.bruttoAverage30s - 1.0) < 0.2)
    }

    @Test
    fun tc30s_gps_tick_gating() {
        val useCase = newUseCase()
        var altitude = 1000.0

        val t0 = 1000L
        val res1 = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(t0),
                currentTimeMillis = t0,
                wallTimeMillis = t0,
                gpsTimestampMillis = t0,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(1.0, altitude),
                varioGpsValue = 1.0,
                baroResult = null,
                windState = null,
                varioValidUntil = t0 + 500,
                isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
            )
        )

        // High-rate baro tick without a new GPS fix; TC30s should not advance.
        val t1 = 1100L
        val res2 = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(t0),
                currentTimeMillis = t1,
                wallTimeMillis = t1,
                gpsTimestampMillis = t0,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(5.0, altitude),
                varioGpsValue = 5.0,
                baroResult = null,
                windState = null,
                varioValidUntil = t1 + 500,
                isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
            )
        )
        assertEquals(res1.bruttoAverage30s, res2.bruttoAverage30s, 1e-6)

        // New GPS fix advances TC30s.
        val t2 = 2000L
        val res3 = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(t2),
                currentTimeMillis = t2,
                wallTimeMillis = t2,
                gpsTimestampMillis = t2,
                deltaTimeSeconds = 0.9,
                varioResult = varioSample(5.0, altitude),
                varioGpsValue = 5.0,
                baroResult = null,
                windState = null,
                varioValidUntil = t2 + 500,
                isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE
            )
        )
        assertTrue(res3.bruttoAverage30s > res2.bruttoAverage30s + 0.1)
    }

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

        glideRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            altitude = 1_000.0,
            varioMs = 2.0,
            macCreadySetting = 0.0,
            windState = wind
        )
        glideRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            altitude = 1_002.0,
            varioMs = 2.0,
            macCreadySetting = 0.0,
            windState = wind
        )

        useCase.reset()

        val afterReset = glideRequest(
            useCase = useCase,
            currentTimeMillis = 3_000L,
            altitude = 1_004.0,
            varioMs = 2.0,
            macCreadySetting = 0.0,
            windState = wind
        )

        assertTrue(afterReset.levoNettoValid)
        val freshUseCase = newUseCaseWithGlideSupport(sinkProvider)
        val fresh = glideRequest(
            useCase = freshUseCase,
            currentTimeMillis = 3_000L,
            altitude = 1_004.0,
            varioMs = 2.0,
            macCreadySetting = 0.0,
            windState = wind
        )

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

        glideRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            altitude = 1_000.0,
            varioMs = 1.0,
            macCreadySetting = 0.0,
            windState = null
        )
        glideRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            altitude = 1_001.0,
            varioMs = 1.0,
            macCreadySetting = 4.0,
            windState = null
        )

        useCase.reset()

        val afterReset = glideRequest(
            useCase = useCase,
            currentTimeMillis = 3_000L,
            altitude = 1_002.0,
            varioMs = 1.0,
            macCreadySetting = 4.0,
            windState = null
        )

        assertTrue(afterReset.speedToFlyValid)
        val freshUseCase = newUseCaseWithGlideSupport(sinkProvider)
        val fresh = glideRequest(
            useCase = freshUseCase,
            currentTimeMillis = 3_000L,
            altitude = 1_002.0,
            varioMs = 1.0,
            macCreadySetting = 4.0,
            windState = null
        )

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

        val firstExit = runCirclingEpisode(
            useCase = useCase,
            startTimeMillis = 1_000L,
            liftRate = 2.0
        )
        assertTrue(firstExit.autoMcValid)
        assertEquals(2.0, firstExit.autoMcMs, 0.05)

        useCase.reset()
        thermalLift[0] = 3.0

        val afterReset = runCirclingEpisode(
            useCase = useCase,
            startTimeMillis = 60_000L,
            liftRate = 3.0
        )
        assertTrue(afterReset.autoMcValid)

        val freshUseCase = newUseCaseWithDynamicThermal(
            sinkProvider = sinkProvider,
            thermalLiftProvider = { 3.0 },
            thermalValidProvider = { true }
        )
        val fresh = runCirclingEpisode(
            useCase = freshUseCase,
            startTimeMillis = 60_000L,
            liftRate = 3.0
        )

        assertTrue(fresh.autoMcValid)
        assertEquals(fresh.autoMcMs, afterReset.autoMcMs, 0.05)
    }
}

private fun newUseCaseWithGlideSupport(
    sinkProvider: StillAirSinkProvider
): CalculateFlightMetricsUseCase {
    return newUseCaseWithDynamicThermal(
        sinkProvider = sinkProvider,
        thermalLiftProvider = { 0.0 },
        thermalValidProvider = { false }
    )
}

private fun newUseCaseWithDynamicThermal(
    sinkProvider: StillAirSinkProvider,
    thermalLiftProvider: () -> Double,
    thermalValidProvider: () -> Boolean
): CalculateFlightMetricsUseCase {
    val helpers = mock<FlightCalculationHelpers>()
    whenever(helpers.calculateNetto(any(), anyOrNull(), any(), any())).thenReturn(
        FlightCalculationHelpers.NettoComputation(0.0, true)
    )
    whenever(helpers.calculateTotalEnergy(any(), any(), any(), any())).thenAnswer { invocation ->
        invocation.getArgument<Double>(0)
    }
    whenever(helpers.calculateCurrentLD(any(), any(), any())).thenReturn(0f)
    whenever(helpers.updateThermalState(any(), any(), any(), any(), any())).thenAnswer { }
    whenever(helpers.updateAGL(any(), any(), any())).thenAnswer { }
    whenever(helpers.recordLocationSample(any(), any())).thenAnswer { }
    whenever(helpers.thermalAverageCurrent).thenReturn(0f)
    whenever(helpers.thermalAverageTotal).thenReturn(0f)
    whenever(helpers.thermalGainCurrent).thenReturn(0.0)
    whenever(helpers.thermalGainValid).thenReturn(false)
    whenever(helpers.currentThermalLiftRate).thenAnswer { thermalLiftProvider() }
    whenever(helpers.currentThermalValid).thenAnswer { thermalValidProvider() }

    return CalculateFlightMetricsUseCase(
        flightHelpers = helpers,
        sinkProvider = sinkProvider,
        windEstimator = WindEstimator()
    )
}

private fun glideRequest(
    useCase: CalculateFlightMetricsUseCase,
    currentTimeMillis: Long,
    altitude: Double,
    varioMs: Double,
    macCreadySetting: Double,
    windState: WindState?
) = useCase.execute(
    FlightMetricsRequest(
        gps = gpsSample(timeMs = currentTimeMillis, speedMs = 30.0),
        currentTimeMillis = currentTimeMillis,
        wallTimeMillis = currentTimeMillis,
        gpsTimestampMillis = currentTimeMillis,
        deltaTimeSeconds = 1.0,
        varioResult = varioSample(varioMs, altitude),
        varioGpsValue = varioMs,
        baroResult = null,
        windState = windState,
        varioValidUntil = currentTimeMillis + 1_000L,
        isFlying = true,
        macCreadySetting = macCreadySetting,
        autoMcEnabled = false,
        flightMode = FlightMode.CRUISE
    )
)

private fun runCirclingEpisode(
    useCase: CalculateFlightMetricsUseCase,
    startTimeMillis: Long,
    liftRate: Double
): FlightMetricsResult {
    var time = startTimeMillis
    var bearing = 0.0
    var result = glideRequestWithBearing(
        useCase = useCase,
        currentTimeMillis = time,
        altitude = 1_000.0,
        varioMs = liftRate,
        bearing = bearing
    )

    repeat(18) {
        time += 1_000L
        bearing = (bearing + 12.0) % 360.0
        result = glideRequestWithBearing(
            useCase = useCase,
            currentTimeMillis = time,
            altitude = 1_000.0 + (it + 1) * liftRate,
            varioMs = liftRate,
            bearing = bearing
        )
    }

    repeat(16) {
        time += 1_000L
        result = glideRequestWithBearing(
            useCase = useCase,
            currentTimeMillis = time,
            altitude = 1_100.0 + (it + 1) * liftRate,
            varioMs = liftRate,
            bearing = bearing
        )
    }

    return result
}

private fun glideRequestWithBearing(
    useCase: CalculateFlightMetricsUseCase,
    currentTimeMillis: Long,
    altitude: Double,
    varioMs: Double,
    bearing: Double
): FlightMetricsResult =
    useCase.execute(
        FlightMetricsRequest(
            gps = gpsSample(timeMs = currentTimeMillis, speedMs = 30.0).copy(bearing = bearing),
            currentTimeMillis = currentTimeMillis,
            wallTimeMillis = currentTimeMillis,
            gpsTimestampMillis = currentTimeMillis,
            deltaTimeSeconds = 1.0,
            varioResult = varioSample(varioMs, altitude),
            varioGpsValue = varioMs,
            baroResult = null,
            windState = null,
            varioValidUntil = currentTimeMillis + 1_000L,
            isFlying = true,
            macCreadySetting = 0.0,
            autoMcEnabled = true,
            flightMode = FlightMode.CRUISE
        )
    )
