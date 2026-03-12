package com.example.xcpro.tasks.racing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingGeometryUtilsTest {
    private data class DistanceFixture(
        val lat1: Double,
        val lon1: Double,
        val lat2: Double,
        val lon2: Double,
        val expectedMeters: Double,
        val toleranceMeters: Double
    )

    @Test
    fun haversineDistanceMeters_isInExpectedRangeForOneDegreeLongitudeAtEquator() {
        val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
            0.0,
            0.0,
            0.0,
            1.0
        )

        assertEquals(111_194.9, distanceMeters, 200.0)
        assertTrue(distanceMeters in 111_000.0..112_500.0)
    }

    @Test
    fun calculateOptimalLineCrossingPoint_usesMeterWidth() {
        val crossing = RacingGeometryUtils.calculateOptimalLineCrossingPoint(
            lineLat = 0.0,
            lineLon = 0.0,
            targetLat = 0.0,
            targetLon = 1.0,
            lineWidthMeters = 10_000.0
        )

        val offsetMeters = RacingGeometryUtils.haversineDistanceMeters(
            0.0,
            0.0,
            crossing.first,
            crossing.second
        )
        assertTrue(offsetMeters in 4_500.0..5_500.0)
    }

    @Test
    fun haversineDistanceMeters_matchesKnownFixtureMatrix() {
        val fixtures = listOf(
            DistanceFixture(
                lat1 = 0.0,
                lon1 = 0.0,
                lat2 = 0.0,
                lon2 = 1.0,
                expectedMeters = 111_194.9,
                toleranceMeters = 200.0
            ),
            DistanceFixture(
                lat1 = 0.0,
                lon1 = 0.0,
                lat2 = 1.0,
                lon2 = 0.0,
                expectedMeters = 111_194.9,
                toleranceMeters = 200.0
            ),
            DistanceFixture(
                lat1 = 45.0,
                lon1 = 0.0,
                lat2 = 45.0,
                lon2 = 1.0,
                expectedMeters = 78_626.0,
                toleranceMeters = 300.0
            ),
            DistanceFixture(
                lat1 = -34.90,
                lon1 = 138.60,
                lat2 = -35.00,
                lon2 = 138.80,
                expectedMeters = 21_358.0,
                toleranceMeters = 400.0
            )
        )

        fixtures.forEachIndexed { index, fixture ->
            val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
                fixture.lat1,
                fixture.lon1,
                fixture.lat2,
                fixture.lon2
            )
            assertEquals(
                "Fixture index $index should resolve distance in meters",
                fixture.expectedMeters,
                distanceMeters,
                fixture.toleranceMeters
            )
        }
    }
}
