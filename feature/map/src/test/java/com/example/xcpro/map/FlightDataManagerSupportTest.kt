package com.example.xcpro.map

import com.example.dfcards.RealTimeFlightData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightDataManagerSupportTest {

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
}
