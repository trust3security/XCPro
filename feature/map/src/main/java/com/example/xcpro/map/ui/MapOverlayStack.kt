package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapGestureSetup
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapModalUI
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.flow.StateFlow

@Composable
@Suppress("LongParameterList", "UNUSED_PARAMETER")
internal fun MapOverlayStack(
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (org.maplibre.android.maps.MapLibreMap) -> Unit,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    taskManager: TaskManagerCoordinator,
    taskType: TaskType,
    createTaskGestureHandler: (TaskGestureCallbacks) -> TaskGestureHandler,
    windArrowState: WindArrowUiState,
    showWindSpeedOnVario: Boolean,
    cameraManager: MapCameraManager,
    currentMode: FlightMode,
    currentZoom: Float,
    onModeChange: (FlightMode) -> Unit,
    currentLocation: MapLocationUiModel?,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    overlayManager: MapOverlayManager,
    onAdsbTargetSelected: (Icao24) -> Unit,
    isAATEditMode: Boolean,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onEnterAATEditMode: (Int) -> Unit,
    onUpdateAATTargetPoint: (Int, Double, Double) -> Unit,
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
    onHamburgerOffsetChange: (Offset) -> Unit,
    onFlightModeOffsetChange: (Offset) -> Unit,
    onBallastOffsetChange: (Offset) -> Unit,
    widgetManager: MapUIWidgetManager,
    screenWidthPx: Float,
    screenHeightPx: Float,
    modalManager: MapModalManager,
    ballastUiState: StateFlow<BallastUiState>,
    hideBallastPill: Boolean,
    onBallastCommand: (BallastCommand) -> Unit,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    cardStyle: CardStyle,
    hiddenCardIds: Set<String>,
    replayState: StateFlow<SessionState>
) {
    val gestureRegions by widgetManager.gestureRegions.collectAsStateWithLifecycle()

    LaunchedEffect(gestureRegions) {
        if (BuildConfig.DEBUG) Log.d("GESTURE_REGIONS", gestureRegions.joinToString(prefix = "[", postfix = "]") { region ->
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
            onMapReady = onMapReady,
            locationManager = locationManager,
            flightDataManager = flightDataManager,
            flightViewModel = flightViewModel,
            overlayManager = overlayManager,
            isUiEditMode = isUiEditMode,
            onEditModeChange = onEditModeChange,
            onContainerSizeChanged = { size ->
                if (size.width > 0 && size.height > 0) {
                    safeContainerSize.value = size
                }
            },
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
            cardStyle = cardStyle,
            hiddenCardIds = hiddenCardIds
        )

        if (!isUiEditMode) {
            MapGestureSetup.GestureHandlerOverlay(
                mapState = mapState,
                taskType = taskType,
                flightDataManager = flightDataManager,
                locationManager = locationManager,
                cameraManager = cameraManager,
                currentMode = currentMode,
                onModeChange = onModeChange,
                currentLocation = currentLocation,
                showReturnButton = showReturnButton,
                isAATEditMode = isAATEditMode,
                createTaskGestureHandler = createTaskGestureHandler,
                onEnterAATEditMode = onEnterAATEditMode,
                onExitAATEditMode = onExitAATEditMode,
                onUpdateAATTargetPoint = onUpdateAATTargetPoint,
                onSyncTaskVisuals = { overlayManager.requestTaskRenderSync() },
                onMapTap = { tap ->
                    val tappedId = overlayManager.findAdsbTargetAt(tap)
                    if (tappedId != null) {
                        onAdsbTargetSelected(tappedId)
                    }
                },
                gestureRegions = gestureRegions,
                modifier = Modifier.zIndex(3.6f)
            )
        }

        MapUIWidgets.FlightModeMenu(
            widgetManager = widgetManager,
            currentMode = currentMode,
            visibleModes = flightDataManager.visibleModes,
            onModeChange = onModeChange,
            flightModeOffset = flightModeOffset.value,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onOffsetChange = onFlightModeOffsetChange,
            isEditMode = isUiEditMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(12f)
        )

        BallastPanel(
            ballastUiState = ballastUiState,
            hideBallastPill = hideBallastPill,
            widgetManager = widgetManager,
            ballastOffset = ballastOffset,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onOffsetChange = onBallastOffsetChange,
            onBallastCommand = onBallastCommand,
            isUiEditMode = isUiEditMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(12f)
        )

        VariometerPanel(
            flightDataManager = flightDataManager,
            widgetManager = widgetManager,
            windArrowState = windArrowState,
            showWindSpeedOnVario = showWindSpeedOnVario,
            variometerUiState = variometerUiState,
            minVariometerSizePx = minVariometerSizePx,
            maxVariometerSizePx = maxVariometerSizePx,
            onVariometerOffsetChange = onVariometerOffsetChange,
            onVariometerSizeChange = onVariometerSizeChange,
            onVariometerLongPress = onVariometerLongPress,
            onVariometerEditFinished = onVariometerEditFinished,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            isUiEditMode = isUiEditMode,
            replayState = replayState
        )

        DistanceCirclesLayer(
            mapState = mapState,
            currentZoom = currentZoom,
            currentLocation = currentLocation,
            showDistanceCircles = showDistanceCircles
        )

        AatEditFab(
            isAATEditMode = isAATEditMode,
            taskType = taskType,
            cameraManager = cameraManager,
            onExitAATEditMode = onExitAATEditMode,
            onSyncTaskVisuals = { overlayManager.requestTaskRenderSync() }
        )

        HamburgerMenu(
            widgetManager = widgetManager,
            hamburgerOffset = hamburgerOffset,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onHamburgerTap = onHamburgerTap,
            onHamburgerLongPress = onHamburgerLongPress,
            onOffsetChange = onHamburgerOffsetChange,
            isUiEditMode = isUiEditMode
        )

        MapModalUI.AirspaceSettingsModalOverlay(modalManager = modalManager)
    }
}
