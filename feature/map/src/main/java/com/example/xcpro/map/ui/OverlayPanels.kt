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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.CompassWidget
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.map.DistanceCirclesCanvas
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.sensors.GPSData
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
    ballastOffset: androidx.compose.runtime.MutableState<Offset>,
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
        onEditFinished = onVariometerEditFinished
    )
}

@Composable
internal fun DistanceCirclesLayer(
    mapState: MapScreenState,
    currentLocation: GPSData?,
    showDistanceCircles: Boolean
) {
    val zoom by mapState.currentZoomFlow.collectAsStateWithLifecycle()
    val latitude = currentLocation?.latLng?.latitude ?: 0.0
    AnimatedVisibility(
        visible = showDistanceCircles,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .zIndex(1f)
    ) {
        DistanceCirclesCanvas(
            mapZoom = zoom,
            mapLatitude = latitude,
            modifier = Modifier.fillMaxSize()
        )
    }
}
