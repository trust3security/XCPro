package com.example.xcpro.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnRelativeAltitudeLabelLayoutPolicyTest {

    @Test
    fun resolve_setsDeltaOnTop_whenBandIsAbove() {
        val layout = OgnRelativeAltitudeLabelLayoutPolicy.resolve(OgnRelativeAltitudeBand.ABOVE)

        assertTrue(layout.deltaOnTop)
    }

    @Test
    fun resolve_setsDeltaOnBottom_whenBandIsBelow() {
        val layout = OgnRelativeAltitudeLabelLayoutPolicy.resolve(OgnRelativeAltitudeBand.BELOW)

        assertFalse(layout.deltaOnTop)
    }

    @Test
    fun resolve_usesTopFallback_whenBandIsNearOrUnknown() {
        assertTrue(
            OgnRelativeAltitudeLabelLayoutPolicy.resolve(OgnRelativeAltitudeBand.NEAR).deltaOnTop
        )
        assertTrue(
            OgnRelativeAltitudeLabelLayoutPolicy.resolve(OgnRelativeAltitudeBand.UNKNOWN).deltaOnTop
        )
    }
}

