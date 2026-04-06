package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.xcpro.livefollow.LiveFollowRoutes
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapPoint
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.MapUiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs

private const val MapScreenScaffoldInputsTag = "MapScreen"
private const val LiveFollowWatchFocusZoom = 14.0

@Composable
internal fun rememberMapScreenScaffoldInputs(
    coroutineScope: CoroutineScope,
    navController: NavHostController,
    drawerState: DrawerState,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    onMapStyleSelected: (String) -> Unit,
    onOpenGeneralSettings: () -> Unit,
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
    managers: MapScreenManagers,
    mapState: MapScreenState,
    mapRuntimeController: MapRuntimeController,
    density: Density,
    safeContainerSizeState: MutableState<IntSize>,
    screenWidthPx: Float = widgetLayout.screenWidthPx,
    screenHeightPx: Float = widgetLayout.screenHeightPx
): MapScreenScaffoldInputs {
    val lifecycleOwner = LocalLifecycleOwner.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val showMapBottomNavigation = remember(navBackStackEntry?.destination?.route) {
        isMapBottomNavigationRoute(navBackStackEntry?.destination?.route)
    }
    var watchedPilotFocusEpoch by remember { mutableIntStateOf(0) }
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
    val onForecastSatelliteOverrideChanged: (Boolean) -> Unit = { enabled ->
        mapViewModel.setForecastSatelliteOverrideEnabled(enabled)
    }
    val onMapReady: (MapLibreMap) -> Unit = { map ->
        mapRuntimeController.onMapReady(map)
        applyMapReadyTrafficOverlayConfig(
            port = createTrafficOverlayRenderPort(managers.overlayManager),
            config = createMapReadyTrafficOverlayConfig(trafficOverlayRuntimeInputs)
        )
        watchedPilotFocusEpoch += 1
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
            selectedMapStyle = mapBindings.baseMapStyleName,
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
                renderSurfaceDiagnostics = managers.renderSurfaceDiagnostics,
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
                overlayManager = managers.overlayManager,
                modalManager = managers.modalManager,
                taskScreenManager = managers.taskScreenManager,
                taskFlightSurfaceUiState = taskBindings.taskFlightSurfaceUiState,
                taskRenderSnapshotProvider = mapViewModel.runtimeDependencies.tasksUseCase::taskRenderSnapshot,
                watchedPilotFocusEpoch = watchedPilotFocusEpoch,
                mapLibreMapProvider = { mapState.mapLibreMap },
                onFocusWatchedPilot = focusWatchedPilot@ { latitudeDeg, longitudeDeg ->
                    val activeMap = mapState.mapLibreMap ?: return@focusWatchedPilot false
                    managers.cameraManager.moveTo(
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

private fun targetMatchesWatchFocusTarget(
    latitudeDeg: Double,
    longitudeDeg: Double,
    target: org.maplibre.android.geometry.LatLng,
    epsilonDeg: Double = 1e-6
): Boolean {
    return abs(target.latitude - latitudeDeg) <= epsilonDeg &&
        abs(target.longitude - longitudeDeg) <= epsilonDeg
}

internal fun isMapBottomNavigationRoute(route: String?): Boolean {
    val normalizedRoute = route?.substringBefore("?")
    return normalizedRoute in MAP_BOTTOM_NAVIGATION_ROUTES
}

private val MAP_BOTTOM_NAVIGATION_ROUTES = setOf(
    LiveFollowRoutes.MAP_ROUTE,
    LiveFollowRoutes.FRIENDS_FLYING
)
