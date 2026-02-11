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
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.toSimpleRacingTask
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEventType
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
    private var racingReplayActive = false
    private var racingReplayAdvanceSnapshot: RacingAdvanceState.Snapshot? = null
    private var racingReplaySpeedSnapshot: Double? = null
    private var racingReplayCadenceSnapshot: ReplayCadenceProfile? = null
    private var lastRacingFix: RacingNavigationFix? = null
    private var demoReplaySnapshot: ReplayUiSnapshot? = null
    private var demoDisplayPoseOverride: DisplayPoseMode? = null
    private var demoReplayCadenceSnapshot: ReplayCadenceProfile? = null
    private var demoReplaySpeedSnapshot: Double? = null
    private var demoReplayBaroStepSnapshot: Long? = null
    private var demoReplayNoiseSnapshot: ReplayNoiseProfile? = null
    private var demoReplayGpsAccuracySnapshot: Float? = null
    private var demoReplayInterpolationSnapshot: ReplayInterpolation? = null
    private var demoReplayTrackHeadingSnapshot: Boolean? = null
    private var demoReplayBearingClampSnapshot: Double? = null
    private var demoReplayIconHeadingSmoothingSnapshot: Boolean? = null
    private var demoReplayRuntimeHeadingSnapshot: Boolean? = null
    private var demoReplayRenderFrameSyncSnapshot: Boolean? = null
    private var demoReplayFrameLogIntervalSnapshot: Long? = null
    private val liveGpsProfileTracker = LiveGpsProfileTracker()

    private val racingFixFlow = flightDataFlow
        .mapNotNull { data -> data?.gps?.let(RacingNavigationFixAdapter::toFix) }
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

    fun onVarioDemoReplaySimLive() {
        scope.launch {
            try {
                captureDemoReplaySnapshot()
                demoDisplayPoseOverride = DisplayPoseMode.SMOOTHED
                mapStateActions.setDisplayPoseMode(DisplayPoseMode.SMOOTHED)
                captureDemoReplayCadenceSnapshot()
                captureDemoReplaySpeedSnapshot()
                captureDemoReplayBaroStepSnapshot()
                captureDemoReplayNoiseSnapshot()
                captureDemoReplayGpsAccuracySnapshot()
                captureDemoReplayInterpolationSnapshot()
                captureDemoReplayTrackHeadingSnapshot()
                captureDemoReplayBearingClampSnapshot()
                captureDemoReplayIconHeadingSmoothingSnapshot()
                captureDemoReplayRuntimeHeadingSnapshot()
                captureDemoReplayRenderFrameSyncSnapshot()
                captureDemoReplayFrameLogIntervalSnapshot()
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
                restoreDemoReplaySnapshot()
                Log.e(TAG, "Failed to start vario demo replay (sim2)", t)
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim2) replay failed"))
            }
        }
    }

    fun onVarioDemoReplaySim3() {
        scope.launch {
            try {
                captureDemoReplaySnapshot()
                captureDemoReplayCadenceSnapshot()
                captureDemoReplaySpeedSnapshot()
                captureDemoReplayBaroStepSnapshot()
                captureDemoReplayNoiseSnapshot()
                captureDemoReplayGpsAccuracySnapshot()
                captureDemoReplayInterpolationSnapshot()
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
                restoreDemoReplaySnapshot()
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
                val overrideMode = demoDisplayPoseOverride
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

    private fun captureDemoReplayCadenceSnapshot() {
        if (demoReplayCadenceSnapshot != null) return
        demoReplayCadenceSnapshot = igcReplayController.getReplayCadence()
    }

    private fun captureDemoReplaySpeedSnapshot() {
        if (demoReplaySpeedSnapshot != null) return
        demoReplaySpeedSnapshot = replaySessionState.value.speedMultiplier
    }

    private fun captureDemoReplayBaroStepSnapshot() {
        if (demoReplayBaroStepSnapshot != null) return
        demoReplayBaroStepSnapshot = igcReplayController.getReplayBaroStepMs()
    }

    private fun captureDemoReplayGpsAccuracySnapshot() {
        if (demoReplayGpsAccuracySnapshot != null) return
        demoReplayGpsAccuracySnapshot = igcReplayController.getReplayGpsAccuracyMeters()
    }

    private fun captureDemoReplayNoiseSnapshot() {
        if (demoReplayNoiseSnapshot != null) return
        demoReplayNoiseSnapshot = igcReplayController.getReplayNoiseProfile()
    }

    private fun captureDemoReplayTrackHeadingSnapshot() {
        if (demoReplayTrackHeadingSnapshot != null) return
        demoReplayTrackHeadingSnapshot = featureFlags.forceReplayTrackHeading
    }

    private fun captureDemoReplayInterpolationSnapshot() {
        if (demoReplayInterpolationSnapshot != null) return
        demoReplayInterpolationSnapshot = igcReplayController.getReplayInterpolation()
    }

    private fun captureDemoReplayBearingClampSnapshot() {
        if (demoReplayBearingClampSnapshot != null) return
        demoReplayBearingClampSnapshot = featureFlags.maxTrackBearingStepDeg
    }

    private fun captureDemoReplayIconHeadingSmoothingSnapshot() {
        if (demoReplayIconHeadingSmoothingSnapshot != null) return
        demoReplayIconHeadingSmoothingSnapshot = featureFlags.useIconHeadingSmoothing
    }

    private fun captureDemoReplayRuntimeHeadingSnapshot() {
        if (demoReplayRuntimeHeadingSnapshot != null) return
        demoReplayRuntimeHeadingSnapshot = featureFlags.useRuntimeReplayHeading
    }

    private fun captureDemoReplayRenderFrameSyncSnapshot() {
        if (demoReplayRenderFrameSyncSnapshot != null) return
        demoReplayRenderFrameSyncSnapshot = featureFlags.useRenderFrameSync
    }

    private fun captureDemoReplayFrameLogIntervalSnapshot() {
        if (demoReplayFrameLogIntervalSnapshot != null) return
        demoReplayFrameLogIntervalSnapshot = featureFlags.sim2FrameLogIntervalMs
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
        val task = taskManager.currentTask.toSimpleRacingTask()
        if (task.waypoints.size < 2) {
            return null
        }
        return task
    }

    private fun buildRacingEventMessage(event: RacingNavigationEvent): String {
        val waypointName = taskManager.currentTask.waypoints
            .getOrNull(event.fromLegIndex)
            ?.title
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
        demoDisplayPoseOverride = null
        demoReplayTrackHeadingSnapshot?.let { featureFlags.forceReplayTrackHeading = it }
        demoReplayTrackHeadingSnapshot = null
        demoReplayBearingClampSnapshot?.let { featureFlags.maxTrackBearingStepDeg = it }
        demoReplayBearingClampSnapshot = null
        demoReplayIconHeadingSmoothingSnapshot?.let { featureFlags.useIconHeadingSmoothing = it }
        demoReplayIconHeadingSmoothingSnapshot = null
        demoReplayRuntimeHeadingSnapshot?.let { featureFlags.useRuntimeReplayHeading = it }
        demoReplayRuntimeHeadingSnapshot = null
        demoReplayRenderFrameSyncSnapshot?.let { featureFlags.useRenderFrameSync = it }
        demoReplayRenderFrameSyncSnapshot = null
        demoReplayFrameLogIntervalSnapshot?.let { featureFlags.sim2FrameLogIntervalMs = it }
        demoReplayFrameLogIntervalSnapshot = null
        demoReplayCadenceSnapshot?.let { igcReplayController.setReplayCadence(it) }
        demoReplayCadenceSnapshot = null
        demoReplaySpeedSnapshot?.let { igcReplayController.setSpeed(it) }
        demoReplaySpeedSnapshot = null
        demoReplayBaroStepSnapshot?.let { igcReplayController.setReplayBaroStepMs(it) }
        demoReplayBaroStepSnapshot = null
        demoReplayNoiseSnapshot?.let { igcReplayController.setReplayNoiseProfile(it) }
        demoReplayNoiseSnapshot = null
        demoReplayGpsAccuracySnapshot?.let { igcReplayController.setReplayGpsAccuracyMeters(it) }
        demoReplayGpsAccuracySnapshot = null
        demoReplayInterpolationSnapshot?.let { igcReplayController.setReplayInterpolation(it) }
        demoReplayInterpolationSnapshot = null
        mapStateActions.setTrackingLocation(snapshot.isTrackingLocation)
        mapStateActions.setShowReturnButton(snapshot.showReturnButton)
        mapStateActions.setShowRecenterButton(snapshot.showRecenterButton)
        mapStateActions.setHasInitiallyCentered(snapshot.hasInitiallyCentered)
        mapStateActions.saveLocation(snapshot.savedLocation, snapshot.savedZoom, snapshot.savedBearing)
    }

    private companion object {
        private const val TAG = "MapScreenReplayCoord"
        private const val VARIO_DEMO_ASSET_PATH = "replay/vario-demo-0-10-0-60s.igc"
        private const val VARIO_DEMO_SIM2_ASSET_PATH = "replay/vario-demo-0-10-0-120s.igc"
        private const val VARIO_DEMO_SIM3_ASSET_PATH = "replay/vario-demo-const-120s.igc"
        private const val RACING_REPLAY_SPEED_MULTIPLIER = 1.0
        private val RACING_REPLAY_CADENCE_PROFILE = ReplayCadenceProfile.LIVE_100MS
        private const val SIM2_FALLBACK_GPS_STEP_MS = 1_000L
        private const val SIM2_FALLBACK_GPS_ACCURACY_M = 5f
        private const val SIM2_BASELINE_STEP_MS = 1_000L
        private const val SIM2_BASELINE_ACCURACY_M = 1f
        private const val SIM2_BASELINE_BEARING_STEP_DEG = 360.0
        private const val SIM2_REPLAY_SPEED_MULTIPLIER = 1.0
        private const val SIM3_STEP_MS = 1_000L
        private const val SIM3_ACCURACY_M = 1f
        private const val SIM3_REPLAY_SPEED_MULTIPLIER = 1.0
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
