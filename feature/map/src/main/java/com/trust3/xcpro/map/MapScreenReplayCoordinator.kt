package com.trust3.xcpro.map

import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.replay.DemoReplayLauncher
import com.trust3.xcpro.map.replay.RacingReplayLogBuilder
import com.trust3.xcpro.replay.IgcReplayController
import com.trust3.xcpro.replay.ReplayCadenceProfile
import com.trust3.xcpro.replay.ReplayMode
import com.trust3.xcpro.replay.ReplayEvent
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.replay.SessionStatus
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.tasks.TaskManagerCoordinator
import com.trust3.xcpro.tasks.TaskNavigationController
import com.trust3.xcpro.tasks.racing.navigation.RacingAdvanceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val featureFlags: MapFeatureFlags,
    private val mapStateStore: MapStateStore,
    private val mapStateActions: MapStateActions,
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

    fun onVarioDemoReplaySim() {
        scope.launch {
            demoReplayLauncher.startRealtimeSim()
        }
    }

    fun onVarioDemoReplaySimLive() {
        scope.launch {
            demoReplayLauncher.startSmoothedRealtimeSim()
        }
    }

    fun onVarioDemoReplaySim3() {
        scope.launch {
            demoReplayLauncher.startLinearRealtimeSim()
        }
    }

    fun onRacingTaskReplay() {
        scope.launch {
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
