package com.example.xcpro.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.navdrawer.NavigationDrawer
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.maps.MapLibreMap

/**
 * Drawer + content scaffold for the map screen, with GPS status and loading overlay.
 */
@Composable
internal fun MapScreenScaffold(inputs: MapScreenScaffoldInputs) {
    MapScreenScaffold(
        drawerState = inputs.drawerState,
        navController = inputs.navController,
        profileExpanded = inputs.profileExpanded,
        mapStyleExpanded = inputs.mapStyleExpanded,
        settingsExpanded = inputs.settingsExpanded,
        initialMapStyle = inputs.initialMapStyle,
        onDrawerItemSelected = inputs.onDrawerItemSelected,
        onMapStyleSelected = inputs.onMapStyleSelected,
        gpsStatus = inputs.gpsStatus,
        isLoadingWaypoints = inputs.isLoadingWaypoints,
        density = inputs.density,
        mapState = inputs.mapState,
        mapInitializer = inputs.mapInitializer,
        onMapReady = inputs.onMapReady,
        locationManager = inputs.locationManager,
        flightDataManager = inputs.flightDataManager,
        flightViewModel = inputs.flightViewModel,
        taskManager = inputs.taskManager,
        taskType = inputs.taskType,
        createTaskGestureHandler = inputs.createTaskGestureHandler,
        windArrowState = inputs.windArrowState,
        showWindSpeedOnVario = inputs.showWindSpeedOnVario,
        cameraManager = inputs.cameraManager,
        currentMode = inputs.currentMode,
        currentZoom = inputs.currentZoom,
        onModeChange = inputs.onModeChange,
        currentLocation = inputs.currentLocation,
        showRecenterButton = inputs.showRecenterButton,
        showReturnButton = inputs.showReturnButton,
        showDistanceCircles = inputs.showDistanceCircles,
        ognSnapshot = inputs.ognSnapshot,
        ognOverlayEnabled = inputs.ognOverlayEnabled,
        adsbSnapshot = inputs.adsbSnapshot,
        adsbOverlayEnabled = inputs.adsbOverlayEnabled,
        selectedAdsbTarget = inputs.selectedAdsbTarget,
        isUiEditMode = inputs.isUiEditMode,
        onEditModeChange = inputs.onEditModeChange,
        isAATEditMode = inputs.isAATEditMode,
        onEnterAATEditMode = inputs.onEnterAATEditMode,
        onUpdateAATTargetPoint = inputs.onUpdateAATTargetPoint,
        onExitAATEditMode = inputs.onExitAATEditMode,
        safeContainerSize = inputs.safeContainerSize,
        overlayManager = inputs.overlayManager,
        modalManager = inputs.modalManager,
        widgetManager = inputs.widgetManager,
        screenWidthPx = inputs.screenWidthPx,
        screenHeightPx = inputs.screenHeightPx,
        variometerUiState = inputs.variometerUiState,
        minVariometerSizePx = inputs.minVariometerSizePx,
        maxVariometerSizePx = inputs.maxVariometerSizePx,
        onVariometerOffsetChange = inputs.onVariometerOffsetChange,
        onVariometerSizeChange = inputs.onVariometerSizeChange,
        onVariometerLongPress = inputs.onVariometerLongPress,
        onVariometerEditFinished = inputs.onVariometerEditFinished,
        hamburgerOffset = inputs.hamburgerOffset,
        flightModeOffset = inputs.flightModeOffset,
        ballastOffset = inputs.ballastOffset,
        onHamburgerOffsetChange = inputs.onHamburgerOffsetChange,
        onFlightModeOffsetChange = inputs.onFlightModeOffsetChange,
        onBallastOffsetChange = inputs.onBallastOffsetChange,
        taskScreenManager = inputs.taskScreenManager,
        waypointData = inputs.waypointData,
        unitsPreferences = inputs.unitsPreferences,
        qnhCalibrationState = inputs.qnhCalibrationState,
        onAutoCalibrateQnh = inputs.onAutoCalibrateQnh,
        onSetManualQnh = inputs.onSetManualQnh,
        onToggleOgnTraffic = inputs.onToggleOgnTraffic,
        onToggleAdsbTraffic = inputs.onToggleAdsbTraffic,
        onAdsbTargetSelected = inputs.onAdsbTargetSelected,
        onDismissAdsbTargetDetails = inputs.onDismissAdsbTargetDetails,
        ballastUiState = inputs.ballastUiState,
        isBallastPillHidden = inputs.isBallastPillHidden,
        onBallastCommand = inputs.onBallastCommand,
        onHamburgerTap = inputs.onHamburgerTap,
        onHamburgerLongPress = inputs.onHamburgerLongPress,
        cardStyle = inputs.cardStyle,
        hiddenCardIds = inputs.hiddenCardIds,
        replayState = inputs.replayState,
        showVarioDemoFab = inputs.showVarioDemoFab,
        onVarioDemoReferenceClick = inputs.onVarioDemoReferenceClick,
        onVarioDemoSimClick = inputs.onVarioDemoSimClick,
        onVarioDemoSim2Click = inputs.onVarioDemoSim2Click,
        onVarioDemoSim3Click = inputs.onVarioDemoSim3Click,
        showRacingReplayFab = inputs.showRacingReplayFab,
        onRacingReplayClick = inputs.onRacingReplayClick
    )
}

