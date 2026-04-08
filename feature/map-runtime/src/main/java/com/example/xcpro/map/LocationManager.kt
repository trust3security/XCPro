package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.domain.MapShiftBiasCalculator
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.runtime.BuildConfig as RuntimeBuildConfig
import com.example.xcpro.replay.ReplayDisplayPose
import org.maplibre.android.geometry.LatLng

class LocationManager(
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions,
    private val featureFlags: MapFeatureFlags,
    private val cameraPreferenceReader: MapCameraPreferenceReader,
    private val locationPreferences: MapLocationPreferencesPort,
    private val paddingProvider: () -> IntArray,
    private val sensorsPort: MapLocationSensorsPort,
    private val cameraControllerProvider: MapCameraControllerProvider,
    private val mapViewSizeProvider: MapViewSizeProvider,
    private val cameraUpdateGate: MapCameraUpdateGate,
    private val locationOverlayPort: MapLocationOverlayPort,
    private val displayPoseSurfacePort: MapDisplayPoseSurfacePort,
    private val renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics,
    private val localOwnshipRenderEnabledProvider: () -> Boolean = { true },
    private val replayHeadingProvider: ((Long) -> Double?)? = null,
    private val replayFixProvider: ((Long) -> ReplayDisplayPose?)? = null
) : MapLocationRuntimePort {
    companion object {
        private const val TAG = "LocationManager"
        private const val INITIAL_ZOOM_LEVEL = 10.0
        private const val CAMERA_MIN_UPDATE_INTERVAL_MS = 80L
        private const val CAMERA_BEARING_EPS_DEG = 2.0
        private const val FRAME_LOG_INTERVAL_MS = 100L
    }

    private val positionController = MapPositionController(
        locationOverlayPort = locationOverlayPort,
        maxBearingStepDegProvider = { featureFlags.maxTrackBearingStepDeg },
        headingSmoothingEnabledProvider = { featureFlags.useIconHeadingSmoothing },
        offsetHistorySize = featureFlags.locationOffsetHistorySize,
        iconRotationConfigProvider = {
            if (featureFlags.allowHeadingWhileStationary) {
                IconRotationConfig.fromMinSpeedThreshold(0.0)
            } else {
                IconRotationConfig.fromMinSpeedThreshold(locationPreferences.getMinSpeedThreshold())
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
                minSpeedProvider = { locationPreferences.getMinSpeedThreshold() },
                adaptiveSmoothingEnabled = featureFlags.useAdaptiveDisplaySmoothing
            )
        )
    )
    private val userInteractionController = MapUserInteractionController(
        mapStateReader = mapStateReader,
        stateActions = stateActions,
        paddingProvider = paddingProvider,
        cameraControllerProvider = cameraControllerProvider,
        logTag = TAG
    )
    private val trackingCameraController = MapTrackingCameraController(
        mapSizeProvider = mapViewSizeProvider,
        mapStateReader = mapStateReader,
        stateActions = stateActions,
        preferenceReader = cameraPreferenceReader,
        paddingProvider = paddingProvider,
        positionController = positionController,
        cameraPolicy = cameraPolicy,
        followCameraMotionPolicy = MapFollowCameraMotionPolicy(),
        cameraUpdateGate = cameraUpdateGate,
        biasResetter = mapShiftBiasResetter,
        cameraControllerProvider = {
            cameraControllerProvider.controllerOrNull()
        },
        featureFlags = featureFlags,
        initialZoomLevel = INITIAL_ZOOM_LEVEL,
        minUpdateIntervalMs = CAMERA_MIN_UPDATE_INTERVAL_MS,
        bearingEpsDeg = CAMERA_BEARING_EPS_DEG
    )
    private val frameLogger = DisplayPoseFrameLogger(
        tag = TAG,
        defaultIntervalMs = FRAME_LOG_INTERVAL_MS,
        timeBaseProvider = { poseCoordinator.timeBase },
        featureFlags = featureFlags
    )
    private val displayPoseRenderer = DisplayPoseRenderCoordinator(
        surfacePort = displayPoseSurfacePort,
        mapStateReader = mapStateReader,
        featureFlags = featureFlags,
        poseCoordinator = poseCoordinator,
        replayHeadingProvider = replayHeadingProvider,
        replayFixProvider = replayFixProvider,
        trackingCameraController = trackingCameraController,
        positionController = positionController,
        frameLogger = frameLogger,
        diagnostics = renderSurfaceDiagnostics
    )
    private val displayPoseRepaintGate = DisplayPoseRepaintGate(
        requestRepaint = {
            cameraControllerProvider.controllerOrNull()?.triggerRepaint()
        },
        diagnostics = renderSurfaceDiagnostics
    )
    private val displayPoseFrameActivityGate = DisplayPoseFrameActivityGate()
    private var localOwnshipRenderEnabled: Boolean = localOwnshipRenderEnabledProvider()

    // Map UI state proxies (MapStateStore is the single owner)
    private var currentUserLocation: LatLng?
        get() = mapStateReader.currentUserLocation.value?.let { LatLng(it.latitude, it.longitude) }
        set(value) {
            stateActions.setCurrentUserLocation(
                value?.let { MapPoint(it.latitude, it.longitude) }
            )
        }

    override fun onLocationPermissionsResult(fineLocationGranted: Boolean) {
        sensorsPort.onLocationPermissionsResult(fineLocationGranted)
    }

    override fun requestLocationPermissions(permissionRequester: MapLocationPermissionRequester) {
        sensorsPort.requestLocationPermissions(permissionRequester)
    }

    override fun stopLocationTracking(force: Boolean) {
        sensorsPort.stopLocationTracking(force)
    }

    /**
     * Restart sensors after returning from sleep mode
     * This ensures GPS and other sensors resume properly when screen turns back on
     */
    override fun restartSensorsIfNeeded() {
        sensorsPort.restartSensorsIfNeeded()
    }

    override fun isGpsEnabled(): Boolean = sensorsPort.isGpsEnabled()

    override fun setActiveProfileId(profileId: String) {
        locationPreferences.setActiveProfileId(profileId)
    }
    override fun updateLocationFromGPS(
        location: MapLocationUiModel,
        orientation: OrientationData
    ) {
        if (!localOwnshipRenderEnabled) {
            return
        }
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

    override fun setLocalOwnshipRenderEnabled(enabled: Boolean) {
        if (localOwnshipRenderEnabled == enabled) {
            return
        }
        localOwnshipRenderEnabled = enabled
        if (!enabled) {
            displayPoseRepaintGate.clear()
            displayPoseFrameActivityGate.clear()
            displayPoseRenderer.clear()
            currentUserLocation = null
            stateActions.setShowRecenterButton(false)
            stateActions.setShowReturnButton(false)
            locationOverlayPort.setBlueLocationVisible(false)
            return
        }
        requestRenderFrameIfEnabled(forceImmediate = true)
    }

    override fun updateOrientation(orientation: OrientationData) {
        displayPoseRenderer.updateOrientation(orientation)
        displayPoseFrameActivityGate.updateOrientation(
            orientation = orientation,
            profile = mapStateReader.displaySmoothingProfile.value,
            hasRenderableInput = hasRenderableDisplayInput()
        )
        requestRenderFrameIfEnabled()
    }

    override fun setReplaySpeedMultiplier(multiplier: Double) {
        if (multiplier.isFinite() && multiplier > 0.0) {
            poseCoordinator.replaySpeedMultiplier = multiplier
        }
    }

    override fun shouldDispatchLiveDisplayFrame(): Boolean {
        if (!localOwnshipRenderEnabled) {
            renderSurfaceDiagnostics.recordDisplayFramePreDispatchSuppressed()
            return false
        }
        val shouldDispatch = displayPoseFrameActivityGate.shouldDispatch(
            hasRenderableInput = hasRenderableDisplayInput(),
            timeBase = poseCoordinator.timeBase,
            mode = mapStateReader.displayPoseMode.value,
            smoothingProfile = mapStateReader.displaySmoothingProfile.value
        )
        if (!shouldDispatch) {
            renderSurfaceDiagnostics.recordDisplayFramePreDispatchSuppressed()
        }
        return shouldDispatch
    }

    override fun onDisplayFrame() {
        if (featureFlags.useRenderFrameSync) {
            requestRenderFrameIfEnabled(forceImmediate = true)
            return
        }
        renderSurfaceDiagnostics.recordRenderFrameDelivered()
        renderDisplayFrame()
        renderSurfaceDiagnostics.recordFrameRendered()
    }

    fun onRenderFrame() {
        if (!featureFlags.useRenderFrameSync) {
            return
        }
        renderSurfaceDiagnostics.recordRenderFrameDelivered()
        renderDisplayFrame()
        renderSurfaceDiagnostics.recordFrameRendered()
        displayPoseRepaintGate.onFrameRendered()
    }

    override fun getDisplayPoseLocation(): LatLng? = displayPoseRenderer.getDisplayPoseLocation()

    override fun getDisplayPoseTimestampMs(): Long? = displayPoseRenderer.getDisplayPoseTimestampMs()

    override fun getDisplayPoseSnapshot(): DisplayPoseSnapshot? = displayPoseRenderer.getDisplayPoseSnapshot()

    override fun setDisplayPoseFrameListener(listener: ((DisplayPoseSnapshot) -> Unit)?) {
        displayPoseRenderer.setDisplayPoseFrameListener(listener)
    }

    private fun renderDisplayFrame() {
        if (!localOwnshipRenderEnabled) {
            return
        }
        displayPoseRenderer.renderDisplayFrame { poseLocation, zoom ->
            saveLocation(poseLocation, zoom, 0.0)
            logLocationDebug {
                "INITIAL CENTERING: centered map on first GPS location: " +
                    "${poseLocation.latitude}, ${poseLocation.longitude}"
            }
        }
    }

    override fun updateLocationFromReplayFrame(
        replayFrame: ReplayLocationFrame,
        orientation: OrientationData
    ) {
        if (!localOwnshipRenderEnabled) {
            return
        }
        logLocationDebug {
            val groundSpeedKnots = String.format(
                "%.1f",
                UnitsConverter.msToKnots(replayFrame.groundSpeedMs)
            )
            "Replay frame: lat=${replayFrame.latitude}, lon=${replayFrame.longitude}, " +
                "accuracy=${replayFrame.accuracyMeters}, gpsAlt=${replayFrame.gpsAltitudeMeters}m, " +
                "gs=${groundSpeedKnots}kt, track=${replayFrame.trackDeg}"
        }

        if (replayFrame.latitude == 0.0 || replayFrame.longitude == 0.0) {
            logLocationDebug {
                "Replay feed: invalid coordinates (lat=${replayFrame.latitude}, lon=${replayFrame.longitude})"
            }
            return
        }
        if (!isValidCoordinate(replayFrame.latitude, replayFrame.longitude)) {
            logLocationDebug {
                "Replay feed: invalid coordinates (lat=${replayFrame.latitude}, lon=${replayFrame.longitude})"
            }
            return
        }

        displayPoseRenderer.updateOrientation(orientation)
        val envelope = feedAdapter.fromReplayFrame(replayFrame, orientation)
        pushRawFix(envelope)
    }

    private fun pushRawFix(envelope: LocationFeedAdapter.RawFixEnvelope) {
        val updateResult = poseCoordinator.updateFromFix(envelope)
        val fixLocation = LatLng(envelope.fix.latitude, envelope.fix.longitude)
        if (updateResult.timeBaseChanged) {
            trackingCameraController.onTimeBaseChanged(fixLocation)
        }
        currentUserLocation = fixLocation
        displayPoseFrameActivityGate.markFixReceived(mapStateReader.displaySmoothingProfile.value)
        requestRenderFrameIfEnabled()
    }

    private fun hasRenderableDisplayInput(): Boolean =
        localOwnshipRenderEnabled && poseCoordinator.timeBase != null

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

    override fun showReturnButton() {
        userInteractionController.showReturnButton()
    }

    override fun returnToSavedLocation(): Boolean = userInteractionController.returnToSavedLocation()

    override fun recenterOnCurrentLocation() {
        userInteractionController.recenterOnCurrentLocation()
    }

    override fun handleUserInteraction(
        currentLocation: MapLocationUiModel?,
        currentZoom: Double,
        currentBearing: Double
    ) {
        userInteractionController.handleUserInteraction(currentLocation, currentZoom, currentBearing)
    }
    private inline fun logLocationDebug(message: () -> String) {
        if (RuntimeBuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }

    private fun requestRenderFrameIfEnabled(forceImmediate: Boolean = false) {
        if (!featureFlags.useRenderFrameSync) {
            return
        }
        renderSurfaceDiagnostics.recordRepaintRequest(forceImmediate = forceImmediate)
        displayPoseRepaintGate.request(
            minIntervalNs = displayPoseMinFrameIntervalNs(poseCoordinator.timeBase),
            forceImmediate = forceImmediate
        )
    }
}
