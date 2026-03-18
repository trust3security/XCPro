package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.MapUiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap

private const val MapScreenScaffoldInputsTag = "MapScreen"

@Composable
internal fun rememberMapScreenScaffoldInputs(
    coroutineScope: CoroutineScope,
    navController: NavHostController,
    drawerState: DrawerState,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    initialMapStyle: String,
    onMapStyleSelected: (String) -> Unit,
    onOpenGeneralSettings: () -> Unit,
    mapViewModel: MapScreenViewModel,
    hotPathBindings: MapScreenHotPathBindings,
    rootUiBinding: MapScreenRootUiBinding,
    bindings: MapScreenBindings,
    profileLookAndFeelBinding: MapScreenProfileLookAndFeelBinding,
    flightCardsBinding: MapScreenFlightCardsBinding,
    widgetLayout: MapScreenWidgetLayoutBinding,
    variometerLayout: VariometerLayoutState,
    flightDataManager: FlightDataManager,
    managers: MapScreenManagers,
    mapState: MapScreenState,
    mapRuntimeController: MapRuntimeController,
    density: Density,
    safeContainerSizeState: MutableState<IntSize>,
    screenWidthPx: Float = widgetLayout.screenWidthPx,
    screenHeightPx: Float = widgetLayout.screenHeightPx
): MapScreenScaffoldInputs {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapBindings = bindings.map
    val taskBindings = bindings.task
    val mapUiState = rootUiBinding.mapUiState
    val trafficActions = rememberMapTrafficUiActions(mapViewModel)
    val onDrawerItemSelected: (String) -> Unit = { item ->
        Log.d(MapScreenScaffoldInputsTag, "Navigation drawer item selected: $item")
        coroutineScope.launch {
            drawerState.close()
            managers.taskScreenManager.handleNavigationTaskSelection(item)
        }
    }
    val onResolvedMapStyleSelected: (String) -> Unit = { style ->
        mapViewModel.setMapStyle(style)
        coroutineScope.launch { mapViewModel.persistMapStyle(style) }
        Log.d(MapScreenScaffoldInputsTag, "Map style selected: $style")
        onMapStyleSelected(style)
    }
    val onTransientMapStyleSelected: (String) -> Unit = { style ->
        mapViewModel.setMapStyle(style)
    }
    val onMapReady: (MapLibreMap) -> Unit = { map ->
        mapRuntimeController.onMapReady(map)
        applyMapReadyTrafficOverlayConfig(
            port = createTrafficOverlayRenderPort(managers.overlayManager),
            config = createMapReadyTrafficOverlayConfig(bindings.traffic)
        )
    }
    val onMapViewBound: () -> Unit = {
        managers.lifecycleManager.syncCurrentOwnerState(lifecycleOwner.lifecycle.currentState)
    }
    val shouldBlockDrawerOpen = MapTaskIntegration.shouldBlockDrawerGestures(
        taskType = taskBindings.taskType,
        isAATEditMode = taskBindings.isAATEditMode
    )
    val openGeneralSettingsFromMap: () -> Unit = {
        if (!shouldBlockDrawerOpen) {
            settingsExpanded.value = true
            coroutineScope.launch {
                if (drawerState.isOpen) {
                    drawerState.close()
                }
                onOpenGeneralSettings()
            }
        }
    }
    return MapScreenScaffoldInputs(
        scaffold = MapScreenScaffoldChromeInputs(
            drawerState = drawerState,
            navController = navController,
            profileExpanded = profileExpanded,
            mapStyleExpanded = mapStyleExpanded,
            settingsExpanded = settingsExpanded,
            initialMapStyle = initialMapStyle,
            onDrawerItemSelected = onDrawerItemSelected,
            onMapStyleSelected = onResolvedMapStyleSelected,
            gpsStatus = mapBindings.gpsStatus,
            isLoadingWaypoints = mapUiState.isLoadingWaypoints,
            onOpenGeneralSettingsFromDrawer = openGeneralSettingsFromMap
        ),
        content = MapScreenContentInputs(
            map = MapScreenMapContentInputs(
                density = density,
                mapState = mapState,
                mapInitializer = managers.mapInitializer,
                onMapReady = onMapReady,
                onMapViewBound = onMapViewBound,
                locationManager = managers.locationManager,
                locationRenderFrameBinder = managers.locationRenderFrameBinder,
                flightDataManager = flightDataManager,
                flightViewModel = flightCardsBinding.flightViewModel,
                taskType = taskBindings.taskType,
                createTaskGestureHandler = mapViewModel::createTaskGestureHandler,
                windArrowState = rootUiBinding.windArrowState,
                showWindSpeedOnVario = rootUiBinding.showWindSpeedOnVario,
                cameraManager = managers.cameraManager,
                currentMode = mapBindings.currentMode,
                currentZoom = hotPathBindings.currentZoom,
                onModeChange = mapViewModel::setFlightMode,
                currentMapStyleName = mapBindings.mapStyleName,
                onTransientMapStyleSelected = onTransientMapStyleSelected,
                currentLocation = hotPathBindings.currentLocation
            ),
            overlays = MapScreenOverlayContentInputs(
                showRecenterButton = mapBindings.showRecenterButton,
                showReturnButton = mapBindings.showReturnButton,
                showDistanceCircles = mapBindings.showDistanceCircles,
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
                overlayManager = managers.overlayManager,
                modalManager = managers.modalManager,
                taskScreenManager = managers.taskScreenManager,
                taskRenderSnapshotProvider = mapViewModel.runtimeDependencies.tasksUseCase::taskRenderSnapshot,
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
                onSettingsTap = openGeneralSettingsFromMap
            ),
            widgets = MapScreenWidgetContentInputs(
                widgetManager = managers.widgetManager,
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
                onVarioDemoReferenceClick = mapViewModel::onVarioDemoReplay,
                onVarioDemoSimClick = mapViewModel::onVarioDemoReplaySim,
                onVarioDemoSim2Click = mapViewModel::onVarioDemoReplaySimLive,
                onVarioDemoSim3Click = mapViewModel::onVarioDemoReplaySim3,
                showRacingReplayFab = mapViewModel.showRacingReplayFab,
                onRacingReplayClick = mapViewModel::onRacingTaskReplay
            )
        )
    )
}
