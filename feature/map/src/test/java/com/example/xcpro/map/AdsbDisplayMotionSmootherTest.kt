package com.example.xcpro.map

import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbDisplayMotionSmootherTest {

    @Test
    fun firstRetargetAfterEntrySeed_usesCurrentSampleTime() {
        val smoother = AdsbDisplayMotionSmoother()
        val base = target(id = "abc123", lat = 0.0, lon = 0.0)
        val moved = base.copy(lat = 1.0, lon = 1.0, distanceMeters = 900.0, bearingDegFromUser = 50.0)

        assertTrue(smoother.onTargets(listOf(base), nowMonoMs = 100_000L))
        assertTrue(smoother.onTargets(listOf(moved), nowMonoMs = 100_100L))

        val frame = smoother.frame(nowMonoMs = 100_400L).single()
        assertEquals(moved.lat, frame.lat, 1e-6)
        assertEquals(moved.lon, frame.lon, 1e-6)
        assertFalse(smoother.hasActiveAnimations(nowMonoMs = 100_400L))
    }

    @Test
    fun freshnessOnlyUpdate_doesNotAnimate() {
        val smoother = AdsbDisplayMotionSmoother()
        val base = target(
            id = "feed00",
            lat = 1.0,
            lon = 2.0
        ).copy(
            effectivePositionEpochSec = 1_710_000_020L,
            positionAgeSec = 2,
            isPositionStale = false
        )
        val contactUpdate = base.copy(
            ageSec = 8,
            isStale = true,
            positionAgeSec = 8,
            isPositionStale = true,
            contactAgeSec = 2
        )

        smoother.onTargets(listOf(base), nowMonoMs = 1_000L)
        smoother.onTargets(listOf(contactUpdate), nowMonoMs = 1_100L)

        val frame = smoother.frame(nowMonoMs = 1_120L).single()

        assertFalse(smoother.hasActiveAnimations(nowMonoMs = 1_120L))
        assertEquals(base.lat, frame.lat, 1e-6)
        assertEquals(base.lon, frame.lon, 1e-6)
    }

    @Test
    fun noAnimationReplacement_refreshesSampleTimeForNextRetarget() {
        val smoother = AdsbDisplayMotionSmoother()
        val base = target(id = "def456", lat = 0.0, lon = 0.0)
        val moved = base.copy(lat = 2.0, lon = 2.0, distanceMeters = 800.0, bearingDegFromUser = 60.0)

        assertTrue(smoother.onTargets(listOf(base), nowMonoMs = 1_000L))
        assertFalse(smoother.onTargets(listOf(base), nowMonoMs = 11_000L))
        assertTrue(smoother.onTargets(listOf(moved), nowMonoMs = 11_100L))

        val midFrame = smoother.frame(nowMonoMs = 11_220L).single()
        assertTrue(midFrame.lat > 0.0)
        assertTrue(midFrame.lat < moved.lat)

        val settled = smoother.frame(nowMonoMs = 11_360L).single()
        assertEquals(moved.lat, settled.lat, 1e-6)
        assertEquals(moved.lon, settled.lon, 1e-6)
        assertFalse(smoother.hasActiveAnimations(nowMonoMs = 11_360L))
    }

    @Test
    fun snapshot_reportsInterpolatedFrameAndAnimationFlag() {
        val smoother = AdsbDisplayMotionSmoother()
        val base = target(id = "0a0a0a", lat = 0.0, lon = 0.0)
        val moved = base.copy(lat = 1.5, lon = 1.5, distanceMeters = 700.0, bearingDegFromUser = 70.0)

        smoother.onTargets(listOf(base), nowMonoMs = 5_000L)
        smoother.onTargets(listOf(moved), nowMonoMs = 5_100L)

        val snapshot = smoother.snapshot(nowMonoMs = 5_220L)
        val frame = snapshot.targets.single()
        assertTrue(snapshot.hasActiveAnimations)
        assertTrue(frame.lat > 0.0)
        assertTrue(frame.lat < moved.lat)
    }

    @Test
    fun snapshot_reportsSettledFrameWhenAnimationCompletes() {
        val smoother = AdsbDisplayMotionSmoother()
        val base = target(id = "0b0b0b", lat = 0.0, lon = 0.0)
        val moved = base.copy(lat = 1.0, lon = 1.0, distanceMeters = 650.0, bearingDegFromUser = 80.0)

        smoother.onTargets(listOf(base), nowMonoMs = 8_000L)
        smoother.onTargets(listOf(moved), nowMonoMs = 8_100L)

        val snapshot = smoother.snapshot(nowMonoMs = 8_400L)
        val frame = snapshot.targets.single()
        assertFalse(snapshot.hasActiveAnimations)
        assertEquals(moved.lat, frame.lat, 1e-6)
        assertEquals(moved.lon, frame.lon, 1e-6)
    }

    private fun target(
        id: String,
        lat: Double,
        lon: Double
    ): AdsbTrafficUiModel = AdsbTrafficUiModel(
        id = Icao24(id),
        callsign = "CALL-$id",
        lat = lat,
        lon = lon,
        altitudeM = 1_200.0,
        speedMps = 30.0,
        trackDeg = 90.0,
        climbMps = 0.5,
        ageSec = 0,
        isStale = false,
        distanceMeters = 1_000.0,
        bearingDegFromUser = 45.0,
        positionSource = null,
        category = null,
        lastContactEpochSec = null
    )
}
