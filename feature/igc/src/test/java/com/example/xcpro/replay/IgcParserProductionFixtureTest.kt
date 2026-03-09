package com.example.xcpro.replay

import com.example.xcpro.core.time.FakeClock
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcParserProductionFixtureTest {

    @Test
    fun parse_productionFixture_hasExpectedShapeAndMonotonicTime() {
        val resource = javaClass.classLoader?.getResourceAsStream(FIXTURE_RESOURCE)
            ?: error("Missing replay fixture: $FIXTURE_RESOURCE")
        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(resource)

        assertTrue("Expected many IGC points from production fixture", log.points.size > 10_000)
        assertTrue("Expected monotonic timestamps", hasMonotonicTimestamps(log.points))
        assertTrue(
            "Expected TAS extension values in production fixture",
            log.points.any { point -> point.trueAirspeedKmh != null }
        )

        val first = log.points.first()
        val last = log.points.last()
        assertTrue("Latitude should be valid", first.latitude in -90.0..90.0 && last.latitude in -90.0..90.0)
        assertTrue("Longitude should be valid", first.longitude in -180.0..180.0 && last.longitude in -180.0..180.0)
    }

    private fun hasMonotonicTimestamps(points: List<IgcPoint>): Boolean {
        return points.zipWithNext().all { (prev, current) ->
            current.timestampMillis >= prev.timestampMillis
        }
    }

    private companion object {
        private const val FIXTURE_RESOURCE = "replay/example-production.igc"
    }
}
