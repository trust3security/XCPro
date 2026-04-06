package com.example.xcpro.map.replay

import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.map.DemoReplaySnapshotController
import com.example.xcpro.map.MapStateActions
import com.example.xcpro.map.MapUiEffect
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayCadenceProfile
import com.example.xcpro.replay.ReplayMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

internal class SyntheticThermalReplayLauncher(
    private val demoReplaySnapshots: DemoReplaySnapshotController,
    private val igcReplayController: IgcReplayController,
    private val syntheticThermalReplayLogBuilder: SyntheticThermalReplayLogBuilder,
    private val syntheticReplayMode: MutableStateFlow<SyntheticThermalReplayMode>,
    private val mapStateActions: MapStateActions,
    private val uiEffects: MutableSharedFlow<MapUiEffect>
) {

    suspend fun start(
        variant: SyntheticThermalReplayVariant,
        replayMode: SyntheticThermalReplayMode,
        displayName: String,
        successMessage: String,
        failureMessage: String
    ) {
        try {
            demoReplaySnapshots.captureUiIfNeeded()
            demoReplaySnapshots.captureRuntimeReplaySettingsIfNeeded()
            igcReplayController.setSpeed(REPLAY_SPEED_MULTIPLIER)
            igcReplayController.setAutoStopAfterFinish(false)
            igcReplayController.stopAndWait(emitCancelledEvent = false)
            igcReplayController.setReplayMode(ReplayMode.REFERENCE, resetAfterSession = true)
            igcReplayController.setReplayCadence(ReplayCadenceProfile.LIVE_100MS)
            val log = syntheticThermalReplayLogBuilder.build(variant)
            igcReplayController.loadLog(log, displayName)
            syntheticReplayMode.value = replayMode
            mapStateActions.setHasInitiallyCentered(false)
            mapStateActions.setShowReturnButton(false)
            mapStateActions.setTrackingLocation(true)
            igcReplayController.play()
            uiEffects.emit(MapUiEffect.ShowToast(successMessage))
        } catch (t: Throwable) {
            syntheticReplayMode.value = SyntheticThermalReplayMode.NONE
            demoReplaySnapshots.restoreIfCaptured()
            AppLogger.e(TAG, "Failed to start $displayName replay", t)
            uiEffects.emit(MapUiEffect.ShowToast(failureMessage))
        }
    }

    private companion object {
        private const val TAG = "SyntheticThermalReplay"
        private const val REPLAY_SPEED_MULTIPLIER = 1.0
    }
}
