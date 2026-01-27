package com.example.xcpro

import android.util.Log
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationController
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.orientation.OrientationSensorData
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.orientation.OrientationClock
import com.example.xcpro.orientation.OrientationEngine
import com.example.xcpro.orientation.SystemOrientationClock
import com.example.xcpro.orientation.shortestDeltaDegrees
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    orientationDataSourceFactory: OrientationDataSourceFactory,
    private val settingsRepository: MapOrientationSettingsRepository,
    private val clock: OrientationClock = SystemOrientationClock()
) : OrientationController {
    private val orientationDataSource = orientationDataSourceFactory.create(scope)
    private val orientationEngine = OrientationEngine()
    private var engineState = OrientationEngine.State()

    private val _orientationFlow = MutableStateFlow(OrientationData())
    override val orientationFlow: StateFlow<OrientationData> = _orientationFlow.asStateFlow()

    private enum class OrientationProfile { CRUISE, CIRCLING }

    private var activeProfile = OrientationProfile.CRUISE
    private var currentSettings = settingsRepository.settingsFlow.value
    private var currentMode = resolveMode(currentSettings)
    private var lastHeadingSampleTime = 0L
    private var lastHeadingSampleBearing = 0.0
    private var lastJitterLogTime = 0L
    private var updatesJob: Job? = null
    private var settingsJob: Job? = null
    private var minSpeedForTrackMs: Double = currentSettings.minSpeedThresholdMs

    companion object {
        private const val TAG = "MapOrientationManager"
        private const val JITTER_TAG = "JITTER"
        private const val BEARING_UPDATE_THROTTLE_MS = 66L // ~15Hz
        private const val BEARING_CHANGE_THRESHOLD = 5.0 // degrees
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

        orientationDataSource.updateMinSpeedThreshold(minSpeedForTrackMs)
        debugLog { "Loaded orientation mode: $currentMode" }

        startSettingsUpdates()
        startOrientationUpdates()
        engineState = engineState.copy(lastOrientation = _orientationFlow.value)
        engineState = orientationEngine.syncLastValidBearing(engineState)
        debugLog { "MapOrientationManager initialized successfully" }
    }

    private fun resolveMode(settings: MapOrientationSettings): MapOrientationMode {
        return if (activeProfile == OrientationProfile.CRUISE) {
            settings.cruiseMode
        } else {
            settings.circlingMode
        }
    }

    private fun startSettingsUpdates() {
        settingsJob = scope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val previousSettings = currentSettings
                currentSettings = settings

                minSpeedForTrackMs = settings.minSpeedThresholdMs
                orientationDataSource.updateMinSpeedThreshold(minSpeedForTrackMs)

                val newMode = resolveMode(settings)
                val modeChanged = newMode != currentMode
                currentMode = newMode

                if (modeChanged || settings != previousSettings) {
                    engineState = engineState.copy(lastOrientation = _orientationFlow.value)
                    engineState = orientationEngine.syncLastValidBearing(engineState)
                    updateOrientation(orientationDataSource.getCurrentData())
                }
            }
        }
    }

    private fun startOrientationUpdates() {
        if (updatesJob?.isActive == true) {
            debugLog { "Orientation updates already running" }
            return
        }

        debugLog { "Starting orientation updates..." }
        updatesJob = scope.launch {
            orientationDataSource.orientationFlow
                .sample(BEARING_UPDATE_THROTTLE_MS)
                .collect { sensorData ->
                    updateOrientation(sensorData)
                }
        }
    }

    private fun updateOrientation(sensorData: OrientationSensorData) {
        val nowMono = clock.nowMonoMs()
        val nowWall = clock.nowWallMs()
        val output = orientationEngine.reduce(
            state = engineState,
            sensorData = sensorData,
            currentMode = currentMode,
            minSpeedThresholdMs = minSpeedForTrackMs,
            nowMonoMs = nowMono,
            nowWallMs = nowWall
        )
        engineState = output.state

        if (!output.didUpdate) {
            return
        }

        val orientationData = output.orientation

        if (!output.bearingResult.isValid &&
            sensorData.isGPSValid &&
            sensorData.groundSpeed < minSpeedForTrackMs &&
            BuildConfig.DEBUG &&
            nowMono % 2000L < 25
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

        if (nowMono % 30 == 0L) {
            debugLog {
                "Orientation: mode=$currentMode, bearing=${orientationData.bearing.toInt()}, " +
                    "source=${orientationData.bearingSource}, valid=${orientationData.isValid}"
            }
        }

        _orientationFlow.value = orientationData

        if (BuildConfig.DEBUG && currentMode == MapOrientationMode.HEADING_UP) {
            logHeadingJitterIfNeeded(
                now = nowMono,
                bearing = orientationData.bearing,
                finalSource = orientationData.bearingSource,
                finalValid = orientationData.isValid,
                sensorData = sensorData
            )
        }
    }

    override fun setOrientationMode(mode: MapOrientationMode) {
        if (currentMode == mode) {
            return
        }

        debugLog { "Map orientation changing: $currentMode -> $mode" }

        when (activeProfile) {
            OrientationProfile.CRUISE -> settingsRepository.setCruiseOrientationMode(mode)
            OrientationProfile.CIRCLING -> settingsRepository.setCirclingOrientationMode(mode)
        }

        currentSettings = when (activeProfile) {
            OrientationProfile.CRUISE -> currentSettings.copy(cruiseMode = mode)
            OrientationProfile.CIRCLING -> currentSettings.copy(circlingMode = mode)
        }
        currentMode = mode
        engineState = engineState.copy(lastOrientation = _orientationFlow.value)
        engineState = orientationEngine.syncLastValidBearing(engineState)

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
        currentMode = resolveMode(currentSettings)
        engineState = engineState.copy(lastOrientation = _orientationFlow.value)
        engineState = orientationEngine.syncLastValidBearing(engineState)

        scope.launch {
            val currentSensorData = orientationDataSource.getCurrentData()
            updateOrientation(currentSensorData)
        }
    }

    override fun onUserInteraction() {
        engineState = orientationEngine.onUserInteraction(engineState, clock.nowMonoMs())
    }

    override fun resetUserOverride() {
        engineState = orientationEngine.resetUserOverride(engineState)
    }

    override fun getCurrentMode(): MapOrientationMode = currentMode

    override fun getCurrentBearing(): Double = _orientationFlow.value.bearing

    override fun isOrientationValid(): Boolean = _orientationFlow.value.isValid

    override fun start() {
        debugLog { "Starting MapOrientationManager..." }
        orientationDataSource.start()
        startOrientationUpdates()
        engineState = engineState.copy(lastOrientation = _orientationFlow.value)
        engineState = orientationEngine.syncLastValidBearing(engineState)
        debugLog { "MapOrientationManager started" }
    }

    override fun stop() {
        debugLog { "Stopping MapOrientationManager..." }
        orientationDataSource.stop()
        updatesJob?.cancel()
        updatesJob = null
        engineState = OrientationEngine.State(lastOrientation = _orientationFlow.value)
        debugLog { "MapOrientationManager stopped" }
    }

    override fun updateFromFlightData(flightData: RealTimeFlightData) {
        orientationDataSource.updateFromFlightData(flightData)
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
}