package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapGestureSetup
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapModalUI
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
import com.example.xcpro.replay.SessionState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.sensors.GPSData
import kotlinx.coroutines.flow.StateFlow

@Composable
@Suppress("LongParameterList")
internal fun MapOverlayStack(
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (org.maplibre.android.maps.MapLibreMap) -> Unit,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    currentFlightModeSelection: com.example.dfcards.FlightModeSelection,
    taskManager: TaskManagerCoordinator,
    orientationManager: MapOrientationManager,
    orientationData: OrientationData,
    cameraManager: MapCameraManager,
    currentMode: FlightMode,
    currentZoom: Float,
    onModeChange: (FlightMode) -> Unit,
    currentLocation: GPSData?,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
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
    hideBallastPill: Boolean,
    onBallastCommand: (BallastCommand) -> Unit,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    cardStyle: CardStyle,
    replayState: StateFlow<SessionState>,
    onReplayPlayPause: () -> Unit,
    onReplayStop: () -> Unit,
    onReplaySpeedChange: (Double) -> Unit,
    onReplaySeek: (Float) -> Unit,
    showReplayDevFab: Boolean,
    onReplayPickFileClick: () -> Unit
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
            cardStyle = cardStyle
        )

        if (!isUiEditMode) {
            MapGestureSetup.GestureHandlerOverlay(
                mapState = mapState,
                taskManager = taskManager,
                flightDataManager = flightDataManager,
                locationManager = locationManager,
                cameraManager = cameraManager,
                currentMode = currentMode,
                onModeChange = onModeChange,
                currentLocation = currentLocation,
                showReturnButton = showReturnButton,
                isAATEditMode = isAATEditMode,
                onAATEditModeChange = onSetAATEditMode,
                gestureRegions = gestureRegions,
                modifier = Modifier.zIndex(3.6f)
            )
        }

        val replaySession by replayState.collectAsStateWithLifecycle()
        ReplayControlsSheet(
            session = replaySession,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f),
            onPlayPause = onReplayPlayPause,
            onStop = onReplayStop,
            onSpeedChanged = onReplaySpeedChange,
            onSeek = onReplaySeek
        )

        MapUIWidgets.FlightModeMenu(
            widgetManager = widgetManager,
            currentMode = currentMode,
            visibleModes = flightDataManager.visibleModes,
            onModeChange = onModeChange,
            flightModeOffset = flightModeOffset.value,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onOffsetChange = { offset -> flightModeOffset.value = offset },
            isEditMode = isUiEditMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(12f)
        )

        CompassPanel(
            orientationData = orientationData,
            orientationManager = orientationManager,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp)
                .zIndex(5f)
        )

        BallastPanel(
            ballastUiState = ballastUiState,
            hideBallastPill = hideBallastPill,
            widgetManager = widgetManager,
            ballastOffset = ballastOffset,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onBallastCommand = onBallastCommand,
            isUiEditMode = isUiEditMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(12f)
        )

        VariometerPanel(
            flightDataManager = flightDataManager,
            widgetManager = widgetManager,
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
            currentZoom = currentZoom,
            currentLocation = currentLocation,
            showDistanceCircles = showDistanceCircles
        )

        AatEditFab(
            isAATEditMode = isAATEditMode,
            taskManager = taskManager,
            cameraManager = cameraManager,
            onExitAATEditMode = onExitAATEditMode
        )

        if (showReplayDevFab) {
            ReplayDevFab(onReplayPickFileClick = onReplayPickFileClick)
        }

        HamburgerMenu(
            widgetManager = widgetManager,
            hamburgerOffset = hamburgerOffset,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onHamburgerTap = onHamburgerTap,
            onHamburgerLongPress = onHamburgerLongPress,
            isUiEditMode = isUiEditMode
        )

        MapModalUI.AirspaceSettingsModalOverlay(modalManager = modalManager)
    }
}
