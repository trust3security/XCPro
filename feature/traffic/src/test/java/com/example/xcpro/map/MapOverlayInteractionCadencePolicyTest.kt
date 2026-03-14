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

}
