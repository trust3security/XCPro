package com.trust3.xcpro.map.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.core.flight.RealTimeFlightData
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.map.MapTaskScreenManager
import com.trust3.xcpro.map.TrafficMapCoordinate
import com.trust3.xcpro.map.components.MapActionButtons
import com.trust3.xcpro.map.model.MapLocationUiModel
import com.trust3.xcpro.map.ui.task.MapTaskScreenUi
import com.trust3.xcpro.qnh.QnhCalibrationState
import com.trust3.xcpro.tasks.TaskFlightSurfaceUiState
import kotlinx.coroutines.flow.StateFlow

internal fun resolveVisibleCurrentLocation(
    renderLocalOwnship: Boolean,
    currentLocation: MapLocationUiModel?
): MapLocationUiModel? = currentLocation.takeIf { renderLocalOwnship }

internal fun resolveTrafficPanelsOwnshipCoordinate(
    renderLocalOwnship: Boolean,
    currentLocation: MapLocationUiModel?
): TrafficMapCoordinate? = currentLocation
    ?.takeIf { renderLocalOwnship }
    ?.let { location ->
        TrafficMapCoordinate(
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

@Composable
internal fun MapTaskManagerRuntimeLayer(
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    currentLocationFlow: StateFlow<MapLocationUiModel?>,
    renderLocalOwnship: Boolean,
    currentQnh: String,
    taskFlightSurfaceUiState: TaskFlightSurfaceUiState
) {
    val currentLocation = currentLocationFlow.collectAsStateWithLifecycle().value
    MapTaskManagerLayer(
        taskScreenManager = taskScreenManager,
        waypointData = waypointData,
        unitsPreferences = unitsPreferences,
        currentLocation = resolveVisibleCurrentLocation(
            renderLocalOwnship = renderLocalOwnship,
            currentLocation = currentLocation
        ),
        currentQnh = currentQnh,
        taskFlightSurfaceUiState = taskFlightSurfaceUiState
    )
}

@Composable
internal fun MapTaskManagerLayer(
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    currentLocation: MapLocationUiModel?,
    currentQnh: String,
    taskFlightSurfaceUiState: TaskFlightSurfaceUiState
) {
    MapTaskScreenUi.AllTaskScreenComponents(
        taskScreenManager = taskScreenManager,
        allWaypoints = waypointData,
        unitsPreferences = unitsPreferences,
        currentQNH = currentQnh,
        currentLocation = currentLocation,
        taskFlightSurfaceUiState = taskFlightSurfaceUiState
    )
}

@Composable
internal fun MapActionButtonsRuntimeLayer(
    currentLocationFlow: StateFlow<MapLocationUiModel?>,
    renderLocalOwnship: Boolean,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showVarioDemoFab: Boolean,
    showAatEditFab: Boolean,
    showRacingReplayFab: Boolean,
    onRecenter: () -> Unit,
    onReturn: () -> Unit,
    onSyntheticThermalReplayClick: () -> Unit,
    onSyntheticThermalReplayWindNoisyClick: () -> Unit,
    onVarioDemoSimClick: () -> Unit,
    onVarioDemoSim2Click: () -> Unit,
    onVarioDemoSim3Click: () -> Unit,
    onRacingReplayClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val currentLocation = currentLocationFlow.collectAsStateWithLifecycle().value
    val localOwnshipRenderState = resolveMapLocalOwnshipRenderState(
        renderLocalOwnship = renderLocalOwnship,
        currentLocation = currentLocation,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton
    )
    MapActionButtonsLayer(
        currentLocation = localOwnshipRenderState.currentLocation,
        showRecenterButton = localOwnshipRenderState.showRecenterButton,
        showReturnButton = localOwnshipRenderState.showReturnButton,
        showVarioDemoFab = showVarioDemoFab,
        showAatEditFab = showAatEditFab,
        showRacingReplayFab = showRacingReplayFab,
        onRecenter = onRecenter,
        onReturn = onReturn,
        onSyntheticThermalReplayClick = onSyntheticThermalReplayClick,
        onSyntheticThermalReplayWindNoisyClick = onSyntheticThermalReplayWindNoisyClick,
        onVarioDemoSimClick = onVarioDemoSimClick,
        onVarioDemoSim2Click = onVarioDemoSim2Click,
        onVarioDemoSim3Click = onVarioDemoSim3Click,
        onRacingReplayClick = onRacingReplayClick,
        modifier = modifier
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
    onSyntheticThermalReplayClick: () -> Unit,
    onSyntheticThermalReplayWindNoisyClick: () -> Unit,
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
        onSyntheticThermalReplayClick = onSyntheticThermalReplayClick,
        onSyntheticThermalReplayWindNoisyClick = onSyntheticThermalReplayWindNoisyClick,
        onVarioDemoSimClick = onVarioDemoSimClick,
        onVarioDemoSim2Click = onVarioDemoSim2Click,
        onVarioDemoSim3Click = onVarioDemoSim3Click,
        onRacingReplayClick = onRacingReplayClick,
        modifier = modifier
    )
}

@Composable
internal fun BoxScope.MapTrafficRuntimePanelsLayer(
    traffic: MapTrafficUiBinding,
    runtimeState: MapTrafficRuntimeState,
    reserveTopEndPrimarySlot: Boolean,
    currentLocationFlow: StateFlow<MapLocationUiModel?>,
    renderLocalOwnship: Boolean,
    unitsPreferences: UnitsPreferences,
    trafficActions: MapTrafficUiActions
) {
    val currentLocation = currentLocationFlow.collectAsStateWithLifecycle().value
    MapTrafficRuntimeLayer(
        traffic = traffic,
        runtimeState = runtimeState,
        reserveTopEndPrimarySlot = reserveTopEndPrimarySlot,
        ownshipCoordinate = resolveTrafficPanelsOwnshipCoordinate(
            renderLocalOwnship = renderLocalOwnship,
            currentLocation = currentLocation
        ),
        unitsPreferences = unitsPreferences,
        trafficActions = trafficActions
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
