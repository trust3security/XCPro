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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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

    private val userEvents = MutableSharedFlow<UserEvent>(extraBufferCapacity = 8)
    private var latestSensorData: OrientationSensorData = orientationDataSource.getCurrentData()
    private var currentSettings = settingsRepository.settingsFlow.value
    private var minSpeedForTrackMs: Double = currentSettings.minSpeedThresholdMs

    private var lastHeadingSampleTime = 0L
    private var lastHeadingSampleBearing = 0.0
    private var lastJitterLogTime = 0L
    private var updatesJob: Job? = null

    companion object {
        private const val TAG = "MapOrientationManager"
        private const val JITTER_TAG = "JITTER"
        private const val BEARING_UPDATE_THROTTLE_MS = 66L // ~15Hz
        private const val JITTER_DELTA_DEG = 10.0
        private const val JITTER_WINDOW_MS = 500L
        private const val JITTER_DEG_PER_SEC = 30.0
        private const val JITTER_LOG_COOLDOWN_MS = 1000L
    }

    private sealed interface UserEvent {
        object UserInteraction : UserEvent
        object ResetUserOverride : UserEvent
        data class FlightMode(val selection: FlightModeSelection) : UserEvent
    }

    private sealed interface OrientationEvent {
        data class Sensor(val data: OrientationSensorData) : OrientationEvent
        data class User(val event: UserEvent) : OrientationEvent
    }

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }

    init {
        debugLog { "MapOrientationManager initializing..." }

        orientationDataSource.updateMinSpeedThreshold(minSpeedForTrackMs)
        startSettingsUpdates()
        startEventLoop()
        engineState = engineState.copy(lastOrientation = _orientationFlow.value)
        debugLog { "MapOrientationManager initialized successfully" }
    }

    private fun startSettingsUpdates() {
        scope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
                val newThreshold = settings.minSpeedThresholdMs
                if (newThreshold != minSpeedForTrackMs) {
                    minSpeedForTrackMs = newThreshold
                    orientationDataSource.updateMinSpeedThreshold(minSpeedForTrackMs)
                }

                if (updatesJob?.isActive == true) {
                    processOrientation(latestSensorData)
                }
            }
        }
    }

    private fun startEventLoop() {
        if (updatesJob?.isActive == true) {
            debugLog { "Orientation updates already running" }
            return
        }

        debugLog { "Starting orientation updates..." }
        val sensorEvents = orientationDataSource.orientationFlow
            .sample(BEARING_UPDATE_THROTTLE_MS)
            .map { OrientationEvent.Sensor(it) }
        val userEventsFlow = userEvents.map { OrientationEvent.User(it) }

        updatesJob = scope.launch {
            merge(sensorEvents, userEventsFlow)
                .collect { event -> handleEvent(event) }
        }
    }

    private fun handleEvent(event: OrientationEvent) {
        when (event) {
            is OrientationEvent.Sensor -> {
                latestSensorData = event.data
                processOrientation(event.data)
            }
            is OrientationEvent.User -> handleUserEvent(event.event)
        }
    }

    private fun handleUserEvent(event: UserEvent) {
        when (event) {
            UserEvent.UserInteraction -> {
                engineState = orientationEngine.onUserInteraction(engineState, clock.nowMonoMs())
            }
            UserEvent.ResetUserOverride -> {
                engineState = orientationEngine.resetUserOverride(engineState)
            }
            is UserEvent.FlightMode -> {
                engineState = orientationEngine.updateProfile(engineState, event.selection)
                processOrientation(latestSensorData)
            }
        }
    }

    private fun processOrientation(sensorData: OrientationSensorData) {
        val nowMono = clock.nowMonoMs()
        val nowWall = clock.nowWallMs()
        val output = orientationEngine.reduce(
            state = engineState,
            sensorData = sensorData,
            settings = currentSettings,
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
                "Orientation: mode=${orientationData.mode}, bearing=${orientationData.bearing.toInt()}, " +
                    "source=${orientationData.bearingSource}, valid=${orientationData.isValid}"
            }
        }

        _orientationFlow.value = orientationData

        if (BuildConfig.DEBUG && orientationData.mode == MapOrientationMode.HEADING_UP) {
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
        if (_orientationFlow.value.mode == mode) {
            return
        }

        debugLog { "Map orientation changing: ${_orientationFlow.value.mode} -> $mode" }

        when (engineState.activeProfile) {
            OrientationEngine.OrientationProfile.CRUISE ->
                settingsRepository.setCruiseOrientationMode(mode)
            OrientationEngine.OrientationProfile.CIRCLING ->
                settingsRepository.setCirclingOrientationMode(mode)
        }
    }

    fun setFlightMode(selection: FlightModeSelection) {
        userEvents.tryEmit(UserEvent.FlightMode(selection))
    }

    override fun onUserInteraction() {
        userEvents.tryEmit(UserEvent.UserInteraction)
    }

    override fun resetUserOverride() {
        userEvents.tryEmit(UserEvent.ResetUserOverride)
    }

    override fun getCurrentMode(): MapOrientationMode = _orientationFlow.value.mode

    override fun getCurrentBearing(): Double = _orientationFlow.value.bearing

    override fun isOrientationValid(): Boolean = _orientationFlow.value.isValid

    override fun start() {
        debugLog { "Starting MapOrientationManager..." }
        orientationDataSource.start()
        startEventLoop()
        engineState = engineState.copy(lastOrientation = _orientationFlow.value)
        debugLog { "MapOrientationManager started" }
    }

    override fun stop() {
        debugLog { "Stopping MapOrientationManager..." }
        orientationDataSource.stop()
        updatesJob?.cancel()
        updatesJob = null
        engineState = engineState.copy(
            isUserOverrideActive = false,
            lastUserInteractionMonoMs = 0L,
            lastValidBearing = 0.0,
            lastValidTrackTimeMs = 0L,
            lastValidHeadingTimeMs = 0L,
            lastOrientation = _orientationFlow.value
        )
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
            val debugSource = orientationDataSource as? OrientationDataSource ?: return
            val snapshot = debugSource.getHeadingDebugSnapshot(now)
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
