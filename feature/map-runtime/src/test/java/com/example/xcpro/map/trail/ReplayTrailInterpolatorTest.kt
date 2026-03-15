package com.example.xcpro.map.trail

import com.example.xcpro.map.trail.domain.ReplayTrailInterpolator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayTrailInterpolatorTest {

    @Test
    fun expand_interpolates_steps() = runTest {
        val clock = FakeClock()
        val interpolator = ReplayTrailInterpolator()

        val first = sample(clock.nowMs(), 46.0, 7.0)
        val firstExpanded = interpolator.expand(first)
        assertEquals(1, firstExpanded.size)

        clock.advance(1_000L)
        val second = sample(clock.nowMs(), 46.0, 7.02)
        val expanded = interpolator.expand(second)
        assertEquals(4, expanded.size)
        assertEquals(clock.nowMs(), expanded.last().timestampMillis)
    }

    @Test
    fun adjusts_timestamp_when_non_monotonic() {
        val interpolator = ReplayTrailInterpolator()
        val first = sample(1_000L, 46.0, 7.0)
        interpolator.expand(first)

        val second = sample(1_000L, 46.0, 7.01)
        val expanded = interpolator.expand(second)

        assertEquals(1_250L, expanded.last().timestampMillis)
    }

    @Test
    fun should_reset_on_backstep() {
        val interpolator = ReplayTrailInterpolator()
        interpolator.expand(sample(5_000L, 46.0, 7.0))

        val backstep = sample(2_000L, 46.0, 7.01)

        assertTrue(interpolator.shouldReset(backstep))
    }

    @Test
    fun should_reset_on_distance() {
        val interpolator = ReplayTrailInterpolator()
        interpolator.expand(sample(1_000L, 46.0, 7.0))

        val far = sample(2_000L, 46.03, 7.0)

        assertTrue(interpolator.shouldReset(far))
    }

    private fun sample(timestampMs: Long, lat: Double, lon: Double): TrailSample {
        return TrailSample(
            latitude = lat,
            longitude = lon,
            timestampMillis = timestampMs,
            altitudeMeters = 1000.0,
            varioMs = 0.5,
            windSpeedMs = 4.0,
            windDirectionFromDeg = 180.0
        )
    }

    private class FakeClock {
        private var nowMs: Long = 0L

        fun nowMs(): Long = nowMs

        fun advance(deltaMs: Long) {
            nowMs += deltaMs
        }
    }
}
