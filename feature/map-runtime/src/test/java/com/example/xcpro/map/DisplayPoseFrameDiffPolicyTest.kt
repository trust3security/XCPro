package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class DisplayPoseFrameDiffPolicyTest {

    @Test
    fun isNoOp_returnsTrue_whenSnapshotsOnlyDifferByTinyVisualDelta() {
        val previous = snapshot(location = LatLng(-35.000000, 149.000000))
        val current = snapshot(location = LatLng(-35.000004, 149.000004))

        assertTrue(DisplayPoseFrameDiffPolicy.isNoOp(previous, current))
    }

    @Test
    fun isNoOp_returnsFalse_whenCameraTargetBearingChanges() {
        val previous = snapshot(cameraTargetBearingDeg = 0.0)
        val current = snapshot(cameraTargetBearingDeg = 2.0)

        assertFalse(DisplayPoseFrameDiffPolicy.isNoOp(previous, current))
    }

    @Test
    fun isNoOp_returnsFalse_whenHeadingValidityChanges() {
        val previous = snapshot(headingValid = false)
        val current = snapshot(headingValid = true)

        assertFalse(DisplayPoseFrameDiffPolicy.isNoOp(previous, current))
    }

    private fun snapshot(
        location: LatLng = LatLng(-35.0, 149.0),
        cameraTargetBearingDeg: Double = 0.0,
        headingValid: Boolean = true
    ): DisplayPoseRenderSnapshot =
        DisplayPoseRenderSnapshot(
            location = location,
            trackDeg = 90.0,
            headingDeg = 92.0,
            headingValid = headingValid,
            bearingAccuracyDeg = 2.0,
            speedAccuracyMs = 0.4,
            mapBearingDeg = 0.0,
            cameraTargetBearingDeg = cameraTargetBearingDeg,
            orientationMode = MapOrientationMode.NORTH_UP,
            speedMs = 0.0,
            timeBase = DisplayClock.TimeBase.MONOTONIC
        )
}