@Composable
internal fun MapScreenScaffold(
    drawerState: DrawerState,
    navController: NavHostController,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    initialMapStyle: String,
    onDrawerItemSelected: (String) -> Unit,
    onMapStyleSelected: (String) -> Unit,
    gpsStatus: GpsStatusUiModel,
    isLoadingWaypoints: Boolean,
    density: Density,
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (MapLibreMap) -> Unit,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    taskManager: TaskManagerCoordinator,
    taskType: TaskType,
    createTaskGestureHandler: (TaskGestureCallbacks) -> TaskGestureHandler,
    windArrowState: WindArrowUiState,
    showWindSpeedOnVario: Boolean,
    cameraManager: MapCameraManager,
    currentMode: FlightMode,
    currentZoom: Float,
    onModeChange: (FlightMode) -> Unit,
    currentLocation: MapLocationUiModel?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    ognSnapshot: OgnTrafficSnapshot,
    ognOverlayEnabled: Boolean,
    adsbSnapshot: AdsbTrafficSnapshot,
    adsbOverlayEnabled: Boolean,
    selectedAdsbTarget: AdsbSelectedTargetDetails?,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    isAATEditMode: Boolean,
    onEnterAATEditMode: (Int) -> Unit,
    onUpdateAATTargetPoint: (Int, Double, Double) -> Unit,
    onExitAATEditMode: () -> Unit,
    safeContainerSize: MutableState<IntSize>,
    overlayManager: MapOverlayManager,
    modalManager: MapModalManager,
    widgetManager: MapUIWidgetManager,
    screenWidthPx: Float,
    screenHeightPx: Float,
    variometerUiState: VariometerUiState,
    minVariometerSizePx: Float,
    maxVariometerSizePx: Float,
    onVariometerOffsetChange: (Offset) -> Unit,
    onVariometerSizeChange: (Float) -> Unit,
    onVariometerLongPress: () -> Unit,
    onVariometerEditFinished: () -> Unit,
    hamburgerOffset: MutableState<Offset>,
    flightModeOffset: MutableState<Offset>,
    ballastOffset: MutableState<Offset>,
    onHamburgerOffsetChange: (Offset) -> Unit,
    onFlightModeOffsetChange: (Offset) -> Unit,
    onBallastOffsetChange: (Offset) -> Unit,
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    qnhCalibrationState: QnhCalibrationState,
    onAutoCalibrateQnh: () -> Unit,
    onSetManualQnh: (Double) -> Unit,
    onToggleOgnTraffic: () -> Unit,
    onToggleAdsbTraffic: () -> Unit,
    onAdsbTargetSelected: (Icao24) -> Unit,
    onDismissAdsbTargetDetails: () -> Unit,
    ballastUiState: StateFlow<BallastUiState>,
    isBallastPillHidden: Boolean,
    onBallastCommand: (BallastCommand) -> Unit,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    cardStyle: CardStyle,
    hiddenCardIds: Set<String>,
    replayState: StateFlow<SessionState>,
    showVarioDemoFab: Boolean,
    onVarioDemoReferenceClick: () -> Unit,
    onVarioDemoSimClick: () -> Unit,
    onVarioDemoSim2Click: () -> Unit,
    onVarioDemoSim3Click: () -> Unit,
    showRacingReplayFab: Boolean,
    onRacingReplayClick: () -> Unit
) {
    NavigationDrawer(
        drawerState = drawerState,
        navController = navController,
        profileExpanded = profileExpanded,
        mapStyleExpanded = mapStyleExpanded,
        settingsExpanded = settingsExpanded,
        initialMapStyle = initialMapStyle,
        onItemSelected = onDrawerItemSelected,
        onMapStyleSelected = onMapStyleSelected,
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                GpsStatusBanner(
                    status = gpsStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                )
                MapScreenContent(
                    density = density,
                    mapState = mapState,
                    mapInitializer = mapInitializer,
                    onMapReady = onMapReady,
                    locationManager = locationManager,
                    flightDataManager = flightDataManager,
                    flightViewModel = flightViewModel,
                    taskManager = taskManager,
                    taskType = taskType,
                    createTaskGestureHandler = createTaskGestureHandler,
                    windArrowState = windArrowState,
                    showWindSpeedOnVario = showWindSpeedOnVario,
                    cameraManager = cameraManager,
                    currentMode = currentMode,
                    currentZoom = currentZoom,
                    onModeChange = onModeChange,
                    currentLocation = currentLocation,
                    showRecenterButton = showRecenterButton,
                    showReturnButton = showReturnButton,
                    showDistanceCircles = showDistanceCircles,
                    ognSnapshot = ognSnapshot,
                    ognOverlayEnabled = ognOverlayEnabled,
                    adsbSnapshot = adsbSnapshot,
                    adsbOverlayEnabled = adsbOverlayEnabled,
                    selectedAdsbTarget = selectedAdsbTarget,
                    isUiEditMode = isUiEditMode,
                    onEditModeChange = onEditModeChange,
                    isAATEditMode = isAATEditMode,
                    onEnterAATEditMode = onEnterAATEditMode,
                    onUpdateAATTargetPoint = onUpdateAATTargetPoint,
                    onExitAATEditMode = onExitAATEditMode,
                    safeContainerSize = safeContainerSize,
                    overlayManager = overlayManager,
                    modalManager = modalManager,
                    widgetManager = widgetManager,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    variometerUiState = variometerUiState,
                    minVariometerSizePx = minVariometerSizePx,
                    maxVariometerSizePx = maxVariometerSizePx,
                    onVariometerOffsetChange = onVariometerOffsetChange,
                    onVariometerSizeChange = onVariometerSizeChange,
                    onVariometerLongPress = onVariometerLongPress,
                    onVariometerEditFinished = onVariometerEditFinished,
                    hamburgerOffset = hamburgerOffset,
                    flightModeOffset = flightModeOffset,
                    ballastOffset = ballastOffset,
                    onHamburgerOffsetChange = onHamburgerOffsetChange,
                    onFlightModeOffsetChange = onFlightModeOffsetChange,
                    onBallastOffsetChange = onBallastOffsetChange,
                    taskScreenManager = taskScreenManager,
                    waypointData = waypointData,
                    unitsPreferences = unitsPreferences,
                    qnhCalibrationState = qnhCalibrationState,
                    onAutoCalibrateQnh = onAutoCalibrateQnh,
                    onSetManualQnh = onSetManualQnh,
                    onToggleOgnTraffic = onToggleOgnTraffic,
                    onToggleAdsbTraffic = onToggleAdsbTraffic,
                    onAdsbTargetSelected = onAdsbTargetSelected,
                    onDismissAdsbTargetDetails = onDismissAdsbTargetDetails,
                    ballastUiState = ballastUiState,
                    isBallastPillHidden = isBallastPillHidden,
                    onBallastCommand = onBallastCommand,
                    onHamburgerTap = onHamburgerTap,
                    onHamburgerLongPress = onHamburgerLongPress,
                    cardStyle = cardStyle,
                    hiddenCardIds = hiddenCardIds,
                    replayState = replayState,
                    showVarioDemoFab = showVarioDemoFab,
                    onVarioDemoReferenceClick = onVarioDemoReferenceClick,
                    onVarioDemoSimClick = onVarioDemoSimClick,
                    onVarioDemoSim2Click = onVarioDemoSim2Click,
                    onVarioDemoSim3Click = onVarioDemoSim3Click,
                    showRacingReplayFab = showRacingReplayFab,
                    onRacingReplayClick = onRacingReplayClick
                )
                if (isLoadingWaypoints) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun GpsStatusBanner(status: GpsStatusUiModel, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        GpsStatusUiModel.NoPermission -> "Location permission needed" to Color(0xFFB00020)
        GpsStatusUiModel.Disabled -> "GPS is off" to Color(0xFFB00020)
        is GpsStatusUiModel.LostFix -> "Waiting for GPS" to Color(0xFFCA8A04)
        GpsStatusUiModel.Searching -> "Searching for GPS" to Color(0xFFCA8A04)
        is GpsStatusUiModel.Ok -> return
    }
    Surface(
        color = color.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}
