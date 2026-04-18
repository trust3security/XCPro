package com.trust3.xcpro.sensors.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereMathTest {

    @Test
    fun pressureToAltitudeMeters_returnsSeaLevelForStandardPressure() {
        assertEquals(0.0, pressureToAltitudeMeters(1013.25), 1e-6)
    }

    @Test
    fun pressureToAltitudeMeters_returnsNaNForInvalidPressure() {
        assertTrue(pressureToAltitudeMeters(0.0).isNaN())
        assertTrue(pressureToAltitudeMeters(Double.NaN).isNaN())
    }

    @Test
    fun computeDensityRatio_returnsOneAtSeaLevelStandardQnh() {
        assertEquals(1.0, computeDensityRatio(0.0, 1013.25), 1e-6)
    }

    @Test
    fun computeDensityRatio_higherQnhIncreasesDensityAtSameAltitude() {
        val lowQnh = computeDensityRatio(2_000.0, 990.0)
        val highQnh = computeDensityRatio(2_000.0, 1030.0)

        assertTrue(highQnh > lowQnh)
    }
}
