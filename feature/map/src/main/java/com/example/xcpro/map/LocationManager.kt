package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.QnhPreferencesRepository
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.GPSData
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.MapOrientationPreferences
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.map.helpers.GliderPaddingHelper
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.MapLocationFilter
import com.example.xcpro.map.MapLibreProjector
import com.example.xcpro.map.MapPositionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import kotlin.math.roundToInt

class LocationManager(
    private val context: Context,
    private val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions,
    private val coroutineScope: CoroutineScope,
    private val qnhPreferencesRepository: QnhPreferencesRepository,
    private val varioServiceManager: VarioServiceManager
) {
    companion object {
        private const val TAG = "LocationManager"
        private const val INITIAL_ZOOM_LEVEL = 10.0
    }

    private val orientationPreferences = MapOrientationPreferences(context)
    private val gliderPaddingHelper = GliderPaddingHelper(context.resources, orientationPreferences)
    private val sensorsController = LocationSensorsController(
        context = context,
        varioServiceManager = varioServiceManager
    )
    private val locationFilter = MapLocationFilter(
        MapLocationFilter.Config(
            thresholdPx = MapFeatureFlags.locationJitterThresholdPx,
            historySize = MapFeatureFlags.locationOffsetHistorySize
        ),
        MapLibreProjector()
    )
    private val positionController = MapPositionController(
        mapState = mapState,
        maxBearingStepDeg = 5.0,
        offsetHistorySize = MapFeatureFlags.locationOffsetHistorySize
    )

    // ✅ PHASE 2: Unified sensor management
    val unifiedSensorManager: UnifiedSensorManager = varioServiceManager.unifiedSensorManager

    // ✅ PHASE 2: Flight data calculator (combines all sensor data + calculations)
    val sensorFusionRepository: SensorFusionRepository = varioServiceManager.sensorFusionRepository
    // Auto QNH is triggered as an explicit one-shot action; there is no persisted toggle.

    init {
        coroutineScope.launch {
            val storedQnh = qnhPreferencesRepository.qnhHpaFlow.first()
            if (storedQnh != null) {
                sensorFusionRepository.setManualQnh(storedQnh)
            } else {
                sensorFusionRepository.requestAutoQnhCalibration()
            }
        }
    }

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

    fun onLocationPermissionsResult(fineLocationGranted: Boolean, coarseLocationGranted: Boolean) {
        sensorsController.onLocationPermissionsResult(
            fineLocationGranted = fineLocationGranted,
            coarseLocationGranted = coarseLocationGranted
        )
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

    fun setManualQnh(qnh: Double) {
        sensorFusionRepository.setManualQnh(qnh)
        coroutineScope.launch {
            qnhPreferencesRepository.setManualQnh(qnh)
        }
    }

    fun autoCalibrateQnh() {
        sensorFusionRepository.requestAutoQnhCalibration()
        coroutineScope.launch {
            qnhPreferencesRepository.clearManualQnh()
        }
    }

    fun resetQnhToStandard() {
        sensorFusionRepository.resetQnhToStandard()
        coroutineScope.launch {
            qnhPreferencesRepository.clearManualQnh()
        }
    }


    fun updateLocationFromGPS(
        location: GPSData,
        orientation: OrientationData
    ) {
        val map = mapState.mapLibreMap ?: run {
            Log.w(TAG, "MapLibreMap null; cannot update location")
            return
        }

        // XCSoar-style jitter gate (SetLocationLazy equivalent)
        val accepted = locationFilter.accept(location.toLatLng(), map)
        val shouldTrackCamera = isTrackingLocation && !showReturnButton
        if (!accepted) {
            if (shouldTrackCamera) {
                updateCameraBearingIfNeeded(map, orientation.bearing)
            }
            return
        }

        currentUserLocation = location.toLatLng()

        val padding = if (shouldTrackCamera) {
            val rawPadding = gliderPaddingHelper.paddingArray()
            positionController.rememberOffset(
                MapPositionController.Offset(
                    x = rawPadding[1].toFloat(), // top padding px
                    y = rawPadding[3].toFloat()  // bottom padding px
                )
            )
            val averaged = positionController.averagedOffset()
            intArrayOf(0, averaged.x.roundToInt(), 0, averaged.y.roundToInt())
        } else {
            null
        }

        positionController.applyAcceptedSample(
            map = map,
            location = location.toLatLng(),
            trackBearing = location.bearing,
            headingDeg = orientation.headingDeg,
            mapBearing = orientation.bearing,
            orientationMode = orientation.mode,
            shouldTrackCamera = shouldTrackCamera,
            padding = padding,
            cameraBearing = orientation.bearing
        )

        handleInitialCentering(location.toLatLng())
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
                saveLocation(location, INITIAL_ZOOM_LEVEL, 0.0)

                Log.d(TAG, "🎯 INITIAL CENTERING: Centered map on first GPS location: ${location.latitude}, ${location.longitude}")
            }
        }
    }

    fun updateLocationFromFlightData(
        liveData: RealTimeFlightData,
        orientation: OrientationData
    ) {
        val groundSpeedKnots = String.format(
            "%.1f",
            UnitsConverter.msToKnots(liveData.groundSpeed)
        )
        Log.d(
            TAG,
            "Replay/live GPS: lat=${liveData.latitude}, lon=${liveData.longitude}, " +
                "accuracy=${liveData.accuracy}, gpsAlt=${liveData.gpsAltitude}m, " +
                "gs=${groundSpeedKnots}kt, track=${liveData.track}"
        )

        if (liveData.latitude == 0.0 || liveData.longitude == 0.0) {
            Log.d(
                TAG,
                "Replay feed: invalid coordinates (lat=${liveData.latitude}, lon=${liveData.longitude})"
            )
            return
        }

        val map = mapState.mapLibreMap ?: run {
            Log.w(TAG, "MapLibreMap is null, cannot update location")
            return
        }

        val newLocation = LatLng(liveData.latitude, liveData.longitude)
        val accepted = locationFilter.accept(newLocation, map)
        val shouldTrackCamera = isTrackingLocation && !showReturnButton
        if (!accepted) {
            if (shouldTrackCamera) {
                updateCameraBearingIfNeeded(map, orientation.bearing)
            }
            return
        }

        currentUserLocation = newLocation
        val padding = if (shouldTrackCamera) {
            val rawPadding = gliderPaddingHelper.paddingArray()
            positionController.rememberOffset(
                MapPositionController.Offset(
                    x = rawPadding[1].toFloat(),
                    y = rawPadding[3].toFloat()
                )
            )
            val averaged = positionController.averagedOffset()
            intArrayOf(0, averaged.x.roundToInt(), 0, averaged.y.roundToInt())
        } else {
            null
        }

        positionController.applyAcceptedSample(
            map = map,
            location = newLocation,
            trackBearing = liveData.track,
            headingDeg = orientation.headingDeg,
            mapBearing = orientation.bearing,
            orientationMode = orientation.mode,
            shouldTrackCamera = shouldTrackCamera,
            padding = padding,
            cameraBearing = orientation.bearing
        )

        handleInitialCentering(newLocation)
    }

    private fun updateCameraBearingIfNeeded(map: org.maplibre.android.maps.MapLibreMap, bearing: Double) {
        val currentPosition = map.cameraPosition
        val delta = shortestDeltaDegrees(currentPosition.bearing, bearing)
        if (kotlin.math.abs(delta) < 2.0) {
            return
        }

        val newCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
            .target(currentPosition.target)
            .zoom(currentPosition.zoom)
            .bearing(bearing)
            .tilt(currentPosition.tilt)
            .build()

        map.moveCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))
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
        Log.d(TAG, "Saved position for return: lat=${location.latitude}, zoom=$zoom, bearing=$bearing")
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

}


