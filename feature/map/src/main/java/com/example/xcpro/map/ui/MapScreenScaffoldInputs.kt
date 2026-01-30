package com.example.xcpro.map.ui

import android.content.Context
import android.util.Log
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavHostController
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.MapUiEvent
import com.example.xcpro.map.MapUiState
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.replay.SessionState
import com.example.xcpro.saveConfig
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.tasks.TaskManagerCoordinator
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
    val onDrawerItemSelected: (String) -> Unit,
    val onMapStyleSelected: (String) -> Unit,
    val gpsStatus: GpsStatus,
    val isLoadingWaypoints: Boolean,
    val density: Density,
    val mapState: MapScreenState,
    val mapInitializer: MapInitializer,
    val onMapReady: (MapLibreMap) -> Unit,
    val locationManager: LocationManager,
    val flightDataManager: FlightDataManager,
    val flightViewModel: FlightDataViewModel,
    val taskManager: TaskManagerCoordinator,
    val windArrowState: WindArrowUiState,
    val showWindSpeedOnVario: Boolean,
    val cameraManager: MapCameraManager,
    val currentMode: FlightMode,
    val currentZoom: Float,
    val onModeChange: (FlightMode) -> Unit,
    val currentLocation: GPSData?,
    val showRecenterButton: Boolean,
    val showReturnButton: Boolean,
    val showDistanceCircles: Boolean,
    val isUiEditMode: Boolean,
    val onEditModeChange: (Boolean) -> Unit,
    val isAATEditMode: Boolean,
    val onSetAATEditMode: (Boolean) -> Unit,
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
    val ballastOffset: MutableState<Offset>,
    val taskScreenManager: MapTaskScreenManager,
    val waypointData: List<WaypointData>,
    val unitsPreferences: UnitsPreferences,
    val qnhCalibrationState: QnhCalibrationState,
    val onAutoCalibrateQnh: () -> Unit,
    val onSetManualQnh: (Double) -> Unit,
    val ballastUiState: StateFlow<BallastUiState>,
    val isBallastPillHidden: Boolean,
    val onBallastCommand: (BallastCommand) -> Unit,
    val onHamburgerTap: () -> Unit,
    val onHamburgerLongPress: () -> Unit,
    val cardStyle: CardStyle,
    val replayState: StateFlow<SessionState>,
    val showVarioDemoFab: Boolean,
    val onVarioDemoReferenceClick: () -> Unit,
    val onVarioDemoSimClick: () -> Unit,
    val onVarioDemoSim2Click: () -> Unit,
    val onVarioDemoSim3Click: () -> Unit,
    val onVarioDemoCarClick: () -> Unit,
    val showRacingReplayFab: Boolean,
    val onRacingReplayClick: () -> Unit
)

private const val MapScreenScaffoldInputsTag = "MapScreen"

@Composable
internal fun rememberMapScreenScaffoldInputs(
    context: Context,
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
    ballastOffsetState: MutableState<Offset>,
    flightViewModel: FlightDataViewModel,
    windArrowState: WindArrowUiState,
    showWindSpeedOnVario: Boolean,
    cardStyle: CardStyle
): MapScreenScaffoldInputs {
    val onDrawerItemSelected: (String) -> Unit = { item ->
        Log.d(MapScreenScaffoldInputsTag, "Navigation drawer item selected: $item")
        coroutineScope.launch {
            drawerState.close()
            managers.taskScreenManager.handleNavigationTaskSelection(item)
        }
    }
    val onResolvedMapStyleSelected: (String) -> Unit = { style ->
        mapViewModel.setMapStyle(style)
        coroutineScope.launch {
            saveConfig(context, style, emptyMap(), profileExpanded.value, mapStyleExpanded.value)
        }
        Log.d(MapScreenScaffoldInputsTag, "Map style selected: $style")
        onMapStyleSelected(style)
    }

    return MapScreenScaffoldInputs(
        drawerState = drawerState,
        navController = navController,
        profileExpanded = profileExpanded,
        mapStyleExpanded = mapStyleExpanded,
        settingsExpanded = settingsExpanded,
        initialMapStyle = initialMapStyle,
        onDrawerItemSelected = onDrawerItemSelected,
        onMapStyleSelected = onResolvedMapStyleSelected,
        gpsStatus = bindings.gpsStatus,
        isLoadingWaypoints = mapUiState.isLoadingWaypoints,
        density = density,
        mapState = mapState,
        mapInitializer = managers.mapInitializer,
        onMapReady = mapRuntimeController::onMapReady,
        locationManager = managers.locationManager,
        flightDataManager = mapViewModel.flightDataManager,
        flightViewModel = flightViewModel,
        taskManager = mapViewModel.taskManager,
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
        isUiEditMode = mapUiState.isUiEditMode,
        onEditModeChange = { enabled -> mapViewModel.onEvent(MapUiEvent.SetUiEditMode(enabled)) },
        isAATEditMode = bindings.isAATEditMode,
        onSetAATEditMode = mapViewModel::setAATEditMode,
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
        ballastOffset = ballastOffsetState,
        taskScreenManager = managers.taskScreenManager,
        waypointData = mapUiState.waypoints,
        unitsPreferences = mapUiState.unitsPreferences,
        qnhCalibrationState = mapUiState.qnhCalibrationState,
        onAutoCalibrateQnh = mapViewModel::onAutoCalibrateQnh,
        onSetManualQnh = mapViewModel::onSetManualQnh,
        ballastUiState = mapViewModel.ballastUiState,
        isBallastPillHidden = mapUiState.hideBallastPill,
        onBallastCommand = mapViewModel::submitBallastCommand,
        onHamburgerTap = { mapViewModel.onEvent(MapUiEvent.ToggleDrawer) },
        onHamburgerLongPress = { mapViewModel.onEvent(MapUiEvent.ToggleUiEditMode) },
        cardStyle = cardStyle,
        replayState = mapViewModel.replaySessionState,
        showVarioDemoFab = mapViewModel.showVarioDemoFab,
        onVarioDemoReferenceClick = mapViewModel::onVarioDemoReplay,
        onVarioDemoSimClick = mapViewModel::onVarioDemoReplaySim,
        onVarioDemoSim2Click = mapViewModel::onVarioDemoReplaySimLive,
        onVarioDemoSim3Click = mapViewModel::onVarioDemoReplaySim3,
        onVarioDemoCarClick = mapViewModel::onVarioDemoReplayCar,
        showRacingReplayFab = mapViewModel.showRacingReplayFab,
        onRacingReplayClick = mapViewModel::onRacingTaskReplay
    )
}
