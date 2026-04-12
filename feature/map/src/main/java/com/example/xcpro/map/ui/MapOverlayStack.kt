package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.MapGestureSetup
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapCameraRuntimePort
import com.example.xcpro.map.MapGestureRegion
import com.example.xcpro.map.MapLocationRenderFrameBinder
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapModalUI
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.MapRenderSurfaceDiagnostics
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.geometry.LatLng

@Composable
@Suppress("LongParameterList", "UNUSED_PARAMETER")
internal fun MapOverlayStack(
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (org.maplibre.android.maps.MapLibreMap) -> Unit,
    onMapViewBound: () -> Unit,
    locationManager: MapLocationRuntimePort,
    locationRenderFrameBinder: MapLocationRenderFrameBinder,
    renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    taskType: TaskType,
    createTaskGestureHandler: (TaskGestureCallbacks) -> TaskGestureHandler,
    windArrowState: WindArrowUiState,
    showWindSpeedOnVario: Boolean,
    cameraManager: MapCameraRuntimePort,
    currentMode: FlightMode,
    visibleModes: List<FlightMode>,
    currentZoomFlow: StateFlow<Float>,
    unitsPreferences: UnitsPreferences,
    onModeChange: (FlightMode) -> Unit,
    currentLocationFlow: StateFlow<MapLocationUiModel?>,
    renderLocalOwnship: Boolean,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    ognOverlayEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    overlayManager: MapOverlayManager,
    onOgnTargetSelected: (String) -> Unit,
    onOgnThermalSelected: (String) -> Unit,
    onAdsbTargetSelected: (Icao24) -> Unit,
    onForecastWindArrowSpeedTap: (LatLng, Double) -> Unit,
    onMapLongPress: (LatLng) -> Unit,
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
    settingsOffset: MutableState<Offset>,
    ballastOffset: MutableState<Offset>,
    hamburgerSizePx: MutableState<Float>,
    settingsSizePx: MutableState<Float>,
    onHamburgerOffsetChange: (Offset) -> Unit,
    onFlightModeOffsetChange: (Offset) -> Unit,
    onSettingsOffsetChange: (Offset) -> Unit,
    onBallastOffsetChange: (Offset) -> Unit,
    onHamburgerSizeChange: (Float) -> Unit,
    onSettingsSizeChange: (Float) -> Unit,
    widgetManager: MapUIWidgetManager,
    screenWidthPx: Float,
    screenHeightPx: Float,
    modalManager: MapModalManager,
    ballastUiState: StateFlow<BallastUiState>,
    hideBallastPill: Boolean,
    onBallastCommand: (BallastCommand) -> Unit,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    onSettingsTap: () -> Unit,
    cardStyle: CardStyle,
    hiddenCardIds: Set<String>,
    replayState: StateFlow<SessionState>
) {
    val gestureRegions by widgetManager.gestureRegions.collectAsStateWithLifecycle()

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
            onMapViewBound = onMapViewBound,
            locationRenderFrameBinder = locationRenderFrameBinder,
            renderSurfaceDiagnostics = renderSurfaceDiagnostics,
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

        MapGestureHandlerRuntimeLayer(
            enabled = !isUiEditMode,
            mapState = mapState,
            taskType = taskType,
            visibleModes = visibleModes,
            locationManager = locationManager,
            cameraManager = cameraManager,
            currentMode = currentMode,
            onModeChange = onModeChange,
            currentLocationFlow = currentLocationFlow,
            renderLocalOwnship = renderLocalOwnship,
            showRecenterButton = showRecenterButton,
            showReturnButton = showReturnButton,
            isAATEditMode = isAATEditMode,
            createTaskGestureHandler = createTaskGestureHandler,
            onEnterAATEditMode = onEnterAATEditMode,
            onExitAATEditMode = onExitAATEditMode,
            onPreviewAATTargetPoint = { waypointIndex, lat, lon ->
                overlayManager.previewAatTargetPoint(waypointIndex, lat, lon)
            },
            onUpdateAATTargetPoint = onUpdateAATTargetPoint,
            onSyncTaskVisuals = { overlayManager.requestTaskRenderSync() },
            ognOverlayEnabled = ognOverlayEnabled,
            showOgnThermalsEnabled = showOgnThermalsEnabled,
            overlayManager = overlayManager,
            onOgnTargetSelected = onOgnTargetSelected,
            onOgnThermalSelected = onOgnThermalSelected,
            onAdsbTargetSelected = onAdsbTargetSelected,
            onForecastWindArrowSpeedTap = onForecastWindArrowSpeedTap,
            onMapLongPress = onMapLongPress,
            gestureRegions = gestureRegions
        )

        MapUIWidgets.FlightModeMenu(
            widgetManager = widgetManager,
            currentMode = currentMode,
            visibleModes = visibleModes,
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

        DistanceCirclesRuntimeLayer(
            mapState = mapState,
            currentZoomFlow = currentZoomFlow,
            currentLocationFlow = currentLocationFlow,
            renderLocalOwnship = renderLocalOwnship,
            showDistanceCircles = showDistanceCircles,
            unitsPreferences = unitsPreferences
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
            hamburgerSizePx = hamburgerSizePx,
            onHamburgerTap = onHamburgerTap,
            onHamburgerLongPress = onHamburgerLongPress,
            onOffsetChange = onHamburgerOffsetChange,
            onSizeChange = onHamburgerSizeChange,
            isUiEditMode = isUiEditMode
        )

        SettingsShortcut(
            widgetManager = widgetManager,
            settingsOffset = settingsOffset,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            settingsSizePx = settingsSizePx,
            onSettingsTap = onSettingsTap,
            onOffsetChange = onSettingsOffsetChange,
            onSizeChange = onSettingsSizeChange,
            isUiEditMode = isUiEditMode
        )

        MapModalUI.AirspaceSettingsModalOverlay(modalManager = modalManager)
    }
}

