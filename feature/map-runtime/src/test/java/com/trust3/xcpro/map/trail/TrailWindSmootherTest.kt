package com.trust3.xcpro.map.trail

import com.trust3.xcpro.map.trail.domain.TrailWindSmoother
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailWindSmootherTest {

    @Test
    fun returns_raw_when_timestamp_invalid() {
        val smoother = TrailWindSmoother(tauMs = 4_000L, minValidSpeedMs = 0.5)

        val sample = smoother.update(speedMs = 5.0, directionFromDeg = 90.0, timestampMs = 0L)

        assertEquals(5.0, sample.speedMs, 1e-6)
        assertEquals(90.0, sample.directionFromDeg, 1e-6)
    }

    @Test
    fun smooths_toward_target_over_time() = runTest {
        val clock = FakeClock()
        val smoother = TrailWindSmoother(tauMs = 4_000L, minValidSpeedMs = 0.5)

        clock.advance(1_000L)
        val first = smoother.update(speedMs = 8.0, directionFromDeg = 0.0, timestampMs = clock.nowMs())
        clock.advance(1_000L)
        val second = smoother.update(speedMs = 8.0, directionFromDeg = 0.0, timestampMs = clock.nowMs())

        assertTrue(second.speedMs >= 0.0)
        assertTrue(second.speedMs >= first.speedMs)
    }

    private class FakeClock {
        private var nowMs: Long = 0L

        fun nowMs(): Long = nowMs

        fun advance(deltaMs: Long) {
            nowMs += deltaMs
        }
    }
}
