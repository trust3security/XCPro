package com.trust3.xcpro

import android.util.Log
import com.example.dfcards.FlightModeSelection
import com.trust3.xcpro.common.orientation.OrientationFlightDataSnapshot
import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.common.orientation.OrientationController
import com.trust3.xcpro.common.orientation.OrientationData
import com.trust3.xcpro.common.orientation.OrientationSensorData
import com.trust3.xcpro.map.BuildConfig
import com.trust3.xcpro.map.MapOrientationRuntimePort
import com.trust3.xcpro.orientation.HeadingJitterLogger
import com.trust3.xcpro.orientation.OrientationClock
import com.trust3.xcpro.orientation.OrientationEngine
import com.trust3.xcpro.orientation.SystemOrientationClock
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    private val scope: CoroutineScope,
    orientationDataSourceFactory: OrientationDataSourceFactory,
    private val settingsRepository: MapOrientationSettingsRepository,
    private val clock: OrientationClock = SystemOrientationClock(),
    orientationDataSourceOverride: OrientationSensorSource? = null
) : OrientationController, MapOrientationRuntimePort {
    private val orientationDataSource =
        orientationDataSourceOverride ?: orientationDataSourceFactory.create(scope)
    private val orientationEngine = OrientationEngine()
    private var engineState = OrientationEngine.State()

    private val _orientationFlow = MutableStateFlow(OrientationData())
    override val orientationFlow: StateFlow<OrientationData> = _orientationFlow.asStateFlow()

    private val userEvents = MutableSharedFlow<UserEvent>(extraBufferCapacity = 8)
    private var latestSensorData: OrientationSensorData = orientationDataSource.getCurrentData()
    private var currentSettings = settingsRepository.settingsFlow.value
    private var minSpeedForTrackMs: Double = currentSettings.minSpeedThresholdMs

    private val jitterLogger = HeadingJitterLogger { now ->
        (orientationDataSource as? OrientationDataSource)?.getHeadingDebugSnapshot(now)
    }
    private var updatesJob: Job? = null
    private var settingsJob: Job? = null
    private var isRunning = false

    companion object {
        private const val TAG = "MapOrientationManager"
        private const val BEARING_UPDATE_THROTTLE_MS = 66L // ~15Hz
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
        if (settingsJob?.isActive == true) {
            return
        }

        settingsJob = scope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
                val newThreshold = settings.minSpeedThresholdMs
                if (newThreshold != minSpeedForTrackMs) {
                    minSpeedForTrackMs = newThreshold
                    orientationDataSource.updateMinSpeedThreshold(minSpeedForTrackMs)
                }

                if (isRunning) {
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
        isRunning = true
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
            jitterLogger.logIfNeeded(
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
        startSettingsUpdates()
        startEventLoop()
        engineState = engineState.copy(lastOrientation = _orientationFlow.value)
        debugLog { "MapOrientationManager started" }
    }

    override fun stop() {
        debugLog { "Stopping MapOrientationManager..." }
        orientationDataSource.stop()
        isRunning = false
        updatesJob?.cancel()
        updatesJob = null
        settingsJob?.cancel()
        settingsJob = null
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

    override fun updateFromFlightData(flightData: OrientationFlightDataSnapshot) {
        orientationDataSource.updateFromFlightData(flightData)
    }

}
