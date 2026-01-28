package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.MapCameraEffects
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.variometer.layout.VariometerUiState
import kotlinx.coroutines.flow.collect
import kotlin.math.min
import kotlin.math.roundToInt

internal data class MapWidgetOffsetStates(
    val hamburgerOffset: MutableState<Offset>,
    val flightModeOffset: MutableState<Offset>,
    val ballastOffset: MutableState<Offset>
)

internal data class VariometerLayoutState(
    val uiState: VariometerUiState,
    val minSizePx: Float,
    val maxSizePx: Float
)

@Composable
internal fun rememberMapWidgetOffsets(
    widgetManager: MapUIWidgetManager,
    screenWidthPx: Float,
    screenHeightPx: Float,
    density: Density
): MapWidgetOffsetStates {
    val widgetPositions = remember(screenWidthPx, screenHeightPx) {
        widgetManager.loadWidgetPositions(screenWidthPx, screenHeightPx, density)
    }
    val hamburgerOffsetState = remember { mutableStateOf(widgetPositions.sideHamburgerOffset) }
    val flightModeOffsetState = remember { mutableStateOf(widgetPositions.flightModeOffset) }
    val ballastOffsetState = remember { mutableStateOf(widgetPositions.ballastOffset) }

    return MapWidgetOffsetStates(
        hamburgerOffset = hamburgerOffsetState,
        flightModeOffset = flightModeOffsetState,
        ballastOffset = ballastOffsetState
    )
}

@Composable
internal fun ensureSafeContainerFallback(
    safeContainerSizeState: MutableState<IntSize>,
    screenWidthPx: Float,
    screenHeightPx: Float
) {
    LaunchedEffect(screenWidthPx, screenHeightPx) {
        if (safeContainerSizeState.value == IntSize.Zero) {
            val fallbackWidth = screenWidthPx.roundToInt().coerceAtLeast(1)
            val fallbackHeight = screenHeightPx.roundToInt().coerceAtLeast(1)
            if (fallbackWidth > 0 && fallbackHeight > 0) {
                safeContainerSizeState.value = IntSize(fallbackWidth, fallbackHeight)
            }
        }
    }
}

@Composable
internal fun rememberVariometerLayout(
    mapViewModel: MapScreenViewModel,
    screenWidthPx: Float,
    screenHeightPx: Float,
    density: Density
): VariometerLayoutState {
    val variometerUiState by mapViewModel.variometerUiState.collectAsStateWithLifecycle()
    val minVariometerSizePx = with(density) { 60.dp.toPx() }
    val maxVariometerSizePx = min(screenWidthPx, screenHeightPx)
    val defaultVariometerSizePx = with(density) { 150.dp.toPx() }

    LaunchedEffect(screenWidthPx, screenHeightPx) {
        mapViewModel.ensureVariometerLayout(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            defaultSizePx = defaultVariometerSizePx,
            minSizePx = minVariometerSizePx,
            maxSizePx = maxVariometerSizePx
        )
    }

    return VariometerLayoutState(
        uiState = variometerUiState,
        minSizePx = minVariometerSizePx,
        maxSizePx = maxVariometerSizePx
    )
}

@Composable
internal fun trackSafeContainerSize(
    safeContainerSize: IntSize,
    onSizeReady: (IntSize) -> Unit
) {
    LaunchedEffect(safeContainerSize) {
        Log.d("MapScreen", "= CONTAINER SIZE CHANGED: $safeContainerSize")
        if (safeContainerSize.width > 0 && safeContainerSize.height > 0) {
            onSizeReady(safeContainerSize)
        }
    }
}

@Composable
internal fun rememberMapRuntimeController(
    overlayManager: MapOverlayManager,
    mapViewModel: MapScreenViewModel,
    cameraManager: MapCameraManager,
    orientationData: OrientationData,
    isReplayPlaying: Boolean
): MapRuntimeController {
    // Centralized camera effects to keep map animations + orientation in sync.
    MapCameraEffects.AllCameraEffects(
        cameraManager = cameraManager,
        bearing = orientationData.bearing,
        orientationMode = orientationData.mode,
        bearingSource = orientationData.bearingSource,
        replayPlaying = isReplayPlaying
    )

    val mapRuntimeController = remember(overlayManager) {
        MapRuntimeController(overlayManager)
    }

    LaunchedEffect(mapRuntimeController) {
        mapViewModel.mapCommands.collect { command ->
            mapRuntimeController.apply(command)
        }
    }

    return mapRuntimeController
}
