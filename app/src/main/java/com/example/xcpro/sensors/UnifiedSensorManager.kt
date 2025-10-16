package com.example.xcpro.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng

/**
 * Unified Sensor Manager - Single Source of Truth for all sensors
 *
 * RESPONSIBILITIES:
 * - Manage LocationManager (GPS + Network providers)
 * - Manage SensorManager (Pressure + Magnetic sensors)
 * - Emit raw sensor data via StateFlows
 * - NO calculations (only sensor management)
 *
 * SSOT PRINCIPLE:
 * - ONE StateFlow per sensor type (GPS, Barometer, Compass)
 * - ALL consumers read from these flows
 * - ZERO duplicate listeners
 *
 * INDUSTRY STANDARDS:
 * - GPS: 1Hz (1000ms) - standard for gliding apps, battery efficient
 * - Barometer: ~20Hz (SENSOR_DELAY_GAME) - for smooth variometer
 * - Magnetometer: ~60Hz (SENSOR_DELAY_UI) - for compass display
 */
class UnifiedSensorManager(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "UnifiedSensorManager"

        // GPS update rate (industry standard for gliding apps)
        private const val GPS_UPDATE_INTERVAL_MS = 1000L  // 1Hz (NOT 10Hz battery killer!)
        private const val GPS_MIN_DISTANCE_M = 0f         // Get all updates

        // Barometer delay (for smooth variometer)
        private const val BARO_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME  // ~20Hz

        // Magnetometer delay (for compass display)
        private const val COMPASS_SENSOR_DELAY = SensorManager.SENSOR_DELAY_UI  // ~60Hz

        // Accelerometer delay (for variometer fusion)
        private const val ACCEL_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME  // ~200Hz
    }

    // Android system services
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Sensors
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val magneticSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val linearAccelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // StateFlows - Single Source of Truth for each sensor
    private val _gpsFlow = MutableStateFlow<GPSData?>(null)
    val gpsFlow: StateFlow<GPSData?> = _gpsFlow.asStateFlow()

    private val _baroFlow = MutableStateFlow<BaroData?>(null)
    val baroFlow: StateFlow<BaroData?> = _baroFlow.asStateFlow()

    private val _compassFlow = MutableStateFlow<CompassData?>(null)
    val compassFlow: StateFlow<CompassData?> = _compassFlow.asStateFlow()

    private val _accelFlow = MutableStateFlow<AccelData?>(null)
    val accelFlow: StateFlow<AccelData?> = _accelFlow.asStateFlow()

    // Service state
    private var isGpsStarted = false
    private var isBaroStarted = false
    private var isCompassStarted = false
    private var isAccelStarted = false
    private var isRotationStarted = false

    private val orientationProcessor = OrientationProcessor()

    // GPS location listener
    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d(TAG, "GPS update: lat=${location.latitude}, lon=${location.longitude}, " +
                    "alt=${location.altitude}m, speed=${location.speed}m/s, " +
                    "bearing=${location.bearing}Â°, accuracy=${location.accuracy}m")

            val gpsData = GPSData(
                latLng = LatLng(location.latitude, location.longitude),
                altitude = if (location.hasAltitude()) location.altitude else 0.0,
                speed = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
                bearing = if (location.hasBearing()) location.bearing.toDouble() else 0.0,
                accuracy = location.accuracy,
                timestamp = location.time
            )

            _gpsFlow.value = gpsData
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
            Log.d(TAG, "GPS status changed: provider=$provider, status=$status")
        }

        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "GPS provider enabled: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "GPS provider disabled: $provider")
        }
    }

    // Barometer and compass sensor listener
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                val pressureHPa = event.values[0].toDouble()
                val baroData = BaroData(
                    pressureHPa = pressureHPa,
                    timestamp = System.currentTimeMillis()
                )
                _baroFlow.value = baroData

                if (baroData.timestamp % 5000 < 50) {
                    Log.d(TAG, "Barometer update: pressure= hPa")
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val heading = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble()))
                val normalizedHeading = (heading + 360) % 360

                val compassData = CompassData(
                    heading = normalizedHeading,
                    accuracy = event.accuracy,
                    timestamp = System.currentTimeMillis()
                )
                _compassFlow.value = compassData

                if (compassData.timestamp % 5000 < 50) {
                    Log.d(TAG, "Compass update: heading=°")
                }
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                orientationProcessor.updateRotationVector(event.values)
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val sample = orientationProcessor.projectVerticalAcceleration(event.values)
                val accelData = AccelData(
                    verticalAcceleration = sample.verticalAcceleration,
                    timestamp = System.currentTimeMillis(),
                    isReliable = sample.isReliable
                )
                _accelFlow.value = accelData

                if (accelData.timestamp % 5000 < 50) {
                    Log.d(
                        TAG,
                        "Accelerometer update: verticalAccel= m/s^2, reliable="
                    )
                }
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor?.let {
            Log.d(TAG, "Sensor accuracy changed: ${it.name}, accuracy=$accuracy")
        }
    }

    /**
     * Start all sensors
     * MUST be called after location permissions are granted
     */
    fun startAllSensors() {
        Log.d(TAG, "Starting all sensors...")
        startGPS()
        startBarometer()
        startCompass()
        startRotationVector()
        startAccelerometer()
        Log.d(TAG, "All sensors started successfully")
    }

    /**
     * Stop all sensors
     * MUST be called when app is backgrounded or destroyed
     */
    fun stopAllSensors() {
        Log.d(TAG, "Stopping all sensors...")
        stopGPS()
        stopBarometer()
        stopCompass()
        stopRotationVector()
        stopAccelerometer()
        Log.d(TAG, "All sensors stopped")
    }

    /**
     * Start GPS tracking (1Hz - industry standard)
     */
    private fun startGPS() {
        if (isGpsStarted) {
            Log.d(TAG, "GPS already started")
            return
        }

        if (!hasLocationPermissions()) {
            Log.e(TAG, "No location permissions - cannot start GPS")
            return
        }

        try {
            // Start GPS provider (high accuracy)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_UPDATE_INTERVAL_MS,  // 1Hz (NOT 10Hz!)
                    GPS_MIN_DISTANCE_M,
                    gpsListener
                )
                Log.d(TAG, "âœ… GPS started (1Hz)")
            } else {
                Log.w(TAG, "âš ï¸ GPS provider not enabled")
            }

            // Also use Network provider for faster initial fix
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    GPS_UPDATE_INTERVAL_MS,
                    GPS_MIN_DISTANCE_M,
                    gpsListener
                )
                Log.d(TAG, "âœ… Network provider started (for fast initial fix)")
            }

            // Get last known location for immediate display
            getLastKnownLocation()?.let { location ->
                Log.d(TAG, "ðŸ“ Using last known location: ${location.latitude}, ${location.longitude}")
                gpsListener.onLocationChanged(location)
            }

            isGpsStarted = true

        } catch (e: SecurityException) {
            Log.e(TAG, "âŒ Security exception starting GPS: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error starting GPS: ${e.message}", e)
        }
    }

    /**
     * Start barometer sensor (~20Hz for smooth variometer)
     */
    private fun startBarometer() {
        if (isBaroStarted) {
            Log.d(TAG, "Barometer already started")
            return
        }

        pressureSensor?.let { sensor ->
            val success = sensorManager.registerListener(
                this,
                sensor,
                BARO_SENSOR_DELAY  // ~20Hz (SENSOR_DELAY_GAME)
            )

            if (success) {
                isBaroStarted = true
                Log.d(TAG, "âœ… Barometer started (~20Hz): ${sensor.name}")
            } else {
                Log.e(TAG, "âŒ Failed to register barometer listener")
            }
        } ?: run {
            Log.w(TAG, "âš ï¸ No barometer sensor available on this device")
        }
    }

    /**
     * Start compass sensor (~60Hz for display)
     */
    private fun startCompass() {
        if (isCompassStarted) {
            Log.d(TAG, "Compass already started")
            return
        }

        magneticSensor?.let { sensor ->
            val success = sensorManager.registerListener(
                this,
                sensor,
                COMPASS_SENSOR_DELAY  // ~60Hz (SENSOR_DELAY_UI)
            )

            if (success) {
                isCompassStarted = true
                Log.d(TAG, "âœ… Compass started (~60Hz): ${sensor.name}")
            } else {
                Log.e(TAG, "âŒ Failed to register compass listener")
            }
        } ?: run {
            Log.w(TAG, "âš ï¸ No compass sensor available on this device")
        }
    }

    /**
     * Stop GPS tracking
     */
    private fun stopGPS() {
        if (!isGpsStarted) return

        try {
            locationManager.removeUpdates(gpsListener)
            isGpsStarted = false
            Log.d(TAG, "ðŸ›‘ GPS stopped")
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error stopping GPS: ${e.message}")
        }
    }

    /**
     * Stop barometer sensor
     */
    private fun stopBarometer() {
        if (!isBaroStarted) return

        pressureSensor?.let {
            sensorManager.unregisterListener(this, it)
            isBaroStarted = false
            Log.d(TAG, "ðŸ›‘ Barometer stopped")
        }
    }

    /**
     * Stop compass sensor
     */
    private fun stopCompass() {
        if (!isCompassStarted) return

        magneticSensor?.let {
            sensorManager.unregisterListener(this, it)
            isCompassStarted = false
            Log.d(TAG, "ðŸ›‘ Compass stopped")
        }
    }

    /**
     * Start rotation vector sensor (provides device orientation)
     */
    private fun startRotationVector() {
        if (isRotationStarted) {
            Log.d(TAG, "Rotation vector already started")
            return
        }

        rotationVectorSensor?.let { sensor ->
            val success = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )

            if (success) {
                isRotationStarted = true
                orientationProcessor.reset()
                Log.d(TAG, "Rotation vector started (~50Hz): ")
            } else {
                Log.e(TAG, "Failed to register rotation vector listener")
            }
        } ?: run {
            Log.w(TAG, "No rotation vector sensor available on this device")
        }
    }

    /**
     * Start accelerometer sensor (~200Hz for variometer fusion)
     */
    private fun startAccelerometer() {
        if (isAccelStarted) {
            Log.d(TAG, "Accelerometer already started")
            return
        }

        linearAccelerometerSensor?.let { sensor ->
            val success = sensorManager.registerListener(
                this,
                sensor,
                ACCEL_SENSOR_DELAY  // ~200Hz (SENSOR_DELAY_GAME)
            )

            if (success) {
                isAccelStarted = true
                Log.d(TAG, "âœ… Accelerometer started (~200Hz): ${sensor.name}")
            } else {
                Log.e(TAG, "âŒ Failed to register accelerometer listener")
            }
        } ?: run {
            Log.w(TAG, "âš ï¸ No linear accelerometer sensor available on this device")
        }
    }

    /**
     * Stop rotation vector sensor
     */
    private fun stopRotationVector() {
        if (!isRotationStarted) return

        rotationVectorSensor?.let {
            sensorManager.unregisterListener(this, it)
        }
        isRotationStarted = false
        orientationProcessor.reset()
        Log.d(TAG, "Rotation vector stopped")
    }

    /**
     * Stop accelerometer sensor
     */
    private fun stopAccelerometer() {
        if (!isAccelStarted) return

        linearAccelerometerSensor?.let {
            sensorManager.unregisterListener(this, it)
            isAccelStarted = false
            Log.d(TAG, "ðŸ›‘ Accelerometer stopped")
        }
    }

    /**
     * Get last known location from any provider
     */
    private fun getLastKnownLocation(): Location? {
        if (!hasLocationPermissions()) return null

        return try {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            // Return the most recent and accurate location
            when {
                gpsLocation != null && networkLocation != null -> {
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                }
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "âŒ Security exception getting last known location: ${e.message}")
            null
        }
    }

    /**
     * Check if location permissions are granted
     */
    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if GPS is enabled on device
     */
    fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * Get sensor availability status
     */
    fun getSensorStatus(): SensorStatus {
        return SensorStatus(
            gpsAvailable = isGpsEnabled(),
            gpsStarted = isGpsStarted,
            baroAvailable = pressureSensor != null,
            baroStarted = isBaroStarted,
            compassAvailable = magneticSensor != null,
            compassStarted = isCompassStarted,
            accelAvailable = linearAccelerometerSensor != null,
            accelStarted = isAccelStarted,
            rotationAvailable = rotationVectorSensor != null,
            rotationStarted = isRotationStarted,
            hasLocationPermissions = hasLocationPermissions()
        )
    }
}

