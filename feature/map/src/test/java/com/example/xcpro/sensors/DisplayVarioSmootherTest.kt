package com.example.xcpro.sensors

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
}
