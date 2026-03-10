package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.MapOrientationPreferences
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.map.helpers.GliderPaddingHelper
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.domain.MapShiftBiasCalculator
import com.example.xcpro.map.MapLocationFilter
import com.example.xcpro.map.MapLibreProjector
import com.example.xcpro.map.MapPositionController
import com.example.xcpro.replay.ReplayDisplayPose
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import kotlinx.coroutines.CoroutineScope

class LocationManager(
    private val context: Context,
    private val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions,
    private val coroutineScope: CoroutineScope,
    private val sensorsUseCase: MapSensorsUseCase,
    private val featureFlags: MapFeatureFlags,
    private val replayHeadingProvider: ((Long) -> Double?)? = null,
    private val replayFixProvider: ((Long) -> ReplayDisplayPose?)? = null
) {
    companion object {
        private const val TAG = "LocationManager"
        private const val INITIAL_ZOOM_LEVEL = 10.0
        private const val CAMERA_MIN_UPDATE_INTERVAL_MS = 80L
        private const val CAMERA_ANIMATION_MS = 250
        private const val CAMERA_BEARING_EPS_DEG = 2.0
        private const val FRAME_LOG_INTERVAL_MS = 100L
    }

    private val orientationPreferences = MapOrientationPreferences(context)
    private val gliderPaddingHelper = GliderPaddingHelper(context.resources, orientationPreferences)
    private val mapCameraPreferenceReader: MapCameraPreferenceReader =
        MapCameraPreferenceReaderAdapter(orientationPreferences)
    private val userCameraControllerProvider: MapCameraControllerProvider =
        MapLibreCameraControllerProvider(mapState)
    private val mapViewSizeProvider: MapViewSizeProvider = MapScreenSizeProvider(mapState)
    private val sensorsController = LocationSensorsController(
        context = context,
        scope = coroutineScope,
        sensorsUseCase = sensorsUseCase
    )
    private val cameraUpdateGateFilter = MapLocationFilter(
        MapLocationFilter.Config(
            thresholdPx = featureFlags.locationJitterThresholdPx,
            historySize = featureFlags.locationOffsetHistorySize
        ),
        MapLibreProjector()
    )
    private val cameraUpdateGate: MapCameraUpdateGate = MapCameraUpdateGateAdapter(
        gate = cameraUpdateGateFilter,
        mapProvider = { mapState.mapLibreMap }
    )
    private val positionController = MapPositionController(
        mapState = mapState,
        maxBearingStepDegProvider = { featureFlags.maxTrackBearingStepDeg },
        headingSmoothingEnabledProvider = { featureFlags.useIconHeadingSmoothing },
        offsetHistorySize = featureFlags.locationOffsetHistorySize,
        iconRotationConfigProvider = {
            if (featureFlags.allowHeadingWhileStationary) {
                IconRotationConfig.fromMinSpeedThreshold(0.0)
            } else {
                IconRotationConfig.fromPreferences(orientationPreferences)
            }
        }
    )
    private val mapShiftBiasCalculator = MapShiftBiasCalculator()
    private val mapShiftBiasResetter: MapShiftBiasResetter =
        MapShiftBiasResetterAdapter(mapShiftBiasCalculator)
    private val cameraPolicy = MapCameraPolicy(
        offsetAverager = object : MapCameraPolicy.OffsetAverager {
            override fun remember(topPx: Float, bottomPx: Float) {
                positionController.rememberOffset(
                    MapPositionController.Offset(x = topPx, y = bottomPx)
                )
            }

            override fun averaged(): MapCameraPolicy.AveragedOffset {
                val averaged = positionController.averagedOffset()
                return MapCameraPolicy.AveragedOffset(
                    topPx = averaged.x,
                    bottomPx = averaged.y
                )
            }
        },
        biasCalculator = mapShiftBiasCalculator
    )
    private val feedAdapter = LocationFeedAdapter()
    private val poseCoordinator = DisplayPoseCoordinator(
        clock = DisplayClockSource(),
        pipeline = DisplayPosePipelineAdapter(
            DisplayPosePipeline(
                minSpeedProvider = { orientationPreferences.getMinSpeedThreshold() },
                adaptiveSmoothingEnabled = featureFlags.useAdaptiveDisplaySmoothing
            )
        )
    )
    private val userInteractionController = MapUserInteractionController(
        mapStateReader = mapStateReader,
        stateActions = stateActions,
        paddingProvider = { gliderPaddingHelper.paddingArray() },
        cameraControllerProvider = userCameraControllerProvider,
        logTag = TAG
    )
    private val trackingCameraController = MapTrackingCameraController(
        mapSizeProvider = mapViewSizeProvider,
        mapStateReader = mapStateReader,
        stateActions = stateActions,
        preferenceReader = mapCameraPreferenceReader,
        paddingProvider = { gliderPaddingHelper.paddingArray() },
        positionController = positionController,
        cameraPolicy = cameraPolicy,
        cameraUpdateGate = cameraUpdateGate,
        biasResetter = mapShiftBiasResetter,
        cameraControllerProvider = {
            userCameraControllerProvider.controllerOrNull()
        },
        featureFlags = featureFlags,
        initialZoomLevel = INITIAL_ZOOM_LEVEL,
        minUpdateIntervalMs = CAMERA_MIN_UPDATE_INTERVAL_MS,
        bearingEpsDeg = CAMERA_BEARING_EPS_DEG,
        defaultAnimationMs = CAMERA_ANIMATION_MS
    )
    private val frameLogger = DisplayPoseFrameLogger(
        tag = TAG,
        defaultIntervalMs = FRAME_LOG_INTERVAL_MS,
        timeBaseProvider = { poseCoordinator.timeBase },
        featureFlags = featureFlags
    )
    private val displayPoseRenderer = DisplayPoseRenderCoordinator(
        mapState = mapState,
        mapStateReader = mapStateReader,
        featureFlags = featureFlags,
        poseCoordinator = poseCoordinator,
        replayHeadingProvider = replayHeadingProvider,
        replayFixProvider = replayFixProvider,
        trackingCameraController = trackingCameraController,
        positionController = positionController,
        frameLogger = frameLogger
    )
    private val renderFrameSync = RenderFrameSync(
        isEnabled = { featureFlags.useRenderFrameSync },
        onRenderFrame = { onRenderFrame() }
    )

    // Map UI state proxies (MapStateStore is the single owner)
    private var currentUserLocation: LatLng?
        get() = mapStateReader.currentUserLocation.value?.let { LatLng(it.latitude, it.longitude) }
        set(value) {
            stateActions.setCurrentUserLocation(
                value?.let { MapStateStore.MapPoint(it.latitude, it.longitude) }
            )
        }

    fun onLocationPermissionsResult(fineLocationGranted: Boolean) {
        sensorsController.onLocationPermissionsResult(fineLocationGranted)
    }

    fun checkAndRequestLocationPermissions(
        locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        sensorsController.checkAndRequestLocationPermissions(locationPermissionLauncher)
    }

    fun stopLocationTracking(force: Boolean = false) {
        sensorsController.stopLocationTracking(force)
    }

    /**
     * Restart sensors after returning from sleep mode
     * This ensures GPS and other sensors resume properly when screen turns back on
     */
    fun restartSensorsIfNeeded() {
        sensorsController.restartSensorsIfNeeded()
    }

    fun isGpsEnabled(): Boolean = sensorsUseCase.isGpsEnabled()

    fun setActiveProfileId(profileId: String) {
        orientationPreferences.setActiveProfileId(profileId)
    }


    fun updateLocationFromGPS(
        location: MapLocationUiModel,
        orientation: OrientationData
    ) {
        displayPoseRenderer.updateOrientation(orientation)
        if (!isValidCoordinate(location.latitude, location.longitude)) {
            logLocationDebug {
                "Live GPS: invalid coordinates (lat=${location.latitude}, lon=${location.longitude})"
            }
            return
        }
        val envelope = feedAdapter.fromGps(location, orientation)
        pushRawFix(envelope)
    }

    fun updateOrientation(orientation: OrientationData) {
        displayPoseRenderer.updateOrientation(orientation)
    }

    fun setReplaySpeedMultiplier(multiplier: Double) {
        if (multiplier.isFinite() && multiplier > 0.0) {
            poseCoordinator.replaySpeedMultiplier = multiplier
        }
    }

    fun onDisplayFrame() {
        if (featureFlags.useRenderFrameSync) {
            userCameraControllerProvider.controllerOrNull()?.triggerRepaint()
            return
        }
        renderDisplayFrame()
    }

    fun onRenderFrame() {
        if (!featureFlags.useRenderFrameSync) {
            return
        }
        renderDisplayFrame()
    }

    data class DisplayPoseSnapshot(
        val location: LatLng,
        val timestampMs: Long,
        val frameId: Long
    )

    fun getDisplayPoseLocation(): LatLng? = displayPoseRenderer.getDisplayPoseLocation()

    fun getDisplayPoseTimestampMs(): Long? =
        displayPoseRenderer.getDisplayPoseTimestampMs()

    fun getDisplayPoseSnapshot(): DisplayPoseSnapshot? =
        displayPoseRenderer.getDisplayPoseSnapshot()

    fun setDisplayPoseFrameListener(listener: ((DisplayPoseSnapshot) -> Unit)?) {
        displayPoseRenderer.setDisplayPoseFrameListener(listener)
    }

    private fun renderDisplayFrame() {
        displayPoseRenderer.renderDisplayFrame { poseLocation, zoom ->
            saveLocation(poseLocation, zoom, 0.0)
            logLocationDebug {
                "INITIAL CENTERING: centered map on first GPS location: " +
                    "${poseLocation.latitude}, ${poseLocation.longitude}"
            }
        }
    }

    fun bindRenderFrameListener(mapView: MapView) {
        renderFrameSync.bind(mapView)
    }

    fun unbindRenderFrameListener() {
        renderFrameSync.unbind()
    }

    fun updateLocationFromFlightData(
        liveData: RealTimeFlightData,
        orientation: OrientationData
    ) {
        logLocationDebug {
            val groundSpeedKnots = String.format(
                "%.1f",
                UnitsConverter.msToKnots(liveData.groundSpeed)
            )
            "Replay/live GPS: lat=${liveData.latitude}, lon=${liveData.longitude}, " +
                "accuracy=${liveData.accuracy}, gpsAlt=${liveData.gpsAltitude}m, " +
                "gs=${groundSpeedKnots}kt, track=${liveData.track}"
        }

        if (liveData.latitude == 0.0 || liveData.longitude == 0.0) {
            logLocationDebug {
                "Replay feed: invalid coordinates (lat=${liveData.latitude}, lon=${liveData.longitude})"
            }
            return
        }
        if (!isValidCoordinate(liveData.latitude, liveData.longitude)) {
            logLocationDebug {
                "Replay feed: invalid coordinates (lat=${liveData.latitude}, lon=${liveData.longitude})"
            }
            return
        }

        displayPoseRenderer.updateOrientation(orientation)
        val envelope = feedAdapter.fromFlightData(liveData, orientation)
        pushRawFix(envelope)
    }

    private fun pushRawFix(envelope: LocationFeedAdapter.RawFixEnvelope) {
        val updateResult = poseCoordinator.updateFromFix(envelope)
        val fixLocation = LatLng(envelope.fix.latitude, envelope.fix.longitude)
        if (updateResult.timeBaseChanged) {
            trackingCameraController.onTimeBaseChanged(fixLocation)
        }
        currentUserLocation = fixLocation
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (!lat.isFinite() || !lon.isFinite()) return false
        if (lat < -90.0 || lat > 90.0) return false
        if (lon < -180.0 || lon > 180.0) return false
        return true
    }

    fun saveLocation(location: LatLng, zoom: Double, bearing: Double) {
        userInteractionController.saveLocation(location, zoom, bearing)
        logLocationDebug {
            "Saved position for return: lat=${location.latitude}, zoom=$zoom, bearing=$bearing"
        }
    }

    fun saveLocationFromGPS(location: MapLocationUiModel?, zoom: Double, bearing: Double) {
        location?.let {
            saveLocation(LatLng(it.latitude, it.longitude), zoom, bearing)
        }
    }

    fun showReturnButton() {
        userInteractionController.showReturnButton()
    }

    fun returnToSavedLocation(): Boolean = userInteractionController.returnToSavedLocation()

    fun recenterOnCurrentLocation() {
        userInteractionController.recenterOnCurrentLocation()
    }

    fun handleUserInteraction(currentLocation: MapLocationUiModel?, currentZoom: Double, currentBearing: Double) {
        userInteractionController.handleUserInteraction(currentLocation, currentZoom, currentBearing)
    }



    private inline fun logLocationDebug(message: () -> String) {
        if (com.example.xcpro.map.BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }
}


