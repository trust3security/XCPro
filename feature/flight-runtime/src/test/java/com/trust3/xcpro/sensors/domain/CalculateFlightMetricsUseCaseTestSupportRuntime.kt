package com.trust3.xcpro.sensors.domain

import com.trust3.xcpro.core.flight.calculations.BarometricAltitudeData
import com.trust3.xcpro.core.flight.filters.ModernVarioResult
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.glider.SpeedBoundsMs
import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.sensors.FlightCalculationHelpers
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import com.trust3.xcpro.weather.wind.model.WindState
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun gpsSample(timeMs: Long, speedMs: Double = 20.0) = GPSData(
    position = GeoPoint(0.0, 0.0),
    altitude = AltitudeM(1000.0),
    speed = SpeedMs(speedMs),
    bearing = 0.0,
    accuracy = 5f,
    timestamp = timeMs
)

internal fun varioSample(vs: Double, alt: Double) = ModernVarioResult(
    altitude = alt,
    verticalSpeed = vs,
    acceleration = 0.0,
    confidence = 0.8
)

internal fun airspeedSample(
    trueMs: Double,
    indicatedMs: Double,
    clockMillis: Long,
    timestampMillis: Long = clockMillis,
    valid: Boolean = true
) = AirspeedSample(
    trueMs = trueMs,
    indicatedMs = indicatedMs,
    timestampMillis = timestampMillis,
    clockMillis = clockMillis,
    valid = valid
)

internal fun newUseCase(
    teAnswer: (InvocationOnMock) -> Double = { invocation -> invocation.getArgument<Double>(0) }
): CalculateFlightMetricsUseCase = newUseCaseWithHelpers(teAnswer).first

internal fun newUseCaseWithHelpers(
    teAnswer: (InvocationOnMock) -> Double = { invocation -> invocation.getArgument<Double>(0) }
): Pair<CalculateFlightMetricsUseCase, FlightCalculationHelpers> {
    val sink = mock<StillAirSinkProvider> {
        on { sinkAtSpeed(any()) }.thenReturn(0.0)
        on { iasBoundsMs() }.thenReturn(null)
        on { ldAtSpeed(any()) }.thenReturn(null)
        on { bestLd() }.thenReturn(null)
    }
    val helpers = mock<FlightCalculationHelpers>()
    whenever(helpers.calculateNetto(any(), anyOrNull(), any(), any(), any())).thenReturn(
        FlightCalculationHelpers.NettoComputation(0.0, true)
    )
    whenever(helpers.calculateTotalEnergy(any(), any(), any(), any())).thenAnswer(teAnswer)
    whenever(helpers.calculateCurrentLD(any(), any(), any())).thenReturn(0f)
    whenever(helpers.updateThermalState(any(), any(), any(), any(), any())).thenAnswer { }
    whenever(helpers.updateAGL(any(), any(), any())).thenAnswer { }
    whenever(helpers.recordLocationSample(any(), any())).thenAnswer { }
    whenever(helpers.thermalAverageCurrent).thenReturn(0f)
    whenever(helpers.thermalAverageTotal).thenReturn(0f)
    whenever(helpers.thermalGainCurrent).thenReturn(0.0)
    whenever(helpers.thermalGainValid).thenReturn(false)

    val useCase = CalculateFlightMetricsUseCase(
        flightHelpers = helpers,
        sinkProvider = sink,
        windEstimator = WindEstimator()
    )
    return useCase to helpers
}

internal fun executeMetricsRequest(
    useCase: CalculateFlightMetricsUseCase,
    currentTimeMillis: Long,
    deltaTimeSeconds: Double,
    varioMs: Double,
    altitude: Double,
    gpsTimestampMillis: Long = currentTimeMillis,
    wallTimeMillis: Long = currentTimeMillis,
    speedMs: Double = 20.0,
    baroResult: BarometricAltitudeData? = null,
    windState: WindState? = null,
    externalAirspeedSample: AirspeedSample? = null,
    allowOnlineTerrainLookup: Boolean = true,
    varioValidUntil: Long = currentTimeMillis + 1_000L,
    isFlying: Boolean = true,
    macCreadySetting: Double = 0.0,
    autoMcEnabled: Boolean = false,
    teCompensationEnabled: Boolean = true,
    flightMode: FlightMode = FlightMode.CRUISE
): FlightMetricsResult = useCase.execute(
    FlightMetricsRequest(
        gps = gpsSample(timeMs = gpsTimestampMillis, speedMs = speedMs),
        currentTimeMillis = currentTimeMillis,
        wallTimeMillis = wallTimeMillis,
        gpsTimestampMillis = gpsTimestampMillis,
        deltaTimeSeconds = deltaTimeSeconds,
        varioResult = varioSample(varioMs, altitude),
        varioGpsValue = varioMs,
        baroResult = baroResult,
        windState = windState,
        externalAirspeedSample = externalAirspeedSample,
        allowOnlineTerrainLookup = allowOnlineTerrainLookup,
        varioValidUntil = varioValidUntil,
        isFlying = isFlying,
        macCreadySetting = macCreadySetting,
        autoMcEnabled = autoMcEnabled,
        teCompensationEnabled = teCompensationEnabled,
        flightMode = flightMode
    )
)

internal fun newUseCaseWithGlideSupport(
    sinkProvider: StillAirSinkProvider
): CalculateFlightMetricsUseCase {
    return newUseCaseWithDynamicThermal(
        sinkProvider = sinkProvider,
        thermalLiftProvider = { 0.0 },
        thermalValidProvider = { false }
    )
}

internal fun newUseCaseWithDynamicThermal(
    sinkProvider: StillAirSinkProvider,
    thermalLiftProvider: () -> Double,
    thermalValidProvider: () -> Boolean
): CalculateFlightMetricsUseCase {
    val helpers = mock<FlightCalculationHelpers>()
    whenever(helpers.calculateNetto(any(), anyOrNull(), any(), any(), any())).thenReturn(
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

internal fun newUseCaseWithDynamicNetto(
    nettoProvider: () -> Double,
    nettoValidProvider: () -> Boolean = { true }
): CalculateFlightMetricsUseCase {
    val sink = mock<StillAirSinkProvider> {
        on { sinkAtSpeed(any()) }.thenReturn(0.0)
        on { iasBoundsMs() }.thenReturn(null)
        on { ldAtSpeed(any()) }.thenReturn(null)
        on { bestLd() }.thenReturn(null)
    }
    val helpers = mock<FlightCalculationHelpers>()
    whenever(helpers.calculateNetto(any(), anyOrNull(), any(), any(), any())).thenAnswer {
        FlightCalculationHelpers.NettoComputation(nettoProvider(), nettoValidProvider())
    }
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
    whenever(helpers.currentThermalLiftRate).thenReturn(0.0)
    whenever(helpers.currentThermalValid).thenReturn(false)

    return CalculateFlightMetricsUseCase(
        flightHelpers = helpers,
        sinkProvider = sink,
        windEstimator = WindEstimator()
    )
}

internal fun glideRequest(
    useCase: CalculateFlightMetricsUseCase,
    currentTimeMillis: Long,
    altitude: Double,
    varioMs: Double,
    macCreadySetting: Double,
    windState: WindState?
): FlightMetricsResult = executeMetricsRequest(
    useCase = useCase,
    currentTimeMillis = currentTimeMillis,
    deltaTimeSeconds = 1.0,
    varioMs = varioMs,
    altitude = altitude,
    speedMs = 30.0,
    windState = windState,
    macCreadySetting = macCreadySetting
)

internal fun runCirclingEpisode(
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

internal fun glideRequestWithBearing(
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
