package com.trust3.xcpro.map.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastAuxiliaryHostsTest {

    @Test
    fun formatCoordinate_roundsToFourDecimalPlaces() {
        assertEquals("12.3457", formatCoordinate(12.34567))
    }

    @Test
    fun formatDirectionDegrees_normalizesIntoCompassRange() {
        assertEquals("350°", formatDirectionDegrees(-10.2))
    }

    @Test
    fun formatWindSpeedForTap_roundsToNearestWholeKnot() {
        assertEquals("13", formatWindSpeedForTap(12.6))
    }
}
