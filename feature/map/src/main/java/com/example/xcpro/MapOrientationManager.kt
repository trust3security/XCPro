package com.example.xcpro

import android.content.Context
import android.util.Log
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationController
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.orientation.OrientationSensorData
import com.example.xcpro.sensors.UnifiedSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class MapOrientationManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val unifiedSensorManager: UnifiedSensorManager
) : OrientationController {
    private val preferences = MapOrientationPreferences(context)
    private val orientationDataSource = OrientationDataSource(unifiedSensorManager, scope)

    private val _orientationFlow = MutableStateFlow(OrientationData())
    override val orientationFlow: StateFlow<OrientationData> = _orientationFlow.asStateFlow()

    private var currentMode = MapOrientationMode.NORTH_UP
    private var isUserOverrideActive = false
    private var lastUserInteractionTime = 0L
    private var lastValidBearing = 0.0
    private var updatesJob: Job? = null
    private var minSpeedForTrackKt: Double = 0.0

    companion object {
        private const val TAG = "MapOrientationManager"
        private const val USER_OVERRIDE_TIMEOUT_MS = 10000L // 10 seconds
        private const val BEARING_UPDATE_THROTTLE_MS = 66L // ~15Hz
        private const val BEARING_CHANGE_THRESHOLD = 5.0 // degrees
    }

    init {
        Log.d(TAG, "🧭 MapOrientationManager initializing...")

        // Load saved orientation mode
        currentMode = preferences.getOrientationMode()
        minSpeedForTrackKt = preferences.getMinSpeedThreshold()
        Log.d(TAG, "📱 Loaded orientation mode: $currentMode")

        // Start orientation data collection
        startOrientationUpdates()
        Log.d(TAG, "✅ MapOrientationManager initialized successfully")
    }

    private fun startOrientationUpdates() {
        if (updatesJob?.isActive == true) {
            Log.d(TAG, "🚀 Orientation updates already running")
            return
        }

        Log.d(TAG, "🚀 Starting orientation updates...")
        updatesJob = scope.launch {
            orientationDataSource.orientationFlow
                .sample(BEARING_UPDATE_THROTTLE_MS) // Throttle updates to ~15Hz
                .collect { sensorData ->
                    updateOrientation(sensorData)
                }
        }
    }

    private fun updateOrientation(sensorData: OrientationSensorData) {
        // Check if user override is still active
        if (isUserOverrideActive) {
            val timeSinceInteraction = System.currentTimeMillis() - lastUserInteractionTime
            if (timeSinceInteraction > USER_OVERRIDE_TIMEOUT_MS) {
                isUserOverrideActive = false
            } else {
                // Keep current bearing during user override
                return
            }
        }

        val bearing = calculateBearing(sensorData)
        val isValid = isBearingValid(sensorData, bearing)

        if (isValid) {
            lastValidBearing = bearing
        }

        val finalBearing = if (isValid) bearing else lastValidBearing

        val orientationData = OrientationData(
            bearing = finalBearing,
            mode = currentMode,
            isValid = isValid,
            timestamp = System.currentTimeMillis()
        )

        // Log bearing updates periodically (every 30 updates to avoid spam)
        if (System.currentTimeMillis() % 30 == 0L) {
            Log.d(TAG, "🧭 Orientation: mode=$currentMode, bearing=${finalBearing.toInt()}°, valid=$isValid")
        }

        _orientationFlow.value = orientationData
    }

    private fun calculateBearing(sensorData: OrientationSensorData): Double {
        return when (currentMode) {
            MapOrientationMode.NORTH_UP -> 0.0

            MapOrientationMode.TRACK_UP -> {
                if (sensorData.groundSpeed >= minSpeedForTrackKt) {
                    // Use GPS track when moving fast enough
                    sensorData.track
                } else {
                    // Keep last valid bearing when moving slowly
                    lastValidBearing
                }
            }

            MapOrientationMode.HEADING_UP -> {
                // Use magnetometer heading, fall back to GPS track if unavailable
                if (sensorData.hasValidHeading) {
                    sensorData.magneticHeading
                } else if (sensorData.groundSpeed >= minSpeedForTrackKt) {
                    sensorData.track
                } else {
                    lastValidBearing
                }
            }
        }
    }

    private fun isBearingValid(sensorData: OrientationSensorData, bearing: Double): Boolean {
        return when (currentMode) {
            MapOrientationMode.NORTH_UP -> true // Always valid

            MapOrientationMode.TRACK_UP -> {
                sensorData.groundSpeed >= minSpeedForTrackKt
            }

            MapOrientationMode.HEADING_UP -> {
                sensorData.hasValidHeading || sensorData.groundSpeed >= minSpeedForTrackKt
            }
        }
    }

    override fun setOrientationMode(mode: MapOrientationMode) {
        if (currentMode != mode) {
            Log.d(TAG, "🔄 Changing orientation mode: $currentMode → $mode")
            minSpeedForTrackKt = preferences.getMinSpeedThreshold()
            currentMode = mode
            preferences.setOrientationMode(mode)

            // Trigger immediate update with new mode
            scope.launch {
                val currentSensorData = orientationDataSource.getCurrentData()
                updateOrientation(currentSensorData)
            }
        }
    }

    override fun onUserInteraction() {
        isUserOverrideActive = true
        lastUserInteractionTime = System.currentTimeMillis()
    }

    override fun resetUserOverride() {
        isUserOverrideActive = false
    }

    override fun getCurrentMode(): MapOrientationMode = currentMode

    override fun getCurrentBearing(): Double = _orientationFlow.value.bearing

    override fun isOrientationValid(): Boolean = _orientationFlow.value.isValid

    override fun start() {
        Log.d(TAG, "▶️ Starting MapOrientationManager...")
        orientationDataSource.start()
        startOrientationUpdates()
        Log.d(TAG, "✅ MapOrientationManager started")
    }

    override fun stop() {
        Log.d(TAG, "⏹️ Stopping MapOrientationManager...")
        orientationDataSource.stop()
        updatesJob?.cancel()
        updatesJob = null
        Log.d(TAG, "✅ MapOrientationManager stopped")
    }

    override fun updateFromFlightData(flightData: RealTimeFlightData) {
        orientationDataSource.updateFromFlightData(flightData)
    }
}

