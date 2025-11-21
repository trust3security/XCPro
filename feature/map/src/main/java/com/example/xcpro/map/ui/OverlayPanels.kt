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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.CompassWidget
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.map.DistanceCirclesCanvas
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.map.FlightDataManager
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun CompassPanel(
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
internal fun BallastPanel(
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
internal fun VariometerPanel(
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
internal fun DistanceCirclesLayer(
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
internal fun androidx.compose.foundation.layout.BoxScope.AatEditFab(
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
internal fun androidx.compose.foundation.layout.BoxScope.ReplayDevFab(
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
internal fun androidx.compose.foundation.layout.BoxScope.HamburgerMenu(
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
