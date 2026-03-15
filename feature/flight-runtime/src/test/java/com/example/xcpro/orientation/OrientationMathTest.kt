package com.example.xcpro.orientation

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationMathTest {
    @Test
    fun normalizeBearingWrapsAndClamps() {
        assertEquals(10.0, normalizeBearing(370.0), 1e-6)
        assertEquals(350.0, normalizeBearing(-10.0), 1e-6)
        assertEquals(0.0, normalizeBearing(Double.NaN), 1e-6)
    }

    @Test
    fun shortestDeltaDegreesWrapsAcrossZero() {
        assertEquals(-20.0, shortestDeltaDegrees(10.0, 350.0), 1e-6)
        assertEquals(20.0, shortestDeltaDegrees(350.0, 10.0), 1e-6)
    }
}
