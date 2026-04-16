package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapCameraRuntimePort
import com.example.xcpro.map.MapLocationRenderFrameBinder
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapPoint
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.TaskRenderSnapshot
import com.example.xcpro.map.MapUiEvent
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapLifecycleRuntimePort
import com.example.xcpro.map.MapRenderSurfaceDiagnostics
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import kotlin.math.abs
import org.maplibre.android.maps.MapLibreMap

private const val LiveFollowWatchFocusZoom = 14.0

@Composable
internal fun rememberMapScreenContentInputs(
    showMapBottomNavigation: Boolean,
    allowFlightSensorStart: Boolean,
    isGeneralSettingsVisible: Boolean,
    renderLocalOwnship: Boolean,
    mapViewModel: MapScreenViewModel,
    hotPathBindings: MapScreenHotPathBindings,
    rootUiBinding: MapScreenRootUiBinding,
    bindings: MapScreenBindings,
    trafficOverlayRuntimeInputs: MapTrafficOverlayRuntimeInputs,
    profileLookAndFeelBinding: MapScreenProfileLookAndFeelBinding,
    flightCardsBinding: MapScreenFlightCardsBinding,
    widgetLayout: MapScreenWidgetLayoutBinding,
    variometerLayout: VariometerLayoutState,
    flightDataManager: FlightDataManager,
    mapInitializer: MapInitializer,
    locationManager: MapLocationRuntimePort,
    locationRenderFrameBinder: MapLocationRenderFrameBinder,
    renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics,
    cameraManager: MapCameraRuntimePort,
    overlayManager: MapOverlayManager,
    modalManager: MapModalManager,
    taskScreenManager: MapTaskScreenManager,
    widgetManager: MapUIWidgetManager,
    lifecycleManager: MapLifecycleRuntimePort,
    mapState: MapScreenState,
    mapRuntimeController: MapRuntimeController,
    taskRenderSnapshotProvider: () -> TaskRenderSnapshot,
    density: Density,
    safeContainerSizeState: MutableState<IntSize>,
    shouldBlockDrawerOpen: Boolean,
    onOpenGeneralSettingsFromMap: () -> Unit,
    screenWidthPx: Float = widgetLayout.screenWidthPx,
    screenHeightPx: Float = widgetLayout.screenHeightPx
): MapScreenContentInputs {
    val lifecycleOwner = LocalLifecycleOwner.current
    var watchedPilotFocusEpoch by remember { mutableIntStateOf(0) }
    val mapBindings = bindings.map
    val taskBindings = bindings.task
    val mapUiState = rootUiBinding.mapUiState
    val trafficActions = rememberMapTrafficUiActions(mapViewModel)
    val onForecastSatelliteOverrideChanged: (Boolean) -> Unit = { enabled ->
        mapViewModel.setForecastSatelliteOverrideEnabled(enabled)
    }
    val onMapReady: (MapLibreMap) -> Unit = { map ->
        mapRuntimeController.onMapReady(map)
        applyMapReadyTrafficOverlayConfig(
            port = createTrafficOverlayRenderPort(overlayManager),
            config = createMapReadyTrafficOverlayConfig(trafficOverlayRuntimeInputs)
        )
        watchedPilotFocusEpoch += 1
    }
    val onMapViewBound: () -> Unit = {
        lifecycleManager.syncCurrentOwnerState(lifecycleOwner.lifecycle.currentState)
    }

    return MapScreenContentInputs(
        map = MapScreenMapContentInputs(
            density = density,
            mapState = mapState,
            mapInitializer = mapInitializer,
            onMapReady = onMapReady,
            onMapViewBound = onMapViewBound,
            locationManager = locationManager,
            locationRenderFrameBinder = locationRenderFrameBinder,
            renderSurfaceDiagnostics = renderSurfaceDiagnostics,
            flightDataManager = flightDataManager,
            flightViewModel = flightCardsBinding.flightViewModel,
            taskType = taskBindings.taskType,
            createTaskGestureHandler = mapViewModel::createTaskGestureHandler,
            windArrowState = rootUiBinding.windArrowState,
            showWindSpeedOnVario = rootUiBinding.showWindSpeedOnVario,
            cameraManager = cameraManager,
            currentMode = mapBindings.currentMode,
            visibleModes = mapBindings.visibleModes,
            currentZoom = hotPathBindings.currentZoom,
            onModeChange = { mode -> mapViewModel.setFlightMode(mode) },
            forecastSatelliteOverrideEnabled = mapBindings.forecastSatelliteOverrideEnabled,
            onForecastSatelliteOverrideChanged = onForecastSatelliteOverrideChanged,
            currentLocation = hotPathBindings.currentLocation
        ),
        overlays = MapScreenOverlayContentInputs(
            showMapBottomNavigation = showMapBottomNavigation,
            renderLocalOwnship = renderLocalOwnship,
            showRecenterButton = mapBindings.showRecenterButton,
            showReturnButton = mapBindings.showReturnButton,
            showDistanceCircles = mapBindings.showDistanceCircles,
            showPilotStatusIndicator = allowFlightSensorStart,
            isGeneralSettingsVisible = isGeneralSettingsVisible,
            traffic = bindings.traffic,
            isUiEditMode = mapUiState.isUiEditMode,
            onEditModeChange = { enabled ->
                mapViewModel.onEvent(MapUiEvent.SetUiEditMode(enabled))
            },
            isAATEditMode = taskBindings.isAATEditMode,
            onEnterAATEditMode = mapViewModel::enterAATEditMode,
            onUpdateAATTargetPoint = mapViewModel::updateAATTargetPoint,
            onExitAATEditMode = mapViewModel::exitAATEditMode,
            safeContainerSize = safeContainerSizeState,
            overlayManager = overlayManager,
            modalManager = modalManager,
            taskScreenManager = taskScreenManager,
            taskFlightSurfaceUiState = taskBindings.taskFlightSurfaceUiState,
            taskRenderSnapshotProvider = taskRenderSnapshotProvider,
            watchedPilotFocusEpoch = watchedPilotFocusEpoch,
            mapLibreMapProvider = { mapState.mapLibreMap },
            onFocusWatchedPilot = focusWatchedPilot@ { latitudeDeg, longitudeDeg ->
                val activeMap = mapState.mapLibreMap ?: return@focusWatchedPilot false
                cameraManager.moveTo(
                    target = MapPoint(latitudeDeg, longitudeDeg),
                    zoom = LiveFollowWatchFocusZoom
                )
                activeMap.cameraPosition.target?.let { target ->
                    targetMatchesWatchFocusTarget(
                        latitudeDeg = latitudeDeg,
                        longitudeDeg = longitudeDeg,
                        target = target
                    )
                } ?: false
            },
            waypointData = mapUiState.waypoints,
            unitsPreferences = mapUiState.unitsPreferences,
            qnhCalibrationState = mapUiState.qnhCalibrationState,
            onAutoCalibrateQnh = mapViewModel::onAutoCalibrateQnh,
            onSetManualQnh = mapViewModel::onSetManualQnh,
            trafficActions = trafficActions,
            ballastUiState = mapViewModel.ballastUiState,
            isBallastPillHidden = mapUiState.hideBallastPill,
            onBallastCommand = mapViewModel::submitBallastCommand,
            onHamburgerTap = {
                if (!shouldBlockDrawerOpen || mapUiState.isDrawerOpen) {
                    mapViewModel.onEvent(MapUiEvent.ToggleDrawer)
                }
            },
            onHamburgerLongPress = { mapViewModel.onEvent(MapUiEvent.ToggleUiEditMode) },
            onSettingsTap = onOpenGeneralSettingsFromMap
        ),
        widgets = MapScreenWidgetContentInputs(
            widgetManager = widgetManager,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            variometerUiState = variometerLayout.uiState,
            minVariometerSizePx = variometerLayout.minSizePx,
            maxVariometerSizePx = variometerLayout.maxSizePx,
            onVariometerOffsetChange = { offset ->
                mapViewModel.onVariometerOffsetCommitted(
                    offset = offset.toOffsetPx(),
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx
                )
            },
            onVariometerSizeChange = { newSize ->
                mapViewModel.onVariometerSizeCommitted(
                    sizePx = newSize,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    minSizePx = variometerLayout.minSizePx,
                    maxSizePx = variometerLayout.maxSizePx
                )
            },
            onVariometerLongPress = {},
            onVariometerEditFinished = {},
            hamburgerOffset = widgetLayout.hamburgerOffsetState,
            flightModeOffset = widgetLayout.flightModeOffsetState,
            settingsOffset = widgetLayout.settingsOffsetState,
            ballastOffset = widgetLayout.ballastOffsetState,
            hamburgerSizePx = widgetLayout.hamburgerSizePxState,
            settingsSizePx = widgetLayout.settingsSizePxState,
            onHamburgerOffsetChange = widgetLayout.onHamburgerOffsetChange,
            onFlightModeOffsetChange = widgetLayout.onFlightModeOffsetChange,
            onSettingsOffsetChange = widgetLayout.onSettingsOffsetChange,
            onBallastOffsetChange = widgetLayout.onBallastOffsetChange,
            onHamburgerSizeChange = widgetLayout.onHamburgerSizeChange,
            onSettingsSizeChange = widgetLayout.onSettingsSizeChange,
            cardStyle = profileLookAndFeelBinding.cardStyle,
            hiddenCardIds = rootUiBinding.hiddenCardIds
        ),
        replay = MapScreenReplayContentInputs(
            replayState = mapViewModel.replaySessionState,
            showVarioDemoFab = mapViewModel.showVarioDemoFab,
            onSyntheticThermalReplayClick = mapViewModel::onSyntheticThermalReplay,
            onSyntheticThermalReplayWindNoisyClick = mapViewModel::onSyntheticThermalReplayWindNoisy,
            onVarioDemoReferenceClick = mapViewModel::onVarioDemoReplay,
            onVarioDemoSimClick = mapViewModel::onVarioDemoReplaySim,
            onVarioDemoSim2Click = mapViewModel::onVarioDemoReplaySimLive,
            onVarioDemoSim3Click = mapViewModel::onVarioDemoReplaySim3,
            showRacingReplayFab = mapViewModel.showRacingReplayFab,
            onRacingReplayClick = mapViewModel::onRacingTaskReplay
        )
    )
}

private fun targetMatchesWatchFocusTarget(
    latitudeDeg: Double,
    longitudeDeg: Double,
    target: org.maplibre.android.geometry.LatLng,
    epsilonDeg: Double = 1e-6
): Boolean {
    return abs(target.latitude - latitudeDeg) <= epsilonDeg &&
        abs(target.longitude - longitudeDeg) <= epsilonDeg
}
