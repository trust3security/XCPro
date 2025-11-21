package com.example.xcpro.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
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
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.tasks.TaskManagerCoordinator
import kotlinx.coroutines.flow.StateFlow
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.replay.IgcReplayController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import com.example.xcpro.map.BuildConfig
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material.FractionalThreshold

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
    hideBallastPill: Boolean,
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
    onReplayDevFabClick: () -> Unit
) {
    val currentMode by mapState.currentModeFlow.collectAsStateWithLifecycle()
    val showDistanceCircles by mapState.showDistanceCirclesFlow.collectAsStateWithLifecycle()
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
            isUiEditMode = isUiEditMode
        )

        DistanceCirclesLayer(
            mapState = mapState,
            flightDataManager = flightDataManager,
            showDistanceCircles = showDistanceCircles
        )

        AatEditFab(
            isAATEditMode = isAATEditMode,
            taskManager = taskManager,
            cameraManager = cameraManager,
            onExitAATEditMode = onExitAATEditMode
        )

        if (showReplayDevFab) {
            ReplayDevFab(onReplayDevFabClick = onReplayDevFabClick)
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

@Composable
private fun CompassPanel(
    orientationData: OrientationData,
    orientationManager: MapOrientationManager,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        CompassWidget(
            orientation = orientationData,
            onModeToggle = {
                val nextMode = when (orientationManager.getCurrentMode()) {
                    MapOrientationMode.NORTH_UP -> MapOrientationMode.TRACK_UP
                    MapOrientationMode.TRACK_UP -> MapOrientationMode.HEADING_UP
                    MapOrientationMode.HEADING_UP -> MapOrientationMode.WIND_UP
                    MapOrientationMode.WIND_UP -> MapOrientationMode.NORTH_UP
                }
                orientationManager.setOrientationMode(nextMode)
            }
        )
    }
}

@Composable
private fun BallastPanel(
    ballastUiState: StateFlow<BallastUiState>,
    hideBallastPill: Boolean,
    widgetManager: MapUIWidgetManager,
    ballastOffset: MutableState<Offset>,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onBallastCommand: (BallastCommand) -> Unit,
    isUiEditMode: Boolean,
    modifier: Modifier = Modifier
) {
    val ballastState by ballastUiState.collectAsStateWithLifecycle()
    val showBallastPill =
        !hideBallastPill && (
            ballastState.isAnimating ||
                ballastState.snapshot.hasBallast ||
                ballastState.snapshot.currentKg > 0.0
            )

    AnimatedVisibility(
        visible = showBallastPill,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
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
}

@Composable
private fun VariometerPanel(
    flightDataManager: FlightDataManager,
    widgetManager: MapUIWidgetManager,
    variometerUiState: VariometerUiState,
    minVariometerSizePx: Float,
    maxVariometerSizePx: Float,
    onVariometerOffsetChange: (Offset) -> Unit,
    onVariometerSizeChange: (Float) -> Unit,
    onVariometerLongPress: () -> Unit,
    onVariometerEditFinished: () -> Unit,
    screenWidthPx: Float,
    screenHeightPx: Float,
    isUiEditMode: Boolean
) {
    val displayNumericVario by flightDataManager.displayVarioFlow.collectAsStateWithLifecycle()
    val animatedVario by animateFloatAsState(
        targetValue = displayNumericVario,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "vario"
    )
    val unitsPreferences = flightDataManager.unitsPreferences
    val varioFormatted by remember(displayNumericVario, unitsPreferences) {
        derivedStateOf {
            UnitsFormatter.verticalSpeed(
                VerticalSpeedMs(displayNumericVario.toDouble()),
                unitsPreferences
            )
        }
    }
    MapUIWidgets.VariometerWidget(
        widgetManager = widgetManager,
        variometerState = variometerUiState,
        needleValue = animatedVario,
        displayValue = displayNumericVario,
        displayLabel = varioFormatted.text,
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
}

@Composable
private fun DistanceCirclesLayer(
    mapState: MapScreenState,
    flightDataManager: FlightDataManager,
    showDistanceCircles: Boolean
) {
    val mapLatitude by flightDataManager.latitudeFlow.collectAsStateWithLifecycle()
    val mapZoom by mapState.currentZoomFlow.collectAsStateWithLifecycle()
    DistanceCirclesCanvas(
        mapZoom = mapZoom,
        mapLatitude = mapLatitude,
        isVisible = showDistanceCircles,
        modifier = Modifier.zIndex(3.7f)
    )
}

@Composable
private fun BoxScope.AatEditFab(
    isAATEditMode: Boolean,
    taskManager: TaskManagerCoordinator,
    cameraManager: MapCameraManager,
    onExitAATEditMode: () -> Unit
) {
    MapTaskIntegration.AATEditModeFAB(
        isAATEditMode = isAATEditMode,
        taskManager = taskManager,
        cameraManager = cameraManager,
        onExitEditMode = onExitAATEditMode,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .zIndex(11f)
    )
}

@Composable
private fun BoxScope.ReplayDevFab(
    onReplayDevFabClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onReplayDevFabClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 96.dp, end = 16.dp)
            .zIndex(15f)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Start sample replay"
        )
    }
}

