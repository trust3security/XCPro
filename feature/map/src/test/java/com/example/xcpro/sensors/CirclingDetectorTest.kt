package com.example.xcpro.sensors

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

        repeat(10) {
            timestamp += 500L
            bearing += 15.0
            state = detector.update(bearing, timestamp, 20.0)
        }
        assertTrue("Expected detector to enter circling after sustained turn", state)

        repeat(10) {
            timestamp += 500L
            // stop turning but keep moving
            state = detector.update(bearing, timestamp, 20.0)
        }
        assertFalse("Expected detector to exit circling after straight flight", state)
    }

    @Test
    fun `ignores low speed turns`() {
        val detector = CirclingDetector()
        var timestamp = 0L
        var bearing = 0.0
        var state = false

        repeat(20) {
            timestamp += 500L
            bearing += 20.0
            state = detector.update(bearing, timestamp, 2.0)
        }
        assertFalse("Low-speed turns should not trigger circling", state)
    }
}
