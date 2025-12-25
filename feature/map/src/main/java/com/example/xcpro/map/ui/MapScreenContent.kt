package com.example.xcpro.map.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.components.MapActionButtons
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.map.ui.MapOverlayStack
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.ui.task.MapTaskScreenUi
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.sensors.GPSData
import kotlinx.coroutines.flow.StateFlow
import com.example.xcpro.seedQnhInputValue
import com.example.xcpro.convertQnhInputToHpa
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.replay.IgcReplayController

@Composable
internal fun MapScreenContent(
    density: Density,
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    taskManager: TaskManagerCoordinator,
    orientationManager: MapOrientationManager,
    orientationData: OrientationData,
    cameraManager: MapCameraManager,
    currentFlightModeSelection: com.example.dfcards.FlightModeSelection,
    currentLocation: GPSData?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    isAATEditMode: Boolean,
    onSetAATEditMode: (Boolean) -> Unit,
    onExitAATEditMode: () -> Unit,
    safeContainerSize: MutableState<IntSize>,
    overlayManager: MapOverlayManager,
    modalManager: MapModalManager,
    widgetManager: MapUIWidgetManager,
    screenWidthPx: Float,
    screenHeightPx: Float,
    variometerUiState: VariometerUiState,
    minVariometerSizePx: Float,
    maxVariometerSizePx: Float,
    onVariometerOffsetChange: (Offset) -> Unit,
    onVariometerSizeChange: (Float) -> Unit,
    onVariometerLongPress: () -> Unit,
    onVariometerEditFinished: () -> Unit,
    hamburgerOffset: MutableState<Offset>,
    flightModeOffset: MutableState<Offset>,
    ballastOffset: MutableState<Offset>,
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    ballastUiState: StateFlow<BallastUiState>,
    isBallastPillHidden: Boolean,
    onBallastCommand: (BallastCommand) -> Unit,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    cardStyle: CardStyle,
    replayState: StateFlow<IgcReplayController.SessionState>,
    onReplayPlayPause: () -> Unit,
    onReplayStop: () -> Unit,
    onReplaySpeedChange: (Double) -> Unit,
    onReplaySeek: (Float) -> Unit,
    showReplayDevFab: Boolean,
    onReplayPickFileClick: () -> Unit
) {
    var showQnhDialog by remember { mutableStateOf(false) }
    var qnhInput by remember { mutableStateOf("") }
    var qnhError by remember { mutableStateOf<String?>(null) }
    var showQnhFab by remember { mutableStateOf(true) }
    val autoQnhEnabled by locationManager.autoQnhEnabledFlow.collectAsState(initial = false)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.border(2.dp, Color.Yellow)
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val maxWidthPx = with(density) { maxWidth.toPx() }
                val maxHeightPx = with(density) { maxHeight.toPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .size(maxWidth, maxHeight)
                ) {
                    MapOverlayStack(
                        mapState = mapState,
                        mapInitializer = mapInitializer,
                        locationManager = locationManager,
                        flightDataManager = flightDataManager,
                        flightViewModel = flightViewModel,
                        currentFlightModeSelection = currentFlightModeSelection,
                        taskManager = taskManager,
                        orientationManager = orientationManager,
                        orientationData = orientationData,
                        cameraManager = cameraManager,
                        currentLocation = currentLocation,
                        showReturnButton = showReturnButton,
                        isAATEditMode = isAATEditMode,
                        isUiEditMode = isUiEditMode,
                        onEditModeChange = onEditModeChange,
                        onSetAATEditMode = onSetAATEditMode,
                        onExitAATEditMode = onExitAATEditMode,
                        safeContainerSize = safeContainerSize,
                        variometerUiState = variometerUiState,
                        minVariometerSizePx = minVariometerSizePx,
                        maxVariometerSizePx = maxVariometerSizePx,
                        onVariometerOffsetChange = onVariometerOffsetChange,
                    onVariometerSizeChange = onVariometerSizeChange,
                    onVariometerLongPress = onVariometerLongPress,
                    onVariometerEditFinished = onVariometerEditFinished,
                    hamburgerOffset = hamburgerOffset,
                    flightModeOffset = flightModeOffset,
                    ballastOffset = ballastOffset,
                        widgetManager = widgetManager,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        density = density,
                        modalManager = modalManager,
                        ballastUiState = ballastUiState,
                        hideBallastPill = isBallastPillHidden,
                        onBallastCommand = onBallastCommand,
                        onHamburgerTap = onHamburgerTap,
                        onHamburgerLongPress = onHamburgerLongPress,
                    cardStyle = cardStyle,
                    replayState = replayState,
                    onReplayPlayPause = onReplayPlayPause,
                    onReplayStop = onReplayStop,
                    onReplaySpeedChange = onReplaySpeedChange,
                    onReplaySeek = onReplaySeek,
                    showReplayDevFab = showReplayDevFab,
                    onReplayPickFileClick = onReplayPickFileClick
                )
            }
        }
        }

        MapTaskManagerLayer(
            taskScreenManager = taskScreenManager,
            waypointData = waypointData,
            onWaypointGoto = { waypoint ->
                cameraManager.moveToWaypoint(waypoint.latitude, waypoint.longitude)
            }
        )

        MapActionButtonsLayer(
            mapState = mapState,
            taskManager = taskManager,
            taskScreenManager = taskScreenManager,
            currentLocation = currentLocation,
            showRecenterButton = showRecenterButton,
            showReturnButton = showReturnButton,
            showDistanceCircles = showDistanceCircles,
            showQnhFab = showQnhFab,
            onToggleDistanceCircles = { overlayManager.toggleDistanceCircles() },
            onReturn = { locationManager.returnToSavedLocation() },
            onShowQnhDialog = {
                val currentQnh = flightDataManager.liveFlightData?.qnh ?: 1013.25
                qnhInput = seedQnhInputValue(currentQnh, unitsPreferences)
                qnhError = null
                showQnhDialog = true
            },
            onDismissQnhFab = { showQnhFab = false }
        )

        QnhDialogHost(
            visible = showQnhDialog,
            qnhInput = qnhInput,
            qnhError = qnhError,
            unitsPreferences = unitsPreferences,
            liveData = flightDataManager.liveFlightData,
            autoQnhEnabled = autoQnhEnabled,
            onAutoQnhToggle = { enabled -> locationManager.setAutoQnhEnabled(enabled) },
            onQnhInputChange = {
                qnhInput = it
                qnhError = null
            },
            onConfirm = { parsed ->
                val qnhHpa = convertQnhInputToHpa(parsed, unitsPreferences)
                locationManager.setManualQnh(qnhHpa)
                showQnhDialog = false
                qnhError = null
            },
            onInvalidInput = { error ->
                qnhError = error
            },
            onResetToStandard = {
                locationManager.resetQnhToStandard()
                showQnhDialog = false
                qnhError = null
            },
            onDismiss = {
                showQnhDialog = false
                qnhError = null
            }
        )
    }
}

