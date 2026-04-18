package com.trust3.xcpro.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayVarioSmootherTest {

    @Test
    fun clamps_to_configured_limit() {
        val smoother = DisplayVarioSmoother(
            smoothTimeSeconds = 0.6,
            decayFactor = 0.9,
            clamp = 7.0
        )

        val value = smoother.smoothVario(raw = 10.0, deltaTime = 0.5, isValid = true)
        assertEquals(7.0, value, 0.0001)
    }

    @Test
    fun decays_when_invalid() {
        val smoother = DisplayVarioSmoother(
            smoothTimeSeconds = 0.6,
            decayFactor = 0.5,
            clamp = 7.0
        )

        val first = smoother.smoothVario(raw = 4.0, deltaTime = 0.3, isValid = true)
        val decayed = smoother.smoothVario(raw = first, deltaTime = 0.3, isValid = false)

        assertTrue(decayed < first)
    }

    @Test
    fun smooth_netto_decays_when_invalid() {
        val smoother = DisplayVarioSmoother(
            smoothTimeSeconds = 0.6,
            decayFactor = 0.5,
            clamp = 7.0
        )

        val first = smoother.smoothNetto(raw = 3.0, deltaTime = 0.3, isValid = true)
        val decayed = smoother.smoothNetto(raw = first, deltaTime = 0.3, isValid = false)

        assertTrue(decayed < first)
    }

    @Test
    fun reset_clears_vario_and_netto_channels() {
        val smoother = DisplayVarioSmoother(
            smoothTimeSeconds = 0.6,
            decayFactor = 0.9,
            clamp = 7.0
        )

        val warmedVario = smoother.smoothVario(raw = 4.0, deltaTime = 0.3, isValid = true)
        val warmedNetto = smoother.smoothNetto(raw = 2.0, deltaTime = 0.3, isValid = true)
        assertTrue(warmedVario > 0.5)
        assertTrue(warmedNetto > 0.2)

        smoother.reset()

        val afterResetVario = smoother.smoothVario(raw = 0.0, deltaTime = 0.3, isValid = true)
        val afterResetNetto = smoother.smoothNetto(raw = 0.0, deltaTime = 0.3, isValid = true)

        assertEquals(0.0, afterResetVario, 1e-6)
        assertEquals(0.0, afterResetNetto, 1e-6)
    }
}
