package com.example.xcpro.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedSampleAverageWindowTest {

    @Test
    fun seedPopulatesAllSamples() {
        val window = FixedSampleAverageWindow(capacity = 3)

        window.seed(2.0)

        assertFalse(window.isEmpty())
        assertEquals(2.0, window.average(), 1e-6)
    }

    @Test
    fun addSampleRollsBufferForward() {
        val window = FixedSampleAverageWindow(capacity = 3)

        window.seed(1.0)
        window.addSample(2.0)
        window.addSample(3.0)
        window.addSample(4.0)

        assertEquals(3.0, window.average(), 1e-6) // samples should be [2,3,4]
    }

    @Test
    fun clearResetsState() {
        val window = FixedSampleAverageWindow(capacity = 2)
        window.seed(5.0)

        window.clear()

        assertTrue(window.isEmpty())
        assertEquals(0.0, window.average(), 1e-6)
    }
}

class TcAverageTrackerTest {
    @Test
    fun holdsLastAverageAfterExit() {
        val tracker = TcAverageTracker()

        tracker.update(1_000L, 2.0, true)   // seed in circling
        tracker.update(2_000L, 2.0, false)  // exit circling

        assertEquals(2.0, tracker.average(), 1e-6)
    }

    @Test
    fun seedsOnReenterAndOverwritesOldSamples() {
        val tracker = TcAverageTracker(capacitySeconds = 2)

        tracker.update(1_000L, 1.0, true)
        tracker.update(2_000L, 1.0, true)
        assertEquals(1.0, tracker.average(), 1e-6)

        // leave circling; average should hold
        tracker.update(3_000L, 0.0, false)
        assertEquals(1.0, tracker.average(), 1e-6)

        // re-enter with different lift; buffer should reseed
        tracker.update(4_000L, 3.0, true)
        assertEquals(3.0, tracker.average(), 1e-6)
    }
}
