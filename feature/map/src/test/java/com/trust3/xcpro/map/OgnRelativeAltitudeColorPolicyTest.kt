package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test

class OgnRelativeAltitudeColorPolicyTest {

    @Test
    fun resolveBand_returnsUnknown_whenDeltaMissingOrInvalid() {
        assertEquals(
            OgnRelativeAltitudeBand.UNKNOWN,
            OgnRelativeAltitudeColorPolicy.resolveBand(deltaMeters = null)
        )
        assertEquals(
            OgnRelativeAltitudeBand.UNKNOWN,
            OgnRelativeAltitudeColorPolicy.resolveBand(deltaMeters = Double.NaN)
        )
    }

    @Test
    fun resolveBand_returnsNear_withinInclusiveBlackBand() {
        val threshold = OgnRelativeAltitudeColorPolicy.BLACK_BAND_METERS

        assertEquals(
            OgnRelativeAltitudeBand.NEAR,
            OgnRelativeAltitudeColorPolicy.resolveBand(deltaMeters = threshold)
        )
        assertEquals(
            OgnRelativeAltitudeBand.NEAR,
            OgnRelativeAltitudeColorPolicy.resolveBand(deltaMeters = -threshold)
        )
        assertEquals(
            OgnRelativeAltitudeBand.NEAR,
            OgnRelativeAltitudeColorPolicy.resolveBand(deltaMeters = threshold - 0.01)
        )
    }

    @Test
    fun resolveBand_returnsAbove_whenDeltaGreaterThanBlackBand() {
        val threshold = OgnRelativeAltitudeColorPolicy.BLACK_BAND_METERS

        assertEquals(
            OgnRelativeAltitudeBand.ABOVE,
            OgnRelativeAltitudeColorPolicy.resolveBand(deltaMeters = threshold + 0.01)
        )
    }

    @Test
    fun resolveBand_returnsBelow_whenDeltaLessThanNegativeBlackBand() {
        val threshold = OgnRelativeAltitudeColorPolicy.BLACK_BAND_METERS

        assertEquals(
            OgnRelativeAltitudeBand.BELOW,
            OgnRelativeAltitudeColorPolicy.resolveBand(deltaMeters = -threshold - 0.01)
        )
    }
}