@Composable
private fun BoxScope.HamburgerMenu(
    widgetManager: MapUIWidgetManager,
    hamburgerOffset: MutableState<Offset>,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    isUiEditMode: Boolean
) {
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
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BoxScope.ReplayControlsSheet(
    session: IgcReplayController.SessionState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChanged: (Double) -> Unit,
    onSeek: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    var sheetHeightPx by remember { mutableStateOf(1f) }
    val swipeableState = rememberSwipeableState(initialValue = ReplaySheetValue.Hidden)
    val anchors = remember(sheetHeightPx) {
        if (sheetHeightPx <= 0f) emptyMap()
        else mapOf(
            0f to ReplaySheetValue.Visible,
            sheetHeightPx to ReplaySheetValue.Hidden
        )
    }

    LaunchedEffect(session.selection) {
        if (session.hasSelection && anchors.isNotEmpty()) {
            swipeableState.animateTo(ReplaySheetValue.Visible)
        } else {
            swipeableState.snapTo(ReplaySheetValue.Hidden)
        }
    }

    val rawOffset = swipeableState.offset.value
    val offsetPx = if (rawOffset.isNaN()) {
        if (session.hasSelection) 0f else sheetHeightPx
    } else {
        rawOffset
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 24.dp)
        .offset {
            IntOffset(0, offsetPx.coerceIn(0f, sheetHeightPx).toInt())
        }
        .let { base ->
            if (anchors.isEmpty()) {
                base
            } else {
                base
                    .swipeable(
                        state = swipeableState,
                        anchors = anchors,
                        thresholds = { _, _ -> FractionalThreshold(0.3f) },
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical
                    )
                    .onGloballyPositioned { coords ->
                        sheetHeightPx = coords.size.height.toFloat()
                    }
            }
        }

    if (session.hasSelection || swipeableState.currentValue == ReplaySheetValue.Visible) {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            ReplayControlsContent(
                state = session,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onSpeedChanged = onSpeedChanged,
                onSeek = onSeek
            )
        }
    }

    if (session.hasSelection && swipeableState.currentValue == ReplaySheetValue.Hidden) {
        AssistChip(
            onClick = { scope.launch { swipeableState.animateTo(ReplaySheetValue.Visible) } },
            label = { Text("Replay controls") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }

}

@Composable
private fun ReplayControlsContent(
    state: IgcReplayController.SessionState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedChanged: (Double) -> Unit,
    onSeek: (Float) -> Unit
) {
    if (!state.hasSelection) return
    val isPlaying = state.status == IgcReplayController.SessionStatus.PLAYING
    val title = state.selection?.displayName ?: "IGC Replay"
    val elapsed = state.elapsedMillis
    val duration = state.durationMillis
    val progress = state.progressFraction
    val speed = state.speedMultiplier

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${formatDuration(elapsed)} / ${formatDuration(duration)} • ${"%.1f".format(speed)}x",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(text = "Timeline", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = progress,
            onValueChange = onSeek,
            modifier = Modifier.fillMaxWidth()
        )
        Text(text = "Speed", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = speed.toFloat(),
                onValueChange = { onSpeedChanged(it.toDouble()) },
                valueRange = 1f..10f,
                modifier = Modifier.fillMaxWidth()
            )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onPlayPause) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause replay"
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play replay"
                    )
                }
            }
            IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop replay"
                )
            }
        }
    }
}

private enum class ReplaySheetValue {
    Hidden,
    Visible
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = (totalSeconds % 60).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val hours = (totalSeconds / 3600).toInt()
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}



