package com.trust3.xcpro.map

import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.map.replay.SyntheticThermalReplayMode
import com.trust3.xcpro.replay.IgcReplayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class SyntheticThermalDiagnosticsAutoStopper(
    private val scope: CoroutineScope,
    private val igcReplayController: IgcReplayController,
    private val syntheticReplayMode: MutableStateFlow<SyntheticThermalReplayMode>,
    private val emitMapCommand: (MapCommand) -> Unit,
    private val uiEffects: MutableSharedFlow<MapUiEffect>,
    private val autoStopMillis: Long = DEFAULT_AUTO_STOP_MS
) {
    private var job: Job? = null

    fun schedule(replayMode: SyntheticThermalReplayMode) {
        cancel()
        if (syntheticReplayMode.value != replayMode || autoStopMillis <= 0L) {
            return
        }
        job = scope.launch {
            delay(autoStopMillis)
            if (syntheticReplayMode.value != replayMode) {
                return@launch
            }
            job = null
            AppLogger.i(TAG, "Synthetic thermal diagnostics auto-stop mode=$replayMode after=${autoStopMillis}ms")
            igcReplayController.stopAndWait(emitCancelledEvent = true)
            emitMapCommand(MapCommand.ExportDiagnostics(DIAGNOSTIC_REASON))
            uiEffects.emit(MapUiEffect.ShowToast("Synthetic thermal diagnostics captured"))
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    companion object {
        const val DEFAULT_AUTO_STOP_MS: Long = 60_000L
        const val DIAGNOSTIC_REASON: String = "synthetic_thermal_auto_stop"
        private const val TAG = "SyntheticThermalDiagStop"
    }
}
