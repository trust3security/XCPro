package com.example.xcpro.map.ui

/**
 * Root MapScreen composable wiring managers, state, and scaffold.
 * Invariants: UI renders state only; mutations are routed through MapScreenViewModel.
 */
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
import com.example.xcpro.hawk.HAWK_VARIO_CARD_ID
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapUiEvent
import com.example.xcpro.map.MapStateStore
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.replay.SessionStatus

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
    openGeneralSettingsOnStart: Boolean,
    onGeneralSettingsLaunchConsumed: () -> Unit,
    mapViewModel: MapScreenViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mapUiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val runtimeDependencies = mapViewModel.runtimeDependencies
    val flightDataManager = runtimeDependencies.flightDataManager
    val orientationManager = runtimeDependencies.orientationManager
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

    val orientationData by orientationManager.orientationFlow.collectAsStateWithLifecycle()
    val windArrowState by mapViewModel.windArrowState.collectAsStateWithLifecycle()
    val showWindSpeedOnVario by mapViewModel.showWindSpeedOnVario.collectAsStateWithLifecycle()
    val showHawkCard by mapViewModel.showHawkCard.collectAsStateWithLifecycle()
    val hiddenCardIds = remember(showHawkCard) {
        if (showHawkCard) emptySet() else setOf(HAWK_VARIO_CARD_ID)
    }

    // Simplified permission state: always enabled, but keep size hydration for card readiness.
    val safeContainerSizeState = remember { mutableStateOf(IntSize.Zero) }
    var safeContainerSize by safeContainerSizeState
    trackSafeContainerSize(safeContainerSize) { size ->
        mapViewModel.updateSafeContainerSize(
            MapStateStore.MapSize(
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
    LaunchedEffect(openGeneralSettingsOnStart) {
        if (openGeneralSettingsOnStart) {
            managers.modalManager.showGeneralSettingsModal()
            onGeneralSettingsLaunchConsumed()
        }
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

    MapScreenOverlayEffects(
        mapState = mapState,
        airspaceState = airspaceState,
        overlayManager = managers.overlayManager,
        ognTargets = bindings.ognTargets,
        ognOverlayEnabled = bindings.ognOverlayEnabled,
        ognThermalHotspots = bindings.ognThermalHotspots,
        showOgnSciaEnabled = bindings.showOgnSciaEnabled,
        ognTargetEnabled = bindings.ognTargetEnabled,
        ognResolvedTarget = bindings.ognResolvedTarget,
        ownshipLocation = bindings.locationForUi,
        showOgnThermalsEnabled = bindings.showOgnThermalsEnabled,
        ognDisplayUpdateMode = bindings.ognDisplayUpdateMode,
        ognGliderTrailSegments = bindings.ognGliderTrailSegments,
        ownshipAltitudeMeters = bindings.ownshipAltitudeMetersForOgn,
        ognAltitudeUnit = bindings.ognAltitudeUnit,
        unitsPreferences = mapUiState.unitsPreferences,
        ognIconSizePx = bindings.ognIconSizePx,
        adsbTargets = bindings.adsbTargets,
        adsbOverlayEnabled = bindings.adsbOverlayEnabled,
        adsbIconSizePx = bindings.adsbIconSizePx,
        adsbEmergencyFlashEnabled = bindings.adsbEmergencyFlashEnabled
    )
    MapWeatherOverlayEffects(overlayManager = managers.overlayManager)

    val currentFlightModeSelection by flightDataManager.currentFlightModeFlow.collectAsStateWithLifecycle()

    MapScreenRuntimeEffects(
        taskType = bindings.taskType,
        drawerState = drawerState,
        isAATEditMode = bindings.isAATEditMode,
        onExitAATEditMode = mapViewModel::exitAATEditMode,
        snailTrailManager = managers.snailTrailManager,
        locationManager = managers.locationManager,
        featureFlags = runtimeDependencies.featureFlags,
        trailUpdateResult = bindings.trailUpdateResult,
        trailSettings = bindings.trailSettings,
        currentZoom = bindings.currentZoom,
        suppressLiveGps = bindings.suppressLiveGps,
        currentFlightModeSelection = currentFlightModeSelection,
        orientationManager = orientationManager
    )

    val mapRuntimeController = rememberMapRuntimeController(
        overlayManager = managers.overlayManager,
        mapViewModel = mapViewModel,
        cameraManager = managers.cameraManager,
        orientationData = orientationData,
        isReplayPlaying = bindings.replaySession.status == SessionStatus.PLAYING
    )

    val locationPermissionLauncher = rememberLocationPermissionLauncher(managers.locationManager)
    MapScreenComposeAndLifecycleEffects(
        lifecycleManager = managers.lifecycleManager,
        runtimeController = mapRuntimeController,
        locationManager = managers.locationManager,
        locationPermissionLauncher = locationPermissionLauncher,
        currentLocation = bindings.locationForUi,
        orientationData = orientationData,
        orientationManager = orientationManager,
        profileUiState = profileLookAndFeelBinding.profileUiState,
        flightDataManager = flightDataManager,
        currentMode = bindings.currentMode,
        onModeChange = mapViewModel::setFlightMode,
        currentFlightModeSelection = currentFlightModeSelection,
        safeContainerSize = safeContainerSize,
        flightCardsBinding = flightCardsBinding,
        initialMapStyle = initialMapStyle,
        onMapStyleResolved = mapViewModel::setMapStyle,
        replaySessionState = bindings.replaySession,
        suppressLiveGps = bindings.suppressLiveGps,
        allowSensorStart = bindings.allowSensorStart
    )

    val widgetLayout = rememberMapScreenWidgetLayoutBinding(density = density)
    ensureSafeContainerFallback(
        safeContainerSizeState = safeContainerSizeState,
        screenWidthPx = widgetLayout.screenWidthPx,
        screenHeightPx = widgetLayout.screenHeightPx
    )

    val variometerLayout = rememberVariometerLayout(
        mapViewModel = mapViewModel,
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
        mapViewModel = mapViewModel,
        mapUiState = mapUiState,
        bindings = bindings,
        managers = managers,
        mapState = mapState,
        mapRuntimeController = mapRuntimeController,
        density = density,
        screenWidthPx = widgetLayout.screenWidthPx,
        screenHeightPx = widgetLayout.screenHeightPx,
        variometerUiState = variometerLayout.uiState,
        minVariometerSizePx = variometerLayout.minSizePx,
        maxVariometerSizePx = variometerLayout.maxSizePx,
        safeContainerSizeState = safeContainerSizeState,
        hamburgerOffsetState = widgetLayout.hamburgerOffsetState,
        flightModeOffsetState = widgetLayout.flightModeOffsetState,
        settingsOffsetState = widgetLayout.settingsOffsetState,
        ballastOffsetState = widgetLayout.ballastOffsetState,
        hamburgerSizePxState = widgetLayout.hamburgerSizePxState,
        settingsSizePxState = widgetLayout.settingsSizePxState,
        onHamburgerOffsetChange = widgetLayout.onHamburgerOffsetChange,
        onFlightModeOffsetChange = widgetLayout.onFlightModeOffsetChange,
        onSettingsOffsetChange = widgetLayout.onSettingsOffsetChange,
        onBallastOffsetChange = widgetLayout.onBallastOffsetChange,
        onHamburgerSizeChange = widgetLayout.onHamburgerSizeChange,
        onSettingsSizeChange = widgetLayout.onSettingsSizeChange,
        flightViewModel = flightCardsBinding.flightViewModel,
        flightDataManager = flightDataManager,
        windArrowState = windArrowState,
        showWindSpeedOnVario = showWindSpeedOnVario,
        cardStyle = profileLookAndFeelBinding.cardStyle,
        hiddenCardIds = hiddenCardIds
    )
    MapScreenScaffold(scaffoldInputs)
}
