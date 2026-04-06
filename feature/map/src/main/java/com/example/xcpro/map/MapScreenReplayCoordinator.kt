package com.example.xcpro.map

import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.replay.DemoReplayLauncher
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.map.replay.SyntheticThermalReplayLogBuilder
import com.example.xcpro.map.replay.SyntheticThermalReplayLauncher
import com.example.xcpro.map.replay.SyntheticThermalReplayMode
import com.example.xcpro.map.replay.SyntheticThermalReplayVariant
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayCadenceProfile
import com.example.xcpro.replay.ReplayMode
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class MapScreenReplayCoordinator(
    private val taskManager: TaskManagerCoordinator,
    private val taskNavigationController: TaskNavigationController,
    private val flightDataFlow: StateFlow<CompleteFlightData?>,
    private val igcReplayController: IgcReplayController,
    private val racingReplayLogBuilder: RacingReplayLogBuilder,
    private val syntheticThermalReplayLogBuilder: SyntheticThermalReplayLogBuilder,
    private val featureFlags: MapFeatureFlags,
    private val mapStateStore: MapStateStore,
    private val mapStateActions: MapStateActions,
    private val syntheticReplayMode: MutableStateFlow<SyntheticThermalReplayMode>,
    private val uiEffects: MutableSharedFlow<MapUiEffect>,
    private val replaySessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope
) {

    private val racingEventDebouncer = RacingNavigationEventDebouncer()
    private val racingReplayLogger = RacingReplayEventLogger()
    private val racingReplaySnapshots = RacingReplaySnapshotController(
        taskManager = taskManager,
        taskNavigationController = taskNavigationController,
        igcReplayController = igcReplayController,
        replaySessionState = replaySessionState,
        mapStateStore = mapStateStore,
        mapStateActions = mapStateActions
    )
    private val demoReplaySnapshots = DemoReplaySnapshotController(
        mapStateStore = mapStateStore,
        mapStateActions = mapStateActions,
        igcReplayController = igcReplayController,
        featureFlags = featureFlags,
        replaySessionState = replaySessionState
    )
    private val demoReplayLauncher = DemoReplayLauncher(
        demoReplaySnapshots = demoReplaySnapshots,
        igcReplayController = igcReplayController,
        featureFlags = featureFlags,
        mapStateActions = mapStateActions,
        uiEffects = uiEffects
    )
    private val syntheticThermalReplayLauncher = SyntheticThermalReplayLauncher(
        demoReplaySnapshots = demoReplaySnapshots,
        igcReplayController = igcReplayController,
        syntheticThermalReplayLogBuilder = syntheticThermalReplayLogBuilder,
        syntheticReplayMode = syntheticReplayMode,
        mapStateActions = mapStateActions,
        uiEffects = uiEffects
    )
    private var racingReplayActive = false
    @Volatile
    private var suppressRacingFixes = false
    private val liveGpsProfileTracker = LiveGpsProfileTracker()

    private val racingFixFlow = combine(flightDataFlow, replaySessionState) { data, session ->
        // Keep live fixes out of the task runtime while racing replay is booting
        // or after replay cleanup has dropped the session selection but before the
        // pre-replay task/nav bundle has been restored.
        if (suppressRacingFixes || (racingReplayActive && session.selection == null)) {
            null
        } else {
            data?.let(RacingNavigationFixAdapter::toFix)
        }
    }
        .mapNotNull { it }
    private val liveGpsProfileFlow = flightDataFlow
        .mapNotNull { data -> data?.gps }
        .onEach { gps ->
            if (replaySessionState.value.selection == null) {
                liveGpsProfileTracker.record(gps)
            }
        }

    fun start() {
        taskNavigationController.bind(racingFixFlow, scope)
        observeReplayEvents()
        observeReplayDisplayPoseMode()
        observeRacingNavigationEvents()
        liveGpsProfileFlow.launchIn(scope)
    }

    fun onVarioDemoReplay() {
        scope.launch {
            clearSyntheticReplayInspectionIfNeeded()
            demoReplayLauncher.startReference()
        }
    }

    fun onVarioDemoReplaySim() {
        scope.launch {
            clearSyntheticReplayInspectionIfNeeded()
            demoReplayLauncher.startRealtimeSim()
        }
    }

    fun onVarioDemoReplaySimLive() {
        scope.launch {
            clearSyntheticReplayInspectionIfNeeded()
            demoReplayLauncher.startSmoothedRealtimeSim()
        }
    }

    fun onVarioDemoReplaySim3() {
        scope.launch {
            clearSyntheticReplayInspectionIfNeeded()
            demoReplayLauncher.startLinearRealtimeSim()
        }
    }

    fun onSyntheticThermalReplay() {
        scope.launch {
            clearSyntheticReplayInspectionIfNeeded()
            syntheticThermalReplayLauncher.start(
                variant = SyntheticThermalReplayVariant.CLEAN,
                replayMode = SyntheticThermalReplayMode.CLEAN,
                displayName = "Synthetic thermal (clean)",
                successMessage = "Synthetic thermal replay started",
                failureMessage = "Synthetic thermal replay failed"
            )
        }
    }

    fun onSyntheticThermalReplayWindNoisy() {
        scope.launch {
            clearSyntheticReplayInspectionIfNeeded()
            syntheticThermalReplayLauncher.start(
                variant = SyntheticThermalReplayVariant.WIND_NOISY,
                replayMode = SyntheticThermalReplayMode.WIND_NOISY,
                displayName = "Synthetic thermal (wind-noisy)",
                successMessage = "Synthetic thermal wind-noisy replay started",
                failureMessage = "Synthetic thermal wind-noisy replay failed"
            )
        }
    }

    fun onRacingTaskReplay() {
        scope.launch {
            clearSyntheticReplayInspectionIfNeeded()
            racingReplayActive = true
            suppressRacingFixes = true
            racingReplayLogger.reset()
            try {
                val task = currentRacingTaskOrNull(taskManager.currentSnapshot())
                if (task == null) {
                    racingReplayActive = false
                    suppressRacingFixes = false
                    uiEffects.emit(
                        MapUiEffect.ShowToast(
                            "Racing replay needs a valid racing task (start, at least 2 turnpoints, finish)"
                        )
                    )
                    return@launch
                }
                racingReplaySnapshots.captureIfNeeded()
                taskNavigationController.resetNavigationState()
                taskManager.setActiveLeg(0)
                taskManager.setRacingAdvanceMode(RacingAdvanceState.Mode.AUTO)
                taskManager.setRacingAdvanceArmed(true)
                igcReplayController.setAutoStopAfterFinish(true)
                igcReplayController.stopAndWait(emitCancelledEvent = false)
                igcReplayController.setReplayMode(ReplayMode.REFERENCE, resetAfterSession = true)
                igcReplayController.setReplayCadence(RACING_REPLAY_CADENCE_PROFILE)
                igcReplayController.setSpeed(RACING_REPLAY_SPEED_MULTIPLIER)
                val log = racingReplayLogBuilder.build(
                    task = task,
                    logPoints = true
                )
                igcReplayController.loadLog(log, "Racing task replay")
                suppressRacingFixes = false
                mapStateActions.setHasInitiallyCentered(false)
                mapStateActions.setShowReturnButton(false)
                mapStateActions.setTrackingLocation(true)
                igcReplayController.play()
                uiEffects.emit(MapUiEffect.ShowToast("Racing task replay started"))
            } catch (t: Throwable) {
                suppressRacingFixes = true
                igcReplayController.stopAndWait(emitCancelledEvent = false)
                restoreRacingReplaySession()
                AppLogger.e(TAG, "Failed to start racing task replay", t)
                uiEffects.emit(MapUiEffect.ShowToast("Racing task replay failed"))
            }
        }
    }

    private fun observeReplayEvents() {
        igcReplayController.events
            .onEach { event ->
                if (syntheticReplayMode.value.isActive) {
                    when (event) {
                        is ReplayEvent.Completed -> {
                            igcReplayController.seekTo(1f)
                        }
                        is ReplayEvent.Failed -> {
                            syntheticReplayMode.value = SyntheticThermalReplayMode.NONE
                            igcReplayController.setAutoStopAfterFinish(false)
                            igcReplayController.stopAndWait(emitCancelledEvent = false)
                            demoReplaySnapshots.restoreIfCaptured()
                        }
                        ReplayEvent.Cancelled -> {
                            syntheticReplayMode.value = SyntheticThermalReplayMode.NONE
                            igcReplayController.setAutoStopAfterFinish(false)
                            demoReplaySnapshots.restoreIfCaptured()
                        }
                    }
                    return@onEach
                }
                if (demoReplaySnapshots.hasSnapshot) {
                    demoReplaySnapshots.restoreIfCaptured()
                    when (event) {
                        is ReplayEvent.Failed -> {
                            igcReplayController.setAutoStopAfterFinish(false)
                            scope.launch {
                                igcReplayController.stopAndWait(emitCancelledEvent = false)
                            }
                        }
                        ReplayEvent.Cancelled -> {
                            igcReplayController.setAutoStopAfterFinish(false)
                        }
                        is ReplayEvent.Completed -> Unit
                    }
                }
                if (racingReplayActive || racingReplaySnapshots.hasSnapshot) {
                    suppressRacingFixes = true
                    when (event) {
                        is ReplayEvent.Failed -> {
                            igcReplayController.setAutoStopAfterFinish(false)
                            igcReplayController.stopAndWait(emitCancelledEvent = false)
                        }
                        ReplayEvent.Cancelled -> {
                            igcReplayController.setAutoStopAfterFinish(false)
                        }
                        is ReplayEvent.Completed -> Unit
                    }
                    restoreRacingReplaySession()
                }
            }
            .launchIn(scope)
    }

    private suspend fun clearSyntheticReplayInspectionIfNeeded() {
        if (!syntheticReplayMode.value.isActive) {
            return
        }
        syntheticReplayMode.value = SyntheticThermalReplayMode.NONE
        igcReplayController.stopAndWait(emitCancelledEvent = false)
        demoReplaySnapshots.restoreIfCaptured()
    }

    private fun observeReplayDisplayPoseMode() {
        replaySessionState
            .onEach { session ->
                val overrideMode = demoReplaySnapshots.currentDisplayPoseOverride()
                val useRawReplay = featureFlags.useRawReplayPose &&
                    session.selection != null &&
                    session.status != SessionStatus.IDLE
                val mode = overrideMode ?: if (useRawReplay) {
                    DisplayPoseMode.RAW_REPLAY
                } else {
                    DisplayPoseMode.SMOOTHED
                }
                mapStateActions.setDisplayPoseMode(mode)
            }
            .launchIn(scope)
    }

    private fun observeRacingNavigationEvents() {
        taskNavigationController.racingEvents
            .onEach { event ->
                if (racingReplayActive) {
                    racingReplayLogger.record(event)
                }
                if (!racingEventDebouncer.shouldEmit(event)) return@onEach
                uiEffects.emit(
                    MapUiEffect.ShowToast(
                        buildRacingEventMessage(taskManager.currentSnapshot(), event)
                    )
                )
            }
            .launchIn(scope)
    }

    private fun restoreRacingReplaySession() {
        val session = replaySessionState.value
        racingReplayLogger.flush(session)
        racingReplayLogger.reset()
        racingReplaySnapshots.restoreIfCaptured()
        racingReplayActive = false
        suppressRacingFixes = false
    }


    private companion object {
        private const val TAG = "MapScreenReplayCoord"
        private const val RACING_REPLAY_SPEED_MULTIPLIER = 1.0
        private val RACING_REPLAY_CADENCE_PROFILE = ReplayCadenceProfile.LIVE_100MS
    }
}
