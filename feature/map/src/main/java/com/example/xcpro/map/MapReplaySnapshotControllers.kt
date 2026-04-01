package com.example.xcpro.map

import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayCadenceProfile
import com.example.xcpro.replay.ReplayInterpolation
import com.example.xcpro.replay.ReplayMode
import com.example.xcpro.replay.ReplayNoiseProfile
import com.example.xcpro.replay.SessionState
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import kotlinx.coroutines.flow.StateFlow

internal class RacingReplaySnapshotController(
    private val taskManager: TaskManagerCoordinator,
    private val taskNavigationController: TaskNavigationController,
    private val igcReplayController: IgcReplayController,
    private val replaySessionState: StateFlow<SessionState>,
    private val mapStateStore: MapStateStore,
    private val mapStateActions: MapStateActions
) {
    private var snapshot: RacingReplaySnapshot? = null

    val hasSnapshot: Boolean
        get() = snapshot != null

    fun captureIfNeeded() {
        if (snapshot != null) return
        snapshot = RacingReplaySnapshot(
            selectedLeg = taskManager.currentLeg,
            navigationState = taskNavigationController.racingState.value,
            advanceSnapshot = taskManager.racingAdvanceSnapshot(),
            replayMode = igcReplayController.getReplayMode(),
            replayCadence = igcReplayController.getReplayCadence(),
            replaySpeed = replaySessionState.value.speedMultiplier,
            autoStopAfterFinish = igcReplayController.isAutoStopAfterFinishEnabled(),
            mapShellSnapshot = RacingReplayMapShellSnapshot(
                isTrackingLocation = mapStateStore.isTrackingLocation.value,
                showReturnButton = mapStateStore.showReturnButton.value,
                showRecenterButton = mapStateStore.showRecenterButton.value,
                hasInitiallyCentered = mapStateStore.hasInitiallyCentered.value
            )
        )
    }

    fun restoreIfCaptured(): Boolean {
        val captured = snapshot ?: return false
        taskNavigationController.restoreReplaySnapshot(
            selectedLeg = captured.selectedLeg,
            navigationState = captured.navigationState,
            advanceSnapshot = captured.advanceSnapshot
        )
        igcReplayController.setReplayMode(captured.replayMode, resetAfterSession = false)
        igcReplayController.setReplayCadence(captured.replayCadence)
        igcReplayController.setSpeed(captured.replaySpeed)
        igcReplayController.setAutoStopAfterFinish(captured.autoStopAfterFinish)
        mapStateActions.setTrackingLocation(captured.mapShellSnapshot.isTrackingLocation)
        mapStateActions.setShowReturnButton(captured.mapShellSnapshot.showReturnButton)
        mapStateActions.setShowRecenterButton(captured.mapShellSnapshot.showRecenterButton)
        mapStateActions.setHasInitiallyCentered(captured.mapShellSnapshot.hasInitiallyCentered)
        snapshot = null
        return true
    }

    private data class RacingReplaySnapshot(
        val selectedLeg: Int,
        val navigationState: RacingNavigationState,
        val advanceSnapshot: RacingAdvanceState.Snapshot,
        val replayMode: ReplayMode,
        val replayCadence: ReplayCadenceProfile,
        val replaySpeed: Double,
        val autoStopAfterFinish: Boolean,
        val mapShellSnapshot: RacingReplayMapShellSnapshot
    )

    private data class RacingReplayMapShellSnapshot(
        val isTrackingLocation: Boolean,
        val showReturnButton: Boolean,
        val showRecenterButton: Boolean,
        val hasInitiallyCentered: Boolean
    )
}

