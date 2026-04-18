package com.trust3.xcpro.thermalling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermallingModePolicyTest {

    @Test
    fun thermalVisible_allowsSwitchAndZoomWhenEnabled() {
        val settings = ThermallingModeSettings(
            enabled = true,
            switchToThermalMode = true,
            applyZoomOnEnter = true
        )

        val decision = resolveThermallingModePolicy(
            settings = settings,
            thermalModeVisible = true,
            hasActiveThermallingSession = false
        )

        assertFalse(decision.shouldBypassAutomation)
        assertTrue(decision.shouldSwitchToThermalMode)
        assertTrue(decision.shouldApplyZoom)
        assertNull(decision.resetReason)
    }

    @Test
    fun thermalHidden_withFallbackEnabled_allowsZoomOnly() {
        val settings = ThermallingModeSettings(
            enabled = true,
            switchToThermalMode = true,
            applyZoomOnEnter = true,
            zoomOnlyFallbackWhenThermalHidden = true
        )

        val decision = resolveThermallingModePolicy(
            settings = settings,
            thermalModeVisible = false,
            hasActiveThermallingSession = false
        )

        assertFalse(decision.shouldBypassAutomation)
        assertFalse(decision.shouldSwitchToThermalMode)
        assertTrue(decision.shouldApplyZoom)
        assertNull(decision.resetReason)
    }

    @Test
    fun thermalHidden_withoutFallback_bypassesAndResetsActiveSession() {
        val settings = ThermallingModeSettings(
            enabled = true,
            switchToThermalMode = true,
            applyZoomOnEnter = true,
            zoomOnlyFallbackWhenThermalHidden = false
        )

        val decision = resolveThermallingModePolicy(
            settings = settings,
            thermalModeVisible = false,
            hasActiveThermallingSession = true
        )

        assertTrue(decision.shouldBypassAutomation)
        assertFalse(decision.shouldSwitchToThermalMode)
        assertFalse(decision.shouldApplyZoom)
        assertEquals(ThermallingModeResetReason.POLICY_BLOCKED, decision.resetReason)
    }

    @Test
    fun featureDisabled_bypassesAndResetsActiveSession() {
        val settings = ThermallingModeSettings(
            enabled = false,
            switchToThermalMode = true,
            applyZoomOnEnter = true
        )

        val decision = resolveThermallingModePolicy(
            settings = settings,
            thermalModeVisible = true,
            hasActiveThermallingSession = true
        )

        assertTrue(decision.shouldBypassAutomation)
        assertFalse(decision.shouldSwitchToThermalMode)
        assertFalse(decision.shouldApplyZoom)
        assertEquals(ThermallingModeResetReason.FEATURE_DISABLED, decision.resetReason)
    }
}
