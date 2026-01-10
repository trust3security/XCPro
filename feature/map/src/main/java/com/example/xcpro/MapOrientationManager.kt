package com.example.xcpro

import android.content.Context
import android.util.Log
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationController
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.orientation.OrientationSensorData
import com.example.xcpro.map.BuildConfig
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MapOrientationManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    orientationDataSourceFactory: OrientationDataSourceFactory
) : OrientationController {
    private val preferences = MapOrientationPreferences(context)
    private val orientationDataSource = orientationDataSourceFactory.create(scope)

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
    private var lastValidTrackTime = 0L
    private var lastValidHeadingTime = 0L
    private var lastHeadingSampleTime = 0L
    private var lastHeadingSampleBearing = 0.0
    private var lastJitterLogTime = 0L
    private var updatesJob: Job? = null
    private var minSpeedForTrackMs: Double = 0.0

    companion object {
        private const val TAG = "MapOrientationManager"
        private const val JITTER_TAG = "JITTER"
        private const val USER_OVERRIDE_TIMEOUT_MS = 10000L // 10 seconds
        private const val BEARING_UPDATE_THROTTLE_MS = 66L // ~15Hz
        private const val BEARING_CHANGE_THRESHOLD = 5.0 // degrees
        private const val TRACK_STALE_TIMEOUT_MS = 10000L // XCSoar parity for track expiry
        private const val HEADING_STALE_TIMEOUT_MS = 5000L // XCSoar parity for heading expiry
        private const val JITTER_DELTA_DEG = 10.0
        private const val JITTER_WINDOW_MS = 500L
        private const val JITTER_DEG_PER_SEC = 30.0
        private const val JITTER_LOG_COOLDOWN_MS = 1000L
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
        orientationDataSource.updateMinSpeedThreshold(minSpeedForTrackMs)
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

        val now = System.currentTimeMillis()
        val bearingResult = calculateBearing(sensorData)

        val normalizedBearing = normalizeBearing(bearingResult.bearing)
        if (bearingResult.isValid) {
            lastValidBearing = normalizedBearing
            if (currentMode == MapOrientationMode.TRACK_UP) {
                lastValidTrackTime = now
            }
            if (currentMode == MapOrientationMode.HEADING_UP) {
                lastValidHeadingTime = now
            }
        }

        val trackIsStale = currentMode == MapOrientationMode.TRACK_UP &&
            (lastValidTrackTime == 0L || now - lastValidTrackTime > TRACK_STALE_TIMEOUT_MS)

        val headingIsStale = currentMode == MapOrientationMode.HEADING_UP &&
            !bearingResult.isValid &&
            (lastValidHeadingTime == 0L || now - lastValidHeadingTime > HEADING_STALE_TIMEOUT_MS)

        val finalBearing = when {
            bearingResult.isValid -> normalizedBearing
            headingIsStale -> 0.0
            trackIsStale -> 0.0
            else -> lastValidBearing
        }
        val finalSource = when {
            bearingResult.isValid -> bearingResult.source
            headingIsStale -> BearingSource.NONE
            trackIsStale -> BearingSource.NONE
            else -> BearingSource.LAST_KNOWN
        }
        val finalValid = bearingResult.isValid ||
            (currentMode == MapOrientationMode.TRACK_UP && !trackIsStale)

        val headingSolution = sensorData.headingSolution
        val orientationData = OrientationData(
            bearing = finalBearing,
            mode = currentMode,
            isValid = finalValid,
            bearingSource = finalSource,
            headingDeg = headingSolution.bearingDeg,
            headingValid = headingSolution.isValid,
            headingSource = headingSolution.source,
            timestamp = now
        )

        if (!bearingResult.isValid &&
            sensorData.isGPSValid &&
            sensorData.groundSpeed < minSpeedForTrackMs &&
            BuildConfig.DEBUG &&
            now % 2000L < 25
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

        if (now % 30 == 0L) {

            debugLog { "Orientation: mode=$currentMode, bearing=${finalBearing.toInt()}, source=$finalSource, valid=$finalValid" }

        }



        _orientationFlow.value = orientationData

        if (BuildConfig.DEBUG && currentMode == MapOrientationMode.HEADING_UP) {
            logHeadingJitterIfNeeded(
                now = now,
                bearing = finalBearing,
                finalSource = finalSource,
                finalValid = finalValid,
                sensorData = sensorData
            )
        }

    }



    private data class BearingResult(
        val bearing: Double,
        val isValid: Boolean,
        val source: BearingSource
    )



    private fun calculateBearing(sensorData: OrientationSensorData): BearingResult {

        return when (currentMode) {

            MapOrientationMode.NORTH_UP -> BearingResult(0.0, true, BearingSource.NONE)



            MapOrientationMode.TRACK_UP -> {

                val hasTrack = sensorData.isGPSValid && sensorData.track.isFinite()
                val valid = hasTrack && sensorData.groundSpeed >= minSpeedForTrackMs
                val bearing = if (hasTrack) sensorData.track else 0.0

                BearingResult(bearing, valid, BearingSource.TRACK)

            }



            MapOrientationMode.HEADING_UP -> {

                val solution = sensorData.headingSolution
                BearingResult(solution.bearingDeg, solution.isValid, solution.source)

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
        orientationDataSource.updateMinSpeedThreshold(minSpeedForTrackMs)

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
        orientationDataSource.updateMinSpeedThreshold(minSpeedForTrackMs)

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
        lastValidTrackTime = 0L
        lastValidHeadingTime = 0L
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

    private fun logHeadingJitterIfNeeded(
        now: Long,
        bearing: Double,
        finalSource: BearingSource,
        finalValid: Boolean,
        sensorData: OrientationSensorData
    ) {
        if (lastHeadingSampleTime == 0L) {
            lastHeadingSampleTime = now
            lastHeadingSampleBearing = bearing
            return
        }

        val dt = now - lastHeadingSampleTime
        val delta = kotlin.math.abs(shortestDeltaDegrees(lastHeadingSampleBearing, bearing))
        val dps = if (dt > 0L) (delta * 1000.0 / dt.toDouble()) else 0.0

        val isJitter = (delta >= JITTER_DELTA_DEG && dt <= JITTER_WINDOW_MS) ||
            dps >= JITTER_DEG_PER_SEC

        if (isJitter && now - lastJitterLogTime >= JITTER_LOG_COOLDOWN_MS) {
            val snapshot = orientationDataSource.getHeadingDebugSnapshot(now)
            val deltaText = String.format(Locale.US, "%.1f", delta)
            val dpsText = String.format(Locale.US, "%.1f", dps)
            val bearingText = String.format(Locale.US, "%.1f", bearing)
            val compassText = snapshot.compassHeading?.let { String.format(Locale.US, "%.1f", it) } ?: "na"
            val attText = snapshot.attitudeHeading?.let { String.format(Locale.US, "%.1f", it) } ?: "na"
            val activeText = snapshot.activeHeading?.let { String.format(Locale.US, "%.1f", it) } ?: "na"
            val trackText = String.format(Locale.US, "%.1f", sensorData.track)
            val gsText = String.format(Locale.US, "%.2f", sensorData.groundSpeed)
            val windSpdText = String.format(Locale.US, "%.2f", sensorData.windSpeed)
            val windFromText = String.format(Locale.US, "%.1f", sensorData.windDirectionFrom)
            Log.w(
                JITTER_TAG,
                "HU_JITTER delta=${deltaText}deg dt=${dt}ms dps=${dpsText} " +
                    "bearing=${bearingText} src=$finalSource valid=$finalValid " +
                    "input=${snapshot.inputSource} active=${snapshot.activeSource} activeHead=${activeText} " +
                    "compass=${compassText} " +
                    "compassAge=${snapshot.compassAgeMs ?: -1}ms compRel=${snapshot.compassReliable} " +
                    "att=${attText} " +
                    "attAge=${snapshot.attitudeAgeMs ?: -1}ms attRel=${snapshot.attitudeReliable} " +
                    "track=${trackText} gs=${gsText} " +
                    "windSpd=${windSpdText} windFrom=${windFromText} " +
                    "headSrc=${sensorData.headingSolution.source} headValid=${sensorData.headingSolution.isValid}"
            )
            lastJitterLogTime = now
        }

        lastHeadingSampleTime = now
        lastHeadingSampleBearing = bearing
    }

    private fun shortestDeltaDegrees(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }
}

