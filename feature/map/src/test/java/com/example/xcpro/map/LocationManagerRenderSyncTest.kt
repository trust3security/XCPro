package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.domain.MapShiftBiasMode
import com.example.xcpro.map.model.MapLocationUiModel
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationManagerRenderSyncTest {

    @Test
    fun updateOrientation_requestsRepaint_whenRenderFrameSyncIsEnabled() {
        val cameraController = FakeCameraController()
        val manager = createManager(
            featureFlags = MapFeatureFlags().apply { useRenderFrameSync = true },
            cameraController = cameraController
        )

        manager.updateOrientation(OrientationData())

        assertEquals(1, cameraController.repaintCount)
    }

    @Test
    fun updateLocationFromGps_requestsRepaint_whenRenderFrameSyncIsEnabled() {
        val cameraController = FakeCameraController()
        val manager = createManager(
            featureFlags = MapFeatureFlags().apply { useRenderFrameSync = true },
            cameraController = cameraController
        )

        manager.updateLocationFromGPS(
            location = MapLocationUiModel(
                latitude = -35.0,
                longitude = 149.0,
                speedMs = 18.0,
                bearingDeg = 90.0,
                accuracyMeters = 3.0,
                timestampMs = 1_000L
            ),
            orientation = OrientationData()
        )

        assertEquals(1, cameraController.repaintCount)
    }

    @Test
    fun onDisplayFrame_requestsImmediateRepaint_whenRenderFrameSyncIsEnabled() {
        val cameraController = FakeCameraController()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
        val manager = createManager(
            featureFlags = MapFeatureFlags().apply { useRenderFrameSync = true },
            cameraController = cameraController,
            renderSurfaceDiagnostics = diagnostics
        )

        manager.onDisplayFrame()

        assertEquals(1, cameraController.repaintCount)
        assertEquals(1L, diagnostics.snapshot().repaintRequestCount)
        assertEquals(1L, diagnostics.snapshot().forcedImmediateRepaintRequestCount)
        assertEquals(1L, diagnostics.snapshot().repaintDispatchCount)
    }

    @Test
    fun shouldDispatchLiveDisplayFrame_falseBeforeFirstFix() {
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
        val manager = createManager(
            featureFlags = MapFeatureFlags(),
            cameraController = FakeCameraController(),
            renderSurfaceDiagnostics = diagnostics
        )

        assertFalse(manager.shouldDispatchLiveDisplayFrame())
        assertEquals(1L, diagnostics.snapshot().displayFramePreDispatchSuppressedCount)
    }

    @Test
    fun shouldDispatchLiveDisplayFrame_trueAfterFix() {
        val manager = createManager(
            featureFlags = MapFeatureFlags(),
            cameraController = FakeCameraController()
        )

        manager.updateLocationFromGPS(
            location = MapLocationUiModel(
                latitude = -35.0,
                longitude = 149.0,
                speedMs = 18.0,
                bearingDeg = 90.0,
                accuracyMeters = 3.0,
                timestampMs = 1_000L
            ),
            orientation = OrientationData()
        )

        assertEquals(true, manager.shouldDispatchLiveDisplayFrame())
    }

    @Test
    fun shouldDispatchLiveDisplayFrame_reactivatesOnProfileChange() {
        val stateReader = FakeMapStateReader()
        val manager = createManager(
            featureFlags = MapFeatureFlags(),
            cameraController = FakeCameraController(),
            mapStateReader = stateReader
        )

        manager.updateLocationFromGPS(
            location = MapLocationUiModel(
                latitude = -35.0,
                longitude = 149.0,
                speedMs = 18.0,
                bearingDeg = 90.0,
                accuracyMeters = 3.0,
                timestampMs = 1_000L
            ),
            orientation = OrientationData()
        )
        manager.shouldDispatchLiveDisplayFrame()
        stateReader.displaySmoothingProfileFlow.value = DisplaySmoothingProfile.RESPONSIVE

        assertEquals(true, manager.shouldDispatchLiveDisplayFrame())
    }

    @Test
    fun shouldDispatchLiveDisplayFrame_reactivatesOnMeaningfulOrientationChange() {
        val manager = createManager(
            featureFlags = MapFeatureFlags(),
            cameraController = FakeCameraController()
        )

        manager.updateLocationFromGPS(
            location = MapLocationUiModel(
                latitude = -35.0,
                longitude = 149.0,
                speedMs = 18.0,
                bearingDeg = 90.0,
                accuracyMeters = 3.0,
                timestampMs = 1_000L
            ),
            orientation = OrientationData()
        )
        manager.updateOrientation(
            OrientationData(
                bearing = 10.0,
                bearingSource = BearingSource.TRACK,
                headingDeg = 10.0,
                headingValid = true,
                headingSource = BearingSource.TRACK
            )
        )

        assertEquals(true, manager.shouldDispatchLiveDisplayFrame())
    }

    @Test
    fun spectatorModeClearsLocalOwnshipStateAndHidesOverlay() {
        val stateActions = RecordingMapStateActions()
        val overlayPort = RecordingLocationOverlayPort()
        val manager = createManager(
            featureFlags = MapFeatureFlags(),
            cameraController = FakeCameraController(),
            stateActions = stateActions,
            locationOverlayPort = overlayPort
        )

        manager.setLocalOwnshipRenderEnabled(false)
        manager.updateLocationFromGPS(
            location = MapLocationUiModel(
                latitude = -35.0,
                longitude = 149.0,
                speedMs = 18.0,
                bearingDeg = 90.0,
                accuracyMeters = 3.0,
                timestampMs = 1_000L
            ),
            orientation = OrientationData()
        )

        assertEquals(listOf(false), overlayPort.visibleCalls)
        assertEquals(listOf<MapPoint?>(null), stateActions.currentUserLocations)
        assertEquals(listOf(false), stateActions.recenterButtonStates)
        assertEquals(listOf(false), stateActions.returnButtonStates)
        assertFalse(overlayPort.updateCalls > 0)
    }

    private fun createManager(
        featureFlags: MapFeatureFlags,
        cameraController: FakeCameraController,
        mapStateReader: FakeMapStateReader = FakeMapStateReader(),
        stateActions: MapStateActions = NoOpMapStateActions(),
        locationOverlayPort: MapLocationOverlayPort = NoOpLocationOverlayPort(),
        renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics =
            MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
    ): LocationManager {
        return LocationManager(
            mapStateReader = mapStateReader,
            stateActions = stateActions,
            featureFlags = featureFlags,
            cameraPreferenceReader = object : MapCameraPreferenceReader {
                override fun getMapShiftBiasMode(): MapShiftBiasMode = MapShiftBiasMode.NONE
                override fun getMapShiftBiasStrength(): Double = 0.0
                override fun getGliderScreenPercent(): Int = 35
            },
            locationPreferences = object : MapLocationPreferencesPort {
                override fun getMinSpeedThreshold(): Double = 0.0
                override fun setActiveProfileId(profileId: String) = Unit
            },
            paddingProvider = { intArrayOf(0, 0, 0, 0) },
            sensorsPort = object : MapLocationSensorsPort {
                override fun onLocationPermissionsResult(fineLocationGranted: Boolean) = Unit
                override fun requestLocationPermissions(permissionRequester: MapLocationPermissionRequester) = Unit
                override fun stopLocationTracking(force: Boolean) = Unit
                override fun restartSensorsIfNeeded() = Unit
                override fun isGpsEnabled(): Boolean = true
            },
            cameraControllerProvider = object : MapCameraControllerProvider {
                override fun controllerOrNull(): MapCameraController = cameraController
            },
            mapViewSizeProvider = object : MapViewSizeProvider {
                override fun size(): MapViewSize = MapViewSize(widthPx = 1080, heightPx = 1920)
            },
            cameraUpdateGate = object : MapCameraUpdateGate {
                override fun accept(location: LatLng): Boolean = true
                override fun resetTo(location: LatLng) = Unit
            },
            locationOverlayPort = locationOverlayPort,
            displayPoseSurfacePort = object : MapDisplayPoseSurfacePort {
                override fun isMapReady(): Boolean = true
                override fun currentCameraBearing(): Double? = 0.0
                override fun distancePerPixelMetersAt(latitude: Double): Double? = null
            },
            renderSurfaceDiagnostics = renderSurfaceDiagnostics
        )
    }

    private class FakeMapStateReader : MapStateReader {
        override val safeContainerSize: StateFlow<MapSize> = MutableStateFlow(MapSize(0, 0))
        override val mapStyleName: StateFlow<String> = MutableStateFlow("Topo")
        override val showRecenterButton: StateFlow<Boolean> = MutableStateFlow(false)
        override val showReturnButton: StateFlow<Boolean> = MutableStateFlow(false)
        override val isTrackingLocation: StateFlow<Boolean> = MutableStateFlow(true)
        override val showDistanceCircles: StateFlow<Boolean> = MutableStateFlow(false)
        override val lastUserPanTime: StateFlow<Long> = MutableStateFlow(0L)
        override val hasInitiallyCentered: StateFlow<Boolean> = MutableStateFlow(true)
        override val savedLocation: StateFlow<MapPoint?> = MutableStateFlow(null)
        override val savedZoom: StateFlow<Double?> = MutableStateFlow(null)
        override val savedBearing: StateFlow<Double?> = MutableStateFlow(null)
        override val lastCameraSnapshot: StateFlow<CameraSnapshot?> = MutableStateFlow(null)
        override val currentMode: StateFlow<FlightMode> = MutableStateFlow(FlightMode.CRUISE)
        override val currentZoom: StateFlow<Float> = MutableStateFlow(10f)
        override val targetLatLng: StateFlow<MapPoint?> = MutableStateFlow(null)
        override val targetZoom: StateFlow<Float?> = MutableStateFlow(null)
        override val currentUserLocation: StateFlow<MapPoint?> = MutableStateFlow(null)
        val displayPoseModeFlow = MutableStateFlow(DisplayPoseMode.SMOOTHED)
        override val displayPoseMode: StateFlow<DisplayPoseMode> = displayPoseModeFlow
        val displaySmoothingProfileFlow = MutableStateFlow(DisplaySmoothingProfile.SMOOTH)
        override val displaySmoothingProfile: StateFlow<DisplaySmoothingProfile> =
            displaySmoothingProfileFlow
    }

    private class NoOpMapStateActions : MapStateActions {
        override fun setShowDistanceCircles(show: Boolean) = Unit
        override fun toggleDistanceCircles() = Unit
        override fun updateCurrentZoom(zoom: Float) = Unit
        override fun setTarget(location: MapPoint?, zoom: Float?) = Unit
        override fun setCurrentUserLocation(location: MapPoint?) = Unit
        override fun setHasInitiallyCentered(centered: Boolean) = Unit
        override fun setTrackingLocation(enabled: Boolean) = Unit
        override fun setShowRecenterButton(show: Boolean) = Unit
        override fun setShowReturnButton(show: Boolean) = Unit
        override fun updateLastUserPanTime(timestampMillis: Long) = Unit
        override fun saveLocation(location: MapPoint?, zoom: Double?, bearing: Double?) = Unit
        override fun updateCameraSnapshot(target: MapPoint?, zoom: Double?, bearing: Double?) = Unit
        override fun setDisplayPoseMode(mode: DisplayPoseMode) = Unit
        override fun setDisplaySmoothingProfile(profile: DisplaySmoothingProfile) = Unit
    }

    private class RecordingMapStateActions : MapStateActions {
        val currentUserLocations = CopyOnWriteArrayList<MapPoint?>()
        val recenterButtonStates = CopyOnWriteArrayList<Boolean>()
        val returnButtonStates = CopyOnWriteArrayList<Boolean>()

        override fun setShowDistanceCircles(show: Boolean) = Unit
        override fun toggleDistanceCircles() = Unit
        override fun updateCurrentZoom(zoom: Float) = Unit
        override fun setTarget(location: MapPoint?, zoom: Float?) = Unit
        override fun setCurrentUserLocation(location: MapPoint?) {
            currentUserLocations += location
        }

        override fun setHasInitiallyCentered(centered: Boolean) = Unit
        override fun setTrackingLocation(enabled: Boolean) = Unit
        override fun setShowRecenterButton(show: Boolean) {
            recenterButtonStates += show
        }

        override fun setShowReturnButton(show: Boolean) {
            returnButtonStates += show
        }

        override fun updateLastUserPanTime(timestampMillis: Long) = Unit
        override fun saveLocation(location: MapPoint?, zoom: Double?, bearing: Double?) = Unit
        override fun updateCameraSnapshot(target: MapPoint?, zoom: Double?, bearing: Double?) = Unit
        override fun setDisplayPoseMode(mode: DisplayPoseMode) = Unit
        override fun setDisplaySmoothingProfile(profile: DisplaySmoothingProfile) = Unit
    }

    private class FakeCameraController : MapCameraController {
        private var position =
            MapCameraPositionSnapshot(target = LatLng(0.0, 0.0), zoom = 10.0, bearing = 0.0, tilt = 0.0)
        var repaintCount: Int = 0

        override val cameraPosition: MapCameraPositionSnapshot
            get() = position

        override fun moveCamera(position: MapCameraPositionSnapshot) {
            this.position = position
        }

        override fun animateCamera(
            position: MapCameraPositionSnapshot,
            durationMs: Int,
            callback: MapCameraController.CancelableCallback?
        ) {
            this.position = position
        }

        override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) = Unit

        override fun triggerRepaint() {
            repaintCount += 1
        }
    }

    private class RecordingLocationOverlayPort : MapLocationOverlayPort {
        var updateCalls: Int = 0
        val visibleCalls = mutableListOf<Boolean>()

        override fun updateBlueLocation(
            location: LatLng,
            trackBearing: Double,
            iconHeading: Double,
            mapBearing: Double,
            orientationMode: com.example.xcpro.common.orientation.MapOrientationMode
        ) {
            updateCalls += 1
        }

        override fun setBlueLocationVisible(visible: Boolean) {
            visibleCalls += visible
        }
    }

    private class NoOpLocationOverlayPort : MapLocationOverlayPort {
        override fun updateBlueLocation(
            location: LatLng,
            trackBearing: Double,
            iconHeading: Double,
            mapBearing: Double,
            orientationMode: com.example.xcpro.common.orientation.MapOrientationMode
        ) = Unit

        override fun setBlueLocationVisible(visible: Boolean) = Unit
    }
}
