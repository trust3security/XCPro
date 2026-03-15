package com.example.xcpro.sensors.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateFlightMetricsUseCaseTc30sTest {

    @Test
    fun tc30s_ignores_single_spike() {
        val useCase = newUseCase()
        var time = 0L
        var altitude = 1_000.0

        repeat(10) {
            executeMetricsRequest(
                useCase = useCase,
                currentTimeMillis = time,
                deltaTimeSeconds = 1.0,
                varioMs = 0.5,
                altitude = altitude,
                varioValidUntil = time + 500L
            )
            time += 1_000L
            altitude += 0.5
        }

        executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 1.0,
            varioMs = 15.0,
            altitude = altitude + 15.0,
            varioValidUntil = time + 500L
        )

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time + 1_000L,
            deltaTimeSeconds = 1.0,
            varioMs = 0.5,
            altitude = altitude + 15.5,
            varioValidUntil = time + 1_500L
        )

        assertTrue(result.bruttoAverage30s < 2.0)
    }

    @Test
    fun tc30s_tracks_constant_climb() {
        val useCase = newUseCase()
        var time = 0L
        var altitude = 0.0

        repeat(30) {
            executeMetricsRequest(
                useCase = useCase,
                currentTimeMillis = time,
                deltaTimeSeconds = 1.0,
                varioMs = 1.0,
                altitude = altitude,
                varioValidUntil = time + 500L
            )
            time += 1_000L
            altitude += 1.0
        }

        val result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 1.0,
            varioMs = 1.0,
            altitude = altitude,
            varioValidUntil = time + 500L
        )

        assertTrue(kotlin.math.abs(result.bruttoAverage30s - 1.0) < 0.2)
    }

    @Test
    fun tc30s_gps_tick_gating() {
        val useCase = newUseCase()
        val altitude = 1_000.0

        val first = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_000L,
            deltaTimeSeconds = 0.1,
            varioMs = 1.0,
            altitude = altitude,
            gpsTimestampMillis = 1_000L,
            varioValidUntil = 1_500L
        )

        val withoutNewGps = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 1_100L,
            deltaTimeSeconds = 0.1,
            varioMs = 5.0,
            altitude = altitude,
            gpsTimestampMillis = 1_000L,
            varioValidUntil = 1_600L
        )

        assertEquals(first.bruttoAverage30s, withoutNewGps.bruttoAverage30s, 1e-6)

        val withNewGps = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = 2_000L,
            deltaTimeSeconds = 0.9,
            varioMs = 5.0,
            altitude = altitude,
            gpsTimestampMillis = 2_000L,
            varioValidUntil = 2_500L
        )

        assertTrue(withNewGps.bruttoAverage30s > withoutNewGps.bruttoAverage30s + 0.1)
    }
}
