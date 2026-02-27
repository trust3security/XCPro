package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.components.MapActionButtons
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.ui.task.MapTaskScreenUi
import com.example.xcpro.qnh.QnhCalibrationState

@Composable
internal fun MapTaskManagerLayer(
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    currentLocation: MapLocationUiModel?,
    currentQnh: String
) {
    MapTaskScreenUi.AllTaskScreenComponents(
        taskScreenManager = taskScreenManager,
        allWaypoints = waypointData,
        unitsPreferences = unitsPreferences,
        currentQNH = currentQnh,
        currentLocation = currentLocation
    )
}

@Composable
internal fun MapActionButtonsLayer(
    currentLocation: MapLocationUiModel?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showVarioDemoFab: Boolean,
    showAatEditFab: Boolean,
    showRacingReplayFab: Boolean,
    onRecenter: () -> Unit,
    onReturn: () -> Unit,
    onVarioDemoReferenceClick: () -> Unit,
    onVarioDemoSimClick: () -> Unit,
    onVarioDemoSim2Click: () -> Unit,
    onVarioDemoSim3Click: () -> Unit,
    onRacingReplayClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    MapActionButtons(
        currentLocation = currentLocation,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton,
        onRecenter = onRecenter,
        onReturn = onReturn,
        showVarioDemoFab = showVarioDemoFab,
        showAatEditFab = showAatEditFab,
        showRacingReplayFab = showRacingReplayFab,
        onVarioDemoReferenceClick = onVarioDemoReferenceClick,
        onVarioDemoSimClick = onVarioDemoSimClick,
        onVarioDemoSim2Click = onVarioDemoSim2Click,
        onVarioDemoSim3Click = onVarioDemoSim3Click,
        onRacingReplayClick = onRacingReplayClick,
        modifier = modifier
    )
}

@Composable
internal fun QnhDialogHost(
    visible: Boolean,
    qnhInput: String,
    qnhError: String?,
    unitsPreferences: UnitsPreferences,
    liveData: RealTimeFlightData?,
    calibrationState: QnhCalibrationState,
    onQnhInputChange: (String) -> Unit,
    onConfirm: (Double) -> Unit,
    onInvalidInput: (String) -> Unit,
    onAutoCalibrate: () -> Unit,
    onDismiss: () -> Unit
) {
    QnhDialog(
        visible = visible,
        qnhInput = qnhInput,
        qnhError = qnhError,
        unitsPreferences = unitsPreferences,
        liveData = liveData,
        calibrationState = calibrationState,
        onQnhInputChange = onQnhInputChange,
        onConfirm = onConfirm,
        onInvalidInput = onInvalidInput,
        onAutoCalibrate = onAutoCalibrate,
        onDismiss = onDismiss
    )
}
