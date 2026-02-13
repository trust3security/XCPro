package com.example.xcpro.map

import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.domain.toUserMessage
import com.example.xcpro.qnh.CalibrateQnhUseCase
import com.example.xcpro.qnh.QnhCalibrationFailureReason
import com.example.xcpro.qnh.QnhCalibrationResult
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MapScreenWaypointQnhCoordinator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MapUiState>,
    private val uiEffects: MutableSharedFlow<MapUiEffect>,
    private val mapWaypointsUseCase: MapWaypointsUseCase,
    private val qnhUseCase: QnhUseCase,
    private val calibrateQnhUseCase: CalibrateQnhUseCase
) {
    fun loadWaypoints() {
        scope.launch {
            uiState.update { it.copy(isLoadingWaypoints = true, waypointError = null) }
            val result = runCatching { mapWaypointsUseCase.loadWaypoints() }
            result
                .onSuccess { waypoints ->
                    uiState.update {
                        it.copy(
                            waypoints = waypoints,
                            isLoadingWaypoints = false,
                            waypointError = if (waypoints.isEmpty()) MapWaypointError.Empty else null
                        )
                    }
                }
                .onFailure { error ->
                    val failure = MapWaypointError.LoadFailed(error)
                    uiState.update {
                        it.copy(
                            waypoints = emptyList(),
                            isLoadingWaypoints = false,
                            waypointError = failure
                        )
                    }
                    uiEffects.tryEmit(
                        MapUiEffect.ShowToast(
                            failure.toUserMessage("Failed to load waypoints")
                        )
                    )
                }
        }
    }

    fun onAutoCalibrateQnh() {
        scope.launch {
            when (val result = calibrateQnhUseCase.execute()) {
                is QnhCalibrationResult.Success -> {
                    val label = String.format(Locale.US, "%.1f", result.value.hpa)
                    uiEffects.emit(MapUiEffect.ShowToast("QNH updated to $label hPa"))
                }
                is QnhCalibrationResult.Failure -> {
                    uiEffects.emit(MapUiEffect.ShowToast(result.reason.toUserMessage()))
                }
            }
        }
    }

    fun onSetManualQnh(hpa: Double) {
        scope.launch {
            qnhUseCase.setManualQnh(hpa)
            val label = String.format(Locale.US, "%.1f", hpa)
            uiEffects.emit(MapUiEffect.ShowToast("QNH set to $label hPa"))
        }
    }
}

private fun QnhCalibrationFailureReason.toUserMessage(): String = when (this) {
    QnhCalibrationFailureReason.REPLAY_MODE -> "Auto calibration disabled in replay"
    QnhCalibrationFailureReason.ALREADY_RUNNING -> "Auto calibration already running"
    QnhCalibrationFailureReason.TIMEOUT -> "Auto calibration timed out"
    QnhCalibrationFailureReason.INVALID_QNH -> "Auto calibration produced invalid QNH"
    QnhCalibrationFailureReason.MISSING_SENSORS -> "Auto calibration needs GPS and baro"
    QnhCalibrationFailureReason.UNKNOWN -> "Auto calibration failed"
}
