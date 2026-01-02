package com.example.xcpro.map

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.example.xcpro.common.orientation.MapOrientationMode
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
    private val mapStateStore: MapStateStore,
    private val coroutineScope: CoroutineScope,
    private val qnhPreferencesRepository: QnhPreferencesRepository,
    private val varioServiceManager: VarioServiceManager
) {
    companion object {
        private const val TAG = "LocationManager"
        private const val INITIAL_ZOOM_LEVEL = 10.0
    }

    private var sensorsStarted = false
    private val orientationPreferences = MapOrientationPreferences(context)
    private val gliderPaddingHelper = GliderPaddingHelper(context.resources, orientationPreferences)
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

    private fun ensureSensorsRunning() {
        val status = unifiedSensorManager.getSensorStatus()
        if (sensorsStarted && status.gpsStarted) {
            return
        }
        if (!sensorsStarted && status.gpsStarted) {
            // Sensors might still be producing data from a previous session, but
            // the vario service (flight data collection, MacCready observers, etc.)
            // is not running. Make sure we spin it up so cards receive data.
            val startedNow = varioServiceManager.start()
            val statusAfterStart = unifiedSensorManager.getSensorStatus()
            sensorsStarted = startedNow || statusAfterStart.gpsStarted
            if (sensorsStarted) {
                return
            }
        }

        val started = varioServiceManager.start()
        val statusAfterStart = unifiedSensorManager.getSensorStatus()
        sensorsStarted = started || statusAfterStart.gpsStarted
        if (!sensorsStarted) {
            Log.w(TAG, "Sensor start deferred (likely waiting on location permission)")
        }
    }

    private fun stopSensors() {
        val status = unifiedSensorManager.getSensorStatus()
        if (!sensorsStarted && !status.gpsStarted) return
        varioServiceManager.stop()
        sensorsStarted = false
    }


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
        get() = mapStateStore.currentUserLocation.value?.let { LatLng(it.latitude, it.longitude) }
        set(value) {
            mapStateStore.setCurrentUserLocation(
                value?.let { MapStateStore.MapPoint(it.latitude, it.longitude) }
            )
        }

    private var hasInitiallyCentered: Boolean
        get() = mapStateStore.hasInitiallyCentered.value
        set(value) {
            mapStateStore.setHasInitiallyCentered(value)
        }

    private var isTrackingLocation: Boolean
        get() = mapStateStore.isTrackingLocation.value
        set(value) {
            mapStateStore.setTrackingLocation(value)
        }

    private var showRecenterButton: Boolean
        get() = mapStateStore.showRecenterButton.value
        set(value) {
            mapStateStore.setShowRecenterButton(value)
        }

    private var lastUserPanTime: Long
        get() = mapStateStore.lastUserPanTime.value
        set(value) {
            mapStateStore.updateLastUserPanTime(value)
        }

    private var showReturnButton: Boolean
        get() = mapStateStore.showReturnButton.value
        set(value) {
            mapStateStore.setShowReturnButton(value)
        }

    private var savedLocation: LatLng?
        get() = mapStateStore.savedLocation.value?.let { LatLng(it.latitude, it.longitude) }
        set(value) {
            mapStateStore.setSavedLocation(
                value?.let { MapStateStore.MapPoint(it.latitude, it.longitude) }
            )
        }

    private var savedZoom: Double?
        get() = mapStateStore.savedZoom.value
        set(value) {
            mapStateStore.setSavedZoom(value)
        }

    private var savedBearing: Double?
        get() = mapStateStore.savedBearing.value
        set(value) {
            mapStateStore.setSavedBearing(value)
        }

    fun onLocationPermissionsResult(fineLocationGranted: Boolean, coarseLocationGranted: Boolean) {
        if (fineLocationGranted || coarseLocationGranted) {
            Log.d(TAG, "Location permissions granted, starting background sensors")
            ensureSensorsRunning()
        } else {
            Log.e(TAG, "Location permissions denied")
        }
    }

    fun checkAndRequestLocationPermissions(
        locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        Log.d(TAG, "🚀 Checking location permissions...")

        val fineLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted || coarseLocationGranted) {
            Log.d(TAG, "✅ Location permissions already granted, starting background sensors")
            ensureSensorsRunning()
        } else {
            Log.d(TAG, "📋 Requesting location permissions...")
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun stopLocationTracking(force: Boolean = false) {
        if (!force) {
            Log.d(TAG, "Background service keeps sensors alive (force=false)")
            return
        }
        Log.d(TAG, "Force stopping background sensors")
        stopSensors()
    }

    /**
     * Restart sensors after returning from sleep mode
     * This ensures GPS and other sensors resume properly when screen turns back on
     */
    fun restartSensorsIfNeeded() {
        Log.d(TAG, "🔄 Checking if sensors need restart after sleep mode...")

        val sensorStatus = unifiedSensorManager.getSensorStatus()

        // Restart if any critical sensor is not running (common after doze/background)
        val needsRestart = (
            (!sensorStatus.gpsStarted && sensorStatus.hasLocationPermissions) ||
            (!sensorStatus.baroStarted && sensorStatus.baroAvailable) ||
            (!sensorStatus.accelStarted && sensorStatus.accelAvailable)
        )
        if (needsRestart) {
            Log.d(
                TAG,
                "One or more sensors stopped (gpsStarted=${sensorStatus.gpsStarted}, baroStarted=${sensorStatus.baroStarted}, accelStarted=${sensorStatus.accelStarted}) - restarting all sensors"
            )

            // Stop everything first to clean up any stale listeners
            stopSensors()

            // Short delay to ensure clean shutdown
            Thread.sleep(100)

            // Restart all sensors
            ensureSensorsRunning()

    // Flight data fusion starts automatically with sensor data flow (no explicit start)
            Log.d(TAG, "Sensors restarted successfully after sleep/doze")
            return
        }

        // If GPS was started but is no longer receiving updates, restart all sensors
        if (!sensorStatus.gpsStarted && sensorStatus.hasLocationPermissions) {
            Log.d(TAG, "📱 Sensors appear to be stopped (likely due to sleep mode), restarting...")

            // Stop everything first to clean up any stale listeners
            stopSensors()

            // Short delay to ensure clean shutdown
            Thread.sleep(100)

            // Restart all sensors
            ensureSensorsRunning()

    // Flight data fusion starts automatically with sensor data flow
            // No explicit start() method needed

            Log.d(TAG, "✅ Sensors restarted successfully after sleep mode")
        } else if (sensorStatus.gpsStarted) {
            Log.d(TAG, "✅ Sensors already running, no restart needed")
        } else {
            Log.d(TAG, "⚠️ No location permissions, cannot restart sensors")
        }
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
        orientationMode: MapOrientationMode = MapOrientationMode.NORTH_UP,
        magneticHeading: Double = 0.0
    ) {
        val map = mapState.mapLibreMap ?: run {
            Log.w(TAG, "MapLibreMap null; cannot update location")
            return
        }

        // XCSoar-style jitter gate (SetLocationLazy equivalent)
        val accepted = locationFilter.accept(location.latLng, map)
        if (!accepted) return

        currentUserLocation = location.latLng

        val shouldTrackCamera = isTrackingLocation && !showReturnButton
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
            location = location.latLng,
            trackBearing = location.bearing,
            magneticHeading = magneticHeading,
            orientationMode = orientationMode,
            shouldTrackCamera = shouldTrackCamera,
            padding = padding,
            cameraBearing = resolveCameraBearing(location.bearing, magneticHeading, orientationMode)
        )

        handleInitialCentering(location.latLng)
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
        orientationMode: MapOrientationMode,
        magneticHeading: Double
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
        if (!locationFilter.accept(newLocation, map)) return

        currentUserLocation = newLocation
        val shouldTrackCamera = isTrackingLocation && !showReturnButton
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
            magneticHeading = magneticHeading,
            orientationMode = orientationMode,
            shouldTrackCamera = shouldTrackCamera,
            padding = padding,
            cameraBearing = resolveCameraBearing(liveData.track, magneticHeading, orientationMode)
        )

        handleInitialCentering(newLocation)
    }

    fun saveLocation(location: LatLng, zoom: Double, bearing: Double) {
        savedLocation = location
        savedZoom = zoom
        savedBearing = bearing
        Log.d(TAG, "📍 Saved position for return: lat=${location.latitude}, zoom=$zoom, bearing=$bearing")
    }

    fun saveLocationFromGPS(location: GPSData?, zoom: Double, bearing: Double) {
        location?.let {
            saveLocation(it.latLng, zoom, bearing)
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


