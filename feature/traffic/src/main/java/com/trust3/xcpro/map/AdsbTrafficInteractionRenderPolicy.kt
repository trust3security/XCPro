package com.trust3.xcpro.map

internal fun resolveAdsbInteractionViewportDeclutterPolicy(
    basePolicy: AdsbTrafficViewportDeclutterPolicy
): AdsbTrafficViewportDeclutterPolicy = AdsbTrafficViewportDeclutterPolicy(
    iconScaleMultiplier = basePolicy.iconScaleMultiplier,
    showAllLabels = false,
    closeTrafficLabelDistanceMeters = basePolicy.closeTrafficLabelDistanceMeters,
    maxTargets = resolveAdsbInteractionMaxTargets(basePolicy.maxTargets)
)

internal fun shouldAnimateAdsbVisuals(
    frameSnapshot: AdsbDisplayMotionSmoother.FrameSnapshot,
    emergencyFlashEnabled: Boolean,
    interactionReducedMotionActive: Boolean
): Boolean {
    if (interactionReducedMotionActive) {
        return false
    }
    return hasActiveAdsbVisualAnimation(
        frameSnapshot = frameSnapshot,
        emergencyFlashEnabled = emergencyFlashEnabled
    )
}

internal fun resolveEffectiveAdsbEmergencyFlashEnabled(
    emergencyFlashEnabled: Boolean,
    interactionReducedMotionActive: Boolean
): Boolean = emergencyFlashEnabled && !interactionReducedMotionActive

private fun resolveAdsbInteractionMaxTargets(baseMaxTargets: Int): Int = when {
    baseMaxTargets >= ADSB_TRAFFIC_MAX_TARGETS -> 72
    baseMaxTargets >= 72 -> 48
    baseMaxTargets >= 48 -> 28
    else -> baseMaxTargets
}
