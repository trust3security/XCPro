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
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.navdrawer.NavigationDrawer
import com.example.xcpro.replay.SessionState
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.variometer.layout.VariometerUiState
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.maps.MapLibreMap

/**
 * Drawer + content scaffold for the map screen, with GPS status and loading overlay.
 */
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
    gpsStatus: GpsStatus,
    isLoadingWaypoints: Boolean,
    density: Density,
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (MapLibreMap) -> Unit,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    taskManager: TaskManagerCoordinator,
    orientationManager: MapOrientationManager,
    orientationData: OrientationData,
    cameraManager: MapCameraManager,
    currentFlightModeSelection: FlightModeSelection,
    currentMode: FlightMode,
    currentZoom: Float,
    onModeChange: (FlightMode) -> Unit,
    currentLocation: GPSData?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    isAATEditMode: Boolean,
    onSetAATEditMode: (Boolean) -> Unit,
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
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    ballastUiState: StateFlow<BallastUiState>,
    isBallastPillHidden: Boolean,
    onBallastCommand: (BallastCommand) -> Unit,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    cardStyle: CardStyle,
    replayState: StateFlow<SessionState>,
    showVarioDemoFab: Boolean,
    onVarioDemoClick: () -> Unit
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
                    orientationManager = orientationManager,
                    orientationData = orientationData,
                    cameraManager = cameraManager,
                    currentFlightModeSelection = currentFlightModeSelection,
                    currentMode = currentMode,
                    currentZoom = currentZoom,
                    onModeChange = onModeChange,
                    currentLocation = currentLocation,
                    showRecenterButton = showRecenterButton,
                    showReturnButton = showReturnButton,
                    showDistanceCircles = showDistanceCircles,
                    isUiEditMode = isUiEditMode,
                    onEditModeChange = onEditModeChange,
                    isAATEditMode = isAATEditMode,
                    onSetAATEditMode = onSetAATEditMode,
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
                    taskScreenManager = taskScreenManager,
                    waypointData = waypointData,
                    unitsPreferences = unitsPreferences,
                    ballastUiState = ballastUiState,
                    isBallastPillHidden = isBallastPillHidden,
                    onBallastCommand = onBallastCommand,
                    onHamburgerTap = onHamburgerTap,
                    onHamburgerLongPress = onHamburgerLongPress,
                    cardStyle = cardStyle,
                    replayState = replayState,
                    showVarioDemoFab = showVarioDemoFab,
                    onVarioDemoClick = onVarioDemoClick
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
private fun GpsStatusBanner(status: GpsStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        GpsStatus.NoPermission -> "Location permission needed" to Color(0xFFB00020)
        GpsStatus.Disabled -> "GPS is off" to Color(0xFFB00020)
        is GpsStatus.LostFix -> "Waiting for GPS" to Color(0xFFCA8A04)
        GpsStatus.Searching -> "Searching for GPS" to Color(0xFFCA8A04)
        is GpsStatus.Ok -> return
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
