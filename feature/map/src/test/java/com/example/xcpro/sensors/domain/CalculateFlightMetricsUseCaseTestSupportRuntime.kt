package com.example.xcpro.sensors.domain

import com.example.dfcards.filters.ModernVarioResult
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightCalculationHelpers
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.weather.wind.model.AirspeedSample
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
    valid: Boolean = true
) = AirspeedSample(
    trueMs = trueMs,
    indicatedMs = indicatedMs,
    timestampMillis = clockMillis,
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
    whenever(helpers.calculateNetto(any(), anyOrNull(), any(), any())).thenReturn(
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
