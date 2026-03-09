package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayCadenceProfile
import com.example.xcpro.replay.ReplayInterpolation
import com.example.xcpro.replay.ReplayMode
import com.example.xcpro.replay.ReplayNoiseProfile
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.example.xcpro.sensors.CompleteFlightData
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
        taskNavigationController = taskNavigationController,
        igcReplayController = igcReplayController,
        replaySessionState = replaySessionState
    )
    private val demoReplaySnapshots = DemoReplaySnapshotController(
        mapStateStore = mapStateStore,
        mapStateActions = mapStateActions,
        igcReplayController = igcReplayController,
        featureFlags = featureFlags,
        replaySessionState = replaySessionState
    )
    private var racingReplayActive = false
    private var lastRacingFix: RacingNavigationFix? = null
    private val liveGpsProfileTracker = LiveGpsProfileTracker()

    private val racingFixFlow = flightDataFlow
        .mapNotNull { data -> data?.let(RacingNavigationFixAdapter::toFix) }
        .onEach { fix -> lastRacingFix = fix }
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
            try {
                demoReplaySnapshots.captureUiIfNeeded()
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
                demoReplaySnapshots.restoreIfCaptured()
                Log.e(TAG, "Failed to start vario demo replay", t)
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo replay failed"))
            }
        }
    }

    fun onVarioDemoReplaySim() {
        scope.launch {
            try {
                demoReplaySnapshots.captureUiIfNeeded()
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
                demoReplaySnapshots.restoreIfCaptured()
                Log.e(TAG, "Failed to start vario demo replay (sim)", t)
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim) replay failed"))
            }
        }
    }

    fun onVarioDemoReplaySimLive() {
        scope.launch {
            try {
                demoReplaySnapshots.captureUiIfNeeded()
                demoReplaySnapshots.setDisplayPoseOverride(DisplayPoseMode.SMOOTHED)
                mapStateActions.setDisplayPoseMode(DisplayPoseMode.SMOOTHED)
                demoReplaySnapshots.captureRuntimeReplaySettingsIfNeeded()
                demoReplaySnapshots.captureFeatureFlagSettingsIfNeeded()
                featureFlags.forceReplayTrackHeading = true
                featureFlags.maxTrackBearingStepDeg = SIM2_BASELINE_BEARING_STEP_DEG
                featureFlags.useIconHeadingSmoothing = false
                featureFlags.useRuntimeReplayHeading = true
                featureFlags.useRenderFrameSync = true
                featureFlags.sim2FrameLogIntervalMs = 0L
                val cadenceMs = SIM2_BASELINE_STEP_MS
                val accuracyM = SIM2_BASELINE_ACCURACY_M
                igcReplayController.setSpeed(SIM2_REPLAY_SPEED_MULTIPLIER)
                igcReplayController.setAutoStopAfterFinish(true)
                Log.i(TAG, "VARIO_DEMO_SIM_LIVE start asset=$VARIO_DEMO_SIM2_ASSET_PATH")
                igcReplayController.stopAndWait(emitCancelledEvent = false)
                igcReplayController.setReplayMode(ReplayMode.REALTIME_SIM, resetAfterSession = true)
                igcReplayController.setReplayCadence(
                    ReplayCadenceProfile(
                        referenceStepMs = cadenceMs,
                        gpsStepMs = cadenceMs
                    )
                )
                igcReplayController.setReplayBaroStepMs(cadenceMs)
                igcReplayController.setReplayNoiseProfile(
                    ReplayNoiseProfile(
                        pressureNoiseSigmaHpa = 0.0,
                        gpsAltitudeNoiseSigmaM = 0.0,
                        jitterMs = 0L
                    )
                )
                igcReplayController.setReplayGpsAccuracyMeters(accuracyM)
                igcReplayController.setReplayInterpolation(ReplayInterpolation.CATMULL_ROM_RUNTIME)
                igcReplayController.loadAsset(VARIO_DEMO_SIM2_ASSET_PATH, "Vario demo (sim2)")
                mapStateActions.setHasInitiallyCentered(false)
                mapStateActions.setShowReturnButton(false)
                mapStateActions.setTrackingLocation(true)
                igcReplayController.play()
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim2) replay started"))
            } catch (t: Throwable) {
                demoReplaySnapshots.restoreIfCaptured()
                Log.e(TAG, "Failed to start vario demo replay (sim2)", t)
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim2) replay failed"))
            }
        }
    }

    fun onVarioDemoReplaySim3() {
        scope.launch {
            try {
                demoReplaySnapshots.captureUiIfNeeded()
                demoReplaySnapshots.captureRuntimeReplaySettingsIfNeeded()
                igcReplayController.setSpeed(SIM3_REPLAY_SPEED_MULTIPLIER)
                igcReplayController.setAutoStopAfterFinish(true)
                Log.i(TAG, "VARIO_DEMO_SIM3 start asset=$VARIO_DEMO_SIM3_ASSET_PATH")
                igcReplayController.stopAndWait(emitCancelledEvent = false)
                igcReplayController.setReplayMode(ReplayMode.REALTIME_SIM, resetAfterSession = true)
                igcReplayController.setReplayCadence(
                    ReplayCadenceProfile(
                        referenceStepMs = SIM3_STEP_MS,
                        gpsStepMs = SIM3_STEP_MS
                    )
                )
                igcReplayController.setReplayBaroStepMs(SIM3_STEP_MS)
                igcReplayController.setReplayNoiseProfile(
                    ReplayNoiseProfile(
                        pressureNoiseSigmaHpa = 0.0,
                        gpsAltitudeNoiseSigmaM = 0.0,
                        jitterMs = 0L
                    )
                )
                igcReplayController.setReplayGpsAccuracyMeters(SIM3_ACCURACY_M)
                igcReplayController.setReplayInterpolation(ReplayInterpolation.LINEAR)
                igcReplayController.loadAsset(VARIO_DEMO_SIM3_ASSET_PATH, "Vario demo (sim3)")
                mapStateActions.setHasInitiallyCentered(false)
                mapStateActions.setShowReturnButton(false)
                mapStateActions.setTrackingLocation(true)
                igcReplayController.play()
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim3) replay started"))
            } catch (t: Throwable) {
                demoReplaySnapshots.restoreIfCaptured()
                Log.e(TAG, "Failed to start vario demo replay (sim3)", t)
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim3) replay failed"))
            }
        }
    }

    fun onRacingTaskReplay() {
        scope.launch {
            racingReplayActive = true
            racingReplayLogger.reset()
            try {
                val task = currentRacingTaskOrNull(taskManager)
                if (task == null) {
                    racingReplayActive = false
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
                racingReplaySnapshots.restore()
                racingReplayActive = false
                Log.e(TAG, "Failed to start racing task replay", t)
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
                if (racingReplayActive) {
                    val session = replaySessionState.value
                    racingReplayLogger.flush(session)
                    racingReplayLogger.reset()
                    racingReplaySnapshots.restore()
                    racingReplayActive = false
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
                uiEffects.emit(MapUiEffect.ShowToast(buildRacingEventMessage(taskManager, event)))
            }
            .launchIn(scope)
    }


    private companion object {
        private const val TAG = "MapScreenReplayCoord"
        private const val VARIO_DEMO_ASSET_PATH = "replay/vario-demo-0-10-0-60s.igc"
        private const val VARIO_DEMO_SIM2_ASSET_PATH = "replay/vario-demo-0-10-0-120s.igc"
        private const val VARIO_DEMO_SIM3_ASSET_PATH = "replay/vario-demo-const-120s.igc"
        private const val RACING_REPLAY_SPEED_MULTIPLIER = 1.0
        private val RACING_REPLAY_CADENCE_PROFILE = ReplayCadenceProfile.LIVE_100MS
        private const val SIM2_BASELINE_STEP_MS = 1_000L
        private const val SIM2_BASELINE_ACCURACY_M = 1f
        private const val SIM2_BASELINE_BEARING_STEP_DEG = 360.0
        private const val SIM2_REPLAY_SPEED_MULTIPLIER = 1.0
        private const val SIM3_STEP_MS = 1_000L
        private const val SIM3_ACCURACY_M = 1f
        private const val SIM3_REPLAY_SPEED_MULTIPLIER = 1.0
    }
}
