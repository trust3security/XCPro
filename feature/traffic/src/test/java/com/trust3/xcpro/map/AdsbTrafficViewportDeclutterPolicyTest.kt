package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbTrafficViewportDeclutterPolicyTest {

    @Test
    fun wideZoom_reducesIconsKeepsFullLabelsOffAndCapsTargets() {
        val policy = resolveAdsbTrafficViewportDeclutterPolicy(7.8f)

        assertEquals(0.68f, policy.iconScaleMultiplier, 0.0001f)
        assertFalse(policy.showAllLabels)
        assertEquals(10_000.0, policy.closeTrafficLabelDistanceMeters, 0.0001)
        assertEquals(28, policy.maxTargets)
    }

    @Test
    fun viewportRangeWithin30Km_showsAllLabelsEvenBeforeLegacyCloseZoom() {
        val policy = resolveAdsbTrafficViewportDeclutterPolicy(
            zoomLevel = 9.3f,
            viewportRangeMeters = 29_500.0
        )

        assertEquals(0.88f, policy.iconScaleMultiplier, 0.0001f)
        assertTrue(policy.showAllLabels)
        assertEquals(10_000.0, policy.closeTrafficLabelDistanceMeters, 0.0001)
        assertEquals(72, policy.maxTargets)
    }

    @Test
    fun closeZoom_fallsBackToShowingAllLabelsWhenViewportRangeUnavailable() {
        val policy = resolveAdsbTrafficViewportDeclutterPolicy(10.8f)

        assertEquals(1.0f, policy.iconScaleMultiplier, 0.0001f)
        assertTrue(policy.showAllLabels)
        assertEquals(10_000.0, policy.closeTrafficLabelDistanceMeters, 0.0001)
        assertEquals(ADSB_TRAFFIC_MAX_TARGETS, policy.maxTargets)
    }
}
