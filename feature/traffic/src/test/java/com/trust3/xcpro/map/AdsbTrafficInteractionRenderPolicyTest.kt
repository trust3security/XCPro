package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbTrafficInteractionRenderPolicyTest {

    @Test
    fun shouldAnimateAdsbVisuals_reducedMotionActive_disablesAnimation() {
        val frameSnapshot = AdsbDisplayMotionSmoother.FrameSnapshot(
            targets = listOf(emergencyTarget()),
            hasActiveAnimations = true,
            activeAnimatedTargetCount = 1
        )

        assertFalse(
            shouldAnimateAdsbVisuals(
                frameSnapshot = frameSnapshot,
                emergencyFlashEnabled = true,
                interactionReducedMotionActive = true
            )
        )
    }

    @Test
    fun resolveEffectiveAdsbEmergencyFlashEnabled_reducedMotion_disablesFlashOnly() {
        assertFalse(
            resolveEffectiveAdsbEmergencyFlashEnabled(
                emergencyFlashEnabled = true,
                interactionReducedMotionActive = true
            )
        )
        assertTrue(
            resolveEffectiveAdsbEmergencyFlashEnabled(
                emergencyFlashEnabled = true,
                interactionReducedMotionActive = false
            )
        )
    }

    @Test
    fun resolveAdsbInteractionViewportDeclutterPolicy_forcesLabelsOffAndOneTierLower() {
        val reduced = resolveAdsbInteractionViewportDeclutterPolicy(
            AdsbTrafficViewportDeclutterPolicy(
                iconScaleMultiplier = 1.0f,
                showAllLabels = true,
                closeTrafficLabelDistanceMeters = 10_000.0,
                maxTargets = ADSB_TRAFFIC_MAX_TARGETS
            )
        )

        assertFalse(reduced.showAllLabels)
        assertEquals(72, reduced.maxTargets)
        assertEquals(1.0f, reduced.iconScaleMultiplier)
    }

    private fun emergencyTarget(): AdsbTrafficUiModel = AdsbTrafficUiModel(
        id = Icao24.from("abc123") ?: error("invalid ICAO24"),
        callsign = "TEST",
        lat = -33.0,
        lon = 151.0,
        altitudeM = 900.0,
        speedMps = 45.0,
        trackDeg = 180.0,
        climbMps = 0.1,
        ageSec = 1,
        isStale = false,
        distanceMeters = 1_000.0,
        bearingDegFromUser = 180.0,
        positionSource = 0,
        category = 3,
        proximityTier = AdsbProximityTier.EMERGENCY,
        lastContactEpochSec = null
    )
}
