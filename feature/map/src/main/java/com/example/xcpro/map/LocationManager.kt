package com.example.xcpro.map

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.sensors.GPSData
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.MapOrientationPreferences
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.map.helpers.GliderPaddingHelper
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.domain.MapShiftBiasCalculator
import com.example.xcpro.map.domain.MapShiftBiasConfig
import com.example.xcpro.map.domain.MapShiftBiasInput
import com.example.xcpro.map.domain.MapShiftBiasMode
import com.example.xcpro.map.domain.ScreenOffset
import com.example.xcpro.map.MapLocationFilter
import com.example.xcpro.map.MapLibreProjector
import com.example.xcpro.map.MapPositionController
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.replay.ReplayDisplayPose
import org.maplibre.android.maps.MapView
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt

class LocationManager(
    private val context: Context,
    private val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions,
    private val coroutineScope: CoroutineScope,
    private val varioServiceManager: VarioServiceManager,
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
    private val iconRotationConfig = if (MapFeatureFlags.allowHeadingWhileStationary) {
        IconRotationConfig.fromMinSpeedThreshold(0.0)
    } else {
        IconRotationConfig.fromPreferences(orientationPreferences)
    }
    private val sensorsController = LocationSensorsController(
        context = context,
        scope = coroutineScope,
        varioServiceManager = varioServiceManager
    )
    private val cameraUpdateGate = MapLocationFilter(
        MapLocationFilter.Config(
            thresholdPx = MapFeatureFlags.locationJitterThresholdPx,
            historySize = MapFeatureFlags.locationOffsetHistorySize
        ),
        MapLibreProjector()
    )
    private val positionController = MapPositionController(
        mapState = mapState,
        maxBearingStepDegProvider = { MapFeatureFlags.maxTrackBearingStepDeg },
        headingSmoothingEnabledProvider = { MapFeatureFlags.useIconHeadingSmoothing },
        offsetHistorySize = MapFeatureFlags.locationOffsetHistorySize,
        iconRotationConfig = iconRotationConfig
    )
    private val mapShiftBiasCalculator = MapShiftBiasCalculator()
    private val displayClock = DisplayClock()
    private val feedAdapter = LocationFeedAdapter()
    private val posePipeline = DisplayPosePipeline(
        minSpeedProvider = { orientationPreferences.getMinSpeedThreshold() },
        adaptiveSmoothingEnabled = MapFeatureFlags.useAdaptiveDisplaySmoothing
    )
    private var latestOrientation: OrientationData = OrientationData()
    private var lastCameraUpdateMs: Long = 0L
    private var lastTimeBase: DisplayClock.TimeBase? = null
    @Volatile private var lastDisplayPoseLocation: LatLng? = null
    @Volatile private var lastDisplayPoseTimestampMs: Long = 0L
    @Volatile private var lastDisplayPoseFrameId: Long = 0L
    @Volatile private var displayPoseFrameListener: ((DisplayPoseSnapshot) -> Unit)? = null
    private var displayFrameCounter: Long = 0L
    private var renderFrameMapView: MapView? = null
    private var lastFrameLogMs: Long = 0L
    private val renderFrameListener = MapView.OnWillStartRenderingFrameListener {
        dispatchRenderFrame()
    }

    // ✅ PHASE 2: Unified sensor management
    val unifiedSensorManager: UnifiedSensorManager = varioServiceManager.unifiedSensorManager

    // ✅ PHASE 2: Flight data calculator (combines all sensor data + calculations)

    // Map UI state proxies (MapStateStore is the single owner)
    private var currentUserLocation: LatLng?
        get() = mapStateReader.currentUserLocation.value?.let { LatLng(it.latitude, it.longitude) }
        set(value) {
            stateActions.setCurrentUserLocation(
                value?.let { MapStateStore.MapPoint(it.latitude, it.longitude) }
            )
        }

    private var hasInitiallyCentered: Boolean
        get() = mapStateReader.hasInitiallyCentered.value
        set(value) {
            stateActions.setHasInitiallyCentered(value)
        }

    private var isTrackingLocation: Boolean
        get() = mapStateReader.isTrackingLocation.value
        set(value) {
            stateActions.setTrackingLocation(value)
        }

    private var showRecenterButton: Boolean
        get() = mapStateReader.showRecenterButton.value
        set(value) {
            stateActions.setShowRecenterButton(value)
        }

    private var lastUserPanTime: Long
        get() = mapStateReader.lastUserPanTime.value
        set(value) {
            stateActions.updateLastUserPanTime(value)
        }

    private var showReturnButton: Boolean
        get() = mapStateReader.showReturnButton.value
        set(value) {
            stateActions.setShowReturnButton(value)
        }

    private val savedLocation: LatLng?
        get() = mapStateReader.savedLocation.value?.let { LatLng(it.latitude, it.longitude) }

    private val savedZoom: Double?
        get() = mapStateReader.savedZoom.value

    private val savedBearing: Double?
        get() = mapStateReader.savedBearing.value

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


    fun updateLocationFromGPS(
        location: GPSData,
        orientation: OrientationData
    ) {
        latestOrientation = orientation
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
        latestOrientation = orientation
    }

    fun setReplaySpeedMultiplier(multiplier: Double) {
        if (multiplier.isFinite() && multiplier > 0.0) {
            displayClock.replaySpeedMultiplier = multiplier
        }
    }

    fun onDisplayFrame() {
        if (MapFeatureFlags.useRenderFrameSync) {
            mapState.mapLibreMap?.triggerRepaint()
            return
        }
        renderDisplayFrame()
    }

    fun onRenderFrame() {
        if (!MapFeatureFlags.useRenderFrameSync) {
            return
        }
        renderDisplayFrame()
    }

    data class DisplayPoseSnapshot(
        val location: LatLng,
        val timestampMs: Long,
        val frameId: Long
    )

    fun getDisplayPoseLocation(): LatLng? = lastDisplayPoseLocation

    fun getDisplayPoseTimestampMs(): Long? =
        lastDisplayPoseTimestampMs.takeIf { it > 0L }

    fun getDisplayPoseSnapshot(): DisplayPoseSnapshot? {
        val location = lastDisplayPoseLocation ?: return null
        val timestamp = lastDisplayPoseTimestampMs
        if (timestamp <= 0L) return null
        val frameId = lastDisplayPoseFrameId
        if (frameId <= 0L) return null
        return DisplayPoseSnapshot(location, timestamp, frameId)
    }

    fun setDisplayPoseFrameListener(listener: ((DisplayPoseSnapshot) -> Unit)?) {
        displayPoseFrameListener = listener
    }

    private fun renderDisplayFrame() {
        val nowMs = displayClock.nowMs()
        val mode = mapStateReader.displayPoseMode.value
        val smoothingProfile = mapStateReader.displaySmoothingProfile.value
        val pose = posePipeline.selectPose(nowMs, mode, smoothingProfile) ?: return
        val map = mapState.mapLibreMap ?: return
        val orientation = latestOrientation
        val forceTrackHeading = MapFeatureFlags.forceReplayTrackHeading &&
            lastTimeBase == DisplayClock.TimeBase.REPLAY
        val runtimeFix = if (forceTrackHeading && MapFeatureFlags.useRuntimeReplayHeading) {
            replayFixProvider?.invoke(nowMs)
        } else {
            null
        }
        val runtimeBearing = runtimeFix?.bearingDeg
            ?: if (forceTrackHeading && MapFeatureFlags.useRuntimeReplayHeading) {
                replayHeadingProvider?.invoke(nowMs)
            } else {
                null
            }
        val poseLocation = runtimeFix?.let { fix ->
            LatLng(fix.latitude, fix.longitude)
        } ?: pose.location
        val poseTimestampMs = runtimeFix?.timestampMillis ?: pose.updatedAtMs
        val headingDeg = when {
            runtimeBearing != null -> runtimeBearing
            forceTrackHeading -> pose.trackDeg
            else -> orientation.headingDeg
        }
        val headingValid = runtimeBearing != null || forceTrackHeading || orientation.headingValid
        val trackDeg = runtimeBearing ?: pose.trackDeg
        val speedMs = runtimeFix?.speedMs ?: pose.speedMs
        val cameraTargetBearing = if (runtimeBearing != null &&
            orientation.mode != MapOrientationMode.NORTH_UP
        ) {
            runtimeBearing
        } else {
            orientation.bearing
        }

        displayFrameCounter += 1
        lastDisplayPoseFrameId = displayFrameCounter
        lastDisplayPoseLocation = poseLocation
        lastDisplayPoseTimestampMs = poseTimestampMs
        if (MapFeatureFlags.useRenderFrameSync) {
            val nowElapsed = android.os.SystemClock.elapsedRealtime()
            val interval = MapFeatureFlags.sim2FrameLogIntervalMs.takeIf { it >= 0L }
                ?: FRAME_LOG_INTERVAL_MS
            if (interval <= 0L || nowElapsed - lastFrameLogMs >= interval) {
                lastFrameLogMs = nowElapsed
                Log.d(
                    TAG,
                    "framePose frame=$lastDisplayPoseFrameId " +
                        "t=$poseTimestampMs " +
                        "lat=${"%.6f".format(poseLocation.latitude)} " +
                        "lon=${"%.6f".format(poseLocation.longitude)} " +
                        "track=${"%.1f".format(trackDeg)} " +
                        "heading=${"%.1f".format(headingDeg)} " +
                        "camera=${"%.1f".format(cameraTargetBearing)} " +
                        "timeBase=${lastTimeBase ?: "NONE"}"
                )
            }
        }
        if (MapFeatureFlags.useRenderFrameSync) {
            displayPoseFrameListener?.invoke(
                DisplayPoseSnapshot(
                    location = poseLocation,
                    timestampMs = poseTimestampMs,
                    frameId = lastDisplayPoseFrameId
                )
            )
        }

        if (!hasInitiallyCentered) {
            handleInitialCentering(poseLocation)
            if (!hasInitiallyCentered) return
        }

        val shouldTrackCamera = isTrackingLocation && !showReturnButton
        if (shouldTrackCamera) {
            val padding = computeSmoothedPadding(
                trackDeg = trackDeg,
                mapBearing = cameraTargetBearing,
                speedMs = speedMs,
                orientationMode = orientation.mode
            )
            if (shouldUpdateCamera(map, poseLocation, cameraTargetBearing, nowMs)) {
                val animationMs = if (MapFeatureFlags.useRuntimeReplayHeading &&
                    lastTimeBase == DisplayClock.TimeBase.REPLAY
                ) {
                    0
                } else {
                    CAMERA_ANIMATION_MS
                }
                positionController.updateCamera(
                    map = map,
                    location = poseLocation,
                    cameraBearing = cameraTargetBearing,
                    padding = padding,
                    animationMs = animationMs
                )
                cameraUpdateGate.resetTo(poseLocation, map)
                lastCameraUpdateMs = nowMs
            }
        } else {
            mapShiftBiasCalculator.reset()
        }

        val cameraBearing = map.cameraPosition.bearing.toDouble()
        val overlayBearing = if (cameraBearing.isFinite()) cameraBearing else orientation.bearing
        positionController.updateOverlay(
            location = poseLocation,
            trackBearing = trackDeg,
            headingDeg = headingDeg,
            headingValid = headingValid,
            bearingAccuracyDeg = pose.bearingAccuracyDeg,
            speedAccuracyMs = pose.speedAccuracyMs,
            mapBearing = overlayBearing,
            orientationMode = orientation.mode,
            speedMs = speedMs,
            nowMs = nowMs,
            frameId = lastDisplayPoseFrameId
        )
    }

    fun bindRenderFrameListener(mapView: MapView) {
        if (renderFrameMapView === mapView) return
        renderFrameMapView?.removeOnWillStartRenderingFrameListener(renderFrameListener)
        mapView.addOnWillStartRenderingFrameListener(renderFrameListener)
        renderFrameMapView = mapView
    }

    fun unbindRenderFrameListener() {
        renderFrameMapView?.removeOnWillStartRenderingFrameListener(renderFrameListener)
        renderFrameMapView = null
    }

    private fun dispatchRenderFrame() {
        if (!MapFeatureFlags.useRenderFrameSync) return
        val mapView = renderFrameMapView ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onRenderFrame()
        } else {
            mapView.post { onRenderFrame() }
        }
    }

    private fun handleInitialCentering(location: LatLng) {
        if (!hasInitiallyCentered && mapState.mapLibreMap != null) {
            mapState.mapLibreMap?.let { map ->
                // For initial centering ONLY, set a reasonable zoom
                // After this, always preserve user's zoom preference
                val zoomToUse = if (map.cameraPosition.zoom < 5.0) {
                    INITIAL_ZOOM_LEVEL  // Only use initial zoom if map is too zoomed out
                } else {
                    map.cameraPosition.zoom  // Otherwise preserve current zoom
                }

                val initialCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(location)
                    .zoom(zoomToUse)
                    .bearing(0.0) // North up initially
                    .tilt(0.0)
                    .build()

                // Honor user-selected glider offset
                val padding = gliderPaddingHelper.paddingArray()

                // Use moveCamera for initial centering too - no animation needed
                map.moveCamera(CameraUpdateFactory.newCameraPosition(initialCameraPosition))
                map.setPadding(padding[0], padding[1], padding[2], padding[3])

                hasInitiallyCentered = true

                // Save initial position for return button
                saveLocation(location, zoomToUse, 0.0)

                logLocationDebug {
                    "🎯 INITIAL CENTERING: Centered map on first GPS location: ${location.latitude}, ${location.longitude}"
                }
            }
        }
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

        latestOrientation = orientation
        val envelope = feedAdapter.fromFlightData(liveData, orientation)
        pushRawFix(envelope)
    }

    private fun pushRawFix(envelope: LocationFeedAdapter.RawFixEnvelope) {
        if (lastTimeBase != envelope.timeBase) {
            // Intentional source switch (live <-> replay) should snap the overlay to the new track.
            posePipeline.resetSmoother()
            mapShiftBiasCalculator.reset()
            lastTimeBase = envelope.timeBase
            mapState.mapLibreMap?.let { map ->
                cameraUpdateGate.resetTo(LatLng(envelope.fix.latitude, envelope.fix.longitude), map)
            }
        }
        displayClock.updateFromFix(envelope.fix.timestampMs, envelope.timeBase)
        posePipeline.pushRawFix(envelope.fix)
        currentUserLocation = LatLng(envelope.fix.latitude, envelope.fix.longitude)
    }

    private fun computeSmoothedPadding(
        trackDeg: Double,
        mapBearing: Double,
        speedMs: Double,
        orientationMode: MapOrientationMode
    ): IntArray {
        val rawPadding = computeBasePadding()
        val biasOffset = computeBiasOffset(trackDeg, mapBearing, speedMs, orientationMode)
        return applyBiasToPadding(rawPadding, biasOffset)
    }

    private fun computeBasePadding(): IntArray {
        val rawPadding = gliderPaddingHelper.paddingArray()
        positionController.rememberOffset(
            MapPositionController.Offset(
                x = rawPadding[1].toFloat(),
                y = rawPadding[3].toFloat()
            )
        )
        val averaged = positionController.averagedOffset()
        return intArrayOf(0, averaged.x.roundToInt(), 0, averaged.y.roundToInt())
    }

    private fun computeBiasOffset(
        trackDeg: Double,
        mapBearing: Double,
        speedMs: Double,
        orientationMode: MapOrientationMode
    ): ScreenOffset {
        val baseMode = orientationPreferences.getMapShiftBiasMode()
        if (baseMode == MapShiftBiasMode.NONE) {
            mapShiftBiasCalculator.reset()
            return ScreenOffset.ZERO
        }
        if (orientationMode != MapOrientationMode.NORTH_UP) {
            mapShiftBiasCalculator.reset()
            return ScreenOffset.ZERO
        }
        if (mapStateReader.currentFlightMode.value == FlightModeSelection.THERMAL) {
            mapShiftBiasCalculator.reset()
            return ScreenOffset.ZERO
        }

        val mapView = mapState.mapView
        val input = MapShiftBiasInput(
            trackBearingDeg = trackDeg.takeIf { it.isFinite() },
            targetBearingDeg = null,
            mapBearingDeg = mapBearing,
            speedMs = speedMs.takeIf { it.isFinite() },
            screenWidthPx = mapView?.width ?: 0,
            screenHeightPx = mapView?.height ?: 0,
            gliderScreenPercent = orientationPreferences.getGliderScreenPercent()
        )
        val config = MapShiftBiasConfig(
            mode = baseMode,
            biasStrength = orientationPreferences.getMapShiftBiasStrength(),
            minSpeedMs = MapFeatureFlags.mapShiftBiasMinSpeedMs,
            historySize = MapFeatureFlags.mapShiftBiasHistorySize,
            maxOffsetFraction = MapFeatureFlags.mapShiftBiasMaxOffsetFraction,
            holdOnInvalid = MapFeatureFlags.mapShiftBiasHoldOnInvalid
        )
        return mapShiftBiasCalculator.update(input, config).offset
    }

    private fun applyBiasToPadding(basePadding: IntArray, biasOffset: ScreenOffset): IntArray {
        val left = if (biasOffset.dxPx > 0.0) {
            basePadding[0] + biasOffset.dxPx.roundToInt()
        } else {
            basePadding[0]
        }
        val right = if (biasOffset.dxPx < 0.0) {
            basePadding[2] + (-biasOffset.dxPx).roundToInt()
        } else {
            basePadding[2]
        }
        val top = if (biasOffset.dyPx > 0.0) {
            basePadding[1] + biasOffset.dyPx.roundToInt()
        } else {
            basePadding[1]
        }
        val bottom = if (biasOffset.dyPx < 0.0) {
            basePadding[3] + (-biasOffset.dyPx).roundToInt()
        } else {
            basePadding[3]
        }
        return intArrayOf(left, top, right, bottom)
    }

    private fun shouldUpdateCamera(
        map: org.maplibre.android.maps.MapLibreMap,
        location: LatLng,
        targetBearing: Double,
        nowMs: Long
    ): Boolean {
        if (MapFeatureFlags.useRenderFrameSync && lastTimeBase == DisplayClock.TimeBase.REPLAY) {
            return true
        }
        val bearingDelta = abs(shortestDeltaDegrees(map.cameraPosition.bearing, targetBearing))
        val bearingMoved = bearingDelta >= CAMERA_BEARING_EPS_DEG
        val timeDue = nowMs - lastCameraUpdateMs >= CAMERA_MIN_UPDATE_INTERVAL_MS
        if (!timeDue && !bearingMoved) return false

        val positionMoved = if (timeDue || bearingMoved) {
            cameraUpdateGate.accept(location, map)
        } else {
            false
        }
        return bearingMoved || (timeDue && positionMoved)
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (!lat.isFinite() || !lon.isFinite()) return false
        if (lat < -90.0 || lat > 90.0) return false
        if (lon < -180.0 || lon > 180.0) return false
        return true
    }

    private fun shortestDeltaDegrees(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    fun saveLocation(location: LatLng, zoom: Double, bearing: Double) {
        stateActions.saveLocation(
            location = MapStateStore.MapPoint(location.latitude, location.longitude),
            zoom = zoom,
            bearing = bearing
        )
        logLocationDebug {
            "Saved position for return: lat=${location.latitude}, zoom=$zoom, bearing=$bearing"
        }
    }

    fun saveLocationFromGPS(location: GPSData?, zoom: Double, bearing: Double) {
        location?.let {
            saveLocation(it.toLatLng(), zoom, bearing)
        }
    }

    fun showReturnButton() {
        showReturnButton = true
        showReturnButton = true
        lastUserPanTime = System.currentTimeMillis()
        Log.d(TAG, "✅ Return button shown due to user interaction")
    }

    fun returnToSavedLocation(): Boolean {
        return savedLocation?.let { location ->
            mapState.mapLibreMap?.let { map ->
                val returnCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(location)
                    .zoom(savedZoom ?: map.cameraPosition.zoom)
                    .bearing(savedBearing ?: map.cameraPosition.bearing)
                    .tilt(map.cameraPosition.tilt)
                    .build()

                gliderPaddingHelper.applyPadding(map)
                map.animateCamera(CameraUpdateFactory.newCameraPosition(returnCameraPosition), 1000)

                showReturnButton = false
                showReturnButton = false
                isTrackingLocation = true
                showRecenterButton = false
                Log.d(TAG, "Returned to saved position")
                true
            } ?: false
        } ?: false
    }

    fun recenterOnCurrentLocation() {
        currentUserLocation?.let { location ->
            mapState.mapLibreMap?.let { map ->
                val currentPosition = map.cameraPosition
                val newCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(location)
                    .zoom(currentPosition.zoom)
                    .bearing(currentPosition.bearing)
                    .tilt(currentPosition.tilt)
                    .build()

                map.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition), 800)
                gliderPaddingHelper.applyPadding(map)

                showRecenterButton = false
                Log.d(TAG, "Recentered to current location")
            }
        }
    }

    fun handleUserInteraction(currentLocation: GPSData?, currentZoom: Double, currentBearing: Double) {
        // Save position before first pan
        if (!showReturnButton) {
            saveLocationFromGPS(currentLocation, currentZoom, currentBearing)
        }
        showReturnButton()
    }



    private inline fun logLocationDebug(message: () -> String) {
        if (com.example.xcpro.map.BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }
}


