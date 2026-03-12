package com.example.xcpro.map

import com.example.dfcards.RealTimeFlightData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightDataManagerSupportTest {

    @Test
    fun resolveDisplayVario_nullSample_returnsZero() {
        assertEquals(0f, resolveDisplayVarioForTest(null), 1e-6f)
    }

    @Test
    fun resolveDisplayVario_validFiniteDisplay_wins() {
        val resolved = resolveDisplayVarioForTest(
            RealTimeFlightData(
                displayVario = 1.24,
                verticalSpeed = 3.0,
                varioValid = true
            )
        )

        assertEquals(1.2f, resolved, 1e-6f)
    }

    @Test
    fun resolveDisplayVario_invalidFiniteDisplayAboveNoiseFloor_stillWins() {
        val resolved = resolveDisplayVarioForTest(
            RealTimeFlightData(
                displayVario = 0.21,
                verticalSpeed = 3.0,
                varioValid = false
            )
        )

        assertEquals(0.2f, resolved, 1e-6f)
    }

    @Test
    fun resolveDisplayVario_invalidFiniteDisplayBelowNoiseFloor_fallsBackToVerticalSpeed() {
        val resolved = resolveDisplayVarioForTest(
            RealTimeFlightData(
                displayVario = 0.0005,
                verticalSpeed = 0.37,
                varioValid = false
            )
        )

        assertEquals(0.4f, resolved, 1e-6f)
    }

    @Test
    fun resolveDisplayVario_nonFiniteDisplay_fallsBackToVerticalSpeed() {
        val resolved = resolveDisplayVarioForTest(
            RealTimeFlightData(
                displayVario = Double.NaN,
                verticalSpeed = 0.26,
                varioValid = false
            )
        )

        assertEquals(0.3f, resolved, 1e-6f)
    }

    @Test
    fun resolveDisplayVario_bucketsToTenthMeterPerSecond() {
        val resolved = resolveDisplayVarioForTest(
            RealTimeFlightData(
                displayVario = 1.04,
                verticalSpeed = 0.0,
                varioValid = true
            )
        )

        assertEquals(1.0f, resolved, 1e-6f)
    }

    @Test
    fun deriveWindIndicatorState_usesWindValidFlag() {
        val state = deriveWindIndicatorState(
            previous = WindIndicatorState(directionFromDeg = 30f, isValid = false, quality = 0, ageSeconds = -1),
            data = RealTimeFlightData(
                windDirection = 182f,
                windQuality = 5,
                windValid = true,
                windAgeSeconds = 4
            )
        )

        assertTrue(state.isValid)
        assertEquals(182f, state.directionFromDeg ?: -1f, 1e-6f)
        assertEquals(5, state.quality)
        assertEquals(4L, state.ageSeconds)
    }

    @Test
    fun deriveWindIndicatorState_invalidWind_keepsLastDirection() {
        val previous = WindIndicatorState(directionFromDeg = 145f, isValid = true, quality = 4, ageSeconds = 2)
        val state = deriveWindIndicatorState(
            previous = previous,
            data = RealTimeFlightData(
                windDirection = 220f,
                windQuality = 5,
                windValid = false,
                windAgeSeconds = 9
            )
        )

        assertFalse(state.isValid)
        assertEquals(145f, state.directionFromDeg ?: -1f, 1e-6f)
        assertEquals(0, state.quality)
        assertEquals(9L, state.ageSeconds)
    }

    private fun resolveDisplayVarioForTest(data: RealTimeFlightData?): Float =
        data.resolveDisplayVario(
            varioBucketMs = 0.1f,
            varioNoiseFloor = 1e-3
        )
}
