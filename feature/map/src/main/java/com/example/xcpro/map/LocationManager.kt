package com.example.xcpro.map

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleOwner
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.QnhPreferencesRepository
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.sensors.FlightDataCalculator
import com.example.xcpro.sensors.GPSData
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.vario.VarioServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class LocationManager(
    private val context: Context,
    private val mapState: MapScreenState,
    private val coroutineScope: CoroutineScope,
    private val qnhPreferencesRepository: QnhPreferencesRepository,
    private val varioServiceManager: VarioServiceManager
) {
    companion object {
        private const val TAG = "LocationManager"
        private const val INITIAL_ZOOM_LEVEL = 10.0
        private const val LOCATION_TOP_PADDING_RATIO = 0.35f
    }

    private var sensorsStarted = false

    private fun ensureSensorsRunning() {
        val status = unifiedSensorManager.getSensorStatus()
        if (sensorsStarted && status.gpsStarted) {
            return
        }
        if (!sensorsStarted && status.gpsStarted) {
            sensorsStarted = true
            return
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
    val flightDataCalculator: FlightDataCalculator = varioServiceManager.flightDataCalculator

    init {
        coroutineScope.launch {
            qnhPreferencesRepository.qnhHpaFlow.collect { storedQnh ->
                storedQnh?.let { flightDataCalculator.setManualQnh(it) }
            }
        }
    }

    // Map UI state proxies (MapScreenState is the single owner)
    private var currentUserLocation: LatLng?
        get() = mapState.currentUserLocation
        set(value) {
            mapState.currentUserLocation = value
        }

    private var hasInitiallyCentered: Boolean
        get() = mapState.hasInitiallyCentered
        set(value) {
            mapState.hasInitiallyCentered = value
        }

    private var isTrackingLocation: Boolean
        get() = mapState.isTrackingLocation
        set(value) {
            mapState.isTrackingLocation = value
        }

    private var showRecenterButton: Boolean
        get() = mapState.showRecenterButton
        set(value) {
            mapState.showRecenterButton = value
        }

    private var lastUserPanTime: Long
        get() = mapState.lastUserPanTime
        set(value) {
            mapState.lastUserPanTime = value
        }

    private var showReturnButton: Boolean
        get() = mapState.showReturnButton
        set(value) {
            mapState.showReturnButton = value
        }

    private var savedLocation: LatLng?
        get() = mapState.savedLocation
        set(value) {
            mapState.savedLocation = value
        }

    private var savedZoom: Double?
        get() = mapState.savedZoom
        set(value) {
            mapState.savedZoom = value
        }

    private var savedBearing: Double?
        get() = mapState.savedBearing
        set(value) {
            mapState.savedBearing = value
        }

    @Composable
    fun LocationPermissionHandler(): ActivityResultLauncher<Array<String>> {
        return rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                Log.d(TAG, "✅ Location permissions granted, starting background sensors")
                ensureSensorsRunning()
            } else {
                Log.e(TAG, "❌ Location permissions denied")
            }
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

            // FlightDataCalculator starts automatically with sensor data flow (no explicit start)
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

            // FlightDataCalculator starts automatically with sensor data flow
            // No explicit start() method needed

            Log.d(TAG, "✅ Sensors restarted successfully after sleep mode")
        } else if (sensorStatus.gpsStarted) {
            Log.d(TAG, "✅ Sensors already running, no restart needed")
        } else {
            Log.d(TAG, "⚠️ No location permissions, cannot restart sensors")
        }
    }

    fun setManualQnh(qnh: Double) {
        flightDataCalculator.setManualQnh(qnh)
        coroutineScope.launch {
            qnhPreferencesRepository.setManualQnh(qnh)
        }
    }

    fun resetQnhToStandard() {
        flightDataCalculator.resetQnhToStandard()
        coroutineScope.launch {
            qnhPreferencesRepository.clearManualQnh()
        }
    }

    fun updateLocationFromGPS(
        location: GPSData,
        orientationMode: MapOrientationMode = MapOrientationMode.NORTH_UP,
        magneticHeading: Double = 0.0
    ) {
        Log.d(TAG, "📍 Updating location overlay: ${location.latLng.latitude}, ${location.latLng.longitude}, " +
                  "bearing=${location.bearing}°, magHeading=${magneticHeading}°, mode=$orientationMode")

        // Update the current user location
        currentUserLocation = location.latLng

        // Update blue location overlay with real GPS data
        // Pass GPS track (bearing) and magnetic heading for proper icon rotation
        mapState.blueLocationOverlay?.updateLocation(location.latLng, location.bearing, magneticHeading, orientationMode)
        mapState.blueLocationOverlay?.setVisible(true)

        // DISABLED: Using Canvas overlay instead of map-based circles
        // mapState.distanceCirclesOverlay?.updateLocation(location.latLng)

        // Handle initial centering
        handleInitialCentering(location.latLng)

        // Handle automatic camera tracking for TRACK_UP mode only
        handleAutomaticCameraTracking(location.latLng, orientationMode)
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

                // Position user at 65% from top for better visibility
                val screenHeight = context.resources.displayMetrics.heightPixels
                val topPadding = (screenHeight * LOCATION_TOP_PADDING_RATIO).toInt()
                val padding = intArrayOf(0, topPadding, 0, 0)

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

    private fun handleAutomaticCameraTracking(location: LatLng, orientationMode: MapOrientationMode) {
        // ✅ Enable camera tracking for ALL modes when user hasn't panned
        // The camera follows the GPS position to keep the aircraft icon in view
        // The icon stays at actual GPS coordinates on the map
        if (isTrackingLocation && !showReturnButton) {
            mapState.mapLibreMap?.let { map ->
                val currentPosition = map.cameraPosition
                val newCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(location)
                    .zoom(currentPosition.zoom) // Always preserve current zoom - NO AUTO-ZOOM
                    .bearing(currentPosition.bearing) // Bearing is handled by MapCameraManager
                    .tilt(currentPosition.tilt) // Preserve current tilt
                    .build()

                val screenHeight = context.resources.displayMetrics.heightPixels
                val topPadding = (screenHeight * LOCATION_TOP_PADDING_RATIO).toInt()
                val padding = intArrayOf(0, topPadding, 0, 0)

                // Instant camera movement for smooth real-time tracking
                // Using moveCamera instead of animateCamera for butter-smooth following
                map.moveCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))
                map.setPadding(padding[0], padding[1], padding[2], padding[3])

                Log.d(TAG, "📍 $orientationMode: Camera following location at ${location.latitude}, ${location.longitude}")
            }
        } else {
            Log.d(TAG, "📍 Camera tracking: mode=$orientationMode, tracking=$isTrackingLocation, returnButton=$showReturnButton")
        }
    }

    fun updateLocationFromFlightData(
        liveData: RealTimeFlightData,
        orientationMode: MapOrientationMode,
        magneticHeading: Double
    ) {
        Log.d(TAG, "📡 GPS Data: fixed=${liveData}, " +
                  "lat=${liveData.latitude}, lon=${liveData.longitude}, " +
                  "accuracy=${liveData.accuracy}, gpsAlt=${liveData.gpsAltitude}m, " +
                  "speed=${String.format("%.1f", UnitsConverter.msToKnots(liveData.groundSpeed))}kt, track=${liveData.track}°")

        // Update position even without perfect GPS fix for smooth tracking
        // Just need valid coordinates (not 0,0)
        if (liveData.latitude != 0.0 && liveData.longitude != 0.0) {
            val newLocation = LatLng(liveData.latitude, liveData.longitude)
            currentUserLocation = newLocation
            Log.d(TAG, "🌍 User location updated: ${liveData.latitude}, ${liveData.longitude}")

            mapState.mapLibreMap?.let { map ->
                try {
                    Log.d(TAG, "🗺️ Map available for sailplane overlay update, style=${map.style != null}")

                    // Update glider icon with GPS track and magnetic heading for proper rotation per mode
                    mapState.blueLocationOverlay?.updateLocation(
                        newLocation,
                        liveData.track,  // GPS track (direction of movement)
                        magneticHeading,  // Magnetic heading (direction nose is pointing)
                        orientationMode   // Current orientation mode
                    )

                    // DISABLED: Using Canvas overlay instead of map-based circles
                    // mapState.distanceCirclesOverlay?.updateLocation(newLocation)

                    // Handle automatic camera tracking for smooth movement
                    handleAutomaticCameraTracking(newLocation, orientationMode)

                    Log.d(TAG, "✅ Custom sailplane overlay updated successfully (track=${liveData.track}°)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error updating sailplane overlay: ${e.message}", e)
                }
            } ?: Log.w(TAG, "⚠️ MapLibreMap is null, cannot update location")
        } else {
            Log.d(TAG, "⚠️ GPS not fixed or invalid coordinates: " +
                      "fixed=${liveData}, " +
                      "lat=${liveData.latitude}, lon=${liveData.longitude}")
        }
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
        mapState.showReturnButton = true
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

                val screenHeight = context.resources.displayMetrics.heightPixels
                val topPadding = (screenHeight * LOCATION_TOP_PADDING_RATIO).toInt()
                map.setPadding(0, topPadding, 0, 0)
                map.animateCamera(CameraUpdateFactory.newCameraPosition(returnCameraPosition), 1000)

                // Hide return button and re-enable location tracking
                showReturnButton = false
                mapState.showReturnButton = false
                isTrackingLocation = true
                showRecenterButton = false
                Log.d(TAG, "🔄 Returned to saved position: lat=${location.latitude}, zoom=$savedZoom")
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

                // Position user at 65% from top
                val screenHeight = context.resources.displayMetrics.heightPixels
                val topPadding = (screenHeight * LOCATION_TOP_PADDING_RATIO).toInt()
                val padding = intArrayOf(0, topPadding, 0, 0)

                map.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition), 800)
                map.setPadding(padding[0], padding[1], padding[2], padding[3])

                showRecenterButton = false
                Log.d(TAG, "✅ Recentered to current location")
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









