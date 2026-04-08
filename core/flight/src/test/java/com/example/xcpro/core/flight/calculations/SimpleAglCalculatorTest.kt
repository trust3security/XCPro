package com.example.xcpro.core.flight.calculations

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimpleAglCalculatorTest {
    @Test
    fun calculateAgl_usesInjectedTerrainReadPort() = runTest {
        val calculator = SimpleAglCalculator(
            terrainElevationReadPort = object : TerrainElevationReadPort {
                override suspend fun getElevationMeters(lat: Double, lon: Double): Double? = 320.0
            }
        )

        val agl = calculator.calculateAgl(
            altitude = 1_000.0,
            lat = -33.0,
            lon = 151.0,
            speed = 25.0
        )

        assertEquals(680.0, agl ?: Double.NaN, 1e-6)
    }

    @Test
    fun calculateAgl_returnsNullWhenTerrainPortHasNoData() = runTest {
        val calculator = SimpleAglCalculator(
            terrainElevationReadPort = object : TerrainElevationReadPort {
                override suspend fun getElevationMeters(lat: Double, lon: Double): Double? = null
            }
        )

        val agl = calculator.calculateAgl(
            altitude = 1_000.0,
            lat = -33.0,
            lon = 151.0,
            speed = 25.0
        )

        assertNull(agl)
    }
}
