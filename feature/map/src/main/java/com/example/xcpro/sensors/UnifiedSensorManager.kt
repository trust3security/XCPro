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
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
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
class UnifiedSensorManager(private val context: Context) : SensorEventListener, SensorDataSource {

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
    override val gpsFlow: StateFlow<GPSData?> = _gpsFlow.asStateFlow()

    private val _baroFlow = MutableStateFlow<BaroData?>(null)
    override val baroFlow: StateFlow<BaroData?> = _baroFlow.asStateFlow()

    private val _compassFlow = MutableStateFlow<CompassData?>(null)
    override val compassFlow: StateFlow<CompassData?> = _compassFlow.asStateFlow()

    private val _accelFlow = MutableStateFlow<AccelData?>(null)
    override val accelFlow: StateFlow<AccelData?> = _accelFlow.asStateFlow()

    private val _attitudeFlow = MutableStateFlow<AttitudeData?>(null)
    override val attitudeFlow: StateFlow<AttitudeData?> = _attitudeFlow.asStateFlow()

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
                altitude = AltitudeM(if (location.hasAltitude()) location.altitude else 0.0),
                speed = SpeedMs(if (location.hasSpeed()) location.speed.toDouble() else 0.0),
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
                    pressureHPa = PressureHpa(pressureHPa),
                    timestamp = System.currentTimeMillis()
                )
                _baroFlow.value = baroData

                if (baroData.timestamp % 5000 < 50) {
                    Log.d(TAG, "Barometer update: pressure=${"%.1f".format(pressureHPa)} hPa")
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
                    Log.d(TAG, "Compass update: heading=${"%.1f".format(normalizedHeading)}°")
                }
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                orientationProcessor.updateRotationVector(event.values)
                orientationProcessor.attitude()?.let { attitude ->
                    _attitudeFlow.value = AttitudeData(
                        headingDeg = attitude.headingDeg,
                        pitchDeg = attitude.pitchDeg,
                        rollDeg = attitude.rollDeg,
                        timestamp = System.currentTimeMillis(),
                        isReliable = attitude.isReliable
                    )
                }
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
                        "Accelerometer update: verticalAccel=${"%.3f".format(accelData.verticalAcceleration)} m/s^2, reliable=${accelData.isReliable}"
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
    fun startAllSensors(): Boolean {
        Log.d(TAG, "Starting all sensors...")
        val gpsStarted = startGPS()
        val baroStarted = startBarometer()
        val compassStarted = startCompass()
        val rotationStarted = startRotationVector()
        val accelStarted = startAccelerometer()
        Log.d(
            TAG,
            "Sensor start status -> gps=$gpsStarted, baro=$baroStarted, compass=$compassStarted, rotation=$rotationStarted, accel=$accelStarted"
        )
        return gpsStarted
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
        private fun startGPS(): Boolean {
        if (isGpsStarted) {
            Log.d(TAG, "GPS already started")
            return true
        }

        if (!hasLocationPermissions()) {
            Log.e(TAG, "No location permissions - cannot start GPS")
            return false
        }

        var gpsProviderStarted = false
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_UPDATE_INTERVAL_MS,
                    GPS_MIN_DISTANCE_M,
                    gpsListener
                )
                gpsProviderStarted = true
                Log.d(TAG, "GPS provider started (1Hz)")
            } else {
                Log.w(TAG, "GPS provider not enabled")
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    GPS_UPDATE_INTERVAL_MS,
                    GPS_MIN_DISTANCE_M,
                    gpsListener
                )
                Log.d(TAG, "Network provider started for fast initial fix")
            }

            if (gpsProviderStarted) {
                getLastKnownLocation()?.let { location ->
                    Log.d(TAG, "Using last known location: ${location.latitude}, ${location.longitude}")
                    gpsListener.onLocationChanged(location)
                }
                isGpsStarted = true
            } else {
                Log.w(TAG, "Unable to register GPS listener - will retry later")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting GPS: ${e.message}")
            gpsProviderStarted = false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GPS: ${e.message}", e)
            gpsProviderStarted = false
        }

        return gpsProviderStarted
    }

    /**
     * Start barometer sensor (~20Hz for smooth variometer)
     */
    private fun startBarometer(): Boolean {
        if (isBaroStarted) {
            Log.d(TAG, "Barometer already started")
            return true
        }

        val sensor = pressureSensor ?: run {
            Log.w(TAG, "No barometer sensor available on this device")
            return false
        }

        val success = sensorManager.registerListener(this, sensor, BARO_SENSOR_DELAY)
        if (success) {
            isBaroStarted = true
            Log.d(TAG, "Barometer started (~20Hz): ${sensor.name}")
        } else {
            Log.e(TAG, "Failed to register barometer listener")
        }
        return success
    }

    /**
     * Start compass sensor (~60Hz for display)
     */
    /**
     * Start compass sensor (~60Hz for display)
     */
        private fun startCompass(): Boolean {
        if (isCompassStarted) {
            Log.d(TAG, "Compass already started")
            return true
        }

        val sensor = magneticSensor ?: run {
            Log.w(TAG, "No compass sensor available on this device")
            return false
        }

        val success = sensorManager.registerListener(this, sensor, COMPASS_SENSOR_DELAY)
        if (success) {
            isCompassStarted = true
            Log.d(TAG, "Compass started (~60Hz): ${sensor.name}")
        } else {
            Log.e(TAG, "Failed to register compass listener")
        }
        return success
    }

    /**
     * Start rotation vector sensor
     */
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
        private fun startRotationVector(): Boolean {
        if (isRotationStarted) {
            Log.d(TAG, "Rotation vector already started")
            return true
        }

        val sensor = rotationVectorSensor ?: run {
            Log.w(TAG, "No rotation vector sensor available on this device")
            return false
        }

        val success = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        if (success) {
            isRotationStarted = true
            Log.d(TAG, "Rotation vector started: ${sensor.name}")
        } else {
            Log.e(TAG, "Failed to register rotation vector listener")
        }
        return success
    }

    /**
     * Start accelerometer sensor (~200Hz for variometer fusion)
     */
    /**
     * Start accelerometer sensor (~200Hz for variometer fusion)
     */
        private fun startAccelerometer(): Boolean {
        if (isAccelStarted) {
            Log.d(TAG, "Accelerometer already started")
            return true
        }

        val sensor = linearAccelerometerSensor ?: run {
            Log.w(TAG, "No linear accelerometer sensor available on this device")
            return false
        }

        val success = sensorManager.registerListener(this, sensor, ACCEL_SENSOR_DELAY)
        if (success) {
            isAccelStarted = true
            Log.d(TAG, "Accelerometer started (~200Hz): ${sensor.name}")
        } else {
            Log.e(TAG, "Failed to register accelerometer listener")
        }
        return success
    }

    /**
     * Stop rotation vector sensor
     */
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

