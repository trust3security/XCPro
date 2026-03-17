package com.example.xcpro.map.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.map.MapSize
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapUiEvent
import com.example.xcpro.map.MapTaskScreenManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
internal fun MapScreenRoot(
    navController: NavHostController,
    drawerState: DrawerState,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    initialMapStyle: String,
    onMapStyleSelected: (String) -> Unit = {},
    onOpenGeneralSettings: () -> Unit,
    mapViewModel: MapScreenViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val runtimeDependencies = mapViewModel.runtimeDependencies
    val flightDataManager = runtimeDependencies.flightDataManager
    val orientationManager = runtimeDependencies.orientationManager
    val rootUiBinding = rememberMapScreenRootUiBinding(
        mapViewModel = mapViewModel,
        flightDataManager = flightDataManager
    )
    MapScreenSideEffects(
        uiEffects = mapViewModel.uiEffects,
        drawerState = drawerState,
        context = context,
        onDrawerOpenChanged = { isOpen ->
            mapViewModel.onEvent(MapUiEvent.SetDrawerOpen(isOpen))
        }
    )
    // Runtime map state owned by the UI layer.
    val mapState = remember { MapScreenState() }
    val mapStateReader = mapViewModel.mapState
    val hotPathBindings = rememberMapScreenHotPathBindings(
        mapViewModel = mapViewModel,
        orientationManager = orientationManager
    )

    // Simplified permission state: always enabled, but keep size hydration for card readiness.
    val safeContainerSizeState = remember { mutableStateOf(IntSize.Zero) }
    var safeContainerSize by safeContainerSizeState
    trackSafeContainerSize(safeContainerSize) { size ->
        mapViewModel.updateSafeContainerSize(
            MapSize(
                widthPx = size.width,
                heightPx = size.height
            )
        )
    }

    val flightCardsBinding = rememberMapScreenFlightCardsBinding(
        navController = navController,
        mapViewModel = mapViewModel
    )
    val profileLookAndFeelBinding = rememberMapScreenProfileLookAndFeelBinding()
    LaunchedEffect(profileLookAndFeelBinding.activeProfileId) {
        mapViewModel.setActiveProfileId(profileLookAndFeelBinding.activeProfileId)
    }
    val airspaceState = rememberMapScreenAirspaceState()

    val managers = rememberMapScreenManagers(
        context = context,
        mapState = mapState,
        mapStateReader = mapStateReader,
        mapStateActions = mapViewModel.mapStateActions,
        orientationManager = orientationManager,
        sensorsUseCase = runtimeDependencies.sensorsUseCase,
        replaySessionState = mapViewModel.replaySessionState,
        replayHeadingProvider = mapViewModel::getInterpolatedReplayHeadingDeg,
        replayFixProvider = mapViewModel::getInterpolatedReplayPose,
        featureFlags = runtimeDependencies.featureFlags,
        coroutineScope = coroutineScope,
        tasksUseCase = runtimeDependencies.tasksUseCase,
        airspaceUseCase = runtimeDependencies.airspaceUseCase,
        waypointFilesUseCase = runtimeDependencies.waypointFilesUseCase
    )
    LaunchedEffect(profileLookAndFeelBinding.activeProfileId, managers.locationManager) {
        managers.locationManager.setActiveProfileId(profileLookAndFeelBinding.activeProfileId)
    }
    val panelState by managers.taskScreenManager.taskPanelState.collectAsStateWithLifecycle()
    val isTaskPanelVisible = panelState != MapTaskScreenManager.TaskPanelState.HIDDEN
    MapScreenBackHandler(
        drawerState = drawerState,
        modalManager = managers.modalManager,
        taskScreenManager = managers.taskScreenManager,
        isTaskPanelVisible = isTaskPanelVisible,
        navController = navController,
        coroutineScope = coroutineScope
    )

    val bindings = rememberMapScreenBindings(
        mapViewModel = mapViewModel,
        mapStateReader = mapStateReader
    )
    val mapBindings = bindings.map
    val sessionBindings = bindings.session
    val taskBindings = bindings.task

    MapAirspaceOverlayEffect(
        mapState = mapState,
        airspaceState = airspaceState,
        overlayManager = managers.overlayManager
    )
    MapTrafficOverlayRuntimeEffects(
        overlayManager = managers.overlayManager,
        traffic = bindings.traffic,
        currentLocation = hotPathBindings.currentLocation,
        unitsPreferences = rootUiBinding.mapUiState.unitsPreferences
    )
    MapWeatherOverlayEffects(overlayManager = managers.overlayManager)

    MapScreenRuntimeEffects(
        taskType = taskBindings.taskType,
        drawerState = drawerState,
        isAATEditMode = taskBindings.isAATEditMode,
        onExitAATEditMode = mapViewModel::exitAATEditMode,
        snailTrailManager = managers.snailTrailManager,
        locationManager = managers.locationManager,
        featureFlags = runtimeDependencies.featureFlags,
        trailUpdateResult = sessionBindings.trailUpdateResult,
        trailSettings = sessionBindings.trailSettings,
        currentZoomFlow = hotPathBindings.currentZoom,
        suppressLiveGps = sessionBindings.suppressLiveGps,
        currentFlightModeSelection = rootUiBinding.currentFlightModeSelection,
        orientationManager = orientationManager
    )

    val mapRuntimeController = rememberMapRuntimeController(
        overlayManager = managers.overlayManager,
        mapViewModel = mapViewModel,
        cameraManager = managers.cameraManager
    )
    TaskViewportCommandEffects(mapViewModel = mapViewModel)
    MapScreenCameraRuntimeEffects(
        cameraManager = managers.cameraManager,
        hotPathBindings = hotPathBindings,
        replaySession = sessionBindings.replaySession
    )

    val locationPermissionRequester = rememberLocationPermissionRequester(managers.locationManager)
    MapScreenComposeAndLifecycleEffects(
        lifecycleManager = managers.lifecycleManager,
        runtimeController = mapRuntimeController,
        locationManager = managers.locationManager,
        locationPermissionRequester = locationPermissionRequester,
        currentLocationFlow = hotPathBindings.currentLocation,
        orientationFlow = hotPathBindings.orientationFlow,
        orientationManager = orientationManager,
        profileUiState = profileLookAndFeelBinding.profileUiState,
        flightDataManager = flightDataManager,
        currentMode = mapBindings.currentMode,
        onModeChange = mapViewModel::setFlightMode,
        currentFlightModeSelection = rootUiBinding.currentFlightModeSelection,
        safeContainerSize = safeContainerSize,
        flightCardsBinding = flightCardsBinding,
        initialMapStyle = initialMapStyle,
        onMapStyleResolved = mapViewModel::setMapStyle,
        replaySessionState = sessionBindings.replaySession,
        useRenderFrameSync = runtimeDependencies.featureFlags.useRenderFrameSync,
        suppressLiveGps = sessionBindings.suppressLiveGps,
        allowSensorStart = sessionBindings.allowSensorStart
    )

    val widgetLayout = rememberMapScreenWidgetLayoutBinding(
        activeProfileId = profileLookAndFeelBinding.activeProfileId,
        density = density
    )
    ensureSafeContainerFallback(
        safeContainerSizeState = safeContainerSizeState,
        screenWidthPx = widgetLayout.screenWidthPx,
        screenHeightPx = widgetLayout.screenHeightPx
    )

    val variometerLayout = rememberVariometerLayout(
        mapViewModel = mapViewModel,
        activeProfileId = profileLookAndFeelBinding.activeProfileId,
        screenWidthPx = widgetLayout.screenWidthPx,
        screenHeightPx = widgetLayout.screenHeightPx,
        density = density
    )

    MapVisibilityLifecycleEffect(mapViewModel)
    val scaffoldInputs = rememberMapScreenScaffoldInputs(
        coroutineScope = coroutineScope,
        navController = navController,
        drawerState = drawerState,
        profileExpanded = profileExpanded,
        mapStyleExpanded = mapStyleExpanded,
        settingsExpanded = settingsExpanded,
        initialMapStyle = initialMapStyle,
        onMapStyleSelected = onMapStyleSelected,
        onOpenGeneralSettings = onOpenGeneralSettings,
        mapViewModel = mapViewModel,
        hotPathBindings = hotPathBindings,
        rootUiBinding = rootUiBinding,
        bindings = bindings,
        profileLookAndFeelBinding = profileLookAndFeelBinding,
        flightCardsBinding = flightCardsBinding,
        widgetLayout = widgetLayout,
        variometerLayout = variometerLayout,
        flightDataManager = flightDataManager,
        managers = managers,
        mapState = mapState,
        mapRuntimeController = mapRuntimeController,
        density = density,
        safeContainerSizeState = safeContainerSizeState
    )
    MapScreenScaffold(inputs = scaffoldInputs) {
        MapScreenScaffoldContentHost(
            inputs = scaffoldInputs,
            weGlideUploadPrompt = rootUiBinding.weGlideUploadPrompt,
            onConfirmWeGlideUploadPrompt = mapViewModel::onConfirmWeGlideUploadPrompt,
            onDismissWeGlideUploadPrompt = mapViewModel::onDismissWeGlideUploadPrompt
        )
    }
}
