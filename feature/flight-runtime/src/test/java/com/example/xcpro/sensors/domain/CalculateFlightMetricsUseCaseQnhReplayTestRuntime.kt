package com.example.xcpro.sensors.domain

import com.example.xcpro.core.flight.calculations.BarometricAltitudeData
import com.example.xcpro.core.flight.calculations.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class CalculateFlightMetricsUseCaseQnhReplayTest {

    @Test
    fun tc30s_stable_on_qnh_change() {
        val useCase = newUseCase()
        var time = 0L
        var altitude = 1_000.0

        repeat(20) {
            executeMetricsRequest(
                useCase = useCase,
                currentTimeMillis = time,
                deltaTimeSeconds = 1.0,
                varioMs = 2.0,
                altitude = altitude,
                baroResult = calibratedBaroResult(
                    altitudeMeters = altitude,
                    qnh = 1013.25,
                    pressureAltitudeMeters = altitude,
                    lastCalibrationTime = 0L
                ),
                varioValidUntil = time + 500L
            )
            time += 1_000L
            altitude += 2.0
        }

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 1.0,
            varioMs = 2.0,
            altitude = altitude,
            baroResult = calibratedBaroResult(
                altitudeMeters = altitude + 52.0,
                qnh = 1000.0,
                pressureAltitudeMeters = altitude,
                lastCalibrationTime = time
            ),
            varioValidUntil = time + 500L
        )

        assertTrue(kotlin.math.abs(result.bruttoAverage30s - 2.0) < 1.0)
    }

    @Test
    fun qnhCalibrationAgeUsesWallTime() {
        val useCase = newUseCase()

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            wallTimeMillis = 10_000L,
            deltaTimeSeconds = 1.0,
            varioMs = 0.5,
            altitude = 1_200.0,
            baroResult = calibratedBaroResult(
                altitudeMeters = 1_200.0,
                qnh = 1013.25,
                pressureAltitudeMeters = 1_200.0,
                lastCalibrationTime = 7_000L
            ),
            varioValidUntil = 1_500L
        )

        assertEquals(3L, result.qnhCalibrationAgeSeconds)
    }

    @Test
    fun te_vario_not_used_when_only_gps_speed() {
        val useCase = newUseCase { 3.21 }

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 0L,
            deltaTimeSeconds = 1.0,
            varioMs = 0.5,
            altitude = 500.0,
            baroResult = calibratedBaroResult(
                altitudeMeters = 500.0,
                qnh = 1013.25,
                pressureAltitudeMeters = 500.0,
                lastCalibrationTime = 0L
            ),
            windState = null,
            varioValidUntil = 500L
        )

        assertTrue(result.teVario == null || result.varioSource != "TE")
    }

    @Test
    fun replayRequest_disablesOnlineTerrainLookupUpdates() {
        val (useCase, helpers) = newUseCaseWithHelpers()

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            deltaTimeSeconds = 0.1,
            varioMs = 0.4,
            altitude = 600.0,
            allowOnlineTerrainLookup = false,
            varioValidUntil = 2_000L
        )

        assertTrue(result.verticalSpeed.isFinite())
        verify(helpers, times(0)).updateAGL(any(), any(), any())
    }

    @Test
    fun absoluteBaroAltitude_remains_authoritative_when_displayAltitudeDiffers() {
        val (useCase, helpers) = newUseCaseWithHelpers()

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            deltaTimeSeconds = 1.0,
            varioMs = 0.4,
            altitude = 1_150.0,
            baroResult = calibratedBaroResult(
                altitudeMeters = 1_000.0,
                qnh = 1013.25,
                pressureAltitudeMeters = 995.0,
                lastCalibrationTime = 0L
            ),
            varioValidUntil = 2_000L
        )

        assertEquals(1_000.0, result.baroAltitude, 1e-6)
        assertEquals(1_000.0, result.navAltitude, 1e-6)
        verify(helpers).updateAGL(eq(1_000.0), any(), eq(20.0))
        verify(helpers).calculateCurrentLD(any(), eq(1_000.0), eq(1_000L))
    }
}

private fun calibratedBaroResult(
    altitudeMeters: Double,
    qnh: Double,
    pressureAltitudeMeters: Double,
    lastCalibrationTime: Long
) = BarometricAltitudeData(
    altitudeMeters = altitudeMeters,
    qnh = qnh,
    isCalibrated = true,
    pressureHPa = qnh,
    temperatureCompensated = true,
    confidenceLevel = ConfidenceLevel.MEDIUM,
    pressureAltitudeMeters = pressureAltitudeMeters,
    gpsDeltaMeters = 0.0,
    lastCalibrationTime = lastCalibrationTime
)
