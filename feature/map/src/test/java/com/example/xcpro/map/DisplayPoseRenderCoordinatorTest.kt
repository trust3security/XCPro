package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.config.MapFeatureFlags
import org.junit.Assert.assertFalse
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DisplayPoseRenderCoordinatorTest {

    @Test
    fun renderDisplayFrame_updatesOverlay_whenCameraUpdateIsUnavailable() {
        val mapState = MapScreenState()
        mapState.mapLibreMap = mock<MapLibreMap>()

        val stateStore = MapStateStore(initialStyleName = "Topo")
        val featureFlags = MapFeatureFlags().apply {
            useRenderFrameSync = false
        }
        val poseCoordinator: DisplayPoseCoordinator = mock()
        val trackingCameraController: MapTrackingCameraController = mock()
        val positionController: MapPositionController = mock()
        val frameLogger: DisplayPoseFrameLogger = mock()
        val pose = DisplayPoseSmoother.DisplayPose(
            location = LatLng(46.0, 7.0),
            trackDeg = 90.0,
            headingDeg = 92.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            accuracyM = 3.0,
            bearingAccuracyDeg = 2.0,
            speedAccuracyMs = 0.4,
            speedMs = 16.0,
            updatedAtMs = 1_950L
        )

        whenever(poseCoordinator.nowMs()).thenReturn(2_000L)
        whenever(poseCoordinator.selectPose(any(), any(), any())).thenReturn(pose)
        whenever(trackingCameraController.updateCamera(any())).thenReturn(null)

        val coordinator = DisplayPoseRenderCoordinator(
            mapState = mapState,
            mapStateReader = stateStore,
            featureFlags = featureFlags,
            poseCoordinator = poseCoordinator,
            replayHeadingProvider = null,
            replayFixProvider = null,
            trackingCameraController = trackingCameraController,
            positionController = positionController,
            frameLogger = frameLogger
        )
        coordinator.updateOrientation(OrientationData())

        var initialCenterCallbackCalled = false
        coordinator.renderDisplayFrame { _, _ ->
            initialCenterCallbackCalled = true
        }

        verify(trackingCameraController, times(1)).updateCamera(any())
        verify(positionController, times(1)).updateOverlay(
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull()
        )
        assertFalse(initialCenterCallbackCalled)
    }
}
