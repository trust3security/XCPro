package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import org.junit.Assert.assertSame
import org.junit.Test

class DisplayPoseSelectorTest {

    @Test
    fun rawReplayModePrefersRawPose() {
        val raw = buildPose(1.0, 2.0)
        val smoothed = buildPose(3.0, 4.0)

        val result = DisplayPoseSelector.selectPose(
            mode = DisplayPoseMode.RAW_REPLAY,
            rawPose = raw,
            smoothedPose = smoothed
        )

        assertSame(raw, result)
    }

    @Test
    fun smoothedModePrefersSmoothedPose() {
        val raw = buildPose(1.0, 2.0)
        val smoothed = buildPose(3.0, 4.0)

        val result = DisplayPoseSelector.selectPose(
            mode = DisplayPoseMode.SMOOTHED,
            rawPose = raw,
            smoothedPose = smoothed
        )

        assertSame(smoothed, result)
    }

    @Test
    fun rawReplayFallsBackToSmoothedWhenMissing() {
        val smoothed = buildPose(3.0, 4.0)

        val result = DisplayPoseSelector.selectPose(
            mode = DisplayPoseMode.RAW_REPLAY,
            rawPose = null,
            smoothedPose = smoothed
        )

        assertSame(smoothed, result)
    }

    @Test
    fun smoothedFallsBackToRawWhenMissing() {
        val raw = buildPose(1.0, 2.0)

        val result = DisplayPoseSelector.selectPose(
            mode = DisplayPoseMode.SMOOTHED,
            rawPose = raw,
            smoothedPose = null
        )

        assertSame(raw, result)
    }

    private fun buildPose(lat: Double, lon: Double): DisplayPoseSmoother.DisplayPose {
        return DisplayPoseSmoother.DisplayPose(
            location = org.maplibre.android.geometry.LatLng(lat, lon),
            trackDeg = 0.0,
            headingDeg = 0.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            accuracyM = 5.0,
            bearingAccuracyDeg = null,
            speedAccuracyMs = null,
            speedMs = 0.0,
            updatedAtMs = 0L
        )
    }
}
