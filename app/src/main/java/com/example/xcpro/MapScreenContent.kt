package com.example.xcpro

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
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
import com.example.xcpro.map.MapTaskScreenUI
import com.example.xcpro.map.MapUIWidgetManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.QnhDialog
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.OrientationData
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.sensors.GPSData
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun MapScreenContent(
    navController: NavHostController,
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
    showReturnButton: Boolean,
    isAATEditMode: Boolean,
    onSetAATEditMode: (Boolean) -> Unit,
    onExitAATEditMode: () -> Unit,
    safeContainerSize: MutableState<IntSize>,
    overlayManager: MapOverlayManager,
    modalManager: MapModalManager,
    widgetManager: MapUIWidgetManager,
    screenWidthPx: Float,
    screenHeightPx: Float,
    variometerOffset: MutableState<Offset>,
    variometerSizePx: MutableState<Float>,
    hamburgerOffset: MutableState<Offset>,
    flightModeOffset: MutableState<Offset>,
    showQnhDialog: MutableState<Boolean>,
    qnhInput: MutableState<String>,
    qnhError: MutableState<String?>,
    showQnhFab: MutableState<Boolean>,
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    ballastUiState: StateFlow<BallastUiState>,
    onBallastCommand: (BallastCommand) -> Unit,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit
) {
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
                        navController = navController,
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
                        onSetAATEditMode = onSetAATEditMode,
                        onExitAATEditMode = onExitAATEditMode,
                        safeContainerSize = safeContainerSize,
                        variometerOffset = variometerOffset,
                        variometerSizePx = variometerSizePx,
                        hamburgerOffset = hamburgerOffset,
                        flightModeOffset = flightModeOffset,
                        widgetManager = widgetManager,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        density = density,
                        modalManager = modalManager,
                        ballastUiState = ballastUiState,
                        onBallastCommand = onBallastCommand,
                        onHamburgerTap = onHamburgerTap,
                        onHamburgerLongPress = onHamburgerLongPress
                    )
                }
            }
        }

        MapTaskScreenUI.AllTaskScreenComponents(
            taskScreenManager = taskScreenManager,
            allWaypoints = waypointData,
            currentQNH = "1013 hPa",
            onWaypointGoto = { waypoint ->
                cameraManager.moveToWaypoint(waypoint.latitude, waypoint.longitude)
            }
        )

        MapActionButtons(
            mapState = mapState,
            taskManager = taskManager,
            taskScreenManager = taskScreenManager,
            currentLocation = currentLocation,
            showReturnButton = showReturnButton,
            onToggleDistanceCircles = { overlayManager.toggleDistanceCircles() },
            onReturn = { locationManager.returnToSavedLocation() },
            onShowQnhDialog = {
                val currentQnh = flightDataManager.liveFlightData?.qnh ?: 1013.25
                qnhInput.value = seedQnhInputValue(currentQnh, unitsPreferences)
                qnhError.value = null
                showQnhDialog.value = true
            },
            showQnhFab = showQnhFab.value,
            onDismissQnhFab = { showQnhFab.value = false },
            modifier = Modifier.fillMaxSize()
        )

        QnhDialog(
            visible = showQnhDialog.value,
            qnhInput = qnhInput.value,
            qnhError = qnhError.value,
            unitsPreferences = unitsPreferences,
            liveData = flightDataManager.liveFlightData,
            onQnhInputChange = {
                qnhInput.value = it
                qnhError.value = null
            },
            onConfirm = { parsed ->
                val qnhHpa = convertQnhInputToHpa(parsed, unitsPreferences)
                locationManager.setManualQnh(qnhHpa)
                showQnhDialog.value = false
                qnhError.value = null
            },
            onInvalidInput = { error ->
                qnhError.value = error
            },
            onResetToStandard = {
                locationManager.resetQnhToStandard()
                showQnhDialog.value = false
                qnhError.value = null
            },
            onDismiss = {
                showQnhDialog.value = false
                qnhError.value = null
            }
        )
    }
}
