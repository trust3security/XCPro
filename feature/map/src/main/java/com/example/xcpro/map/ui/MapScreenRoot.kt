package com.example.xcpro.map.ui

import android.annotation.SuppressLint
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.livefollow.watch.LiveFollowWatchViewModel
import com.example.xcpro.map.MapSize
import com.example.xcpro.map.MapOrientationFlightDataRuntimeBinder
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.MapUiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
internal fun MapScreenRoot(
    navController: NavHostController,
    drawerState: DrawerState,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    allowFlightSensorStart: Boolean,
    isGeneralSettingsVisible: Boolean,
    onMapStyleSelected: (String) -> Unit = {},
    onOpenGeneralSettings: () -> Unit,
    mapViewModel: MapScreenViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val liveFollowWatchViewModel: LiveFollowWatchViewModel = hiltViewModel()
    val liveFollowWatchUiState by liveFollowWatchViewModel.uiState.collectAsStateWithLifecycle()
    val renderLocalOwnship =
        shouldRenderLocalOwnship(allowFlightSensorStart = allowFlightSensorStart, watchMapRenderState = liveFollowWatchUiState.mapRenderState)
    val renderLocalOwnshipState = rememberUpdatedState(renderLocalOwnship)
    val runtimeDependencies = mapViewModel.runtimeDependencies
    val flightDataManager = runtimeDependencies.flightDataManager
    val orientationManager = runtimeDependencies.orientationManager
    val orientationFlow = orientationManager.orientationFlow
    val rootUiBinding = rememberMapScreenRootUiBinding(mapViewModel = mapViewModel)
    MapScreenSideEffects(
        uiEffects = mapViewModel.uiEffects,
        drawerState = drawerState,
        context = context,
        onDrawerOpenChanged = { isOpen -> mapViewModel.onEvent(MapUiEvent.SetDrawerOpen(isOpen)) }
    )
    // Runtime map state owned by the UI layer.
    val mapState = remember { MapScreenState() }
    val mapStateReader = mapViewModel.mapState
    val hotPathBindings = rememberMapScreenHotPathBindings(mapViewModel = mapViewModel, orientationFlow = orientationFlow)
    // Simplified permission state: always enabled, but keep size hydration for card readiness.
    val safeContainerSizeState = remember { mutableStateOf(IntSize.Zero) }
    var safeContainerSize by safeContainerSizeState
    trackSafeContainerSize(safeContainerSize) { size ->
        mapViewModel.updateSafeContainerSize(MapSize(widthPx = size.width, heightPx = size.height))
    }
    val flightCardsBinding = rememberMapScreenFlightCardsBinding(navController = navController, mapViewModel = mapViewModel)
    val profileLookAndFeelBinding = rememberMapScreenProfileLookAndFeelBinding()
    LaunchedEffect(profileLookAndFeelBinding.activeProfileId) {
        mapViewModel.setActiveProfileId(profileLookAndFeelBinding.activeProfileId)
    }
    val airspaceState = rememberMapScreenAirspaceState()
    val taskManagerInputs = remember(mapViewModel) {
        MapScreenManagersTaskInputs(
            taskRenderSnapshotProvider = mapViewModel::taskRenderSnapshot,
            taskWaypointCountProvider = mapViewModel::currentTaskWaypointCount,
            currentTaskProvider = mapViewModel::currentTask,
            clearTask = mapViewModel::clearTask,
            saveTask = mapViewModel::saveTask
        )
    }
    val managers = rememberMapScreenManagers(
        context = context,
        mapState = mapState,
        mapStateReader = mapStateReader,
        mapStateActions = mapViewModel.mapStateActions,
        orientationRuntimePort = orientationManager,
        onOrientationUserInteraction = orientationManager::onUserInteraction,
        sensorsUseCase = runtimeDependencies.sensorsUseCase,
        replaySessionState = mapViewModel.replaySessionState,
        replayHeadingProvider = mapViewModel::getInterpolatedReplayHeadingDeg,
        replayFixProvider = mapViewModel::getInterpolatedReplayPose,
        featureFlags = runtimeDependencies.featureFlags,
        coroutineScope = coroutineScope,
        taskInputs = taskManagerInputs,
        airspaceUseCase = runtimeDependencies.airspaceUseCase,
        waypointFilesUseCase = runtimeDependencies.waypointFilesUseCase,
        localOwnshipRenderEnabled = { renderLocalOwnshipState.value }
    )
    LaunchedEffect(profileLookAndFeelBinding.activeProfileId, managers.locationManager) { managers.locationManager.setActiveProfileId(profileLookAndFeelBinding.activeProfileId) }
    val panelState by managers.taskScreenManager.taskPanelState.collectAsStateWithLifecycle(); val isTaskPanelVisible =
        panelState != MapTaskScreenManager.TaskPanelState.HIDDEN
    MapScreenBackHandler(
        drawerState = drawerState,
        modalManager = managers.modalManager,
        taskScreenManager = managers.taskScreenManager,
        isTaskPanelVisible = isTaskPanelVisible,
        navController = navController,
        coroutineScope = coroutineScope
    )

    val bindings = rememberMapScreenBindings(mapViewModel = mapViewModel, mapStateReader = mapStateReader)
    val taskRenderSnapshotProvider = mapViewModel::taskRenderSnapshot
    val sessionBindings = bindings.session; val taskBindings = bindings.task
    val trafficOverlayRuntimeInputs = rememberMapTrafficOverlayRuntimeInputs(mapViewModel = mapViewModel, currentLocation = hotPathBindings.currentLocation)
    MapAirspaceOverlayEffect(mapState = mapState, airspaceState = airspaceState, overlayManager = managers.overlayManager)
    MapTrafficOverlayRuntimeEffects(overlayManager = managers.overlayManager, inputs = trafficOverlayRuntimeInputs, renderLocalOwnship = renderLocalOwnship)
    MapWeatherOverlayEffects(overlayManager = managers.overlayManager)
    MapScreenRuntimeEffects(
        taskType = taskBindings.taskType,
        drawerState = drawerState,
        isAATEditMode = taskBindings.isAATEditMode,
        onExitAATEditMode = mapViewModel::exitAATEditMode,
        snailTrailManager = managers.snailTrailManager,
        locationManager = managers.locationManager,
        trailUpdateResult = sessionBindings.trailUpdateResult,
        trailSettings = sessionBindings.trailSettings,
        currentZoomFlow = hotPathBindings.currentZoom,
        renderLocalOwnship = renderLocalOwnship,
        currentFlightModeSelection = rootUiBinding.currentFlightModeSelection,
        onApplyOrientationFlightModeSelection = mapViewModel::applyOrientationFlightModeSelection
    )
    val mapRuntimeController = rememberMapRuntimeController(
        overlayManager = managers.overlayManager,
        mapViewModel = mapViewModel,
        cameraManager = managers.cameraManager,
        taskRenderSnapshotProvider = taskRenderSnapshotProvider
    )
    TaskViewportCommandEffects(mapViewModel = mapViewModel)
    MapScreenCameraRuntimeEffects(cameraManager = managers.cameraManager, hotPathBindings = hotPathBindings, replaySession = sessionBindings.replaySession)
    val locationPermissionRequester = rememberLocationPermissionRequester(managers.locationManager)
    MapScreenComposeAndLifecycleEffects(
        lifecycleManager = managers.lifecycleManager,
        runtimeController = mapRuntimeController,
        locationManager = managers.locationManager,
        locationPermissionRequester = locationPermissionRequester,
        currentLocationFlow = hotPathBindings.currentLocation,
        orientationFlow = hotPathBindings.orientationFlow,
        orientationFlightDataRuntimeBinder = remember(flightDataManager, orientationManager) {
            MapOrientationFlightDataRuntimeBinder(flightDataManager = flightDataManager, orientationController = orientationManager)
        },
        profileUiState = profileLookAndFeelBinding.profileUiState,
        flightDataManager = flightDataManager,
        currentFlightModeSelection = rootUiBinding.currentFlightModeSelection,
        safeContainerSize = safeContainerSize,
        flightCardsBinding = flightCardsBinding,
        replaySessionState = sessionBindings.replaySession,
        useRenderFrameSync = runtimeDependencies.featureFlags.useRenderFrameSync,
        suppressLiveGps = sessionBindings.suppressLiveGps,
        allowSensorStart = sessionBindings.allowSensorStart && allowFlightSensorStart,
        renderLocalOwnship = renderLocalOwnship
    )
    val widgetLayout = rememberMapScreenWidgetLayoutBinding(activeProfileId = profileLookAndFeelBinding.activeProfileId, density = density)
    ensureSafeContainerFallback(safeContainerSizeState = safeContainerSizeState, screenWidthPx = widgetLayout.screenWidthPx, screenHeightPx = widgetLayout.screenHeightPx)
    val variometerLayout = rememberVariometerLayout(
        mapViewModel = mapViewModel,
        activeProfileId = profileLookAndFeelBinding.activeProfileId,
        screenWidthPx = widgetLayout.screenWidthPx,
        screenHeightPx = widgetLayout.screenHeightPx,
        density = density
    )
    MapVisibilityLifecycleEffect(mapViewModel)
    val scaffoldChromeBindings = rememberMapScreenScaffoldChromeBindings(
        coroutineScope = coroutineScope,
        navController = navController,
        drawerState = drawerState,
        profileExpanded = profileExpanded,
        mapStyleExpanded = mapStyleExpanded,
        settingsExpanded = settingsExpanded,
        onMapStyleSelected = onMapStyleSelected,
        onOpenGeneralSettings = onOpenGeneralSettings,
        mapViewModel = mapViewModel,
        rootUiBinding = rootUiBinding,
        bindings = bindings,
        taskScreenManager = managers.taskScreenManager
    )
    val mapReadyBindings = rememberMapScreenMapReadyBindings(mapRuntimeController = mapRuntimeController, overlayManager = managers.overlayManager, trafficOverlayRuntimeInputs = trafficOverlayRuntimeInputs)
    val mapContentInputs = rememberMapScreenMapContentInputs(
        mapViewModel = mapViewModel,
        hotPathBindings = hotPathBindings,
        rootUiBinding = rootUiBinding,
        bindings = bindings,
        flightCardsBinding = flightCardsBinding,
        flightDataManager = flightDataManager,
        mapInitializer = managers.mapInitializer,
        locationManager = managers.locationManager,
        locationRenderFrameBinder = managers.locationRenderFrameBinder,
        renderSurfaceDiagnostics = managers.renderSurfaceDiagnostics,
        cameraManager = managers.cameraManager,
        lifecycleManager = managers.lifecycleManager,
        mapState = mapState,
        mapReadyBindings = mapReadyBindings,
        density = density
    )
    val overlayContentInputs = rememberMapScreenOverlayContentInputs(
        showMapBottomNavigation = scaffoldChromeBindings.shared.showMapBottomNavigation,
        allowFlightSensorStart = allowFlightSensorStart,
        isGeneralSettingsVisible = isGeneralSettingsVisible,
        renderLocalOwnship = renderLocalOwnship,
        mapViewModel = mapViewModel,
        rootUiBinding = rootUiBinding,
        bindings = bindings,
        cameraManager = managers.cameraManager,
        overlayManager = managers.overlayManager,
        modalManager = managers.modalManager,
        taskScreenManager = managers.taskScreenManager,
        mapState = mapState,
        taskRenderSnapshotProvider = taskRenderSnapshotProvider,
        safeContainerSizeState = safeContainerSizeState,
        shouldBlockDrawerOpen = scaffoldChromeBindings.shared.shouldBlockDrawerOpen,
        onOpenGeneralSettingsFromMap = scaffoldChromeBindings.shared.onOpenGeneralSettingsFromMap,
        mapReadyBindings = mapReadyBindings
    )
    val widgetContentInputs = buildMapScreenWidgetContentInputs(
        mapViewModel = mapViewModel,
        rootUiBinding = rootUiBinding,
        profileLookAndFeelBinding = profileLookAndFeelBinding,
        widgetLayout = widgetLayout,
        variometerLayout = variometerLayout,
        widgetManager = managers.widgetManager
    )
    val replayContentInputs = buildMapScreenReplayContentInputs(mapViewModel = mapViewModel)
    val contentInputs = MapScreenContentInputs(map = mapContentInputs, overlays = overlayContentInputs, widgets = widgetContentInputs, replay = replayContentInputs)
    MapScreenScaffold(inputs = scaffoldChromeBindings.scaffold) {
        MapScreenScaffoldContentHost(
            inputs = contentInputs,
            weGlideUploadPrompt = rootUiBinding.weGlideUploadPrompt,
            onConfirmWeGlideUploadPrompt = mapViewModel::onConfirmWeGlideUploadPrompt,
            onDismissWeGlideUploadPrompt = mapViewModel::onDismissWeGlideUploadPrompt
        )
    }
}
