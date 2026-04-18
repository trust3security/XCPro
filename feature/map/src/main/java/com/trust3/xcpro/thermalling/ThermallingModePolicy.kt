package com.trust3.xcpro.thermalling

enum class ThermallingModeResetReason {
    FEATURE_DISABLED,
    POLICY_BLOCKED
}

data class ThermallingModePolicyDecision(
    val shouldBypassAutomation: Boolean,
    val shouldSwitchToThermalMode: Boolean,
    val shouldApplyZoom: Boolean,
    val resetReason: ThermallingModeResetReason?
)

fun resolveThermallingModePolicy(
    settings: ThermallingModeSettings,
    thermalModeVisible: Boolean,
    hasActiveThermallingSession: Boolean
): ThermallingModePolicyDecision {
    val modeSwitchAllowed = settings.switchToThermalMode && thermalModeVisible
    val zoomAllowed = settings.applyZoomOnEnter &&
        (thermalModeVisible || settings.zoomOnlyFallbackWhenThermalHidden)
    val policyBlocked = !modeSwitchAllowed && !zoomAllowed

    val bypass = !settings.enabled || policyBlocked
    val resetReason = when {
        !hasActiveThermallingSession -> null
        !settings.enabled -> ThermallingModeResetReason.FEATURE_DISABLED
        policyBlocked -> ThermallingModeResetReason.POLICY_BLOCKED
        else -> null
    }

    return ThermallingModePolicyDecision(
        shouldBypassAutomation = bypass,
        shouldSwitchToThermalMode = !bypass && modeSwitchAllowed,
        shouldApplyZoom = !bypass && zoomAllowed,
        resetReason = resetReason
    )
}
