package com.example.xcpro.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayRuntimeInterpolatorTest {

    @Test
    fun `interpolated movement stores segment distance in meters`() {
        val p1 = IgcPoint(
            timestampMillis = 0L,
            latitude = 0.0,
            longitude = 0.0,
            gpsAltitude = 1000.0,
            pressureAltitude = 1000.0
        )
        val p2 = IgcPoint(
            timestampMillis = 10_000L,
            latitude = 0.0,
            longitude = 0.01,
            gpsAltitude = 1000.0,
            pressureAltitude = 1000.0
        )
        val expected = IgcReplayMath.groundVector(current = p2, previous = p1)

        val interpolator = ReplayRuntimeInterpolator(listOf(p1, p2))
        val interpolated = interpolator.interpolate(5_000L)

        assertTrue(interpolated != null)
        val movement = interpolated!!.movement
        assertEquals(expected.distanceMeters, movement.distanceMeters, 1e-6)
        assertEquals(expected.speedMs, movement.speedMs, 1e-6)
    }
}
