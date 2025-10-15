package com.example.xcpro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.dfcards.RealTimeFlightData
// TODO: Refactor to use UnifiedSensorManager instead of deleted FlightDataManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlin.math.*

class OrientationDataSource(
    private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // TODO: Replace with UnifiedSensorManager reference
    // private val flightDataManager = FlightDataManager(context)

    private val _orientationFlow = MutableStateFlow(OrientationSensorData())
    val orientationFlow: StateFlow<OrientationSensorData> = _orientationFlow.asStateFlow()

    // Sensor data arrays for heading calculation
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // Low-pass filter for sensor smoothing
    private var filteredMagneticHeading = 0.0
    private var lastHeadingUpdateTime = 0L

    // GPS data from flight data manager
    private var currentFlightData = RealTimeFlightData()

    private var isStarted = false

    companion object {
        private const val TAG = "OrientationDataSource"
        private const val ALPHA = 0.8f // Low-pass filter constant
        private const val HEADING_UPDATE_INTERVAL_MS = 50L // 20Hz
        private const val MAGNETIC_DECLINATION = 0.0 // Can be set based on location
        private const val HEADING_VALIDATION_THRESHOLD = 360.0
    }

    init {
        Log.d(TAG, "📡 OrientationDataSource initializing...")
        Log.d(TAG, "🔧 Sensor availability: ${getSensorInfo()}")

        // TODO: Refactor to observe UnifiedSensorManager GPS flow
        // CoroutineScope(Dispatchers.Main).launch {
        //     flightDataManager.flightDataFlow.collect { flightData ->
        //         Log.d(TAG, "📍 GPS data update: lat=${flightData.latitude}, lon=${flightData.longitude}, " +
        //                   "speed=${flightData.groundSpeed}kt, track=${flightData.track}°, " +
        //                   "fixed=${flightData}")
        //         currentFlightData = flightData
        //         updateOrientationData()
        //     }
        // }
        Log.d(TAG, "✅ OrientationDataSource initialized")
    }

    fun start() {
        if (isStarted) {
            Log.d(TAG, "⚠️ OrientationDataSource already started")
            return
        }
        isStarted = true
        Log.d(TAG, "▶️ Starting OrientationDataSource...")

        // TODO: Start GPS tracking via UnifiedSensorManager
        // Log.d(TAG, "🚀 Starting flight data collection for GPS track...")
        // flightDataManager.forceStartDataCollection()

        // Register sensor listeners
        magnetometer?.let { sensor ->
            Log.d(TAG, "🧲 Registering magnetometer sensor")
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        } ?: Log.w(TAG, "❌ No magnetometer sensor available")

        accelerometer?.let { sensor ->
            Log.d(TAG, "📱 Registering accelerometer sensor")
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        } ?: Log.w(TAG, "❌ No accelerometer sensor available")

        gyroscope?.let { sensor ->
            Log.d(TAG, "🌀 Registering gyroscope sensor")
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        } ?: Log.d(TAG, "ℹ️ No gyroscope sensor (optional)")

        Log.d(TAG, "✅ OrientationDataSource started successfully")
    }

    fun stop() {
        if (!isStarted) {
            Log.d(TAG, "⚠️ OrientationDataSource already stopped")
            return
        }
        isStarted = false
        Log.d(TAG, "⏹️ Stopping OrientationDataSource...")

        Log.d(TAG, "🔌 Unregistering sensor listeners")
        sensorManager.unregisterListener(this)

        // TODO: Stop GPS tracking via UnifiedSensorManager
        // Log.d(TAG, "🛑 Stopping flight data collection")
        // flightDataManager.stopDataCollection()

        Log.d(TAG, "✅ OrientationDataSource stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Apply low-pass filter to accelerometer data
                gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0]
                gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1]
                gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2]

                // Log accelerometer data occasionally to avoid spam
                if (System.currentTimeMillis() % 1000 < 50) { // Once per second approximately
                    Log.d(TAG, "📱 Accelerometer: x=${event.values[0]}, y=${event.values[1]}, z=${event.values[2]}")
                }
                calculateHeading()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Apply low-pass filter to magnetometer data
                geomagnetic[0] = ALPHA * geomagnetic[0] + (1 - ALPHA) * event.values[0]
                geomagnetic[1] = ALPHA * geomagnetic[1] + (1 - ALPHA) * event.values[1]
                geomagnetic[2] = ALPHA * geomagnetic[2] + (1 - ALPHA) * event.values[2]

                // Log magnetometer data occasionally to avoid spam
                if (System.currentTimeMillis() % 1000 < 50) { // Once per second approximately
                    Log.d(TAG, "🧲 Magnetometer: x=${event.values[0]}, y=${event.values[1]}, z=${event.values[2]}")
                }
                calculateHeading()
            }

            Sensor.TYPE_GYROSCOPE -> {
                // Log gyroscope data occasionally
                if (System.currentTimeMillis() % 2000 < 50) { // Once per 2 seconds approximately
                    Log.d(TAG, "🌀 Gyroscope: x=${event.values[0]}, y=${event.values[1]}, z=${event.values[2]}")
                }
                // Gyroscope data can be used for future improvements
                // Currently not implemented for basic orientation
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val sensorName = when (sensor?.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            else -> "Unknown sensor"
        }

        val accuracyText = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
            SensorManager.SENSOR_STATUS_NO_CONTACT -> "NO_CONTACT"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
            else -> "UNKNOWN"
        }

        Log.d(TAG, "🎯 Sensor accuracy changed: $sensorName = $accuracyText")
    }

    private fun calculateHeading() {
        val currentTime = System.currentTimeMillis()

        // Throttle heading calculations
        if (currentTime - lastHeadingUpdateTime < HEADING_UPDATE_INTERVAL_MS) {
            return
        }
        lastHeadingUpdateTime = currentTime

        // Calculate rotation matrix from accelerometer and magnetometer
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            gravity,
            geomagnetic
        )

        if (success) {
            // Get orientation from rotation matrix
            SensorManager.getOrientation(rotationMatrix, orientation)

            // Convert azimuth from radians to degrees
            var azimuthRad = orientation[0]
            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble())

            // Normalize to 0-360 degrees
            azimuthDeg = (azimuthDeg + 360) % 360

            // Apply magnetic declination correction if needed
            azimuthDeg = (azimuthDeg + MAGNETIC_DECLINATION) % 360

            // Apply smoothing filter
            val prevHeading = filteredMagneticHeading
            filteredMagneticHeading = if (filteredMagneticHeading == 0.0) {
                azimuthDeg
            } else {
                smoothBearingTransition(filteredMagneticHeading, azimuthDeg)
            }

            // Log heading calculations periodically (every 2 seconds to avoid spam)
            if (currentTime % 2000 < HEADING_UPDATE_INTERVAL_MS) {
                Log.d(TAG, "🧭 Heading calculated: raw=${azimuthDeg.toInt()}°, " +
                          "filtered=${filteredMagneticHeading.toInt()}°, " +
                          "prev=${prevHeading.toInt()}°")
            }

            updateOrientationData()
        } else {
            if (currentTime % 3000 < HEADING_UPDATE_INTERVAL_MS) { // Log failure every 3 seconds
                Log.w(TAG, "❌ Failed to calculate rotation matrix - insufficient sensor data")
            }
        }
    }

    private fun smoothBearingTransition(oldBearing: Double, newBearing: Double): Double {
        var diff = newBearing - oldBearing

        // Handle 360/0 degree boundary
        if (diff > 180) {
            diff -= 360
        } else if (diff < -180) {
            diff += 360
        }

        // Apply smoothing
        val smoothedDiff = diff * 0.3 // Adjust smoothing factor as needed
        return (oldBearing + smoothedDiff + 360) % 360
    }

    private fun updateOrientationData() {
        val hasValidSensors = magnetometer != null && accelerometer != null
        val hasMagneticHeading = hasValidSensors && filteredMagneticHeading > 0

        val orientationData = OrientationSensorData(
            track = currentFlightData.track,
            magneticHeading = filteredMagneticHeading,
            groundSpeed = currentFlightData.groundSpeed,
            isGPSValid = currentFlightData.groundSpeed > 0,
            hasValidHeading = hasMagneticHeading,
            timestamp = System.currentTimeMillis()
        )

        // Log orientation data updates periodically (every 3 seconds to avoid spam)
        if (System.currentTimeMillis() % 3000 < 100) {
            Log.d(TAG, "📊 Orientation update: " +
                      "track=${orientationData.track}°, " +
                      "magHeading=${orientationData.magneticHeading.toInt()}°, " +
                      "speed=${orientationData.groundSpeed}kt, " +
                      "gpsValid=${orientationData.isGPSValid}, " +
                      "headingValid=${orientationData.hasValidHeading}")
        }

        _orientationFlow.value = orientationData
    }

    fun getCurrentData(): OrientationSensorData {
        return _orientationFlow.value
    }

    fun hasRequiredSensors(): Boolean {
        return magnetometer != null && accelerometer != null
    }

    fun getSensorInfo(): String {
        val sensors = mutableListOf<String>()
        if (magnetometer != null) sensors.add("Magnetometer")
        if (accelerometer != null) sensors.add("Accelerometer")
        if (gyroscope != null) sensors.add("Gyroscope")

        return if (sensors.isNotEmpty()) {
            "Available: ${sensors.joinToString(", ")}"
        } else {
            "No orientation sensors available"
        }
    }
}