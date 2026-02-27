package com.example.xcpro.map.ui
import android.util.Log
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavHostController
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.MapUiEvent
import com.example.xcpro.map.MapUiState
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.navigation.SettingsRoutes
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
internal data class MapScreenScaffoldInputs(
    val drawerState: DrawerState,
    val navController: NavHostController,
    val profileExpanded: MutableState<Boolean>,
    val mapStyleExpanded: MutableState<Boolean>,
    val settingsExpanded: MutableState<Boolean>,
    val initialMapStyle: String,
    val currentMapStyleName: String,
    val onDrawerItemSelected: (String) -> Unit,
    val onMapStyleSelected: (String) -> Unit,
    val onTransientMapStyleSelected: (String) -> Unit,
    val gpsStatus: GpsStatusUiModel,
    val isLoadingWaypoints: Boolean,
    val density: Density,
    val mapState: MapScreenState,
    val mapInitializer: MapInitializer,
    val onMapReady: (MapLibreMap) -> Unit,
    val onMapViewBound: () -> Unit,
    val locationManager: LocationManager,
    val flightDataManager: FlightDataManager,
    val flightViewModel: FlightDataViewModel,
    val taskType: TaskType,
    val createTaskGestureHandler: (TaskGestureCallbacks) -> TaskGestureHandler,
    val windArrowState: WindArrowUiState,
    val showWindSpeedOnVario: Boolean,
    val cameraManager: MapCameraManager,
    val currentMode: FlightMode,
    val currentZoom: Float,
    val onModeChange: (FlightMode) -> Unit,
    val currentLocation: MapLocationUiModel?,
    val showRecenterButton: Boolean,
    val showReturnButton: Boolean,
    val showDistanceCircles: Boolean,
    val ognSnapshot: OgnTrafficSnapshot, val ognOverlayEnabled: Boolean,
    val ognThermalHotspots: List<OgnThermalHotspot>, val showOgnSciaEnabled: Boolean,
    val showOgnThermalsEnabled: Boolean,
    val adsbSnapshot: AdsbTrafficSnapshot, val adsbOverlayEnabled: Boolean,
    val selectedOgnTarget: OgnTrafficTarget?, val selectedOgnThermal: OgnThermalHotspot?,
    val selectedAdsbTarget: AdsbSelectedTargetDetails?,
    val isUiEditMode: Boolean,
    val onEditModeChange: (Boolean) -> Unit,
    val isAATEditMode: Boolean,
    val onEnterAATEditMode: (Int) -> Unit,
    val onUpdateAATTargetPoint: (Int, Double, Double) -> Unit,
    val onExitAATEditMode: () -> Unit,
    val safeContainerSize: MutableState<IntSize>,
    val overlayManager: MapOverlayManager,
    val modalManager: MapModalManager,
    val widgetManager: MapUIWidgetManager,
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val variometerUiState: VariometerUiState,
    val minVariometerSizePx: Float,
    val maxVariometerSizePx: Float,
    val onVariometerOffsetChange: (Offset) -> Unit,
    val onVariometerSizeChange: (Float) -> Unit,
    val onVariometerLongPress: () -> Unit,
    val onVariometerEditFinished: () -> Unit,
    val hamburgerOffset: MutableState<Offset>,
    val flightModeOffset: MutableState<Offset>,
    val settingsOffset: MutableState<Offset>,
    val ballastOffset: MutableState<Offset>,
    val onHamburgerOffsetChange: (Offset) -> Unit,
    val onFlightModeOffsetChange: (Offset) -> Unit,
    val onSettingsOffsetChange: (Offset) -> Unit,
    val onBallastOffsetChange: (Offset) -> Unit,
    val taskScreenManager: MapTaskScreenManager,
    val waypointData: List<WaypointData>,
    val unitsPreferences: UnitsPreferences,
    val qnhCalibrationState: QnhCalibrationState,
    val onAutoCalibrateQnh: () -> Unit,
    val onSetManualQnh: (Double) -> Unit,
    val onToggleOgnTraffic: () -> Unit, val onToggleOgnScia: () -> Unit,
    val onToggleOgnThermals: () -> Unit,
    val onToggleAdsbTraffic: () -> Unit, val onOgnTargetSelected: (String) -> Unit,
    val onOgnThermalSelected: (String) -> Unit,
    val onAdsbTargetSelected: (Icao24) -> Unit,
    val onDismissOgnTargetDetails: () -> Unit, val onDismissOgnThermalDetails: () -> Unit,
    val onDismissAdsbTargetDetails: () -> Unit,
    val ballastUiState: StateFlow<BallastUiState>,
    val isBallastPillHidden: Boolean,
    val onBallastCommand: (BallastCommand) -> Unit,
    val onHamburgerTap: () -> Unit,
    val onHamburgerLongPress: () -> Unit,
    val onSettingsTap: () -> Unit,
    val cardStyle: CardStyle,
    val hiddenCardIds: Set<String>,
    val replayState: StateFlow<SessionState>,
    val showVarioDemoFab: Boolean,
    val onVarioDemoReferenceClick: () -> Unit, val onVarioDemoSimClick: () -> Unit,
    val onVarioDemoSim2Click: () -> Unit, val onVarioDemoSim3Click: () -> Unit,
    val showRacingReplayFab: Boolean,
    val onRacingReplayClick: () -> Unit
)
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
    onHamburgerOffsetChange: (Offset) -> Unit,
    onFlightModeOffsetChange: (Offset) -> Unit,
    onSettingsOffsetChange: (Offset) -> Unit,
    onBallastOffsetChange: (Offset) -> Unit,
    flightViewModel: FlightDataViewModel,
    flightDataManager: FlightDataManager,
    windArrowState: WindArrowUiState,
    showWindSpeedOnVario: Boolean,
    cardStyle: CardStyle, hiddenCardIds: Set<String>
): MapScreenScaffoldInputs {
    val lifecycleOwner = LocalLifecycleOwner.current
    val onDrawerItemSelected: (String) -> Unit = { item ->
        Log.d(MapScreenScaffoldInputsTag, "Navigation drawer item selected: $item")
        coroutineScope.launch { drawerState.close(); managers.taskScreenManager.handleNavigationTaskSelection(item) }
    }
    val onResolvedMapStyleSelected: (String) -> Unit = { style ->
        mapViewModel.setMapStyle(style)
        coroutineScope.launch { mapViewModel.persistMapStyle(style) }
        Log.d(MapScreenScaffoldInputsTag, "Map style selected: $style")
        onMapStyleSelected(style)
    }
    val onTransientMapStyleSelected: (String) -> Unit = { style -> mapViewModel.setMapStyle(style) }
    val onMapReady: (MapLibreMap) -> Unit = { map ->
        mapRuntimeController.onMapReady(map)
        managers.overlayManager.setOgnDisplayUpdateMode(bindings.ognDisplayUpdateMode)
        managers.overlayManager.setOgnIconSizePx(bindings.ognIconSizePx)
        managers.overlayManager.setAdsbIconSizePx(bindings.adsbIconSizePx)
        managers.overlayManager.reapplyForecastOverlay()
        managers.overlayManager.reapplySkySightSatelliteOverlay()
        managers.overlayManager.reapplyWeatherRainOverlay()
    }
    val onMapViewBound: () -> Unit = { managers.lifecycleManager.syncCurrentOwnerState(lifecycleOwner.lifecycle.currentState) }
    val shouldBlockDrawerOpen = MapTaskIntegration.shouldBlockDrawerGestures(taskType = bindings.taskType, isAATEditMode = bindings.isAATEditMode)
    val onSettingsTap: () -> Unit = {
        if (!shouldBlockDrawerOpen) {
            settingsExpanded.value = true
            coroutineScope.launch {
                if (drawerState.isOpen) drawerState.close()
                if (navController.currentDestination?.route != SettingsRoutes.GENERAL) navController.navigate(SettingsRoutes.GENERAL)
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
        onEditModeChange = { enabled -> mapViewModel.onEvent(MapUiEvent.SetUiEditMode(enabled)) },
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
        onVariometerOffsetChange = { offset -> mapViewModel.onVariometerOffsetCommitted(offset = offset.toOffsetPx(), screenWidthPx = screenWidthPx, screenHeightPx = screenHeightPx) },
        onVariometerSizeChange = { newSize -> mapViewModel.onVariometerSizeCommitted(sizePx = newSize, screenWidthPx = screenWidthPx, screenHeightPx = screenHeightPx, minSizePx = minVariometerSizePx, maxSizePx = maxVariometerSizePx) },
        onVariometerLongPress = {},
        onVariometerEditFinished = {},
        hamburgerOffset = hamburgerOffsetState,
        flightModeOffset = flightModeOffsetState,
        settingsOffset = settingsOffsetState,
        ballastOffset = ballastOffsetState,
        onHamburgerOffsetChange = onHamburgerOffsetChange,
        onFlightModeOffsetChange = onFlightModeOffsetChange,
        onSettingsOffsetChange = onSettingsOffsetChange,
        onBallastOffsetChange = onBallastOffsetChange,
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
        onHamburgerTap = { if (!shouldBlockDrawerOpen || mapUiState.isDrawerOpen) mapViewModel.onEvent(MapUiEvent.ToggleDrawer) },
        onHamburgerLongPress = { mapViewModel.onEvent(MapUiEvent.ToggleUiEditMode) },
        onSettingsTap = onSettingsTap,
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
