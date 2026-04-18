package com.trust3.xcpro.sensors

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
