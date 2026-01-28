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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
// G REMOVED DataQuality - no longer used
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import android.util.Log

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
    val taskManager = mapViewModel.taskManager

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
    val mapEntry = remember(navController) { navController.getBackStackEntry("map") }
    val flightViewModel: FlightDataViewModel = viewModel(mapEntry)
    // GAA REFACTORED: No longer collect cardStates here - CardContainer handles it directly
    val profileModeCards by flightViewModel.profileModeCards.collectAsStateWithLifecycle()
    val profileModeTemplates by flightViewModel.profileModeTemplates.collectAsStateWithLifecycle()
    val activeTemplateId by flightViewModel.activeTemplateId.collectAsStateWithLifecycle()
    val cardPreferences = mapViewModel.cardPreferences

    // GAA Initialize FlightDataManager
    val flightDataManager = mapViewModel.flightDataManager

    // GAA Profile ViewModel
    val profileViewModel: com.example.xcpro.profiles.ProfileViewModel = hiltViewModel()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val activeProfileId = profileUiState.activeProfile?.id ?: "default"
    val lookAndFeelPreferences = remember(context) { LookAndFeelPreferences(context) }
    val cardStyleFlow = remember(activeProfileId) {
        lookAndFeelPreferences.observeCardStyle(activeProfileId)
    }
    val cardStyle by cardStyleFlow.collectAsStateWithLifecycle(
        initialValue = lookAndFeelPreferences.getCardStyle(activeProfileId)
    )

    val managers = rememberMapScreenManagers(
        context = context,
        mapState = mapState,
        mapStateReader = mapStateReader,
        taskManager = taskManager,
        mapStateActions = mapViewModel.mapStateActions,
        orientationManager = orientationManager,
        varioServiceManager = mapViewModel.varioServiceManager,
        igcReplayController = mapViewModel.igcReplayController,
        coroutineScope = coroutineScope
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

    // G AAT Edit Mode State - Track when AAT pin editing is active
    val isAATEditMode = bindings.isAATEditMode
    
    // Map FlightMode to FlightModeSelection using FlightDataManager
    val currentFlightModeSelection = flightDataManager.currentFlightMode
    LaunchedEffect(activeProfileId, currentFlightModeSelection) {
        Log.d("MapScreenRoot", "activeProfile=$activeProfileId mode=$currentFlightModeSelection")
    }
    MapScreenRuntimeEffects(
        taskManager = taskManager,
        drawerState = drawerState,
        isAATEditMode = isAATEditMode,
        onExitAATEditMode = mapViewModel::exitAATEditMode,
        snailTrailManager = snailTrailManager,
        locationManager = locationManager,
        trailUpdateResult = trailUpdateResult,
        trailSettings = trailSettings,
        currentZoom = currentZoom,
        suppressLiveGps = suppressLiveGps,
        currentFlightModeSelection = currentFlightModeSelection,
        orientationManager = orientationManager
    )
    val showDistanceCircles = bindings.showDistanceCircles
    val cardHydrationReady = bindings.cardHydrationReady

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
            cardPreferences = cardPreferences,
            profileModeCards = profileModeCards,
            profileModeTemplates = profileModeTemplates,
            activeTemplateId = activeTemplateId,
            initialMapStyle = initialMapStyle,
            onMapStyleResolved = mapViewModel::setMapStyle,
            cardsReady = cardHydrationReady,
            replaySessionState = replaySession,
            suppressLiveGps = suppressLiveGps,
            allowSensorStart = allowSensorStart
        )

    // G CENTRALIZED LIFECYCLE EFFECTS - Replace individual DisposableEffect blocks
    MapLifecycleEffects.LifecycleObserverEffect(lifecycleManager)
    DisposableEffect(lifecycleManager) {
        onDispose { lifecycleManager.cleanup() }
    }

    // Load widget positions using existing widgetManager and density
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val (hamburgerOffsetState, flightModeOffsetState, ballastOffsetState) = 
        rememberMapWidgetOffsets(widgetManager, screenWidthPx, screenHeightPx, density)

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

    val scaffoldInputs = rememberMapScreenScaffoldInputs(
        context = context,
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
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        variometerUiState = variometerUiState,
        minVariometerSizePx = minVariometerSizePx,
        maxVariometerSizePx = maxVariometerSizePx,
        safeContainerSizeState = safeContainerSizeState,
        hamburgerOffsetState = hamburgerOffsetState,
        flightModeOffsetState = flightModeOffsetState,
        ballastOffsetState = ballastOffsetState,
        flightViewModel = flightViewModel,
        windArrowState = windArrowState,
        cardStyle = cardStyle
    )

    MapScreenScaffold(scaffoldInputs)
}

