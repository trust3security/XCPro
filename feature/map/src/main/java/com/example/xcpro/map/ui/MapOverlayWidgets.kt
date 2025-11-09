package com.example.xcpro.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.util.Log
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.CompassWidget
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.DistanceCirclesCanvas
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapGestureSetup
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapModalUI
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.tasks.TaskManagerCoordinator
import kotlinx.coroutines.flow.StateFlow
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle

@Composable
@Suppress("LongParameterList")
internal fun MapOverlayStack(
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    currentFlightModeSelection: com.example.dfcards.FlightModeSelection,
    taskManager: TaskManagerCoordinator,
    orientationManager: MapOrientationManager,
    orientationData: OrientationData,
    cameraManager: MapCameraManager,
    currentLocation: GPSData?,
    showReturnButton: Boolean,
    isAATEditMode: Boolean,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onSetAATEditMode: (Boolean) -> Unit,
    onExitAATEditMode: () -> Unit,
    safeContainerSize: MutableState<IntSize>,
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
    widgetManager: MapUIWidgetManager,
    screenWidthPx: Float,
    screenHeightPx: Float,
    density: Density,
    modalManager: MapModalManager,
    ballastUiState: StateFlow<BallastUiState>,
    onBallastCommand: (BallastCommand) -> Unit,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    cardStyle: CardStyle
) {
    val currentMode by mapState.currentModeFlow.collectAsState()
    val showDistanceCircles by mapState.showDistanceCirclesFlow.collectAsState()
    val gestureRegions by widgetManager.gestureRegions.collectAsState()

    LaunchedEffect(gestureRegions) {
        Log.d("GESTURE_REGIONS", gestureRegions.joinToString(prefix = "[", postfix = "]") { region ->
            "${region.target}:${region.bounds}"
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            widgetManager.clearGestureRegion(MapOverlayGestureTarget.CARD_GRID)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(3f)
    ) {
        MapMainLayers(
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
            onContainerSizeChanged = { size -> safeContainerSize.value = size },
            modifier = Modifier.fillMaxSize(),
            onCardLayerPositioned = { bounds ->
                if (bounds == Rect.Zero) {
                    widgetManager.clearGestureRegion(MapOverlayGestureTarget.CARD_GRID)
                } else {
                    widgetManager.updateGestureRegion(
                        target = MapOverlayGestureTarget.CARD_GRID,
                        bounds = bounds,
                        consumeGestures = isUiEditMode
                    )
                }
            },
            cardStyle = cardStyle
        )

        if (!isUiEditMode) {
            MapGestureSetup.GestureHandlerOverlay(
                mapState = mapState,
                taskManager = taskManager,
                flightDataManager = flightDataManager,
                locationManager = locationManager,
                cameraManager = cameraManager,
                currentLocation = currentLocation,
                showReturnButton = showReturnButton,
                isAATEditMode = isAATEditMode,
                onAATEditModeChange = onSetAATEditMode,
                gestureRegions = gestureRegions,
                modifier = Modifier.zIndex(3.6f)
            )
        }

        MapUIWidgets.FlightModeMenu(
            widgetManager = widgetManager,
            currentMode = currentMode,
            visibleModes = flightDataManager.visibleModes,
            onModeChange = { newMode -> mapState.updateFlightMode(newMode) },
            flightModeOffset = flightModeOffset.value,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onOffsetChange = { offset -> flightModeOffset.value = offset },
            isEditMode = isUiEditMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(12f)
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
        val showBallastPill =
            ballastState.isAnimating || ballastState.snapshot.hasBallast || ballastState.snapshot.currentKg > 0.0

        AnimatedVisibility(
            visible = showBallastPill,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(5f)
        ) {
            MapUIWidgets.BallastWidget(
                widgetManager = widgetManager,
                ballastState = ballastState,
                onCommand = onBallastCommand,
                ballastOffset = ballastOffset.value,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                onOffsetChange = { offset -> ballastOffset.value = offset },
                isEditMode = isUiEditMode
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

        MapUIWidgets.VariometerWidget(
            widgetManager = widgetManager,
            variometerState = variometerUiState,
            needleValue = animatedVario,
            displayValue = displayNumericVario,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            minSizePx = minVariometerSizePx,
            maxSizePx = maxVariometerSizePx,
            isEditMode = isUiEditMode,
            onOffsetChange = onVariometerOffsetChange,
            onSizeChange = onVariometerSizeChange,
            onLongPress = onVariometerLongPress,
            onEditFinished = onVariometerEditFinished,
            modifier = Modifier.zIndex(if (isUiEditMode) 12f else 3f)
        )

        DistanceCirclesCanvas(
            mapZoom = mapState.mapLibreMap?.cameraPosition?.zoom?.toFloat() ?: 10f,
            mapLatitude = flightDataManager.liveFlightData?.latitude ?: 0.0,
            isVisible = showDistanceCircles,
            modifier = Modifier.zIndex(3.7f)
        )

        MapTaskIntegration.AATEditModeFAB(
            isAATEditMode = isAATEditMode,
            taskManager = taskManager,
            cameraManager = cameraManager,
            onExitEditMode = onExitAATEditMode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .zIndex(11f)
        )

        MapUIWidgets.SideHamburgerMenu(
            widgetManager = widgetManager,
            hamburgerOffset = hamburgerOffset.value,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onHamburgerTap = onHamburgerTap,
            onHamburgerLongPress = onHamburgerLongPress,
            onOffsetChange = { offset -> hamburgerOffset.value = offset },
            isEditMode = isUiEditMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(12f)
        )

        MapModalUI.AirspaceSettingsModalOverlay(modalManager = modalManager)
    }
}



