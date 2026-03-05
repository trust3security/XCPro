package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOverlayInteractionCadencePolicyTest {

    @Test
    fun resolveInteractionAwareIntervalMs_inactive_returnsBase() {
        assertEquals(
            120L,
            resolveInteractionAwareIntervalMs(
                baseIntervalMs = 120L,
                interactionActive = false,
                interactionFloorMs = 600L
            )
        )
    }

    @Test
    fun resolveInteractionAwareIntervalMs_active_appliesFloor() {
        assertEquals(
            600L,
            resolveInteractionAwareIntervalMs(
                baseIntervalMs = 120L,
                interactionActive = true,
                interactionFloorMs = 600L
            )
        )
    }

    @Test
    fun shouldSkipWeatherRainApplyDuringInteraction_activeInsideWindow_returnsTrue() {
        assertTrue(
            shouldSkipWeatherRainApplyDuringInteraction(
                interactionActive = true,
                enabled = true,
                hasFrameSelection = true,
                lastAppliedMonoMs = 1_000L,
                nowMonoMs = 1_300L,
                minIntervalMs = 800L
            )
        )
    }

    @Test
    fun shouldSkipWeatherRainApplyDuringInteraction_inactiveOrNotRenderable_returnsFalse() {
        assertFalse(
            shouldSkipWeatherRainApplyDuringInteraction(
                interactionActive = false,
                enabled = true,
                hasFrameSelection = true,
                lastAppliedMonoMs = 1_000L,
                nowMonoMs = 1_100L
            )
        )
        assertFalse(
            shouldSkipWeatherRainApplyDuringInteraction(
                interactionActive = true,
                enabled = false,
                hasFrameSelection = true,
                lastAppliedMonoMs = 1_000L,
                nowMonoMs = 1_100L
            )
        )
        assertFalse(
            shouldSkipWeatherRainApplyDuringInteraction(
                interactionActive = true,
                enabled = true,
                hasFrameSelection = false,
                lastAppliedMonoMs = 1_000L,
                nowMonoMs = 1_100L
            )
        )
    }

    @Test
    fun effectiveWeatherRainTransitionDurationMs_interactionActive_forcesZero() {
        assertEquals(
            0L,
            effectiveWeatherRainTransitionDurationMs(
                interactionActive = true,
                requestedDurationMs = 650L
            )
        )
    }

    @Test
    fun shouldThrottleOverlayFrontOrderDuringInteraction_activeInsideWindow_returnsTrue() {
        assertTrue(
            shouldThrottleOverlayFrontOrderDuringInteraction(
                interactionActive = true,
                lastAppliedMonoMs = 10_000L,
                nowMonoMs = 10_010L,
                minIntervalMs = 33L
            )
        )
    }

    @Test
    fun shouldThrottleOverlayFrontOrderDuringInteraction_notActiveOrOutsideWindow_returnsFalse() {
        assertFalse(
            shouldThrottleOverlayFrontOrderDuringInteraction(
                interactionActive = false,
                lastAppliedMonoMs = 10_000L,
                nowMonoMs = 10_010L
            )
        )
        assertFalse(
            shouldThrottleOverlayFrontOrderDuringInteraction(
                interactionActive = true,
                lastAppliedMonoMs = 10_000L,
                nowMonoMs = 10_100L,
                minIntervalMs = 33L
            )
        )
    }

    @Test
    fun resolveMapInteractionDeactivateDelayMs_activateRequest_returnsZero() {
        assertEquals(
            0L,
            resolveMapInteractionDeactivateDelayMs(
                interactionWasActive = true,
                requestedActive = true,
                graceMs = 500L
            )
        )
    }

    @Test
    fun resolveMapInteractionDeactivateDelayMs_noActiveInteraction_returnsZero() {
        assertEquals(
            0L,
            resolveMapInteractionDeactivateDelayMs(
                interactionWasActive = false,
                requestedActive = false,
                graceMs = 500L
            )
        )
    }

    @Test
    fun resolveMapInteractionDeactivateDelayMs_deactivateAfterInteraction_returnsGraceWindow() {
        assertEquals(
            500L,
            resolveMapInteractionDeactivateDelayMs(
                interactionWasActive = true,
                requestedActive = false,
                graceMs = 500L
            )
        )
    }
}
