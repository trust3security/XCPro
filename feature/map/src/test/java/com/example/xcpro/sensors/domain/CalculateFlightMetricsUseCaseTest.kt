package com.example.xcpro.sensors.domain

import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.dfcards.filters.ModernVarioResult
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightCalculationHelpers
import com.example.xcpro.sensors.GPSData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.maplibre.android.geometry.LatLng

private fun gpsSample(timeMs: Long) = GPSData(
    latLng = LatLng(0.0, 0.0),
    altitude = AltitudeM(1000.0),
    speed = SpeedMs(20.0),
    bearing = 0.0,
    accuracy = 5f,
    timestamp = timeMs
)

private fun varioSample(vs: Double, alt: Double) = ModernVarioResult(
    altitude = alt,
    verticalSpeed = vs,
    acceleration = 0.0,
    confidence = 0.8
)

class CalculateFlightMetricsUseCaseTest {

    private fun newUseCase(
        teAnswer: (InvocationOnMock) -> Double = { invocation -> invocation.getArgument<Double>(0) }
    ): CalculateFlightMetricsUseCase {
        val sink = mock<StillAirSinkProvider> {
            on { sinkAtSpeed(any()) }.thenReturn(0.0)
        }
        val helpers = mock<FlightCalculationHelpers>()
        whenever(helpers.calculateNetto(any(), anyOrNull(), any())).thenReturn(
            FlightCalculationHelpers.NettoComputation(0.0, true)
        )
        whenever(helpers.calculateTotalEnergy(any(), any(), any(), any())).thenAnswer(teAnswer)
        whenever(helpers.calculateCurrentLD(any(), any())).thenReturn(0f)
        whenever(helpers.updateThermalState(any(), any(), any(), any())).thenAnswer { }
        whenever(helpers.updateAGL(any(), any(), any())).thenAnswer { }
        whenever(helpers.recordLocationSample(any())).thenAnswer { }
        whenever(helpers.thermalAverageCurrent).thenReturn(0f)
        whenever(helpers.thermalAverageTotal).thenReturn(0f)
        whenever(helpers.thermalGainCurrent).thenReturn(0.0)
        whenever(helpers.thermalGainValid).thenReturn(false)

        return CalculateFlightMetricsUseCase(
            flightHelpers = helpers,
            sinkProvider = sink,
            windEstimator = WindEstimator(sink)
        )
    }

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
                    varioValidUntil = time + 500
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
                    varioValidUntil = time + 500
                )
        )
        // TC30s should still reflect ~2 m/s average, not jump
        assertTrue(kotlin.math.abs(res.bruttoAverage30s - 2.0) < 1.0)
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
                varioValidUntil = time + 500
            )
        )

        assertTrue(result.teVario == null || result.varioSource != "TE")
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
                    deltaTimeSeconds = 1.0,
                    varioResult = varioSample(0.5, altitude),
                    varioGpsValue = 0.5,
                    baroResult = null,
                    windState = null,
                    varioValidUntil = time + 500
                )
            )
            time += 1000; altitude += 0.5
        }
        // spike
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time),
                currentTimeMillis = time,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(15.0, altitude + 15),
                varioGpsValue = 15.0,
                baroResult = null,
                windState = null,
                varioValidUntil = time + 500
            )
        )
        val res = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time + 1000),
                currentTimeMillis = time + 1000,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(0.5, altitude + 15.5),
                varioGpsValue = 0.5,
                baroResult = null,
                windState = null,
                varioValidUntil = time + 1500
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
                    deltaTimeSeconds = 1.0,
                    varioResult = varioSample(1.0, altitude),
                    varioGpsValue = 1.0,
                    baroResult = null,
                    windState = null,
                    varioValidUntil = time + 500
                )
            )
            time += 1_000
            altitude += 1.0
        }

        val res = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(time),
                currentTimeMillis = time,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(1.0, altitude),
                varioGpsValue = 1.0,
                baroResult = null,
                windState = null,
                varioValidUntil = time + 500
            )
        )

        assertTrue(kotlin.math.abs(res.bruttoAverage30s - 1.0) < 0.2)
    }
}
