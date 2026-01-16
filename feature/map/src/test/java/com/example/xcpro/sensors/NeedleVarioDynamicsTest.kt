package com.example.xcpro.sensors

import org.junit.Assert.assertTrue
import org.junit.Test

class NeedleVarioDynamicsTest {

    @Test
    fun reaches_95_percent_in_0_6_seconds_no_overshoot() {
        val filter = NeedleVarioDynamics(t95Seconds = 0.6, clamp = 7.0)
        var value = 0.0
        val dt = 0.02
        repeat(30) {
            value = filter.update(target = 1.0, deltaTimeSeconds = dt, isValid = true)
            assertTrue("needle overshot target", value <= 1.0 + 1e-6)
        }
        assertTrue("needle did not reach 95% in 0.6s (value=$value)", value >= 0.95 - 1e-3)
    }

    @Test
    fun decays_toward_zero_when_invalid() {
        val filter = NeedleVarioDynamics(t95Seconds = 0.6, clamp = 7.0)
        var value = filter.update(target = 2.0, deltaTimeSeconds = 0.1, isValid = true)
        val decayed = filter.update(target = 2.0, deltaTimeSeconds = 0.1, isValid = false)
        assertTrue(decayed < value)
    }
}
