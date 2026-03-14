package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.convertQnhInputToHpa
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.seedQnhInputValue
import java.util.Locale

internal data class MapScreenQnhUiState(
    val currentQnhLabel: String,
    val dialogInputs: MapQnhDialogInputs,
    val openDialog: () -> Unit
)

internal fun formatCurrentQnhLabel(qnh: Double?): String {
    val resolvedQnh = qnh ?: 1013.25
    return String.format(Locale.US, "%.1f hPa", resolvedQnh)
}

@Composable
internal fun rememberMapScreenQnhUiState(
    flightDataManager: FlightDataManager,
    unitsPreferences: UnitsPreferences,
    qnhCalibrationState: QnhCalibrationState,
    onAutoCalibrateQnh: () -> Unit,
    onSetManualQnh: (Double) -> Unit
): MapScreenQnhUiState {
    val liveFlightData by flightDataManager.liveFlightDataFlow.collectAsStateWithLifecycle()
    val currentQnhLabel = remember(liveFlightData?.qnh) {
        formatCurrentQnhLabel(liveFlightData?.qnh)
    }

    var showQnhDialog by remember { mutableStateOf(false) }
    var qnhInput by remember { mutableStateOf("") }
    var qnhError by remember { mutableStateOf<String?>(null) }

    val openDialog: () -> Unit = {
        val currentQnh = liveFlightData?.qnh ?: 1013.25
        qnhInput = seedQnhInputValue(currentQnh, unitsPreferences)
        qnhError = null
        showQnhDialog = true
    }

    val dialogInputs = MapQnhDialogInputs(
        visible = showQnhDialog,
        input = qnhInput,
        error = qnhError,
        unitsPreferences = unitsPreferences,
        liveFlightData = liveFlightData,
        calibrationState = qnhCalibrationState,
        onInputChange = {
            qnhInput = it
            qnhError = null
        },
        onConfirm = { parsed ->
            onSetManualQnh(convertQnhInputToHpa(parsed, unitsPreferences))
            showQnhDialog = false
            qnhError = null
        },
        onInvalidInput = { error ->
            qnhError = error
        },
        onAutoCalibrate = {
            onAutoCalibrateQnh()
            showQnhDialog = false
            qnhError = null
        },
        onDismiss = {
            showQnhDialog = false
            qnhError = null
        }
    )

    return MapScreenQnhUiState(
        currentQnhLabel = currentQnhLabel,
        dialogInputs = dialogInputs,
        openDialog = openDialog
    )
}
