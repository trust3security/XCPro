package com.example.xcpro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
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
import com.example.xcpro.map.DistanceCirclesCanvas
import com.example.xcpro.map.components.MapActionButtons
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapGestureSetup
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapModalUI
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.MapTaskScreenUI
import com.example.xcpro.map.MapUIWidgetManager
import com.example.xcpro.map.MapUIWidgets
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.QnhDialog
import com.example.xcpro.map.MapMainLayers
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastPill
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.profiles.FlightModeIndicator
import com.example.xcpro.skysight.SkysightMapOverlay
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.CompassWidget
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.MapOrientationMode
import com.example.xcpro.OrientationData
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.sensors.GPSData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun MapScreenContent(
    navController: NavHostController,
    drawerState: DrawerState,
    coroutineScope: CoroutineScope,
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
    showQnhDialog: MutableState<Boolean>,
    qnhInput: MutableState<String>,
    qnhError: MutableState<String?>,
    showQnhFab: MutableState<Boolean>,
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    ballastUiState: StateFlow<BallastUiState>,
    onBallastCommand: (BallastCommand) -> Unit
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
                    MapMainLayers(
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
                        onContainerSizeChanged = { size -> safeContainerSize.value = size },
                        modifier = Modifier.fillMaxSize(),
                        convertToRealTime = ::convertToRealTimeFlightData
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(3.5f)
                    ) {
                        SkysightMapOverlay(
                            onOpenSettings = { navController.navigate("skysight_settings") },
                            mapLibreMap = mapState.mapLibreMap,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    MapGestureSetup.GestureHandlerOverlay(
                        mapState = mapState,
                        taskManager = taskManager,
                        flightDataManager = flightDataManager,
                        locationManager = locationManager,
                        cameraManager = cameraManager,
                        currentLocation = currentLocation,
                        showReturnButton = showReturnButton,
                        isAATEditMode = isAATEditMode,
                        onAATEditModeChange = onSetAATEditMode
                    )

                    FlightModeIndicator(
                        currentMode = mapState.currentMode,
                        onModeChange = { newMode -> mapState.updateFlightMode(newMode) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = mapState.iconSize.dp + 8.dp, start = 16.dp)
                            .zIndex(5f)
                    )

                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 80.dp, end = 16.dp)
                            .zIndex(5f)
                    ) {
                        CompassWidget(
                            orientation = orientationData,
                            onModeToggle = {
                                val nextMode = when (orientationManager.getCurrentMode()) {
                                    MapOrientationMode.NORTH_UP -> MapOrientationMode.TRACK_UP
                                    MapOrientationMode.TRACK_UP -> MapOrientationMode.HEADING_UP
                                    MapOrientationMode.HEADING_UP -> MapOrientationMode.NORTH_UP
                                }
                                orientationManager.setOrientationMode(nextMode)
                            }
                        )
                    }

                    val ballastState by ballastUiState.collectAsState()
                    val showBallastPill = ballastState.snapshot.hasBallast ||
                        ballastState.isAnimating ||
                        ballastState.snapshot.currentKg > 0.0

                    AnimatedVisibility(
                        visible = showBallastPill,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 140.dp, end = 16.dp)
                            .zIndex(5f)
                    ) {
                        BallastPill(
                            state = ballastState,
                            onCommand = onBallastCommand
                        )
                    }

                    val targetVario = (flightDataManager.rawVerticalSpeed
                        ?: flightDataManager.liveFlightData?.verticalSpeed)?.toFloat() ?: 0f
                    val displayNumericVario = (flightDataManager.smoothedVerticalSpeed
                        ?: flightDataManager.liveFlightData?.verticalSpeed)?.toFloat() ?: 0f
                    val animatedVario by animateFloatAsState(
                        targetValue = targetVario,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "vario"
                    )

                    val minSizePx = with(density) { 60.dp.toPx() }
                    val maxSizePx = with(density) { 200.dp.toPx() }

                    MapUIWidgets.DraggableVariometer(
                        variometerNeedleValue = animatedVario,
                        variometerDisplayValue = displayNumericVario,
                        variometerOffset = variometerOffset.value,
                        variometerSizePx = variometerSizePx.value,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        minSizePx = minSizePx,
                        maxSizePx = maxSizePx,
                        widgetManager = widgetManager,
                        density = density,
                        onOffsetChange = { offset -> variometerOffset.value = offset },
                        onSizeChange = { size -> variometerSizePx.value = size },
                        modifier = Modifier.zIndex(if (mapState.isUIEditMode) 4f else 1f)
                    )

                    DistanceCirclesCanvas(
                        mapZoom = mapState.mapLibreMap?.cameraPosition?.zoom?.toFloat() ?: 10f,
                        mapLatitude = flightDataManager.liveFlightData?.latitude ?: 0.0,
                        isVisible = mapState.showDistanceCircles,
                        modifier = Modifier.zIndex(3.7f)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .zIndex(11f)
                    ) {
                        MapTaskIntegration.AATEditModeFAB(
                            isAATEditMode = isAATEditMode,
                            taskManager = taskManager,
                            cameraManager = cameraManager,
                            onExitEditMode = onExitAATEditMode
                        )
                    }

                    MapUIWidgets.DraggableHamburgerMenu(
                        hamburgerOffset = hamburgerOffset.value,
                        iconSize = mapState.iconSize,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        widgetManager = widgetManager,
                        density = density,
                        drawerState = drawerState,
                        coroutineScope = coroutineScope,
                        onOffsetChange = { offset -> hamburgerOffset.value = offset },
                        onSizeChange = { size -> mapState.iconSize = size },
                        modifier = Modifier.zIndex(4f)
                    )

                    MapModalUI.AirspaceSettingsModalOverlay(
                        modalManager = modalManager
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
