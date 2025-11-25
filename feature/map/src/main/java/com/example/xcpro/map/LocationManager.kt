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
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.GPSData
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.MapOrientationPreferences
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.map.helpers.GliderPaddingHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.collections.ArrayDeque

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
        // XCSoar parity: ignore sub-pixel GPS jitter before recentering
        private const val RECENTER_PIXEL_THRESHOLD = 0.75f
        private const val MIN_MOVEMENT_METERS = 1.0
        private const val NORTH_WIND_AVG_WINDOW = 30
    }

    private var sensorsStarted = false
    private val orientationPreferences = MapOrientationPreferences(context)
    private val gliderPaddingHelper = GliderPaddingHelper(context.resources, orientationPreferences)
    private val northWindPositionAverager = PositionAverager(NORTH_WIND_AVG_WINDOW)

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

    init {
        coroutineScope.launch {
            qnhPreferencesRepository.qnhHpaFlow.collect { storedQnh ->
                storedQnh?.let { sensorFusionRepository.setManualQnh(it) }
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
        Log.d(TAG, "Updating location overlay: ${location.latLng.latitude}, ${location.latLng.longitude}, " +
                  "bearing=${location.bearing}°, magHeading=${magneticHeading}°, mode=$orientationMode")

        // Skip tiny moves to reduce overlay jitter
        if (!hasMovedEnough(currentUserLocation, location.latLng)) {
            return
        }

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
                // Skip recenter if projected move is below sub-pixel noise
                if (!shouldRecentre(location, map)) {
                    Log.v(TAG, "RECENTER_SKIPPED: below pixel threshold (${location.latitude}, ${location.longitude})")
                    return@let
                }
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

                Log.d(TAG, "INITIAL CENTERING: Centered map on first GPS location: ${location.latitude}, ${location.longitude}")
            }
        }
    }
    private fun handleAutomaticCameraTracking(location: LatLng, orientationMode: MapOrientationMode) {
        // Enable camera tracking for ALL modes when user hasn't panned
        // The camera follows the GPS position to keep the aircraft icon in view
        // The icon stays at actual GPS coordinates on the map
        if (isTrackingLocation && !showReturnButton) {
            mapState.mapLibreMap?.let { map ->
                // Skip recenter if projected move is below sub-pixel noise
                if (!shouldRecentre(location, map)) {
                    Log.v(TAG, "RECENTER_SKIPPED: below pixel threshold (${location.latitude}, ${location.longitude})")
                    return@let
                }

                val targetLocation = when (orientationMode) {
                    MapOrientationMode.NORTH_UP,
                    MapOrientationMode.WIND_UP -> northWindPositionAverager.add(location)
                    else -> {
                        northWindPositionAverager.clear()
                        location
                    }
                }

                val currentPosition = map.cameraPosition
                val newCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(targetLocation)
                    .zoom(currentPosition.zoom) // Always preserve current zoom - NO AUTO-ZOOM
                    .bearing(currentPosition.bearing) // Bearing is handled by MapCameraManager
                    .tilt(currentPosition.tilt) // Preserve current tilt
                    .build()

                // Instant camera movement for smooth real-time tracking
                // Using moveCamera instead of animateCamera for butter-smooth following
                map.moveCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))
                gliderPaddingHelper.applyPadding(map)

                Log.d(TAG, "$orientationMode: Camera following location at ${location.latitude}, ${location.longitude}")
            }
        } else {
            Log.d(TAG, "Camera tracking: mode=$orientationMode, tracking=$isTrackingLocation, returnButton=$showReturnButton")
        }
    }

    /**
     * Mirror XCSoar's SetLocationLazy: only recentre if the new GPS fix would move the aircraft
     * more than ~0.75px on screen. Prevents left/right flicker from sub-meter noise.
     */
    private fun shouldRecentre(target: LatLng, map: MapLibreMap): Boolean {
        return try {
            val projection = map.projection
            val currentCenter = map.cameraPosition.target ?: return true

            val currentPoint = projection.toScreenLocation(currentCenter)
            val targetPoint = projection.toScreenLocation(target)

            val dx = targetPoint.x - currentPoint.x
            val dy = targetPoint.y - currentPoint.y
            val distSq = dx * dx + dy * dy

            distSq >= RECENTER_PIXEL_THRESHOLD * RECENTER_PIXEL_THRESHOLD
        } catch (e: Exception) {
            Log.w(TAG, "shouldRecentre: projection failed, defaulting to recenter", e)
            true
        }
    }

    fun updateLocationFromFlightData(
        liveData: RealTimeFlightData,
        orientationMode: MapOrientationMode,
        magneticHeading: Double
    ) {
        Log.d(
            TAG,
            "GPS Data: fixed=$liveData, lat=${liveData.latitude}, lon=${liveData.longitude}, " +
                "accuracy=${liveData.accuracy}, gpsAlt=${liveData.gpsAltitude}m, " +
                "speed=${String.format("%.1f", UnitsConverter.msToKnots(liveData.groundSpeed))}kt, track=${liveData.track}"
        )

        // Update position even without perfect GPS fix for smooth tracking
        if (liveData.latitude != 0.0 && liveData.longitude != 0.0) {
            val newLocation = LatLng(liveData.latitude, liveData.longitude)

            // Skip tiny moves to reduce overlay jitter
            if (!hasMovedEnough(currentUserLocation, newLocation)) {
                return
            }

            currentUserLocation = newLocation
            Log.d(TAG, "User location updated: ${liveData.latitude}, ${liveData.longitude}")

            mapState.mapLibreMap?.let { map ->
                try {
                    Log.d(TAG, "Map available for sailplane overlay update, style=${map.style != null}")

                    // Update glider icon with GPS track and magnetic heading for proper rotation per mode
                    mapState.blueLocationOverlay?.let { overlay ->
                        overlay.updateLocation(
                            newLocation,
                            liveData.track,  // GPS track (direction of movement)
                            magneticHeading,  // Magnetic heading (direction nose is pointing)
                            orientationMode   // Current orientation mode
                        )
                        overlay.setVisible(true)
                    }

                    // Ensure replay sessions also snap the camera to the aircraft once at start
                    handleInitialCentering(newLocation)
                    // DISABLED: Using Canvas overlay instead of map-based circles
                    // mapState.distanceCirclesOverlay?.updateLocation(newLocation)

                    // Handle automatic camera tracking for smooth movement
                    handleAutomaticCameraTracking(newLocation, orientationMode)

                    Log.d(TAG, "Custom sailplane overlay updated successfully (track=${liveData.track})")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating sailplane overlay: ${e.message}", e)
                }
            } ?: Log.w(TAG, "MapLibreMap is null, cannot update location")
        } else {
            Log.d(
                TAG,
                "GPS not fixed or invalid coordinates: fixed=$liveData, lat=${liveData.latitude}, lon=${liveData.longitude}"
            )
        }
    }

    fun saveLocation(location: LatLng, zoom: Double, bearing: Double) {
        savedLocation = location
        savedZoom = zoom
        savedBearing = bearing
        Log.d(TAG, "Saved position for return: lat=${location.latitude}, zoom=$zoom, bearing=$bearing")
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
        Log.d(TAG, "Return button shown due to user interaction")
    }

    fun returnToSavedLocation(): Boolean {
        return savedLocation?.let { location ->
            mapState.mapLibreMap?.let { map ->
                if (!shouldRecentre(location, map)) {
                    Log.v(
                        TAG,
                        "RECENTER_SKIPPED (return): below pixel threshold (${location.latitude}, ${location.longitude})"
                    )
                    showReturnButton = false
                    mapState.showReturnButton = false
                    return false
                }

                val returnCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(location)
                    .zoom(savedZoom ?: map.cameraPosition.zoom)
                    .bearing(savedBearing ?: map.cameraPosition.bearing)
                    .tilt(map.cameraPosition.tilt)
                    .build()

                gliderPaddingHelper.applyPadding(map)
                map.animateCamera(CameraUpdateFactory.newCameraPosition(returnCameraPosition), 1000)

                showReturnButton = false
                mapState.showReturnButton = false
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
                if (!shouldRecentre(location, map)) {
                    Log.v(TAG, "RECENTER_SKIPPED (manual): below pixel threshold (, )")
                    return
                }

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

    /**
     * Simple movement guard to ignore <1 m deltas (reduces jitter).
     */
    private fun hasMovedEnough(prev: LatLng?, next: LatLng): Boolean {
        if (prev == null) return true
        return distanceMeters(prev, next) >= MIN_MOVEMENT_METERS
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val sinLat = kotlin.math.sin(dLat / 2)
        val sinLon = kotlin.math.sin(dLon / 2)
        val term = sinLat * sinLat + kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * sinLon * sinLon
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(term), kotlin.math.sqrt(1 - term))
        return R * c
    }
}
private class PositionAverager(private val capacity: Int) {
    private val buffer = ArrayDeque<LatLng>(capacity)
    private var sumLat = 0.0
    private var sumLon = 0.0

    fun add(value: LatLng): LatLng {
        if (buffer.size == capacity) {
            val removed = buffer.removeFirst()
            sumLat -= removed.latitude
            sumLon -= removed.longitude
        }
        buffer.addLast(value)
        sumLat += value.latitude
        sumLon += value.longitude
        return averaged()
    }

    fun clear() {
        buffer.clear()
        sumLat = 0.0
        sumLon = 0.0
    }

    private fun averaged(): LatLng {
        if (buffer.isEmpty()) return LatLng(0.0, 0.0)
        val size = buffer.size.toDouble()
        return LatLng(sumLat / size, sumLon / size)
    }
}














