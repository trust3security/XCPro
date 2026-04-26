package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class DisplayPoseCoordinatorTest {

    @Test
    fun updateFromFix_reports_time_base_changes_and_resets() {
        val clock = FakeClock()
        val pipeline = FakePipeline()
        val coordinator = DisplayPoseCoordinator(clock, pipeline)

        val first = coordinator.updateFromFix(buildEnvelope(1000L, DisplayClock.TimeBase.MONOTONIC))
        assertTrue(first.timeBaseChanged)
        assertEquals(DisplayClock.TimeBase.MONOTONIC, coordinator.timeBase)
        assertEquals(1, pipeline.resetCalls)
        assertEquals(1, pipeline.pushCalls)
        assertEquals(1000L, clock.lastUpdateTimestamp)
        assertEquals(DisplayClock.TimeBase.MONOTONIC, clock.lastUpdateBase)

        val second = coordinator.updateFromFix(buildEnvelope(1100L, DisplayClock.TimeBase.MONOTONIC))
        assertFalse(second.timeBaseChanged)
        assertEquals(1, pipeline.resetCalls)
        assertEquals(2, pipeline.pushCalls)

        val third = coordinator.updateFromFix(buildEnvelope(2000L, DisplayClock.TimeBase.REPLAY))
        assertTrue(third.timeBaseChanged)
        assertEquals(2, pipeline.resetCalls)
        assertEquals(DisplayClock.TimeBase.REPLAY, coordinator.timeBase)
    }

    @Test
    fun replaySpeedMultiplier_passes_through() {
        val clock = FakeClock()
        val coordinator = DisplayPoseCoordinator(clock, FakePipeline())

        coordinator.replaySpeedMultiplier = 2.5

        assertEquals(2.5, clock.replaySpeedMultiplier, 0.0)
    }

    @Test
    fun selectPose_delegates_to_pipeline() {
        val clock = FakeClock()
        val pipeline = FakePipeline()
        val coordinator = DisplayPoseCoordinator(clock, pipeline)
        val expected = DisplayPoseSmoother.DisplayPose(
            location = LatLng(1.0, 2.0),
            trackDeg = 90.0,
            headingDeg = 80.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            accuracyM = 5.0,
            bearingAccuracyDeg = 1.0,
            speedAccuracyMs = 0.5,
            speedMs = 12.0,
            updatedAtMs = 1234L
        )
        pipeline.selectPoseResult = expected

        val result = coordinator.selectPose(
            1234L,
            DisplayPoseMode.SMOOTHED,
            DisplaySmoothingProfile.SMOOTH
        )

        assertEquals(expected, result)
    }

    @Test
    fun nowMs_delegates_to_clock() {
        val clock = FakeClock().apply { nowMsValue = 4242L }
        val coordinator = DisplayPoseCoordinator(clock, FakePipeline())

        assertEquals(4242L, coordinator.nowMs())
    }

    private fun buildEnvelope(
        timestampMs: Long,
        timeBase: DisplayClock.TimeBase
    ): LocationFeedAdapter.RawFixEnvelope {
        return LocationFeedAdapter.RawFixEnvelope(
            fix = DisplayPoseSmoother.RawFix(
                latitude = 1.0,
                longitude = 2.0,
                speedMs = 10.0,
                trackDeg = 45.0,
                headingDeg = 40.0,
                accuracyM = 5.0,
                bearingAccuracyDeg = 1.0,
                speedAccuracyMs = 0.5,
                timestampMs = timestampMs,
                orientationMode = MapOrientationMode.NORTH_UP
            ),
            timeBase = timeBase
        )
    }

    private class FakeClock : DisplayTimeSource {
        var nowMsValue: Long = 0L
        var lastUpdateTimestamp: Long? = null
        var lastUpdateBase: DisplayClock.TimeBase? = null
        override var replaySpeedMultiplier: Double = 1.0

        override fun updateFromFix(timestampMs: Long, base: DisplayClock.TimeBase) {
            lastUpdateTimestamp = timestampMs
            lastUpdateBase = base
        }

        override fun nowMs(): Long = nowMsValue

        override fun clear() = Unit
    }

    private class FakePipeline : PosePipeline {
        var resetCalls = 0
        var pushCalls = 0
        var selectPoseResult: DisplayPoseSmoother.DisplayPose? = null

        override fun pushRawFix(fix: DisplayPoseSmoother.RawFix) {
            pushCalls++
        }

        override fun resetSmoother() {
            resetCalls++
        }

        override fun selectPose(
            nowMs: Long,
            mode: DisplayPoseMode,
            smoothingProfile: DisplaySmoothingProfile
        ): DisplayPoseSmoother.DisplayPose? {
            return selectPoseResult
        }

        override fun clear() = Unit
    }
}