internal class DemoReplaySnapshotController(
    private val mapStateStore: MapStateStore,
    private val mapStateActions: MapStateActions,
    private val igcReplayController: IgcReplayController,
    private val featureFlags: MapFeatureFlags,
    private val replaySessionState: StateFlow<SessionState>
) {
    private var replayUiSnapshot: ReplayUiSnapshot? = null
    private var displayPoseOverride: DisplayPoseMode? = null
    private var replayCadenceSnapshot: ReplayCadenceProfile? = null
    private var replaySpeedSnapshot: Double? = null
    private var replayBaroStepSnapshot: Long? = null
    private var replayNoiseSnapshot: ReplayNoiseProfile? = null
    private var replayGpsAccuracySnapshot: Float? = null
    private var replayInterpolationSnapshot: ReplayInterpolation? = null
    private var replayTrackHeadingSnapshot: Boolean? = null
    private var replayBearingClampSnapshot: Double? = null
    private var replayIconHeadingSmoothingSnapshot: Boolean? = null
    private var replayRuntimeHeadingSnapshot: Boolean? = null
    private var replayRenderFrameSyncSnapshot: Boolean? = null
    private var replayFrameLogIntervalSnapshot: Long? = null

    val hasSnapshot: Boolean
        get() = replayUiSnapshot != null

    fun setDisplayPoseOverride(mode: DisplayPoseMode?) {
        displayPoseOverride = mode
    }

    fun currentDisplayPoseOverride(): DisplayPoseMode? = displayPoseOverride

    fun captureUiIfNeeded() {
        if (replayUiSnapshot != null) return
        replayUiSnapshot = ReplayUiSnapshot(
            isTrackingLocation = mapStateStore.isTrackingLocation.value,
            showReturnButton = mapStateStore.showReturnButton.value,
            showRecenterButton = mapStateStore.showRecenterButton.value,
            hasInitiallyCentered = mapStateStore.hasInitiallyCentered.value,
            savedLocation = mapStateStore.savedLocation.value,
            savedZoom = mapStateStore.savedZoom.value,
            savedBearing = mapStateStore.savedBearing.value
        )
    }

    fun captureRuntimeReplaySettingsIfNeeded() {
        if (replayCadenceSnapshot == null) {
            replayCadenceSnapshot = igcReplayController.getReplayCadence()
        }
        if (replaySpeedSnapshot == null) {
            replaySpeedSnapshot = replaySessionState.value.speedMultiplier
        }
        if (replayBaroStepSnapshot == null) {
            replayBaroStepSnapshot = igcReplayController.getReplayBaroStepMs()
        }
        if (replayNoiseSnapshot == null) {
            replayNoiseSnapshot = igcReplayController.getReplayNoiseProfile()
        }
        if (replayGpsAccuracySnapshot == null) {
            replayGpsAccuracySnapshot = igcReplayController.getReplayGpsAccuracyMeters()
        }
        if (replayInterpolationSnapshot == null) {
            replayInterpolationSnapshot = igcReplayController.getReplayInterpolation()
        }
    }

    fun captureFeatureFlagSettingsIfNeeded() {
        if (replayTrackHeadingSnapshot == null) {
            replayTrackHeadingSnapshot = featureFlags.forceReplayTrackHeading
        }
        if (replayBearingClampSnapshot == null) {
            replayBearingClampSnapshot = featureFlags.maxTrackBearingStepDeg
        }
        if (replayIconHeadingSmoothingSnapshot == null) {
            replayIconHeadingSmoothingSnapshot = featureFlags.useIconHeadingSmoothing
        }
        if (replayRuntimeHeadingSnapshot == null) {
            replayRuntimeHeadingSnapshot = featureFlags.useRuntimeReplayHeading
        }
        if (replayRenderFrameSyncSnapshot == null) {
            replayRenderFrameSyncSnapshot = featureFlags.useRenderFrameSync
        }
        if (replayFrameLogIntervalSnapshot == null) {
            replayFrameLogIntervalSnapshot = featureFlags.sim2FrameLogIntervalMs
        }
    }

    fun restoreIfCaptured() {
        val snapshot = replayUiSnapshot ?: return
        replayUiSnapshot = null
        displayPoseOverride = null

        replayTrackHeadingSnapshot?.let { featureFlags.forceReplayTrackHeading = it }
        replayTrackHeadingSnapshot = null
        replayBearingClampSnapshot?.let { featureFlags.maxTrackBearingStepDeg = it }
        replayBearingClampSnapshot = null
        replayIconHeadingSmoothingSnapshot?.let { featureFlags.useIconHeadingSmoothing = it }
        replayIconHeadingSmoothingSnapshot = null
        replayRuntimeHeadingSnapshot?.let { featureFlags.useRuntimeReplayHeading = it }
        replayRuntimeHeadingSnapshot = null
        replayRenderFrameSyncSnapshot?.let { featureFlags.useRenderFrameSync = it }
        replayRenderFrameSyncSnapshot = null
        replayFrameLogIntervalSnapshot?.let { featureFlags.sim2FrameLogIntervalMs = it }
        replayFrameLogIntervalSnapshot = null

        replayCadenceSnapshot?.let(igcReplayController::setReplayCadence)
        replayCadenceSnapshot = null
        replaySpeedSnapshot?.let(igcReplayController::setSpeed)
        replaySpeedSnapshot = null
        replayBaroStepSnapshot?.let(igcReplayController::setReplayBaroStepMs)
        replayBaroStepSnapshot = null
        replayNoiseSnapshot?.let(igcReplayController::setReplayNoiseProfile)
        replayNoiseSnapshot = null
        replayGpsAccuracySnapshot?.let(igcReplayController::setReplayGpsAccuracyMeters)
        replayGpsAccuracySnapshot = null
        replayInterpolationSnapshot?.let(igcReplayController::setReplayInterpolation)
        replayInterpolationSnapshot = null

        mapStateActions.setTrackingLocation(snapshot.isTrackingLocation)
        mapStateActions.setShowReturnButton(snapshot.showReturnButton)
        mapStateActions.setShowRecenterButton(snapshot.showRecenterButton)
        mapStateActions.setHasInitiallyCentered(snapshot.hasInitiallyCentered)
        mapStateActions.saveLocation(snapshot.savedLocation, snapshot.savedZoom, snapshot.savedBearing)
    }
}

private data class ReplayUiSnapshot(
    val isTrackingLocation: Boolean,
    val showReturnButton: Boolean,
    val showRecenterButton: Boolean,
    val hasInitiallyCentered: Boolean,
    val savedLocation: MapPoint?,
    val savedZoom: Double?,
    val savedBearing: Double?
)
