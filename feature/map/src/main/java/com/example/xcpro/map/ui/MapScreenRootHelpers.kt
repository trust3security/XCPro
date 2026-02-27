package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.MapCameraEffects
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.widgets.MapWidgetId
import com.example.xcpro.map.widgets.MapWidgetLayoutViewModel
import com.example.xcpro.map.widgets.MapWidgetOffsets
import com.example.xcpro.variometer.layout.VariometerUiState
import kotlinx.coroutines.flow.collect
import kotlin.math.min
import kotlin.math.roundToInt

internal data class MapWidgetOffsetStates(
    val hamburgerOffset: MutableState<Offset>,
    val flightModeOffset: MutableState<Offset>,
    val settingsOffset: MutableState<Offset>,
    val ballastOffset: MutableState<Offset>
)

internal data class VariometerLayoutState(
    val uiState: VariometerUiState,
    val minSizePx: Float,
    val maxSizePx: Float
)

internal data class MapScreenWidgetLayoutBinding(
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val hamburgerOffsetState: MutableState<Offset>,
    val flightModeOffsetState: MutableState<Offset>,
    val settingsOffsetState: MutableState<Offset>,
    val ballastOffsetState: MutableState<Offset>,
    val onHamburgerOffsetChange: (Offset) -> Unit,
    val onFlightModeOffsetChange: (Offset) -> Unit,
    val onSettingsOffsetChange: (Offset) -> Unit,
    val onBallastOffsetChange: (Offset) -> Unit
)

@Composable
internal fun rememberMapScreenWidgetLayoutBinding(
    density: Density
): MapScreenWidgetLayoutBinding {
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val widgetLayoutViewModel: MapWidgetLayoutViewModel = hiltViewModel()
    val densityScale = remember(density) { DensityScale(density = density.density, fontScale = density.fontScale) }
    val widgetOffsets by widgetLayoutViewModel.offsets.collectAsStateWithLifecycle()
    LaunchedEffect(screenWidthPx, screenHeightPx, densityScale) {
        widgetLayoutViewModel.loadLayout(screenWidthPx, screenHeightPx, densityScale)
    }
    val resolvedWidgetOffsets = widgetOffsets ?: MapWidgetOffsets(
        sideHamburger = OffsetPx.Zero,
        flightMode = OffsetPx.Zero,
        settingsShortcut = OffsetPx.Zero,
        ballast = OffsetPx.Zero
    )
    val offsetStates = rememberMapWidgetOffsets(resolvedWidgetOffsets)
    val onHamburgerOffsetChange: (Offset) -> Unit = { offset ->
        offsetStates.hamburgerOffset.value = offset
        widgetLayoutViewModel.updateOffset(MapWidgetId.SIDE_HAMBURGER, offset.toOffsetPx())
    }
    val onFlightModeOffsetChange: (Offset) -> Unit = { offset ->
        offsetStates.flightModeOffset.value = offset
        widgetLayoutViewModel.updateOffset(MapWidgetId.FLIGHT_MODE, offset.toOffsetPx())
    }
    val onSettingsOffsetChange: (Offset) -> Unit = { offset ->
        offsetStates.settingsOffset.value = offset
        widgetLayoutViewModel.updateOffset(MapWidgetId.SETTINGS_SHORTCUT, offset.toOffsetPx())
    }
    val onBallastOffsetChange: (Offset) -> Unit = { offset ->
        offsetStates.ballastOffset.value = offset
        widgetLayoutViewModel.updateOffset(MapWidgetId.BALLAST, offset.toOffsetPx())
    }
    return MapScreenWidgetLayoutBinding(
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        hamburgerOffsetState = offsetStates.hamburgerOffset,
        flightModeOffsetState = offsetStates.flightModeOffset,
        settingsOffsetState = offsetStates.settingsOffset,
        ballastOffsetState = offsetStates.ballastOffset,
        onHamburgerOffsetChange = onHamburgerOffsetChange,
        onFlightModeOffsetChange = onFlightModeOffsetChange,
        onSettingsOffsetChange = onSettingsOffsetChange,
        onBallastOffsetChange = onBallastOffsetChange
    )
}

@Composable
internal fun rememberMapWidgetOffsets(
    widgetOffsets: MapWidgetOffsets
): MapWidgetOffsetStates {
    val hamburgerOffsetState = remember { mutableStateOf(widgetOffsets.sideHamburger.toComposeOffset()) }
    val flightModeOffsetState = remember { mutableStateOf(widgetOffsets.flightMode.toComposeOffset()) }
    val settingsOffsetState = remember { mutableStateOf(widgetOffsets.settingsShortcut.toComposeOffset()) }
    val ballastOffsetState = remember { mutableStateOf(widgetOffsets.ballast.toComposeOffset()) }

    LaunchedEffect(widgetOffsets.sideHamburger) {
        hamburgerOffsetState.value = widgetOffsets.sideHamburger.toComposeOffset()
    }
    LaunchedEffect(widgetOffsets.flightMode) {
        flightModeOffsetState.value = widgetOffsets.flightMode.toComposeOffset()
    }
    LaunchedEffect(widgetOffsets.settingsShortcut) {
        settingsOffsetState.value = widgetOffsets.settingsShortcut.toComposeOffset()
    }
    LaunchedEffect(widgetOffsets.ballast) {
        ballastOffsetState.value = widgetOffsets.ballast.toComposeOffset()
    }

    return MapWidgetOffsetStates(
        hamburgerOffset = hamburgerOffsetState,
        flightModeOffset = flightModeOffsetState,
        settingsOffset = settingsOffsetState,
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
