package com.trust3.xcpro.sensors.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightMetricsDisplayRuntimeTest {

    @Test
    fun ground_zero_settles_only_primary_display_vario() {
        val runtime = FlightMetricsDisplayRuntime()
        var outputs = runtime.update(
            bruttoVario = 0.02,
            varioValid = true,
            baselineVario = 0.02,
            baselineVarioValid = true,
            rawDisplayNetto = 0.02,
            nettoValid = true,
            deltaTimeSeconds = 1.0,
            isFlying = false,
            groundSpeedMs = 0.0
        )
        assertTrue(outputs.displayVario > 0.0)

        repeat(3) {
            outputs = runtime.update(
                bruttoVario = 0.02,
                varioValid = true,
                baselineVario = 0.02,
                baselineVarioValid = true,
                rawDisplayNetto = 0.02,
                nettoValid = true,
                deltaTimeSeconds = 1.0,
                isFlying = false,
                groundSpeedMs = 0.0
            )
        }

        assertEquals(0.0, outputs.displayVario, 1e-6)
        assertTrue(outputs.displayBaselineVario > 0.0)
        assertTrue(outputs.displayNetto > 0.0)
        assertTrue(outputs.displayNeedleVario > 0.0)
        assertTrue(outputs.displayNeedleVarioFast > 0.0)
    }

    @Test
    fun vario_and_netto_channels_remain_independent() {
        val runtime = FlightMetricsDisplayRuntime()

        val warmed = runtime.update(
            bruttoVario = 4.0,
            varioValid = true,
            baselineVario = 0.0,
            baselineVarioValid = true,
            rawDisplayNetto = 0.0,
            nettoValid = true,
            deltaTimeSeconds = 0.1,
            isFlying = true,
            groundSpeedMs = 20.0
        )

        assertTrue(warmed.displayVario > 0.5)
        assertEquals(0.0, warmed.displayNetto, 1e-6)

        val baselineOnly = runtime.update(
            bruttoVario = 0.0,
            varioValid = true,
            baselineVario = 2.0,
            baselineVarioValid = true,
            rawDisplayNetto = 0.0,
            nettoValid = true,
            deltaTimeSeconds = 0.1,
            isFlying = true,
            groundSpeedMs = 20.0
        )

        assertTrue(baselineOnly.displayVario > 0.0)
        assertTrue(baselineOnly.displayBaselineVario > 0.0)
        assertEquals(0.0, baselineOnly.displayNetto, 1e-6)
    }

    @Test
    fun needles_track_brutto_even_when_ground_zero_forces_display_to_zero() {
        val runtime = FlightMetricsDisplayRuntime()
        var outputs = runtime.update(
            bruttoVario = 0.02,
            varioValid = true,
            baselineVario = 0.0,
            baselineVarioValid = true,
            rawDisplayNetto = 0.0,
            nettoValid = true,
            deltaTimeSeconds = 1.0,
            isFlying = false,
            groundSpeedMs = 0.0
        )

        repeat(3) {
            outputs = runtime.update(
                bruttoVario = 0.02,
                varioValid = true,
                baselineVario = 0.0,
                baselineVarioValid = true,
                rawDisplayNetto = 0.0,
                nettoValid = true,
                deltaTimeSeconds = 1.0,
                isFlying = false,
                groundSpeedMs = 0.0
            )
        }

        assertEquals(0.0, outputs.displayVario, 1e-6)
        assertTrue(outputs.displayNeedleVario > 0.0)
        assertTrue(outputs.displayNeedleVarioFast > 0.0)
    }

    @Test
    fun invalid_inputs_decay_through_helper_surface() {
        val runtime = FlightMetricsDisplayRuntime()

        val warmed = runtime.update(
            bruttoVario = 4.0,
            varioValid = true,
            baselineVario = 3.0,
            baselineVarioValid = true,
            rawDisplayNetto = 2.0,
            nettoValid = true,
            deltaTimeSeconds = 0.2,
            isFlying = true,
            groundSpeedMs = 25.0
        )
        assertTrue(warmed.displayVario > 0.0)
        assertTrue(warmed.displayBaselineVario > 0.0)
        assertTrue(warmed.displayNetto > 0.0)
        assertTrue(warmed.displayNeedleVario > 0.0)

        val decayed = runtime.update(
            bruttoVario = 0.0,
            varioValid = false,
            baselineVario = 0.0,
            baselineVarioValid = false,
            rawDisplayNetto = 0.0,
            nettoValid = false,
            deltaTimeSeconds = 0.2,
            isFlying = true,
            groundSpeedMs = 25.0
        )

        assertTrue(decayed.displayVario < warmed.displayVario)
        assertTrue(decayed.displayBaselineVario < warmed.displayBaselineVario)
        assertTrue(decayed.displayNetto < warmed.displayNetto)
        assertTrue(decayed.displayNeedleVario < warmed.displayNeedleVario)
        assertTrue(decayed.displayNeedleVarioFast < warmed.displayNeedleVarioFast)
    }

    @Test
    fun reset_clears_all_display_state() {
        val runtime = FlightMetricsDisplayRuntime()
        repeat(6) {
            runtime.update(
                bruttoVario = 4.0,
                varioValid = true,
                baselineVario = 3.0,
                baselineVarioValid = true,
                rawDisplayNetto = 2.0,
                nettoValid = true,
                deltaTimeSeconds = 0.2,
                isFlying = true,
                groundSpeedMs = 25.0
            )
        }

        runtime.reset()

        val afterReset = runtime.update(
            bruttoVario = 0.0,
            varioValid = true,
            baselineVario = 0.0,
            baselineVarioValid = true,
            rawDisplayNetto = 0.0,
            nettoValid = true,
            deltaTimeSeconds = 0.2,
            isFlying = true,
            groundSpeedMs = 25.0
        )

        assertEquals(0.0, afterReset.displayVario, 1e-6)
        assertEquals(0.0, afterReset.displayBaselineVario, 1e-6)
        assertEquals(0.0, afterReset.displayNetto, 1e-6)
        assertEquals(0.0, afterReset.displayNeedleVario, 1e-6)
        assertEquals(0.0, afterReset.displayNeedleVarioFast, 1e-6)
    }
}