@Composable
private fun MapTaskManagerLayer(
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    onWaypointGoto: (WaypointData) -> Unit
) {
    MapTaskScreenUi.AllTaskScreenComponents(
        taskScreenManager = taskScreenManager,
        allWaypoints = waypointData,
        currentQNH = "1013 hPa",
        onWaypointGoto = onWaypointGoto
    )
}

@Composable
private fun MapActionButtonsLayer(
    mapState: MapScreenState,
    taskManager: TaskManagerCoordinator,
    taskScreenManager: MapTaskScreenManager,
    currentLocation: GPSData?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    showQnhFab: Boolean,
    onToggleDistanceCircles: () -> Unit,
    onReturn: () -> Unit,
    onShowQnhDialog: () -> Unit,
    onDismissQnhFab: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    MapActionButtons(
        mapState = mapState,
        taskManager = taskManager,
        taskScreenManager = taskScreenManager,
        currentLocation = currentLocation,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton,
        showDistanceCircles = showDistanceCircles,
        onToggleDistanceCircles = onToggleDistanceCircles,
        onReturn = onReturn,
        onShowQnhDialog = onShowQnhDialog,
        showQnhFab = showQnhFab,
        onDismissQnhFab = onDismissQnhFab,
        modifier = modifier
    )
}

@Composable
private fun QnhDialogHost(
    visible: Boolean,
    qnhInput: String,
    qnhError: String?,
    unitsPreferences: UnitsPreferences,
    liveData: RealTimeFlightData?,
    autoQnhEnabled: Boolean,
    onAutoQnhToggle: (Boolean) -> Unit,
    onQnhInputChange: (String) -> Unit,
    onConfirm: (Double) -> Unit,
    onInvalidInput: (String) -> Unit,
    onResetToStandard: () -> Unit,
    onDismiss: () -> Unit
) {
    QnhDialog(
        visible = visible,
        qnhInput = qnhInput,
        qnhError = qnhError,
        unitsPreferences = unitsPreferences,
        liveData = liveData,
        autoQnhEnabled = autoQnhEnabled,
        onAutoQnhToggle = onAutoQnhToggle,
        onQnhInputChange = onQnhInputChange,
        onConfirm = onConfirm,
        onInvalidInput = onInvalidInput,
        onResetToStandard = onResetToStandard,
        onDismiss = onDismiss
    )
}
