package com.example.xcpro.sensors.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateFlightMetricsUseCaseResetTest {

    @Test
    fun reset_clears_baseline_display_smoother_state() {
        val useCase = newUseCase()
        var time = warmDisplayState(useCase)

        val preReset = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 0.1,
            varioMs = 4.0,
            altitude = 1_100.0
        )
        assertTrue(preReset.displayBaselineVario > 0.5)

        useCase.reset()
        time += 100L

        val postReset = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 0.1,
            varioMs = 0.0,
            altitude = 1_100.0
        )

        assertTrue(kotlin.math.abs(postReset.displayBaselineVario) < 0.1)
    }

    @Test
    fun reset_clears_primary_display_smoother_state() {
        val useCase = newUseCase()
        var time = warmDisplayState(useCase)

        val preReset = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 0.1,
            varioMs = 4.0,
            altitude = 1_100.0
        )
        assertTrue(preReset.displayVario > 0.5)

        useCase.reset()
        time += 100L

        val postReset = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 0.1,
            varioMs = 0.0,
            altitude = 1_100.0
        )

        assertTrue(kotlin.math.abs(postReset.displayVario) < 0.1)
    }

    @Test
    fun reset_clears_display_needle_state() {
        val useCase = newUseCase()
        var time = warmDisplayState(useCase)

        val preReset = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 0.1,
            varioMs = 4.0,
            altitude = 1_100.0
        )
        assertTrue(preReset.displayNeedleVario > 0.5)
        assertTrue(preReset.displayNeedleVarioFast > 0.5)

        useCase.reset()
        time += 100L

        val postReset = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 0.1,
            varioMs = 0.0,
            altitude = 1_100.0
        )

        assertTrue(kotlin.math.abs(postReset.displayNeedleVario) < 0.1)
        assertTrue(kotlin.math.abs(postReset.displayNeedleVarioFast) < 0.1)
    }

    @Test
    fun ground_zero_settles_to_zero_when_stationary_on_ground() {
        val useCase = newUseCase()
        var time = 1_000L

        var result = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 1.0,
            varioMs = 0.02,
            altitude = 1_000.0,
            speedMs = 0.0,
            isFlying = false
        )
        assertTrue(result.displayVario > 0.0)

        repeat(3) {
            time += 1_000L
            result = executeMetricsRequest(
                useCase = useCase,
                currentTimeMillis = time,
                deltaTimeSeconds = 1.0,
                varioMs = 0.02,
                altitude = 1_000.0,
                speedMs = 0.0,
                isFlying = false
            )
        }

        assertEquals(0.0, result.displayVario, 1e-6)
    }

    @Test
    fun reset_clears_display_netto_state() {
        val nettoValue = doubleArrayOf(3.0)
        val useCase = newUseCaseWithDynamicNetto(nettoProvider = { nettoValue[0] })
        var time = 1_000L

        repeat(12) {
            executeMetricsRequest(
                useCase = useCase,
                currentTimeMillis = time,
                deltaTimeSeconds = 0.1,
                varioMs = 0.0,
                altitude = 1_000.0
            )
            time += 100L
        }

        val preReset = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 0.1,
            varioMs = 0.0,
            altitude = 1_000.0
        )
        assertTrue(preReset.displayNetto > 0.5)

        useCase.reset()
        nettoValue[0] = 0.0
        time += 100L

        val postReset = executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 0.1,
            varioMs = 0.0,
            altitude = 1_000.0
        )

        assertTrue(kotlin.math.abs(postReset.displayNetto) < 0.1)
    }
}

private fun warmDisplayState(
    useCase: CalculateFlightMetricsUseCase,
    startTimeMillis: Long = 1_000L
): Long {
    var time = startTimeMillis
    repeat(12) {
        executeMetricsRequest(
            useCase = useCase,
            currentTimeMillis = time,
            deltaTimeSeconds = 0.1,
            varioMs = 4.0,
            altitude = 1_000.0 + it
        )
        time += 100L
    }
    return time
}
