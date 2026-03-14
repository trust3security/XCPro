package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapWeatherRainInteractionCadencePolicyTest {

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
}
