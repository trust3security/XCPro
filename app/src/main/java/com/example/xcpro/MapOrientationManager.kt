package com.example.xcpro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlin.math.*

enum class MapOrientationMode {
    NORTH_UP,
    TRACK_UP,
    HEADING_UP
}

data class OrientationData(
    val bearing: Double = 0.0,
    val mode: MapOrientationMode = MapOrientationMode.NORTH_UP,
    val isValid: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

class MapOrientationManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val preferences = MapOrientationPreferences(context)
    private val orientationDataSource = OrientationDataSource(context)

    private val _orientationFlow = MutableStateFlow(OrientationData())
    val orientationFlow: StateFlow<OrientationData> = _orientationFlow.asStateFlow()

    private var currentMode = MapOrientationMode.NORTH_UP
    private var isUserOverrideActive = false
    private var lastUserInteractionTime = 0L
    private var lastValidBearing = 0.0
    private var updatesJob: Job? = null

    companion object {
        private const val TAG = "MapOrientationManager"
        private const val USER_OVERRIDE_TIMEOUT_MS = 10000L // 10 seconds
        private const val BEARING_UPDATE_THROTTLE_MS = 66L // ~15Hz
        private const val MIN_SPEED_FOR_TRACK_KT = 2.0  // Reduced from 5.0 to 2.0 knots (3.7 km/h)
        private const val BEARING_CHANGE_THRESHOLD = 5.0 // degrees
    }

    init {
        Log.d(TAG, "🧭 MapOrientationManager initializing...")

        // Load saved orientation mode
        currentMode = preferences.getOrientationMode()
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
                if (sensorData.groundSpeed >= MIN_SPEED_FOR_TRACK_KT) {
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
                } else if (sensorData.groundSpeed >= MIN_SPEED_FOR_TRACK_KT) {
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
                sensorData.isGPSValid && sensorData.groundSpeed >= MIN_SPEED_FOR_TRACK_KT
            }

            MapOrientationMode.HEADING_UP -> {
                sensorData.hasValidHeading ||
                (sensorData.isGPSValid && sensorData.groundSpeed >= MIN_SPEED_FOR_TRACK_KT)
            }
        }
    }

    fun setOrientationMode(mode: MapOrientationMode) {
        if (currentMode != mode) {
            Log.d(TAG, "🔄 Changing orientation mode: $currentMode → $mode")
            currentMode = mode
            preferences.setOrientationMode(mode)

            // Trigger immediate update with new mode
            scope.launch {
                val currentSensorData = orientationDataSource.getCurrentData()
                updateOrientation(currentSensorData)
            }
        }
    }

    fun onUserInteraction() {
        isUserOverrideActive = true
        lastUserInteractionTime = System.currentTimeMillis()
    }

    fun resetUserOverride() {
        isUserOverrideActive = false
    }

    fun getCurrentMode(): MapOrientationMode = currentMode

    fun getCurrentBearing(): Double = _orientationFlow.value.bearing

    fun isOrientationValid(): Boolean = _orientationFlow.value.isValid

    fun start() {
        Log.d(TAG, "▶️ Starting MapOrientationManager...")
        orientationDataSource.start()
        startOrientationUpdates()
        Log.d(TAG, "✅ MapOrientationManager started")
    }

    fun stop() {
        Log.d(TAG, "⏹️ Stopping MapOrientationManager...")
        orientationDataSource.stop()
        updatesJob?.cancel()
        updatesJob = null
        Log.d(TAG, "✅ MapOrientationManager stopped")
    }
}

data class OrientationSensorData(
    val track: Double = 0.0,
    val magneticHeading: Double = 0.0,
    val groundSpeed: Double = 0.0,
    val isGPSValid: Boolean = false,
    val hasValidHeading: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
