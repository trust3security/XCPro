package com.example.xcpro.map.ui.effects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapComposeEffectsCadenceTest {

    @Test
    fun shouldDispatchDisplayPoseFrame_firstFrame_dispatches() {
        assertTrue(
            shouldDispatchDisplayPoseFrame(
                frameNanos = 10_000_000L,
                lastDispatchNanos = 0L,
                minIntervalNanos = DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS
            )
        )
    }

    @Test
    fun shouldDispatchDisplayPoseFrame_insideInterval_skips() {
        assertFalse(
            shouldDispatchDisplayPoseFrame(
                frameNanos = 20_000_000L,
                lastDispatchNanos = 10_000_000L,
                minIntervalNanos = 16_666_667L
            )
        )
    }

    @Test
    fun shouldDispatchDisplayPoseFrame_atOrAfterInterval_dispatches() {
        assertTrue(
            shouldDispatchDisplayPoseFrame(
                frameNanos = 26_666_667L,
                lastDispatchNanos = 10_000_000L,
                minIntervalNanos = 16_666_667L
            )
        )
    }

    @Test
    fun shouldDispatchDisplayPoseFrame_nonMonotonicFrame_skips() {
        assertFalse(
            shouldDispatchDisplayPoseFrame(
                frameNanos = 9_000_000L,
                lastDispatchNanos = 10_000_000L,
                minIntervalNanos = 16_666_667L
            )
        )
    }
}