@Composable
private fun MapGestureHandlerRuntimeLayer(
    enabled: Boolean,
    mapState: MapScreenState,
    taskType: TaskType,
    visibleModes: List<FlightMode>,
    locationManager: MapLocationRuntimePort,
    cameraManager: MapCameraRuntimePort,
    currentMode: FlightMode,
    onModeChange: (FlightMode) -> Unit,
    currentLocationFlow: StateFlow<MapLocationUiModel?>,
    renderLocalOwnship: Boolean,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    isAATEditMode: Boolean,
    createTaskGestureHandler: (TaskGestureCallbacks) -> TaskGestureHandler,
    onEnterAATEditMode: (Int) -> Unit,
    onExitAATEditMode: () -> Unit,
    onPreviewAATTargetPoint: (Int, Double, Double) -> Unit,
    onUpdateAATTargetPoint: (Int, Double, Double) -> Unit,
    onSyncTaskVisuals: () -> Unit,
    ognOverlayEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    overlayManager: MapOverlayManager,
    onOgnTargetSelected: (String) -> Unit,
    onOgnThermalSelected: (String) -> Unit,
    onAdsbTargetSelected: (Icao24) -> Unit,
    onForecastWindArrowSpeedTap: (LatLng, Double) -> Unit,
    onMapLongPress: (LatLng) -> Unit,
    gestureRegions: List<MapGestureRegion>
) {
    if (!enabled) return

    val currentLocation = currentLocationFlow.collectAsStateWithLifecycle().value
    val localOwnshipRenderState = resolveMapLocalOwnshipRenderState(
        renderLocalOwnship = renderLocalOwnship,
        currentLocation = currentLocation,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton
    )
    MapGestureSetup.GestureHandlerOverlay(
        mapState = mapState,
        taskType = taskType,
        visibleModes = visibleModes,
        locationManager = locationManager,
        cameraManager = cameraManager,
        currentMode = currentMode,
        onModeChange = onModeChange,
        currentLocation = localOwnshipRenderState.currentLocation,
        showReturnButton = localOwnshipRenderState.showReturnButton,
        isAATEditMode = isAATEditMode,
        createTaskGestureHandler = createTaskGestureHandler,
        onEnterAATEditMode = onEnterAATEditMode,
        onExitAATEditMode = onExitAATEditMode,
        onPreviewAATTargetPoint = onPreviewAATTargetPoint,
        onUpdateAATTargetPoint = onUpdateAATTargetPoint,
        onSyncTaskVisuals = onSyncTaskVisuals,
        onMapTap = { tap ->
            if (ognOverlayEnabled) {
                val tappedOgnId = overlayManager.findOgnTargetAt(tap)
                if (tappedOgnId != null) {
                    onOgnTargetSelected(tappedOgnId)
                    return@GestureHandlerOverlay
                }
            }

            if (ognOverlayEnabled && showOgnThermalsEnabled) {
                val tappedThermalId = overlayManager.findOgnThermalHotspotAt(tap)
                if (tappedThermalId != null) {
                    onOgnThermalSelected(tappedThermalId)
                    return@GestureHandlerOverlay
                }
            }

            val tappedAdsbId = overlayManager.findAdsbTargetAt(tap)
            if (tappedAdsbId != null) {
                onAdsbTargetSelected(tappedAdsbId)
                return@GestureHandlerOverlay
            }

            val tappedWindSpeedKt = overlayManager.findForecastWindArrowSpeedAt(tap)
            if (tappedWindSpeedKt != null) {
                onForecastWindArrowSpeedTap(tap, tappedWindSpeedKt)
            }
        },
        onMapLongPress = onMapLongPress,
        gestureRegions = gestureRegions,
        modifier = Modifier.zIndex(3.6f)
    )
}

@Composable
private fun DistanceCirclesRuntimeLayer(
    mapState: MapScreenState,
    currentZoomFlow: StateFlow<Float>,
    currentLocationFlow: StateFlow<MapLocationUiModel?>,
    renderLocalOwnship: Boolean,
    showDistanceCircles: Boolean,
    unitsPreferences: UnitsPreferences
) {
    val currentZoom by currentZoomFlow.collectAsStateWithLifecycle()
    val currentLocation = currentLocationFlow.collectAsStateWithLifecycle().value
    DistanceCirclesLayer(
        mapState = mapState,
        currentZoom = currentZoom,
        currentLocation = currentLocation.takeIf { renderLocalOwnship },
        showDistanceCircles = showDistanceCircles,
        unitsPreferences = unitsPreferences
    )
}
