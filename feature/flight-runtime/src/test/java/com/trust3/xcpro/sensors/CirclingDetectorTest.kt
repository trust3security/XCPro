package com.trust3.xcpro.sensors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CirclingDetectorTest {

    @Test
    fun `enters and exits circling with sustained turn`() {
        val detector = CirclingDetector()
        var timestamp = 0L
        var bearing = 0.0
        var state = false

        repeat(40) {
            timestamp += 500L
            bearing += 15.0
            state = detector.update(bearing, timestamp, isFlying = true).isCircling
        }
        assertTrue("Expected detector to enter circling after sustained turn", state)

        repeat(35) {
            timestamp += 500L
            // stop turning but keep moving
            state = detector.update(bearing, timestamp, isFlying = true).isCircling
        }
        assertFalse("Expected detector to exit circling after straight flight", state)
    }

    @Test
    fun `detects circling when flying even at low speed`() {
        val detector = CirclingDetector()
        var timestamp = 0L
        var bearing = 0.0
        var state = false

        repeat(40) {
            timestamp += 500L
            bearing += 20.0
            state = detector.update(bearing, timestamp, isFlying = true).isCircling
        }
        assertTrue("Low-speed turns should still trigger circling when flying", state)
    }
}
