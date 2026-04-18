package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.BearingSource
import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.common.orientation.OrientationData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPoseFrameActivityGateTest {

    @Test
    fun shouldDispatch_returnsFalse_withoutRenderableInput() {
        val fixture = GateFixture()

        assertFalse(
            fixture.gate.shouldDispatch(
                hasRenderableInput = false,
                timeBase = DisplayClock.TimeBase.MONOTONIC,
                mode = DisplayPoseMode.SMOOTHED,
                smoothingProfile = DisplaySmoothingProfile.SMOOTH
            )
        )
    }

    @Test
    fun fixReceived_activatesUntilProfileSettleWindowExpires() {
        val fixture = GateFixture()

        fixture.gate.markFixReceived(DisplaySmoothingProfile.SMOOTH)

        assertTrue(
            fixture.gate.shouldDispatch(
                hasRenderableInput = true,
                timeBase = DisplayClock.TimeBase.MONOTONIC,
                mode = DisplayPoseMode.SMOOTHED,
                smoothingProfile = DisplaySmoothingProfile.SMOOTH
            )
        )

        fixture.nowMonoMs = 499L
        assertTrue(
            fixture.gate.shouldDispatch(
                hasRenderableInput = true,
                timeBase = DisplayClock.TimeBase.MONOTONIC,
                mode = DisplayPoseMode.SMOOTHED,
                smoothingProfile = DisplaySmoothingProfile.SMOOTH
            )
        )

        fixture.nowMonoMs = 501L
        assertFalse(
            fixture.gate.shouldDispatch(
                hasRenderableInput = true,
                timeBase = DisplayClock.TimeBase.MONOTONIC,
                mode = DisplayPoseMode.SMOOTHED,
                smoothingProfile = DisplaySmoothingProfile.SMOOTH
            )
        )
    }

    @Test
    fun updateOrientation_ignoresTinyChanges() {
        val fixture = GateFixture()
        fixture.gate.markFixReceived(DisplaySmoothingProfile.RESPONSIVE)
        fixture.nowMonoMs = 300L
        fixture.gate.shouldDispatch(
            hasRenderableInput = true,
            timeBase = DisplayClock.TimeBase.MONOTONIC,
            mode = DisplayPoseMode.SMOOTHED,
            smoothingProfile = DisplaySmoothingProfile.RESPONSIVE
        )
        fixture.nowMonoMs = 251L
        fixture.gate.updateOrientation(
            orientation = orientation(bearing = 10.0),
            profile = DisplaySmoothingProfile.RESPONSIVE,
            hasRenderableInput = true
        )
        fixture.nowMonoMs = 600L
        fixture.gate.updateOrientation(
            orientation = orientation(bearing = 10.2),
            profile = DisplaySmoothingProfile.RESPONSIVE,
            hasRenderableInput = true
        )

        assertFalse(
            fixture.gate.shouldDispatch(
                hasRenderableInput = true,
                timeBase = DisplayClock.TimeBase.MONOTONIC,
                mode = DisplayPoseMode.SMOOTHED,
                smoothingProfile = DisplaySmoothingProfile.RESPONSIVE
            )
        )
    }

    @Test
    fun configChange_reactivatesWhenRenderableInputExists() {
        val fixture = GateFixture()
        fixture.gate.markFixReceived(DisplaySmoothingProfile.RESPONSIVE)
        fixture.gate.shouldDispatch(
            hasRenderableInput = true,
            timeBase = DisplayClock.TimeBase.MONOTONIC,
            mode = DisplayPoseMode.SMOOTHED,
            smoothingProfile = DisplaySmoothingProfile.RESPONSIVE
        )
        fixture.nowMonoMs = 300L
        fixture.gate.shouldDispatch(
            hasRenderableInput = true,
            timeBase = DisplayClock.TimeBase.MONOTONIC,
            mode = DisplayPoseMode.SMOOTHED,
            smoothingProfile = DisplaySmoothingProfile.RESPONSIVE
        )
        fixture.nowMonoMs = 700L

        assertTrue(
            fixture.gate.shouldDispatch(
                hasRenderableInput = true,
                timeBase = DisplayClock.TimeBase.MONOTONIC,
                mode = DisplayPoseMode.RAW_REPLAY,
                smoothingProfile = DisplaySmoothingProfile.RESPONSIVE
            )
        )
    }

    @Test
    fun replayBypassesGate() {
        val fixture = GateFixture()

        assertTrue(
            fixture.gate.shouldDispatch(
                hasRenderableInput = false,
                timeBase = DisplayClock.TimeBase.REPLAY,
                mode = DisplayPoseMode.SMOOTHED,
                smoothingProfile = DisplaySmoothingProfile.SMOOTH
            )
        )
    }

    private fun orientation(bearing: Double): OrientationData =
        OrientationData(
            bearing = bearing,
            mode = MapOrientationMode.TRACK_UP,
            isValid = true,
            bearingSource = BearingSource.TRACK,
            headingDeg = bearing,
            headingValid = true,
            headingSource = BearingSource.TRACK
        )

    private class GateFixture {
        var nowMonoMs: Long = 0L
        val gate = DisplayPoseFrameActivityGate(nowMonoMs = { nowMonoMs })
    }
}
