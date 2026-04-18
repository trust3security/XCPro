package com.trust3.xcpro.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnGliderCloseProximityColorPolicyTest {

    @Test
    fun shouldUseRed_returnsTrue_atInclusiveThresholds() {
        assertTrue(
            OgnGliderCloseProximityColorPolicy.shouldUseRed(
                distanceMeters = OgnGliderCloseProximityColorPolicy.CLOSE_DISTANCE_METERS,
                deltaMeters = OgnGliderCloseProximityColorPolicy.CLOSE_VERTICAL_METERS
            )
        )
        assertTrue(
            OgnGliderCloseProximityColorPolicy.shouldUseRed(
                distanceMeters = OgnGliderCloseProximityColorPolicy.CLOSE_DISTANCE_METERS,
                deltaMeters = -OgnGliderCloseProximityColorPolicy.CLOSE_VERTICAL_METERS
            )
        )
    }

    @Test
    fun shouldUseRed_returnsFalse_whenOutsideEitherThreshold() {
        assertFalse(
            OgnGliderCloseProximityColorPolicy.shouldUseRed(
                distanceMeters = OgnGliderCloseProximityColorPolicy.CLOSE_DISTANCE_METERS + 0.01,
                deltaMeters = 0.0
            )
        )
        assertFalse(
            OgnGliderCloseProximityColorPolicy.shouldUseRed(
                distanceMeters = 800.0,
                deltaMeters = OgnGliderCloseProximityColorPolicy.CLOSE_VERTICAL_METERS + 0.01
            )
        )
    }

    @Test
    fun shouldUseRed_returnsFalse_whenInputsMissingOrInvalid() {
        assertFalse(
            OgnGliderCloseProximityColorPolicy.shouldUseRed(
                distanceMeters = null,
                deltaMeters = 0.0
            )
        )
        assertFalse(
            OgnGliderCloseProximityColorPolicy.shouldUseRed(
                distanceMeters = 800.0,
                deltaMeters = null
            )
        )
        assertFalse(
            OgnGliderCloseProximityColorPolicy.shouldUseRed(
                distanceMeters = Double.NaN,
                deltaMeters = 0.0
            )
        )
    }
}
