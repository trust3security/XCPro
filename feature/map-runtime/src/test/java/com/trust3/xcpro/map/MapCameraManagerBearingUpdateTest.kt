package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapCameraManagerBearingUpdateTest {

    @Test
    fun northUp_stepsFromCurrentBearing_towardNorth() {
        val result = resolveCameraBearingUpdate(
            currentBearing = 120.0,
            requestedBearing = 270.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            maxBearingStepDeg = 5.0
        )

        assertEquals(115.0, result ?: Double.NaN, 1e-6)
    }

    @Test
    fun trackUp_appliesTarget_whenChangeIsAboveDeadband() {
        val result = resolveCameraBearingUpdate(
            currentBearing = 100.0,
            requestedBearing = 104.0,
            orientationMode = MapOrientationMode.TRACK_UP,
            maxBearingStepDeg = 5.0
        )

        assertEquals(104.0, result ?: Double.NaN, 1e-6)
    }

    @Test
    fun trackUp_returnsNull_whenChangeIsWithinDeadband() {
        val result = resolveCameraBearingUpdate(
            currentBearing = 100.0,
            requestedBearing = 101.0,
            orientationMode = MapOrientationMode.TRACK_UP,
            maxBearingStepDeg = 5.0
        )

        assertNull(result)
    }

    @Test
    fun trackUp_usesShortestPathAcrossWrapBoundary() {
        val result = resolveCameraBearingUpdate(
            currentBearing = 2.0,
            requestedBearing = 358.0,
            orientationMode = MapOrientationMode.TRACK_UP,
            maxBearingStepDeg = 5.0
        )

        assertEquals(358.0, result ?: Double.NaN, 1e-6)
    }
}
