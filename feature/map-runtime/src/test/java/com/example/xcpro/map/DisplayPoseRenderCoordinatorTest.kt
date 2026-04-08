package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.config.MapFeatureFlags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import org.maplibre.android.geometry.LatLng
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DisplayPoseRenderCoordinatorTest {

    @Test
    fun renderDisplayFrame_updatesOverlay_whenCameraUpdateIsUnavailable() {
        val stateStore = mock<MapStateReader>().apply {
            whenever(displayPoseMode).thenReturn(MutableStateFlow(DisplayPoseMode.SMOOTHED))
            whenever(displaySmoothingProfile).thenReturn(MutableStateFlow(DisplaySmoothingProfile.SMOOTH))
        }
        val surfacePort = mock<MapDisplayPoseSurfacePort>().apply {
            whenever(isMapReady()).thenReturn(true)
            whenever(currentCameraBearing()).thenReturn(0.0)
        }
        val featureFlags = MapFeatureFlags().apply {
            useRenderFrameSync = false
        }
        val poseCoordinator: DisplayPoseCoordinator = mock()
        val trackingCameraController: MapTrackingCameraController = mock()
        val positionController: MapPositionController = mock()
        val frameLogger: DisplayPoseFrameLogger = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
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
        whenever(poseCoordinator.timeBase).thenReturn(DisplayClock.TimeBase.MONOTONIC)
        whenever(trackingCameraController.updateCamera(any())).thenReturn(null)

        val coordinator = DisplayPoseRenderCoordinator(
            surfacePort = surfacePort,
            mapStateReader = stateStore,
            featureFlags = featureFlags,
            poseCoordinator = poseCoordinator,
            replayHeadingProvider = null,
            replayFixProvider = null,
            trackingCameraController = trackingCameraController,
            positionController = positionController,
            frameLogger = frameLogger,
            diagnostics = diagnostics
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

    @Test
    fun renderDisplayFrame_notifiesListener_inNonRenderFrameSyncMode() {
        val stateStore = mock<MapStateReader>().apply {
            whenever(displayPoseMode).thenReturn(MutableStateFlow(DisplayPoseMode.SMOOTHED))
            whenever(displaySmoothingProfile).thenReturn(MutableStateFlow(DisplaySmoothingProfile.SMOOTH))
        }
        val surfacePort = mock<MapDisplayPoseSurfacePort>().apply {
            whenever(isMapReady()).thenReturn(true)
            whenever(currentCameraBearing()).thenReturn(0.0)
        }
        val featureFlags = MapFeatureFlags().apply {
            useRenderFrameSync = false
        }
        val poseCoordinator: DisplayPoseCoordinator = mock()
        val trackingCameraController: MapTrackingCameraController = mock()
        val positionController: MapPositionController = mock()
        val frameLogger: DisplayPoseFrameLogger = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 777L })
        val pose = DisplayPoseSmoother.DisplayPose(
            location = LatLng(46.0, 7.0),
            trackDeg = 90.0,
            headingDeg = 92.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            accuracyM = 3.0,
            bearingAccuracyDeg = 2.0,
            speedAccuracyMs = 0.4,
            speedMs = 12.0,
            updatedAtMs = 1_950L
        )

        whenever(poseCoordinator.nowMs()).thenReturn(2_000L)
        whenever(poseCoordinator.selectPose(any(), any(), any())).thenReturn(pose)
        whenever(poseCoordinator.timeBase).thenReturn(DisplayClock.TimeBase.MONOTONIC)
        whenever(trackingCameraController.updateCamera(any())).thenReturn(null)

        val coordinator = DisplayPoseRenderCoordinator(
            surfacePort = surfacePort,
            mapStateReader = stateStore,
            featureFlags = featureFlags,
            poseCoordinator = poseCoordinator,
            replayHeadingProvider = null,
            replayFixProvider = null,
            trackingCameraController = trackingCameraController,
            positionController = positionController,
            frameLogger = frameLogger,
            diagnostics = diagnostics
        )
        coordinator.updateOrientation(OrientationData())

        var snapshot: DisplayPoseSnapshot? = null
        coordinator.setDisplayPoseFrameListener { emitted ->
            snapshot = emitted
        }

        coordinator.renderDisplayFrame { _, _ -> }

        assertNotNull(snapshot)
        assertEquals(LatLng(46.0, 7.0), snapshot!!.location)
        assertEquals(1_950L, snapshot!!.timestampMs)
        assertEquals(1L, snapshot!!.frameId)
        assertEquals(DisplayClock.TimeBase.MONOTONIC, snapshot!!.timeBase)
    }

    @Test
    fun renderDisplayFrame_notifiesListener_inRenderFrameSyncMode() {
        val stateStore = mock<MapStateReader>().apply {
            whenever(displayPoseMode).thenReturn(MutableStateFlow(DisplayPoseMode.SMOOTHED))
            whenever(displaySmoothingProfile).thenReturn(MutableStateFlow(DisplaySmoothingProfile.SMOOTH))
        }
        val surfacePort = mock<MapDisplayPoseSurfacePort>().apply {
            whenever(isMapReady()).thenReturn(true)
            whenever(currentCameraBearing()).thenReturn(0.0)
        }
        val featureFlags = MapFeatureFlags().apply {
            useRenderFrameSync = true
        }
        val poseCoordinator: DisplayPoseCoordinator = mock()
        val trackingCameraController: MapTrackingCameraController = mock()
        val positionController: MapPositionController = mock()
        val frameLogger: DisplayPoseFrameLogger = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 778L })
        val pose = DisplayPoseSmoother.DisplayPose(
            location = LatLng(46.1, 7.1),
            trackDeg = 90.0,
            headingDeg = 92.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            accuracyM = 3.0,
            bearingAccuracyDeg = 2.0,
            speedAccuracyMs = 0.4,
            speedMs = 12.0,
            updatedAtMs = 2_050L
        )

        whenever(poseCoordinator.nowMs()).thenReturn(2_100L)
        whenever(poseCoordinator.selectPose(any(), any(), any())).thenReturn(pose)
        whenever(poseCoordinator.timeBase).thenReturn(DisplayClock.TimeBase.REPLAY)
        whenever(trackingCameraController.updateCamera(any())).thenReturn(null)

        val coordinator = DisplayPoseRenderCoordinator(
            surfacePort = surfacePort,
            mapStateReader = stateStore,
            featureFlags = featureFlags,
            poseCoordinator = poseCoordinator,
            replayHeadingProvider = null,
            replayFixProvider = null,
            trackingCameraController = trackingCameraController,
            positionController = positionController,
            frameLogger = frameLogger,
            diagnostics = diagnostics
        )
        coordinator.updateOrientation(OrientationData())

        var snapshot: DisplayPoseSnapshot? = null
        coordinator.setDisplayPoseFrameListener { emitted ->
            snapshot = emitted
        }

        coordinator.renderDisplayFrame { _, _ -> }

        assertNotNull(snapshot)
        assertEquals(DisplayClock.TimeBase.REPLAY, snapshot!!.timeBase)
        assertEquals(1L, snapshot!!.frameId)
    }

    @Test
    fun getDisplayPoseSnapshot_keepsRenderedReplayTimeBase_afterCoordinatorSwitchesMode() {
        val stateStore = mock<MapStateReader>().apply {
            whenever(displayPoseMode).thenReturn(MutableStateFlow(DisplayPoseMode.SMOOTHED))
            whenever(displaySmoothingProfile).thenReturn(MutableStateFlow(DisplaySmoothingProfile.SMOOTH))
        }
        val surfacePort = mock<MapDisplayPoseSurfacePort>().apply {
            whenever(isMapReady()).thenReturn(true)
            whenever(currentCameraBearing()).thenReturn(0.0)
        }
        val poseCoordinator: DisplayPoseCoordinator = mock()
        val trackingCameraController: MapTrackingCameraController = mock()
        val positionController: MapPositionController = mock()
        val frameLogger: DisplayPoseFrameLogger = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 779L })
        val pose = DisplayPoseSmoother.DisplayPose(
            location = LatLng(46.2, 7.2),
            trackDeg = 90.0,
            headingDeg = 92.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            accuracyM = 3.0,
            bearingAccuracyDeg = 2.0,
            speedAccuracyMs = 0.4,
            speedMs = 12.0,
            updatedAtMs = 2_150L
        )

        whenever(poseCoordinator.nowMs()).thenReturn(2_200L)
        whenever(poseCoordinator.selectPose(any(), any(), any())).thenReturn(pose)
        whenever(poseCoordinator.timeBase).thenReturn(
            DisplayClock.TimeBase.REPLAY,
            DisplayClock.TimeBase.MONOTONIC
        )
        whenever(trackingCameraController.updateCamera(any())).thenReturn(null)

        val coordinator = DisplayPoseRenderCoordinator(
            surfacePort = surfacePort,
            mapStateReader = stateStore,
            featureFlags = MapFeatureFlags(),
            poseCoordinator = poseCoordinator,
            replayHeadingProvider = null,
            replayFixProvider = null,
            trackingCameraController = trackingCameraController,
            positionController = positionController,
            frameLogger = frameLogger,
            diagnostics = diagnostics
        )
        coordinator.updateOrientation(OrientationData())

        coordinator.renderDisplayFrame { _, _ -> }
        val snapshot = coordinator.getDisplayPoseSnapshot()

        assertNotNull(snapshot)
        assertEquals(DisplayClock.TimeBase.REPLAY, snapshot!!.timeBase)
        assertEquals(1L, snapshot.frameId)
    }

    @Test
    fun getDisplayPoseSnapshot_keepsRenderedLiveTimeBase_afterCoordinatorSwitchesToReplay() {
        val stateStore = mock<MapStateReader>().apply {
            whenever(displayPoseMode).thenReturn(MutableStateFlow(DisplayPoseMode.SMOOTHED))
            whenever(displaySmoothingProfile).thenReturn(MutableStateFlow(DisplaySmoothingProfile.SMOOTH))
        }
        val surfacePort = mock<MapDisplayPoseSurfacePort>().apply {
            whenever(isMapReady()).thenReturn(true)
            whenever(currentCameraBearing()).thenReturn(0.0)
        }
        val poseCoordinator: DisplayPoseCoordinator = mock()
        val trackingCameraController: MapTrackingCameraController = mock()
        val positionController: MapPositionController = mock()
        val frameLogger: DisplayPoseFrameLogger = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 780L })
        val pose = DisplayPoseSmoother.DisplayPose(
            location = LatLng(46.3, 7.3),
            trackDeg = 90.0,
            headingDeg = 92.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            accuracyM = 3.0,
            bearingAccuracyDeg = 2.0,
            speedAccuracyMs = 0.4,
            speedMs = 12.0,
            updatedAtMs = 2_250L
        )

        whenever(poseCoordinator.nowMs()).thenReturn(2_300L)
        whenever(poseCoordinator.selectPose(any(), any(), any())).thenReturn(pose)
        whenever(poseCoordinator.timeBase).thenReturn(
            DisplayClock.TimeBase.MONOTONIC,
            DisplayClock.TimeBase.REPLAY
        )
        whenever(trackingCameraController.updateCamera(any())).thenReturn(null)

        val coordinator = DisplayPoseRenderCoordinator(
            surfacePort = surfacePort,
            mapStateReader = stateStore,
            featureFlags = MapFeatureFlags(),
            poseCoordinator = poseCoordinator,
            replayHeadingProvider = null,
            replayFixProvider = null,
            trackingCameraController = trackingCameraController,
            positionController = positionController,
            frameLogger = frameLogger,
            diagnostics = diagnostics
        )
        coordinator.updateOrientation(OrientationData())

        coordinator.renderDisplayFrame { _, _ -> }
        val snapshot = coordinator.getDisplayPoseSnapshot()

        assertNotNull(snapshot)
        assertEquals(DisplayClock.TimeBase.MONOTONIC, snapshot!!.timeBase)
        assertEquals(1L, snapshot.frameId)
    }

    @Test
    fun renderDisplayFrame_updatesOverlayForSmallButVisibleLiveMotion() {
        val stateStore = mock<MapStateReader>().apply {
            whenever(displayPoseMode).thenReturn(MutableStateFlow(DisplayPoseMode.SMOOTHED))
            whenever(displaySmoothingProfile).thenReturn(MutableStateFlow(DisplaySmoothingProfile.SMOOTH))
        }
        val surfacePort = mock<MapDisplayPoseSurfacePort>().apply {
            whenever(isMapReady()).thenReturn(true)
            whenever(currentCameraBearing()).thenReturn(0.0)
            whenever(distancePerPixelMetersAt(any())).thenReturn(0.25)
        }
        val featureFlags = MapFeatureFlags().apply {
            useRenderFrameSync = false
        }
        val poseCoordinator: DisplayPoseCoordinator = mock()
        val trackingCameraController: MapTrackingCameraController = mock()
        val positionController: MapPositionController = mock()
        val frameLogger: DisplayPoseFrameLogger = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 781L })
        val firstPose = DisplayPoseSmoother.DisplayPose(
            location = LatLng(46.0000000, 7.0000000),
            trackDeg = 90.0,
            headingDeg = 92.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            accuracyM = 3.0,
            bearingAccuracyDeg = 2.0,
            speedAccuracyMs = 0.4,
            speedMs = 12.0,
            updatedAtMs = 2_350L
        )
        val secondPose = firstPose.copy(
            location = LatLng(46.0000030, 7.0000000),
            updatedAtMs = 2_375L
        )

        whenever(poseCoordinator.nowMs()).thenReturn(2_400L, 2_425L)
        whenever(poseCoordinator.selectPose(any(), any(), any())).thenReturn(firstPose, secondPose)
        whenever(poseCoordinator.timeBase).thenReturn(DisplayClock.TimeBase.MONOTONIC)
        whenever(trackingCameraController.updateCamera(any())).thenReturn(null)

        val coordinator = DisplayPoseRenderCoordinator(
            surfacePort = surfacePort,
            mapStateReader = stateStore,
            featureFlags = featureFlags,
            poseCoordinator = poseCoordinator,
            replayHeadingProvider = null,
            replayFixProvider = null,
            trackingCameraController = trackingCameraController,
            positionController = positionController,
            frameLogger = frameLogger,
            diagnostics = diagnostics
        )
        coordinator.updateOrientation(OrientationData())

        coordinator.renderDisplayFrame { _, _ -> }
        coordinator.renderDisplayFrame { _, _ -> }

        verify(trackingCameraController, times(2)).updateCamera(any())
        verify(positionController, times(2)).updateOverlay(
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
        assertEquals(0L, diagnostics.snapshot().displayFrameNoOpSkippedCount)
    }

    @Test
    fun renderDisplayFrame_skipsNoOpFrames_whenRenderedOutputIsUnchanged() {
        val stateStore = mock<MapStateReader>().apply {
            whenever(displayPoseMode).thenReturn(MutableStateFlow(DisplayPoseMode.SMOOTHED))
            whenever(displaySmoothingProfile).thenReturn(MutableStateFlow(DisplaySmoothingProfile.SMOOTH))
        }
        val surfacePort = mock<MapDisplayPoseSurfacePort>().apply {
            whenever(isMapReady()).thenReturn(true)
            whenever(currentCameraBearing()).thenReturn(0.0)
        }
        val featureFlags = MapFeatureFlags().apply {
            useRenderFrameSync = false
        }
        val poseCoordinator: DisplayPoseCoordinator = mock()
        val trackingCameraController: MapTrackingCameraController = mock()
        val positionController: MapPositionController = mock()
        val frameLogger: DisplayPoseFrameLogger = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 456L })
        val pose = DisplayPoseSmoother.DisplayPose(
            location = LatLng(46.0, 7.0),
            trackDeg = 90.0,
            headingDeg = 92.0,
            orientationMode = MapOrientationMode.NORTH_UP,
            accuracyM = 3.0,
            bearingAccuracyDeg = 2.0,
            speedAccuracyMs = 0.4,
            speedMs = 0.0,
            updatedAtMs = 1_950L
        )

        whenever(poseCoordinator.nowMs()).thenReturn(2_000L)
        whenever(poseCoordinator.selectPose(any(), any(), any())).thenReturn(pose)
        whenever(poseCoordinator.timeBase).thenReturn(DisplayClock.TimeBase.MONOTONIC)
        whenever(trackingCameraController.updateCamera(any())).thenReturn(null)

        val coordinator = DisplayPoseRenderCoordinator(
            surfacePort = surfacePort,
            mapStateReader = stateStore,
            featureFlags = featureFlags,
            poseCoordinator = poseCoordinator,
            replayHeadingProvider = null,
            replayFixProvider = null,
            trackingCameraController = trackingCameraController,
            positionController = positionController,
            frameLogger = frameLogger,
            diagnostics = diagnostics
        )
        coordinator.updateOrientation(OrientationData())

        val emittedSnapshots = mutableListOf<DisplayPoseSnapshot>()
        coordinator.setDisplayPoseFrameListener { snapshot ->
            emittedSnapshots += snapshot
        }
        coordinator.renderDisplayFrame { _, _ -> }
        coordinator.renderDisplayFrame { _, _ -> }

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
        assertEquals(1L, diagnostics.snapshot().displayFrameNoOpSkippedCount)
        assertEquals(1, emittedSnapshots.size)
    }
}
