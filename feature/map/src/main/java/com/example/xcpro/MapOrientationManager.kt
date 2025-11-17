package com.example.xcpro

import android.content.Context
import android.util.Log
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationController
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.orientation.OrientationSensorData
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.map.BuildConfig
import java.util.Locale
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

    private enum class OrientationProfile { CRUISE, CIRCLING }

    private var cruiseMode = preferences.getCruiseOrientationMode()
    private var circlingMode = preferences.getCirclingOrientationMode()
    private var activeProfile = OrientationProfile.CRUISE
    private var currentMode = cruiseMode
    private var isUserOverrideActive = false
    private var lastUserInteractionTime = 0L
    private var lastValidBearing = 0.0
    private var updatesJob: Job? = null
    private var minSpeedForTrackMs: Double = 0.0

    companion object {
        private const val TAG = "MapOrientationManager"
        private const val USER_OVERRIDE_TIMEOUT_MS = 10000L // 10 seconds
        private const val BEARING_UPDATE_THROTTLE_MS = 66L // ~15Hz
        private const val BEARING_CHANGE_THRESHOLD = 5.0 // degrees
    }

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }

    init {
        debugLog { "MapOrientationManager initializing..." }

        // Load saved orientation mode
        minSpeedForTrackMs = preferences.getMinSpeedThreshold()
        debugLog { "Loaded orientation mode: $currentMode" }

        // Start orientation data collection
        startOrientationUpdates()
        lastValidBearing = normalizeBearing(_orientationFlow.value.bearing)
        debugLog { "MapOrientationManager initialized successfully" }
    }

    private fun startOrientationUpdates() {
        if (updatesJob?.isActive == true) {
            debugLog { "Orientation updates already running" }
            return
        }

        debugLog { "Starting orientation updates..." }
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

        val (bearing, isValid) = calculateBearing(sensorData)

        val normalizedBearing = normalizeBearing(bearing)
        if (isValid) {
            lastValidBearing = normalizedBearing
        }
        val finalBearing = if (isValid) normalizedBearing else lastValidBearing

        val orientationData = OrientationData(

            bearing = finalBearing,

            mode = currentMode,

            isValid = isValid,

            timestamp = System.currentTimeMillis()

        )

        if (!isValid &&
            sensorData.isGPSValid &&
            sensorData.groundSpeed < minSpeedForTrackMs &&
            BuildConfig.DEBUG &&
            System.currentTimeMillis() % 2000L < 25
        ) {
            Log.v(
                TAG,
                String.format(
                    Locale.US,
                    "TRACK_UP gate: speed=%.2f m/s < threshold=%.2f m/s",
                    sensorData.groundSpeed,
                    minSpeedForTrackMs
                )
            )
        }

        // Log bearing updates periodically (every 30 updates to avoid spam)

        if (System.currentTimeMillis() % 30 == 0L) {

            debugLog { "Orientation: mode=$currentMode, bearing=${finalBearing.toInt()}, valid=$isValid" }

        }



        _orientationFlow.value = orientationData

    }



    private data class BearingResult(val bearing: Double, val isValid: Boolean)



    private fun calculateBearing(sensorData: OrientationSensorData): BearingResult {

        return when (currentMode) {

            MapOrientationMode.NORTH_UP -> BearingResult(0.0, true)



            MapOrientationMode.TRACK_UP -> {

                val valid = sensorData.isGPSValid && sensorData.groundSpeed >= minSpeedForTrackMs

                val bearing = if (valid) sensorData.track else 0.0

                BearingResult(bearing, valid)

            }



            MapOrientationMode.HEADING_UP -> {

                when {

                    sensorData.hasValidHeading -> BearingResult(sensorData.magneticHeading, true)

                    sensorData.isGPSValid && sensorData.groundSpeed >= minSpeedForTrackMs ->

                        BearingResult(sensorData.track, true)

                    else -> BearingResult(0.0, false)

                }

            }

        }

    }




    private fun OrientationProfile.mode(): MapOrientationMode = when (this) {

        OrientationProfile.CRUISE -> cruiseMode

        OrientationProfile.CIRCLING -> circlingMode

    }



    private fun OrientationProfile.setMode(mode: MapOrientationMode) {

        when (this) {

            OrientationProfile.CRUISE -> {

                cruiseMode = mode

                preferences.setCruiseOrientationMode(mode)

            }

            OrientationProfile.CIRCLING -> {

                circlingMode = mode

                preferences.setCirclingOrientationMode(mode)

            }

        }

    }




    override fun setOrientationMode(mode: MapOrientationMode) {

        if (currentMode == mode) {

            return

        }


        debugLog { "Map orientation changing: $currentMode -> $mode" }
        minSpeedForTrackMs = preferences.getMinSpeedThreshold()

        activeProfile.setMode(mode)

        currentMode = mode
        lastValidBearing = normalizeBearing(_orientationFlow.value.bearing)



        // Trigger immediate update with new mode

        scope.launch {

            val currentSensorData = orientationDataSource.getCurrentData()

            updateOrientation(currentSensorData)

        }

    }




    fun setFlightMode(selection: FlightModeSelection) {

        val newProfile = if (selection == FlightModeSelection.THERMAL) {

            OrientationProfile.CIRCLING

        } else {

            OrientationProfile.CRUISE

        }

        if (newProfile == activeProfile) {

            return

        }

        activeProfile = newProfile

        currentMode = activeProfile.mode()
        lastValidBearing = normalizeBearing(_orientationFlow.value.bearing)

        scope.launch {

            val currentSensorData = orientationDataSource.getCurrentData()

            updateOrientation(currentSensorData)

        }

    }



    fun reloadFromPreferences() {

        cruiseMode = preferences.getCruiseOrientationMode()

        circlingMode = preferences.getCirclingOrientationMode()

        minSpeedForTrackMs = preferences.getMinSpeedThreshold()

        currentMode = activeProfile.mode()
        lastValidBearing = normalizeBearing(_orientationFlow.value.bearing)

        scope.launch {

            val currentSensorData = orientationDataSource.getCurrentData()

            updateOrientation(currentSensorData)

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
        debugLog { "Starting MapOrientationManager..." }
        orientationDataSource.start()
        startOrientationUpdates()
        lastValidBearing = normalizeBearing(_orientationFlow.value.bearing)
        debugLog { "MapOrientationManager started" }
    }

    override fun stop() {
        debugLog { "Stopping MapOrientationManager..." }
        orientationDataSource.stop()
        updatesJob?.cancel()
        updatesJob = null
        lastValidBearing = 0.0
        debugLog { "MapOrientationManager stopped" }
    }

    override fun updateFromFlightData(flightData: RealTimeFlightData) {
        orientationDataSource.updateFromFlightData(flightData)
    }

    private fun normalizeBearing(value: Double): Double {
        if (!value.isFinite()) {
            return 0.0
        }
        var result = value % 360.0
        if (result < 0) {
            result += 360.0
        }
        return result
    }
}

