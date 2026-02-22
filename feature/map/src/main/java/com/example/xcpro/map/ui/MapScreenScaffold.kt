package com.example.xcpro.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.navdrawer.NavigationDrawer

/**
 * Drawer + content scaffold for the map screen, with GPS status and loading overlay.
 */
@Composable
internal fun MapScreenScaffold(inputs: MapScreenScaffoldInputs) {
    NavigationDrawer(
        drawerState = inputs.drawerState,
        navController = inputs.navController,
        profileExpanded = inputs.profileExpanded,
        mapStyleExpanded = inputs.mapStyleExpanded,
        settingsExpanded = inputs.settingsExpanded,
        initialMapStyle = inputs.initialMapStyle,
        onItemSelected = inputs.onDrawerItemSelected,
        onMapStyleSelected = inputs.onMapStyleSelected,
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                GpsStatusBanner(
                    status = inputs.gpsStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                )
                MapScreenContent(
                    density = inputs.density,
                    mapState = inputs.mapState,
                    mapInitializer = inputs.mapInitializer,
                    onMapReady = inputs.onMapReady,
                    onMapViewBound = inputs.onMapViewBound,
                    locationManager = inputs.locationManager,
                    flightDataManager = inputs.flightDataManager,
                    flightViewModel = inputs.flightViewModel,
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
                    ognThermalHotspots = inputs.ognThermalHotspots,
                    showOgnThermalsEnabled = inputs.showOgnThermalsEnabled,
                    showOgnGliderTrailsEnabled = inputs.showOgnGliderTrailsEnabled,
                    adsbSnapshot = inputs.adsbSnapshot,
                    adsbOverlayEnabled = inputs.adsbOverlayEnabled,
                    selectedOgnTarget = inputs.selectedOgnTarget,
                    selectedOgnThermal = inputs.selectedOgnThermal,
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
                    onToggleOgnThermals = inputs.onToggleOgnThermals,
                    onToggleOgnGliderTrails = inputs.onToggleOgnGliderTrails,
                    onToggleAdsbTraffic = inputs.onToggleAdsbTraffic,
                    onOgnTargetSelected = inputs.onOgnTargetSelected,
                    onOgnThermalSelected = inputs.onOgnThermalSelected,
                    onAdsbTargetSelected = inputs.onAdsbTargetSelected,
                    onDismissOgnTargetDetails = inputs.onDismissOgnTargetDetails,
                    onDismissOgnThermalDetails = inputs.onDismissOgnThermalDetails,
                    onDismissAdsbTargetDetails = inputs.onDismissAdsbTargetDetails,
                    ballastUiState = inputs.ballastUiState,
                    isBallastPillHidden = inputs.isBallastPillHidden,
                    onBallastCommand = inputs.onBallastCommand,
                    onHamburgerTap = inputs.onHamburgerTap,
                    onHamburgerLongPress = inputs.onHamburgerLongPress,
                    onOpenWeatherSettingsFromTab = inputs.onOpenWeatherSettingsFromTab,
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
                if (inputs.isLoadingWaypoints) {
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
