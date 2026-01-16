package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayCadenceProfile
import com.example.xcpro.replay.ReplayMode
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEventType
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class MapScreenReplayCoordinator(
    private val taskManager: TaskManagerCoordinator,
    private val taskNavigationController: TaskNavigationController,
    private val flightDataRepository: FlightDataRepository,
    private val igcReplayController: IgcReplayController,
    private val racingReplayLogBuilder: RacingReplayLogBuilder,
    private val mapStateStore: MapStateStore,
    private val mapStateActions: MapStateActions,
    private val uiEffects: MutableSharedFlow<MapUiEffect>,
    private val replaySessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope
) {

    private val racingEventDebouncer = RacingNavigationEventDebouncer()
    private val racingReplayLogger = RacingReplayEventLogger()
    private var racingReplayActive = false
    private var racingReplayAdvanceSnapshot: RacingAdvanceState.Snapshot? = null
    private var racingReplaySpeedSnapshot: Double? = null
    private var racingReplayCadenceSnapshot: ReplayCadenceProfile? = null
    private var lastRacingFix: RacingNavigationFix? = null
    private var demoReplaySnapshot: ReplayUiSnapshot? = null

    private val racingFixFlow = flightDataRepository.flightData
        .mapNotNull { data -> data?.gps?.let(RacingNavigationFixAdapter::toFix) }
        .onEach { fix -> lastRacingFix = fix }

    fun start() {
        taskNavigationController.bind(racingFixFlow, scope)
        observeReplayEvents()
        observeReplayDisplayPoseMode()
        observeRacingNavigationEvents()
    }

    fun onVarioDemoReplay() {
        scope.launch {
            try {
                captureDemoReplaySnapshot()
                igcReplayController.setAutoStopAfterFinish(true)
                Log.i(TAG, "VARIO_DEMO start asset=$VARIO_DEMO_ASSET_PATH")
                igcReplayController.stopAndWait(emitCancelledEvent = false)
                igcReplayController.setReplayMode(ReplayMode.REFERENCE, resetAfterSession = true)
                igcReplayController.loadAsset(VARIO_DEMO_ASSET_PATH, "Vario demo")
                mapStateActions.setHasInitiallyCentered(false)
                mapStateActions.setShowReturnButton(false)
                mapStateActions.setTrackingLocation(true)
                igcReplayController.play()
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo replay started"))
            } catch (t: Throwable) {
                restoreDemoReplaySnapshot()
                Log.e(TAG, "Failed to start vario demo replay", t)
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo replay failed"))
            }
        }
    }

    fun onVarioDemoReplaySim() {
        scope.launch {
            try {
                captureDemoReplaySnapshot()
                igcReplayController.setAutoStopAfterFinish(true)
                Log.i(TAG, "VARIO_DEMO_SIM start asset=$VARIO_DEMO_ASSET_PATH")
                igcReplayController.stopAndWait(emitCancelledEvent = false)
                igcReplayController.setReplayMode(ReplayMode.REALTIME_SIM, resetAfterSession = true)
                igcReplayController.loadAsset(VARIO_DEMO_ASSET_PATH, "Vario demo (sim)")
                mapStateActions.setHasInitiallyCentered(false)
                mapStateActions.setShowReturnButton(false)
                mapStateActions.setTrackingLocation(true)
                igcReplayController.play()
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim) replay started"))
            } catch (t: Throwable) {
                restoreDemoReplaySnapshot()
                Log.e(TAG, "Failed to start vario demo replay (sim)", t)
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim) replay failed"))
            }
        }
    }

    fun onRacingTaskReplay() {
        scope.launch {
            racingReplayActive = true
            racingReplayLogger.reset()
            try {
                val task = currentRacingTaskOrNull()
                if (task == null) {
                    racingReplayActive = false
                    uiEffects.emit(
                        MapUiEffect.ShowToast("Racing replay needs an active racing task with at least 2 waypoints")
                    )
                    return@launch
                }
                captureRacingReplayAdvanceSnapshot()
                captureRacingReplaySpeedSnapshot()
                captureRacingReplayCadenceSnapshot()
                taskNavigationController.resetNavigationState()
                taskManager.setActiveLeg(0)
                taskNavigationController.setAdvanceMode(RacingAdvanceState.Mode.AUTO)
                taskNavigationController.setAdvanceArmed(true)
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
                mapStateActions.setHasInitiallyCentered(false)
                mapStateActions.setShowReturnButton(false)
                mapStateActions.setTrackingLocation(true)
                igcReplayController.play()
                uiEffects.emit(MapUiEffect.ShowToast("Racing task replay started"))
            } catch (t: Throwable) {
                restoreRacingReplayAdvanceSnapshot()
                restoreRacingReplaySpeedSnapshot()
                restoreRacingReplayCadenceSnapshot()
                racingReplayActive = false
                Log.e(TAG, "Failed to start racing task replay", t)
                uiEffects.emit(MapUiEffect.ShowToast("Racing task replay failed"))
            }
        }
    }

    private fun observeReplayEvents() {
        igcReplayController.events
            .onEach { event ->
                if (demoReplaySnapshot != null) {
                    restoreDemoReplaySnapshot()
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
                if (racingReplayActive) {
                    val session = replaySessionState.value
                    racingReplayLogger.flush(session)
                    racingReplayLogger.reset()
                    restoreRacingReplayAdvanceSnapshot()
                    restoreRacingReplaySpeedSnapshot()
                    restoreRacingReplayCadenceSnapshot()
                    racingReplayActive = false
                }
            }
            .launchIn(scope)
    }

    private fun observeReplayDisplayPoseMode() {
        replaySessionState
            .onEach { session ->
                val useRawReplay = MapFeatureFlags.useRawReplayPose &&
                    session.selection != null &&
                    session.status != SessionStatus.IDLE
                val mode = if (useRawReplay) DisplayPoseMode.RAW_REPLAY else DisplayPoseMode.SMOOTHED
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
                if (BuildConfig.DEBUG) {
                    val fix = lastRacingFix
                    val fixLabel = if (fix != null) {
                        "fix=${fix.lat},${fix.lon} fixT=${fix.timestampMillis}"
                    } else {
                        "fix=unknown"
                    }
                    Log.i(
                        TAG,
                        "RACING_EVENT type=${event.type} from=${event.fromLegIndex} " +
                            "to=${event.toLegIndex} t=${event.timestampMillis} $fixLabel"
                    )
                }
                if (!racingEventDebouncer.shouldEmit(event)) return@onEach
                uiEffects.emit(MapUiEffect.ShowToast(buildRacingEventMessage(event)))
            }
            .launchIn(scope)
    }

    private fun captureRacingReplayAdvanceSnapshot() {
        if (racingReplayAdvanceSnapshot != null) return
        racingReplayAdvanceSnapshot = taskNavigationController.snapshot()
    }

    private fun restoreRacingReplayAdvanceSnapshot() {
        val snapshot = racingReplayAdvanceSnapshot ?: return
        taskNavigationController.setAdvanceMode(snapshot.mode)
        taskNavigationController.setAdvanceArmed(snapshot.isArmed)
        racingReplayAdvanceSnapshot = null
    }

    private fun captureRacingReplaySpeedSnapshot() {
        if (racingReplaySpeedSnapshot != null) return
        racingReplaySpeedSnapshot = replaySessionState.value.speedMultiplier
    }

    private fun restoreRacingReplaySpeedSnapshot() {
        val snapshot = racingReplaySpeedSnapshot ?: return
        igcReplayController.setSpeed(snapshot)
        racingReplaySpeedSnapshot = null
    }

    private fun captureRacingReplayCadenceSnapshot() {
        if (racingReplayCadenceSnapshot != null) return
        racingReplayCadenceSnapshot = igcReplayController.getReplayCadence()
    }

    private fun restoreRacingReplayCadenceSnapshot() {
        val snapshot = racingReplayCadenceSnapshot ?: return
        igcReplayController.setReplayCadence(snapshot)
        racingReplayCadenceSnapshot = null
    }

    private fun currentRacingTaskOrNull(): SimpleRacingTask? {
        if (taskManager.taskType != TaskType.RACING) {
            return null
        }
        val task = taskManager.getRacingTaskManager().currentRacingTask
        if (task.waypoints.size < 2) {
            return null
        }
        return task
    }

    private fun buildRacingEventMessage(event: RacingNavigationEvent): String {
        val task = taskManager.getRacingTaskManager().currentRacingTask
        val reachedIndex = event.fromLegIndex
        val waypointName = task.waypoints.getOrNull(reachedIndex)?.title
        return when (event.type) {
            RacingNavigationEventType.START ->
                if (waypointName != null) "Start crossed: $waypointName" else "Start crossed"
            RacingNavigationEventType.TURNPOINT ->
                if (waypointName != null) "Turnpoint reached: $waypointName" else "Turnpoint reached"
            RacingNavigationEventType.FINISH ->
                if (waypointName != null) "Finish reached: $waypointName" else "Finish reached"
        }
    }

    private fun captureDemoReplaySnapshot() {
        if (demoReplaySnapshot != null) return
        demoReplaySnapshot = ReplayUiSnapshot(
            isTrackingLocation = mapStateStore.isTrackingLocation.value,
            showReturnButton = mapStateStore.showReturnButton.value,
            showRecenterButton = mapStateStore.showRecenterButton.value,
            hasInitiallyCentered = mapStateStore.hasInitiallyCentered.value,
            savedLocation = mapStateStore.savedLocation.value,
            savedZoom = mapStateStore.savedZoom.value,
            savedBearing = mapStateStore.savedBearing.value
        )
    }

    private fun restoreDemoReplaySnapshot() {
        val snapshot = demoReplaySnapshot ?: return
        demoReplaySnapshot = null
        mapStateActions.setTrackingLocation(snapshot.isTrackingLocation)
        mapStateActions.setShowReturnButton(snapshot.showReturnButton)
        mapStateActions.setShowRecenterButton(snapshot.showRecenterButton)
        mapStateActions.setHasInitiallyCentered(snapshot.hasInitiallyCentered)
        mapStateActions.saveLocation(snapshot.savedLocation, snapshot.savedZoom, snapshot.savedBearing)
    }

    private companion object {
        private const val TAG = "MapScreenReplayCoord"
        private const val VARIO_DEMO_ASSET_PATH = "replay/vario-demo-0-10-0-60s.igc"
        private const val RACING_REPLAY_SPEED_MULTIPLIER = 1.0
        private val RACING_REPLAY_CADENCE_PROFILE = ReplayCadenceProfile.LIVE_100MS
    }
}

private data class ReplayUiSnapshot(
    val isTrackingLocation: Boolean,
    val showReturnButton: Boolean,
    val showRecenterButton: Boolean,
    val hasInitiallyCentered: Boolean,
    val savedLocation: MapStateStore.MapPoint?,
    val savedZoom: Double?,
    val savedBearing: Double?
)
