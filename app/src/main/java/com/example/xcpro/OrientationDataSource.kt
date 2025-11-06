package com.example.xcpro

import android.hardware.SensorManager
import android.util.Log
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.sensors.AttitudeData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.UnifiedSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrientationDataSource(
    private val unifiedSensorManager: UnifiedSensorManager,
    private val scope: CoroutineScope
) {

    private val _orientationFlow = MutableStateFlow(OrientationSensorData())
    val orientationFlow: StateFlow<OrientationSensorData> = _orientationFlow.asStateFlow()

    private var currentFlightData = RealTimeFlightData()

    private var sensorJob: Job? = null
    private var isStarted = false

    private var filteredMagneticHeading = 0.0
    private var hasComputedHeading = false
    private var lastHeadingUpdateTime = 0L
    private var lastReliableHeadingTime = 0L

    private var latestCompass: CompassData? = null
    private var latestAttitude: AttitudeData? = null

    companion object {
        private const val TAG = "OrientationDataSource"
        private const val HEADING_UPDATE_INTERVAL_MS = 50L     // 20Hz
        private const val HEADING_STALE_THRESHOLD_MS = 1_500L  // 1.5 seconds
        private const val SMOOTHING_FACTOR = 0.3
    }

    init {
        Log.d(TAG, "📡 OrientationDataSource initializing with UnifiedSensorManager")
        Log.d(TAG, "🔧 Sensor availability: ${getSensorInfo()}")
    }

    fun updateFromFlightData(flightData: RealTimeFlightData) {
        currentFlightData = flightData
        updateOrientationData()
    }

    fun start() {
        if (isStarted) {
            Log.d(TAG, "⚠️ OrientationDataSource already started")
            return
        }
        isStarted = true
        Log.d(TAG, "▶️ Starting OrientationDataSource (Unified)")

        sensorJob = scope.launch {
            launch {
                unifiedSensorManager.compassFlow.collect { compass ->
                    compass?.let { handleCompassUpdate(it) }
                }
            }
            launch {
                unifiedSensorManager.attitudeFlow.collect { attitude ->
                    attitude?.let { handleAttitudeUpdate(it) }
                }
            }
        }
    }

    fun stop() {
        if (!isStarted) {
            Log.d(TAG, "⚠️ OrientationDataSource already stopped")
            return
        }
        isStarted = false
        Log.d(TAG, "⏹️ Stopping OrientationDataSource (Unified)")
        sensorJob?.cancel()
        sensorJob = null
        resetHeadingState()
        Log.d(TAG, "✅ OrientationDataSource stopped")
    }

    private fun handleCompassUpdate(compass: CompassData) {
        latestCompass = compass
        val reliable = compass.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
        updateHeading(compass.heading, reliable)
    }

    private fun handleAttitudeUpdate(attitude: AttitudeData) {
        latestAttitude = attitude
        updateHeading(attitude.headingDeg, attitude.isReliable)
    }

    private fun updateHeading(newHeading: Double, reliable: Boolean) {
        val now = System.currentTimeMillis()

        if (hasComputedHeading && now - lastHeadingUpdateTime < HEADING_UPDATE_INTERVAL_MS) {
            if (reliable) {
                lastReliableHeadingTime = now
            }
            return
        }

        filteredMagneticHeading = if (!hasComputedHeading) {
            newHeading
        } else {
            smoothBearingTransition(filteredMagneticHeading, newHeading)
        }

        hasComputedHeading = true
        lastHeadingUpdateTime = now
        if (reliable) {
            lastReliableHeadingTime = now
        }

        updateOrientationData()
    }

    private fun updateOrientationData() {
        val now = System.currentTimeMillis()
        val headingFresh = hasComputedHeading && (now - lastReliableHeadingTime) <= HEADING_STALE_THRESHOLD_MS

        val orientationData = OrientationSensorData(
            track = currentFlightData.track,
            magneticHeading = filteredMagneticHeading,
            groundSpeed = currentFlightData.groundSpeed,
            isGPSValid = hasGpsFix(),
            hasValidHeading = headingFresh,
            timestamp = now
        )

        if (now % 3000 < 100) {
            Log.d(
                TAG,
                "📊 Orientation update: " +
                    "track=${orientationData.track}°, " +
                    "magHeading=${orientationData.magneticHeading.toInt()}°, " +
                    "speed=${orientationData.groundSpeed}kt, " +
                    "gpsValid=${orientationData.isGPSValid}, " +
                    "headingValid=${orientationData.hasValidHeading}"
            )
        }

        _orientationFlow.value = orientationData
    }

    private fun smoothBearingTransition(oldBearing: Double, newBearing: Double): Double {
        var diff = newBearing - oldBearing
        if (diff > 180) diff -= 360.0
        else if (diff < -180) diff += 360.0
        return (oldBearing + diff * SMOOTHING_FACTOR + 360.0) % 360.0
    }

    private fun resetHeadingState() {
        filteredMagneticHeading = 0.0
        hasComputedHeading = false
        lastHeadingUpdateTime = 0L
        lastReliableHeadingTime = 0L
        latestCompass = null
        latestAttitude = null
    }

    fun getCurrentData(): OrientationSensorData = _orientationFlow.value

    fun hasRequiredSensors(): Boolean {
        val status = unifiedSensorManager.getSensorStatus()
        return status.compassAvailable || status.rotationAvailable
    }

    fun getSensorInfo(): String {
        val status = unifiedSensorManager.getSensorStatus()
        val sensors = mutableListOf<String>()
        if (status.compassAvailable) sensors.add("Compass")
        if (status.accelAvailable) sensors.add("Accelerometer")
        if (status.rotationAvailable) sensors.add("Rotation Vector")
        return if (sensors.isNotEmpty()) {
            "Available: ${sensors.joinToString(", ")}"
        } else {
            "No orientation sensors available"
        }
    }

    private fun hasGpsFix(): Boolean {
        return currentFlightData.accuracy > 0.0 ||
            currentFlightData.latitude != 0.0 ||
            currentFlightData.longitude != 0.0
    }
}
