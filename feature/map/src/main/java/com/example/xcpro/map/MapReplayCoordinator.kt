package com.example.xcpro.map
/**
 * Replay action coordinator for MapScreenViewModel.
 * Invariants: state writes go through MapStateActions; UI effects flow via MapUiEffect.
 */


import android.net.Uri
import android.util.Log
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles replay actions for the map screen without exposing map state mutations.
 * Invariants: state writes go through MapStateActions; UI effects are emitted via MapUiEffect.
 */
internal class MapReplayCoordinator(
    private val sessionState: StateFlow<SessionState>,
    private val igcReplayController: IgcReplayController,
    private val stateActions: MapStateActions,
    private val uiEffects: MutableSharedFlow<MapUiEffect>,
    private val scope: CoroutineScope
) {

    fun onReplayPlayPause() {
        scope.launch {
            when (sessionState.value.status) {
                SessionStatus.PLAYING -> igcReplayController.pause()
                SessionStatus.PAUSED -> igcReplayController.play()
                SessionStatus.IDLE -> {
                    val selection = sessionState.value.selection
                    if (selection == null) {
                        try {
                            igcReplayController.loadAsset(DEV_REPLAY_ASSET_PATH)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Auto replay failed", t)
                            uiEffects.emit(MapUiEffect.ShowToast("IGC replay asset missing"))
                            return@launch
                        }
                    }
                    stateActions.setHasInitiallyCentered(false)
                    stateActions.setShowReturnButton(false)
                    stateActions.setTrackingLocation(true)
                    igcReplayController.play()
                }
            }
        }
    }

    fun onReplayStop() {
        igcReplayController.stop()
    }

    fun onReplaySpeedChanged(multiplier: Double) {
        scope.launch {
            if (!ensureReplaySelectionLoaded()) return@launch
            igcReplayController.setSpeed(multiplier)
        }
    }

    fun onReplaySeek(progress: Float) {
        scope.launch {
            if (!ensureReplaySelectionLoaded()) return@launch
            igcReplayController.seekTo(progress)
        }
    }

    fun onReplayDevAutoplay() {
        scope.launch {
            try {
                igcReplayController.loadAsset(DEV_REPLAY_ASSET_PATH)
                igcReplayController.play()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start dev replay", t)
            }
        }
    }

    fun onVarioDemoReplay() {
        scope.launch {
            try {
                Log.i(TAG, "VARIO_DEMO start asset=$VARIO_DEMO_ASSET_PATH")
                igcReplayController.stop()
                igcReplayController.loadAsset(VARIO_DEMO_ASSET_PATH, "Vario demo")
                stateActions.setHasInitiallyCentered(false)
                stateActions.setShowReturnButton(false)
                stateActions.setTrackingLocation(true)
                igcReplayController.play()
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo replay started"))
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start vario demo replay", t)
                uiEffects.emit(MapUiEffect.ShowToast("Vario demo replay failed"))
            }
        }
    }

    /**
     * Launches an IGC replay from a user-selected SAF Uri.
     * AI-NOTE: Use NonCancellable so a quick scope cancellation doesn't misreport a load as failed.
     */
    fun onReplayFileChosen(uri: Uri, displayName: String?) {
        scope.launch {
            Log.i(TAG, "REPLAY_FILE chosen uri=$uri name=$displayName")
            uiEffects.emit(MapUiEffect.ShowToast("Loading replay..."))
            val loadResult = runCatching {
                withContext(NonCancellable) { igcReplayController.loadFile(uri, displayName) }
            }

            loadResult
                .onSuccess {
                    Log.i(TAG, "REPLAY_FILE load success uri=$uri")
                    stateActions.setHasInitiallyCentered(false)
                    stateActions.setShowReturnButton(false)
                    stateActions.setTrackingLocation(true)
                    Log.i(TAG, "REPLAY_FILE starting play uri=$uri")
                    igcReplayController.play()
                }
                .onFailure { t ->
                    if (t is CancellationException) {
                        Log.w(TAG, "Replay load cancelled after prepare uri=$uri", t)
                    } else {
                        Log.e(TAG, "Replay load failed uri=$uri", t)
                        uiEffects.emit(
                            MapUiEffect.ShowToast("Replay failed: ${t.message ?: "Unknown error"}")
                        )
                    }
                }
        }
    }

    private suspend fun ensureReplaySelectionLoaded(): Boolean {
        if (sessionState.value.selection != null) return true
        return try {
            igcReplayController.loadAsset(DEV_REPLAY_ASSET_PATH)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Replay asset missing", t)
            uiEffects.emit(MapUiEffect.ShowToast("IGC replay asset missing"))
            false
        }
    }

    private companion object {
        private const val TAG = "MapReplayCoordinator"
        private const val DEV_REPLAY_ASSET_PATH = "replay/2025-11-11.igc"
        private const val VARIO_DEMO_ASSET_PATH = "replay/vario-demo-0-10-0-30s.igc"
    }
}
