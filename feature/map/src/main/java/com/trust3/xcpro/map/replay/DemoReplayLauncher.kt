package com.trust3.xcpro.map.replay

import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.map.DemoReplaySnapshotController
import com.trust3.xcpro.map.DisplayPoseMode
import com.trust3.xcpro.map.MapStateActions
import com.trust3.xcpro.map.MapUiEffect
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.replay.IgcReplayController
import com.trust3.xcpro.replay.ReplayCadenceProfile
import com.trust3.xcpro.replay.ReplayInterpolation
import com.trust3.xcpro.replay.ReplayMode
import com.trust3.xcpro.replay.ReplayNoiseProfile
import kotlinx.coroutines.flow.MutableSharedFlow

internal class DemoReplayLauncher(
    private val demoReplaySnapshots: DemoReplaySnapshotController,
    private val igcReplayController: IgcReplayController,
    private val featureFlags: MapFeatureFlags,
    private val mapStateActions: MapStateActions,
    private val uiEffects: MutableSharedFlow<MapUiEffect>
) {

    suspend fun startRealtimeSim() {
        try {
            demoReplaySnapshots.captureUiIfNeeded()
            igcReplayController.setAutoStopAfterFinish(true)
            igcReplayController.stopAndWait(emitCancelledEvent = false)
            igcReplayController.setReplayMode(ReplayMode.REALTIME_SIM, resetAfterSession = true)
            igcReplayController.loadAsset(VARIO_DEMO_ASSET_PATH, "Vario demo (sim)")
            prepareReplayTrackingState()
            igcReplayController.play()
            uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim) replay started"))
        } catch (t: Throwable) {
            demoReplaySnapshots.restoreIfCaptured()
            AppLogger.e(TAG, "Failed to start vario demo replay (sim)", t)
            uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim) replay failed"))
        }
    }

    suspend fun startSmoothedRealtimeSim() {
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
            igcReplayController.setSpeed(SIM2_REPLAY_SPEED_MULTIPLIER)
            igcReplayController.setAutoStopAfterFinish(true)
            igcReplayController.stopAndWait(emitCancelledEvent = false)
            igcReplayController.setReplayMode(ReplayMode.REALTIME_SIM, resetAfterSession = true)
            igcReplayController.setReplayCadence(ReplayCadenceProfile(referenceStepMs = SIM2_STEP_MS, gpsStepMs = SIM2_STEP_MS))
            igcReplayController.setReplayBaroStepMs(SIM2_STEP_MS)
            igcReplayController.setReplayNoiseProfile(ReplayNoiseProfile(pressureNoiseSigmaHpa = 0.0, gpsAltitudeNoiseSigmaM = 0.0, jitterMs = 0L))
            igcReplayController.setReplayGpsAccuracyMeters(SIM2_ACCURACY_M)
            igcReplayController.setReplayInterpolation(ReplayInterpolation.CATMULL_ROM_RUNTIME)
            igcReplayController.loadAsset(VARIO_DEMO_SIM2_ASSET_PATH, "Vario demo (sim2)")
            prepareReplayTrackingState()
            igcReplayController.play()
            uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim2) replay started"))
        } catch (t: Throwable) {
            demoReplaySnapshots.restoreIfCaptured()
            AppLogger.e(TAG, "Failed to start vario demo replay (sim2)", t)
            uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim2) replay failed"))
        }
    }

    suspend fun startLinearRealtimeSim() {
        try {
            demoReplaySnapshots.captureUiIfNeeded()
            demoReplaySnapshots.captureRuntimeReplaySettingsIfNeeded()
            igcReplayController.setSpeed(SIM3_REPLAY_SPEED_MULTIPLIER)
            igcReplayController.setAutoStopAfterFinish(true)
            igcReplayController.stopAndWait(emitCancelledEvent = false)
            igcReplayController.setReplayMode(ReplayMode.REALTIME_SIM, resetAfterSession = true)
            igcReplayController.setReplayCadence(ReplayCadenceProfile(referenceStepMs = SIM3_STEP_MS, gpsStepMs = SIM3_STEP_MS))
            igcReplayController.setReplayBaroStepMs(SIM3_STEP_MS)
            igcReplayController.setReplayNoiseProfile(ReplayNoiseProfile(pressureNoiseSigmaHpa = 0.0, gpsAltitudeNoiseSigmaM = 0.0, jitterMs = 0L))
            igcReplayController.setReplayGpsAccuracyMeters(SIM3_ACCURACY_M)
            igcReplayController.setReplayInterpolation(ReplayInterpolation.LINEAR)
            igcReplayController.loadAsset(VARIO_DEMO_SIM3_ASSET_PATH, "Vario demo (sim3)")
            prepareReplayTrackingState()
            igcReplayController.play()
            uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim3) replay started"))
        } catch (t: Throwable) {
            demoReplaySnapshots.restoreIfCaptured()
            AppLogger.e(TAG, "Failed to start vario demo replay (sim3)", t)
            uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim3) replay failed"))
        }
    }

    private fun prepareReplayTrackingState() {
        mapStateActions.setHasInitiallyCentered(false)
        mapStateActions.setShowReturnButton(false)
        mapStateActions.setTrackingLocation(true)
    }

    private companion object {
        private const val TAG = "DemoReplayLauncher"
        private const val VARIO_DEMO_ASSET_PATH = "replay/vario-demo-0-10-0-60s.igc"
        private const val VARIO_DEMO_SIM2_ASSET_PATH = "replay/vario-demo-0-10-0-120s.igc"
        private const val VARIO_DEMO_SIM3_ASSET_PATH = "replay/vario-demo-const-120s.igc"
        private const val SIM2_STEP_MS = 1_000L
        private const val SIM2_ACCURACY_M = 1f
        private const val SIM2_BASELINE_BEARING_STEP_DEG = 360.0
        private const val SIM2_REPLAY_SPEED_MULTIPLIER = 1.0
        private const val SIM3_STEP_MS = 1_000L
        private const val SIM3_ACCURACY_M = 1f
        private const val SIM3_REPLAY_SPEED_MULTIPLIER = 1.0
    }
}
