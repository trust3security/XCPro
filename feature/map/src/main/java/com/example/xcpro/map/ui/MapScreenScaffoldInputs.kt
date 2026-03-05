package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.MapUiEvent
import com.example.xcpro.map.MapUiState
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.variometer.layout.VariometerUiState
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
    mapViewModel: MapScreenViewModel,
    mapUiState: MapUiState,
    bindings: MapScreenBindings,
    managers: MapScreenManagers,
    mapState: MapScreenState,
    mapRuntimeController: MapRuntimeController,
    density: Density,
    screenWidthPx: Float,
    screenHeightPx: Float,
    variometerUiState: VariometerUiState,
    minVariometerSizePx: Float,
    maxVariometerSizePx: Float,
    safeContainerSizeState: MutableState<IntSize>,
    hamburgerOffsetState: MutableState<Offset>,
    flightModeOffsetState: MutableState<Offset>,
    settingsOffsetState: MutableState<Offset>,
    ballastOffsetState: MutableState<Offset>,
    hamburgerSizePxState: MutableState<Float>,
    settingsSizePxState: MutableState<Float>,
    onHamburgerOffsetChange: (Offset) -> Unit,
    onFlightModeOffsetChange: (Offset) -> Unit,
    onSettingsOffsetChange: (Offset) -> Unit,
    onBallastOffsetChange: (Offset) -> Unit,
    onHamburgerSizeChange: (Float) -> Unit,
    onSettingsSizeChange: (Float) -> Unit,
    flightViewModel: FlightDataViewModel,
    flightDataManager: FlightDataManager,
    windArrowState: WindArrowUiState,
    showWindSpeedOnVario: Boolean,
    cardStyle: CardStyle,
    hiddenCardIds: Set<String>
): MapScreenScaffoldInputs {
    val lifecycleOwner = LocalLifecycleOwner.current
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
        managers.overlayManager.setOgnDisplayUpdateMode(bindings.ognDisplayUpdateMode)
        managers.overlayManager.setOgnIconSizePx(bindings.ognIconSizePx)
        managers.overlayManager.setAdsbIconSizePx(bindings.adsbIconSizePx)
        managers.overlayManager.setAdsbEmergencyFlashEnabled(bindings.adsbEmergencyFlashEnabled)
    }
    val onMapViewBound: () -> Unit = {
        managers.lifecycleManager.syncCurrentOwnerState(lifecycleOwner.lifecycle.currentState)
    }
    val shouldBlockDrawerOpen = MapTaskIntegration.shouldBlockDrawerGestures(
        taskType = bindings.taskType,
        isAATEditMode = bindings.isAATEditMode
    )
    val openGeneralSettingsFromMap: () -> Unit = {
        if (!shouldBlockDrawerOpen) {
            settingsExpanded.value = true
            coroutineScope.launch {
                if (drawerState.isOpen) {
                    drawerState.close()
                }
                managers.modalManager.showGeneralSettingsModal()
            }
        }
    }
    return MapScreenScaffoldInputs(
        drawerState = drawerState,
        navController = navController,
        profileExpanded = profileExpanded,
        mapStyleExpanded = mapStyleExpanded,
        settingsExpanded = settingsExpanded,
        initialMapStyle = initialMapStyle,
        currentMapStyleName = bindings.mapStyleName,
        onDrawerItemSelected = onDrawerItemSelected,
        onMapStyleSelected = onResolvedMapStyleSelected,
        onTransientMapStyleSelected = onTransientMapStyleSelected,
        gpsStatus = bindings.gpsStatus,
        isLoadingWaypoints = mapUiState.isLoadingWaypoints,
        density = density,
        mapState = mapState,
        mapInitializer = managers.mapInitializer,
        onMapReady = onMapReady,
        onMapViewBound = onMapViewBound,
        locationManager = managers.locationManager,
        flightDataManager = flightDataManager,
        flightViewModel = flightViewModel,
        taskType = bindings.taskType,
        createTaskGestureHandler = mapViewModel::createTaskGestureHandler,
        windArrowState = windArrowState,
        showWindSpeedOnVario = showWindSpeedOnVario,
        cameraManager = managers.cameraManager,
        currentMode = bindings.currentMode,
        currentZoom = bindings.currentZoom,
        onModeChange = mapViewModel::setFlightMode,
        currentLocation = bindings.locationForUi,
        showRecenterButton = bindings.showRecenterButton,
        showReturnButton = bindings.showReturnButton,
        showDistanceCircles = bindings.showDistanceCircles,
        ognSnapshot = bindings.ognSnapshot,
        ognOverlayEnabled = bindings.ognOverlayEnabled,
        ognThermalHotspots = bindings.ognThermalHotspots,
        showOgnSciaEnabled = bindings.showOgnSciaEnabled,
        showOgnThermalsEnabled = bindings.showOgnThermalsEnabled,
        adsbSnapshot = bindings.adsbSnapshot,
        adsbOverlayEnabled = bindings.adsbOverlayEnabled,
        selectedOgnTarget = bindings.selectedOgnTarget,
        selectedOgnThermal = bindings.selectedOgnThermal,
        selectedAdsbTarget = bindings.selectedAdsbTarget,
        isUiEditMode = mapUiState.isUiEditMode,
        onEditModeChange = { enabled ->
            mapViewModel.onEvent(MapUiEvent.SetUiEditMode(enabled))
        },
        isAATEditMode = bindings.isAATEditMode,
        onEnterAATEditMode = mapViewModel::enterAATEditMode,
        onUpdateAATTargetPoint = mapViewModel::updateAATTargetPoint,
        onExitAATEditMode = mapViewModel::exitAATEditMode,
        safeContainerSize = safeContainerSizeState,
        overlayManager = managers.overlayManager,
        modalManager = managers.modalManager,
        widgetManager = managers.widgetManager,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        variometerUiState = variometerUiState,
        minVariometerSizePx = minVariometerSizePx,
        maxVariometerSizePx = maxVariometerSizePx,
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
                minSizePx = minVariometerSizePx,
                maxSizePx = maxVariometerSizePx
            )
        },
        onVariometerLongPress = {},
        onVariometerEditFinished = {},
        hamburgerOffset = hamburgerOffsetState,
        flightModeOffset = flightModeOffsetState,
        settingsOffset = settingsOffsetState,
        ballastOffset = ballastOffsetState,
        hamburgerSizePx = hamburgerSizePxState,
        settingsSizePx = settingsSizePxState,
        onHamburgerOffsetChange = onHamburgerOffsetChange,
        onFlightModeOffsetChange = onFlightModeOffsetChange,
        onSettingsOffsetChange = onSettingsOffsetChange,
        onBallastOffsetChange = onBallastOffsetChange,
        onHamburgerSizeChange = onHamburgerSizeChange,
        onSettingsSizeChange = onSettingsSizeChange,
        taskScreenManager = managers.taskScreenManager,
        waypointData = mapUiState.waypoints,
        unitsPreferences = mapUiState.unitsPreferences,
        qnhCalibrationState = mapUiState.qnhCalibrationState,
        onAutoCalibrateQnh = mapViewModel::onAutoCalibrateQnh,
        onSetManualQnh = mapViewModel::onSetManualQnh,
        onToggleOgnTraffic = mapViewModel::onToggleOgnTraffic,
        onToggleOgnScia = mapViewModel::onToggleOgnScia,
        onToggleOgnThermals = mapViewModel::onToggleOgnThermals,
        onToggleAdsbTraffic = mapViewModel::onToggleAdsbTraffic,
        onOgnTargetSelected = mapViewModel::onOgnTargetSelected,
        onOgnThermalSelected = mapViewModel::onOgnThermalSelected,
        onAdsbTargetSelected = mapViewModel::onAdsbTargetSelected,
        onDismissOgnTargetDetails = mapViewModel::dismissSelectedOgnTarget,
        onDismissOgnThermalDetails = mapViewModel::dismissSelectedOgnThermal,
        onDismissAdsbTargetDetails = mapViewModel::dismissSelectedAdsbTarget,
        ballastUiState = mapViewModel.ballastUiState,
        isBallastPillHidden = mapUiState.hideBallastPill,
        onBallastCommand = mapViewModel::submitBallastCommand,
        onHamburgerTap = {
            if (!shouldBlockDrawerOpen || mapUiState.isDrawerOpen) {
                mapViewModel.onEvent(MapUiEvent.ToggleDrawer)
            }
        },
        onHamburgerLongPress = { mapViewModel.onEvent(MapUiEvent.ToggleUiEditMode) },
        onOpenGeneralSettingsFromDrawer = openGeneralSettingsFromMap,
        onSettingsTap = openGeneralSettingsFromMap,
        cardStyle = cardStyle,
        hiddenCardIds = hiddenCardIds,
        replayState = mapViewModel.replaySessionState,
        showVarioDemoFab = mapViewModel.showVarioDemoFab,
        onVarioDemoReferenceClick = mapViewModel::onVarioDemoReplay,
        onVarioDemoSimClick = mapViewModel::onVarioDemoReplaySim,
        onVarioDemoSim2Click = mapViewModel::onVarioDemoReplaySimLive,
        onVarioDemoSim3Click = mapViewModel::onVarioDemoReplaySim3,
        showRacingReplayFab = mapViewModel.showRacingReplayFab,
        onRacingReplayClick = mapViewModel::onRacingTaskReplay
    )
}
