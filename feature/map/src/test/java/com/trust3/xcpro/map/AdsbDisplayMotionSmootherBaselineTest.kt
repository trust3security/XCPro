package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdsbDisplayMotionSmootherBaselineTest {

    @Test
    fun rewindSampleWithEarlierPositionTime_isSnappedWithoutReverseAnimation() {
        val smoother = AdsbDisplayMotionSmoother()
        val forward = target(
            id = "abc123",
            lat = 0.0,
            lon = 1.0
        ).copy(
            effectivePositionEpochSec = 1_710_000_010L,
            isPositionStale = false
        )
        val rewind = forward.copy(
            lon = 0.0,
            effectivePositionEpochSec = 1_710_000_000L,
            isPositionStale = false
        )

        smoother.onTargets(listOf(forward), nowMonoMs = 100_000L)
        smoother.onTargets(listOf(rewind), nowMonoMs = 100_100L)

        val midFrame = smoother.frame(nowMonoMs = 100_220L).single()

        assertFalse(smoother.hasActiveAnimations(nowMonoMs = 100_220L))
        assertEquals(0.0, midFrame.lon, 1e-6)
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
        lastContactEpochSec = null,
        positionAgeSec = 1,
        isPositionStale = false,
        effectivePositionEpochSec = 0L
    )
}
