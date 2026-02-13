package com.example.xcpro.map.ui
/**
 * Root MapScreen composable wiring managers, state, and scaffold.
 * Invariants: UI renders state only; mutations are routed through MapScreenViewModel.
 */

import com.example.xcpro.map.ui.effects.MapComposeEffects
import com.example.xcpro.map.MapLifecycleEffects
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapUiEvent
import com.example.xcpro.map.MapStateStore
import com.example.dfcards.dfcards.FlightDataViewModel
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.xcpro.airspace.AirspaceViewModel
// G REMOVED DataQuality - no longer used
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelViewModel
import android.util.Log
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.map.widgets.MapWidgetId
import com.example.xcpro.map.widgets.MapWidgetLayoutViewModel
import com.example.xcpro.map.widgets.MapWidgetOffsets
import com.example.xcpro.hawk.HAWK_VARIO_CARD_ID
import com.example.xcpro.tasks.rememberTaskManagerCoordinator
import kotlinx.coroutines.launch

/**
 * G PHASE 2: Convert CompleteFlightData (from FlightDataCalculator) to RealTimeFlightData (for cards)
 *
 * This adapter function maintains backward compatibility with existing card system
 * while migrating to the new unified sensor architecture.
 */
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
    mapViewModel: MapScreenViewModel
) {
    val context = LocalContext.current
    val mapUiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val taskManager = rememberTaskManagerCoordinator()
    MapScreenSideEffects(
        uiEffects = mapViewModel.uiEffects,
        drawerState = drawerState,
        context = context,
        onDrawerOpenChanged = { isOpen ->
            mapViewModel.onEvent(MapUiEvent.SetDrawerOpen(isOpen))
        }
    )

    // ?o. Runtime map state owned by the UI layer
    val mapState = remember { MapScreenState() }
    val mapStateReader = mapViewModel.mapState

    // GAA Map Orientation Manager
    val orientationManager = mapViewModel.orientationManager
    val orientationData by orientationManager.orientationFlow.collectAsStateWithLifecycle()
    val windArrowState by mapViewModel.windArrowState.collectAsStateWithLifecycle()
    val showWindSpeedOnVario by mapViewModel.showWindSpeedOnVario.collectAsStateWithLifecycle()
    val showHawkCard by mapViewModel.showHawkCard.collectAsStateWithLifecycle()
    val hiddenCardIds = remember(showHawkCard) {
        if (showHawkCard) emptySet() else setOf(HAWK_VARIO_CARD_ID)
    }

    // GAA SIMPLIFIED: Remove permission dialog variables, always enable everything
    val safeContainerSizeState = remember { mutableStateOf(IntSize.Zero) }
    var safeContainerSize by safeContainerSizeState

    // GAA DEBUG: Track container size changes
    trackSafeContainerSize(safeContainerSize) { size ->
        mapViewModel.updateSafeContainerSize(
            MapStateStore.MapSize(
                widthPx = size.width,
                heightPx = size.height
            )
        )
    }

    // GAA Flight Cards ViewModel
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val mapEntry = remember(navBackStackEntry) { navController.getBackStackEntry("map") }
    val flightViewModel: FlightDataViewModel = hiltViewModel(mapEntry)
    // GAA REFACTORED: No longer collect cardStates here - CardContainer handles it directly
    val profileModeCards by flightViewModel.profileModeCards.collectAsStateWithLifecycle()
    val profileModeTemplates by flightViewModel.profileModeTemplates.collectAsStateWithLifecycle()
    val activeTemplateId by flightViewModel.activeTemplateId.collectAsStateWithLifecycle()
    LaunchedEffect(flightViewModel) {
        mapViewModel.cardIngestionCoordinator.bindCards(flightViewModel)
    }

    // GAA Initialize FlightDataManager
    val flightDataManager = mapViewModel.flightDataManager

    // GAA Profile ViewModel
    val profileViewModel: com.example.xcpro.profiles.ProfileViewModel = hiltViewModel()
    val lookAndFeelViewModel: LookAndFeelViewModel = hiltViewModel()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val activeProfileId = profileUiState.activeProfile?.id ?: "default"
    LaunchedEffect(activeProfileId) {
        lookAndFeelViewModel.setProfileId(activeProfileId)
    }
    val lookAndFeelUiState by lookAndFeelViewModel.uiState.collectAsStateWithLifecycle()
    val cardStyle = remember(lookAndFeelUiState.cardStyleId) {
        CardStyle.values().find { it.id == lookAndFeelUiState.cardStyleId }
            ?: CardStyle.default
    }
    val airspaceViewModel: AirspaceViewModel = hiltViewModel()
    val airspaceState by airspaceViewModel.uiState.collectAsStateWithLifecycle()

    val managers = rememberMapScreenManagers(
        context = context,
        mapState = mapState,
        mapStateReader = mapStateReader,
        taskManager = taskManager,
        mapStateActions = mapViewModel.mapStateActions,
        orientationManager = orientationManager,
        sensorsUseCase = mapViewModel.mapSensorsRuntimeUseCase,
        replaySessionState = mapViewModel.replaySessionState,
        replayHeadingProvider = mapViewModel::getInterpolatedReplayHeadingDeg,
        replayFixProvider = mapViewModel::getInterpolatedReplayPose,
        featureFlags = mapViewModel.mapFeatureFlags,
        coroutineScope = coroutineScope,
        airspaceUseCase = mapViewModel.mapAirspaceUseCase,
        waypointFilesUseCase = mapViewModel.mapWaypointFilesUseCase
    )
    val snailTrailManager = managers.snailTrailManager
    val overlayManager = managers.overlayManager
    val widgetManager = managers.widgetManager
    val taskScreenManager = managers.taskScreenManager
    val cameraManager = managers.cameraManager
    val locationManager = managers.locationManager
    val lifecycleManager = managers.lifecycleManager
    val modalManager = managers.modalManager
    val mapInitializer = managers.mapInitializer
    val panelState by taskScreenManager.taskPanelState.collectAsStateWithLifecycle()
    val isTaskPanelVisible = panelState != com.example.xcpro.map.MapTaskScreenManager.TaskPanelState.HIDDEN

    BackHandler(enabled = drawerState.isOpen || modalManager.isAnyModalOpen() || isTaskPanelVisible) {
        when {
            drawerState.isOpen -> {
                coroutineScope.launch { drawerState.close() }
            }
            modalManager.handleBackGesture() -> Unit
            taskScreenManager.handleBackGesture() -> Unit
            else -> {
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(mapState.mapLibreMap, airspaceState.enabledFiles, airspaceState.classStates) {
        overlayManager.refreshAirspace(mapState.mapLibreMap)
    }
    val bindings = rememberMapScreenBindings(
        mapViewModel = mapViewModel,
        mapStateReader = mapStateReader
    )
    val gpsStatus = bindings.gpsStatus
    val showRecenterButton = bindings.showRecenterButton
    val showReturnButton = bindings.showReturnButton
    val currentMode = bindings.currentMode
    val currentZoom = bindings.currentZoom
    val replaySession = bindings.replaySession
    val suppressLiveGps = bindings.suppressLiveGps
    val allowSensorStart = bindings.allowSensorStart
    val locationForUi = bindings.locationForUi
    val trailSettings = bindings.trailSettings
    val trailUpdateResult = bindings.trailUpdateResult
    val ognTargets = bindings.ognTargets
    val ognOverlayEnabled = bindings.ognOverlayEnabled
    val ognIconSizePx = bindings.ognIconSizePx
    val adsbTargets = bindings.adsbTargets
    val adsbOverlayEnabled = bindings.adsbOverlayEnabled
    val adsbIconSizePx = bindings.adsbIconSizePx

    // G AAT Edit Mode State - Track when AAT pin editing is active
    val isAATEditMode = bindings.isAATEditMode

    LaunchedEffect(ognTargets, ognOverlayEnabled) {
        overlayManager.updateOgnTrafficTargets(
            if (ognOverlayEnabled) ognTargets else emptyList()
        )
    }
    LaunchedEffect(ognIconSizePx) {
        overlayManager.setOgnIconSizePx(ognIconSizePx)
    }
    LaunchedEffect(adsbTargets, adsbOverlayEnabled) {
        overlayManager.updateAdsbTrafficTargets(
            if (adsbOverlayEnabled) adsbTargets else emptyList()
        )
    }
    LaunchedEffect(adsbIconSizePx) {
        overlayManager.setAdsbIconSizePx(adsbIconSizePx)
    }
    
    // Map FlightMode to FlightModeSelection using FlightDataManager
    val currentFlightModeSelection = flightDataManager.currentFlightMode
    LaunchedEffect(activeProfileId, currentFlightModeSelection) {
        Log.d("MapScreenRoot", "activeProfile=$activeProfileId mode=$currentFlightModeSelection")
    }
    MapScreenRuntimeEffects(
        taskType = bindings.taskType,
        drawerState = drawerState,
        isAATEditMode = isAATEditMode,
        onExitAATEditMode = mapViewModel::exitAATEditMode,
        snailTrailManager = snailTrailManager,
        locationManager = locationManager,
        featureFlags = mapViewModel.mapFeatureFlags,
        trailUpdateResult = trailUpdateResult,
        trailSettings = trailSettings,
        currentZoom = currentZoom,
        suppressLiveGps = suppressLiveGps,
        currentFlightModeSelection = currentFlightModeSelection,
        orientationManager = orientationManager
    )
    val showDistanceCircles = bindings.showDistanceCircles
    // G Location Permission Launcher through LocationManager
    val locationPermissionLauncher = rememberLocationPermissionLauncher(locationManager)


    // G Variometer test state for debug effects
    // G CENTRALIZED EFFECTS - Replace all individual LaunchedEffect blocks
    // G REFACTORED: Removed cardStates parameter - no longer needed
        MapComposeEffects.AllMapEffects(
            locationManager = locationManager,
            locationPermissionLauncher = locationPermissionLauncher,
            currentLocation = locationForUi,
            orientationData = orientationData,
            orientationManager = orientationManager,
            uiState = profileUiState,
            flightDataManager = flightDataManager,
            currentMode = currentMode,
            onModeChange = mapViewModel::setFlightMode,
            currentFlightModeSelection = currentFlightModeSelection,
            safeContainerSize = safeContainerSize,
            flightViewModel = flightViewModel,
            profileModeCards = profileModeCards,
            profileModeTemplates = profileModeTemplates,
            activeTemplateId = activeTemplateId,
            initialMapStyle = initialMapStyle,
            onMapStyleResolved = mapViewModel::setMapStyle,
            replaySessionState = replaySession,
            suppressLiveGps = suppressLiveGps,
            allowSensorStart = allowSensorStart
        )

    // G CENTRALIZED LIFECYCLE EFFECTS - Replace individual DisposableEffect blocks
    MapLifecycleEffects.LifecycleObserverEffect(lifecycleManager)
    DisposableEffect(lifecycleManager) {
        onDispose { lifecycleManager.cleanup() }
    }

    // Load widget positions from ViewModel-backed layout preferences (SSOT).
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val widgetLayoutViewModel: MapWidgetLayoutViewModel = hiltViewModel()
    val densityScale = remember(density) { DensityScale(density = density.density, fontScale = density.fontScale) }
    val widgetOffsets by widgetLayoutViewModel.offsets.collectAsStateWithLifecycle()
    LaunchedEffect(screenWidthPx, screenHeightPx, densityScale) {
        widgetLayoutViewModel.loadLayout(screenWidthPx, screenHeightPx, densityScale)
    }
    val resolvedWidgetOffsets = widgetOffsets ?: MapWidgetOffsets(
        sideHamburger = OffsetPx.Zero,
        flightMode = OffsetPx.Zero,
        ballast = OffsetPx.Zero
    )
    val (hamburgerOffsetState, flightModeOffsetState, ballastOffsetState) =
        rememberMapWidgetOffsets(resolvedWidgetOffsets)
    val onHamburgerOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit = { offset ->
        hamburgerOffsetState.value = offset
        widgetLayoutViewModel.updateOffset(MapWidgetId.SIDE_HAMBURGER, offset.toOffsetPx())
    }
    val onFlightModeOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit = { offset ->
        flightModeOffsetState.value = offset
        widgetLayoutViewModel.updateOffset(MapWidgetId.FLIGHT_MODE, offset.toOffsetPx())
    }
    val onBallastOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit = { offset ->
        ballastOffsetState.value = offset
        widgetLayoutViewModel.updateOffset(MapWidgetId.BALLAST, offset.toOffsetPx())
    }

    // If the card layer hasn't reported its safe bounds yet, seed the size from the screen metrics
    // so MapScreenViewModel can mark cardHydrationReady once flight data arrives. The card grid
    // will overwrite this fallback as soon as its own onGloballyPositioned callback fires.
    ensureSafeContainerFallback(safeContainerSizeState, screenWidthPx, screenHeightPx)

    val variometerLayout = rememberVariometerLayout(
        mapViewModel = mapViewModel,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        density = density
    )
    val variometerUiState = variometerLayout.uiState
    val minVariometerSizePx = variometerLayout.minSizePx
    val maxVariometerSizePx = variometerLayout.maxSizePx

    val mapRuntimeController = rememberMapRuntimeController(
        overlayManager = overlayManager,
        mapViewModel = mapViewModel,
        cameraManager = cameraManager,
        orientationData = orientationData,
        isReplayPlaying = replaySession.status == com.example.xcpro.replay.SessionStatus.PLAYING
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapViewModel) {
        mapViewModel.setMapVisible(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> mapViewModel.setMapVisible(true)
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> mapViewModel.setMapVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewModel.setMapVisible(false)
        }
    }

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
        taskManager = taskManager,
        mapUiState = mapUiState,
        bindings = bindings,
        managers = managers,
        mapState = mapState,
        mapRuntimeController = mapRuntimeController,
        density = density,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        variometerUiState = variometerUiState,
        minVariometerSizePx = minVariometerSizePx,
        maxVariometerSizePx = maxVariometerSizePx,
        safeContainerSizeState = safeContainerSizeState,
        hamburgerOffsetState = hamburgerOffsetState,
        flightModeOffsetState = flightModeOffsetState,
        ballastOffsetState = ballastOffsetState,
        onHamburgerOffsetChange = onHamburgerOffsetChange,
        onFlightModeOffsetChange = onFlightModeOffsetChange,
        onBallastOffsetChange = onBallastOffsetChange,
        flightViewModel = flightViewModel,
        windArrowState = windArrowState,
        showWindSpeedOnVario = showWindSpeedOnVario,
        cardStyle = cardStyle,
        hiddenCardIds = hiddenCardIds
    )

    MapScreenScaffold(scaffoldInputs)
}

