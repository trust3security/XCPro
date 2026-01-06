package com.example.xcpro.map.ui
/**
 * Root MapScreen composable wiring managers, state, and scaffold.
 * Invariants: UI renders state only; mutations are routed through MapScreenViewModel.
 */


import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.ui.effects.MapComposeEffects
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.MapCameraEffects
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapLifecycleEffects
import com.example.xcpro.map.MapLifecycleManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapUiEvent
import com.example.xcpro.map.MapStateStore
import com.example.ui1.icons.Task
import com.example.dfcards.dfcards.CardContainer
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.dfcards.FlightModeSelection
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.collect
import com.example.xcpro.FileWaypointRepo
import com.example.xcpro.saveConfig
// G£à REMOVED DataQuality - no longer used
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToInt

private const val TAG = "MapScreen"
private const val MAP_PREFS_NAME = "MapPrefs"
const val INITIAL_LATITUDE = -30.87

/**
 * G£à PHASE 2: Convert CompleteFlightData (from FlightDataCalculator) to RealTimeFlightData (for cards)
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
    onSaveConfig: () -> Unit = {},
    mapViewModel: MapScreenViewModel
) {
    val context = LocalContext.current
    val replayFilePicker = rememberReplayFilePicker(context, mapViewModel::onReplayFileChosen)

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
    val modes = FlightMode.values()

    // ?o. Map Orientation Manager
    val orientationManager = mapViewModel.orientationManager
    val orientationData by orientationManager.orientationFlow.collectAsStateWithLifecycle()
    val taskManager = mapViewModel.taskManager  // ?o. Using coordinator for task management
    val waypointRepo = remember(mapUiState.waypoints) { FileWaypointRepo(mapUiState.waypoints) }

    // G£à SIMPLIFIED: Remove permission dialog variables, always enable everything
    val safeContainerSizeState = remember { mutableStateOf(IntSize.Zero) }
    var safeContainerSize by safeContainerSizeState

    // G£à DEBUG: Track container size changes
    LaunchedEffect(safeContainerSize) {
        Log.d("MapScreen", "=ƒöì CONTAINER SIZE CHANGED: $safeContainerSize")
        if (safeContainerSize.width > 0 && safeContainerSize.height > 0) {
            mapViewModel.updateSafeContainerSize(
            MapStateStore.MapSize(
                widthPx = safeContainerSize.width,
                heightPx = safeContainerSize.height
            )
        )
        }
    }

    // G£à Flight Cards ViewModel
    val flightViewModel: FlightDataViewModel = viewModel()
    // G£à REFACTORED: No longer collect cardStates here - CardContainer handles it directly
    val selectedCardIds by flightViewModel.selectedCardIds.collectAsStateWithLifecycle()
    val profileModeCards by flightViewModel.profileModeCards.collectAsStateWithLifecycle()
    val profileModeTemplates by flightViewModel.profileModeTemplates.collectAsStateWithLifecycle()
    val activeTemplateId by flightViewModel.activeTemplateId.collectAsStateWithLifecycle()
    val cardPreferences = mapViewModel.cardPreferences

    // G£à Initialize FlightDataManager
    val flightDataManager = mapViewModel.flightDataManager
    // Map Overlay Manager - centralized overlay management
    val overlayManager = remember(mapState, taskManager, context, mapStateReader, mapViewModel) {
        MapOverlayManager(context, mapState, mapStateReader, taskManager, mapViewModel)
    }

    // G£à UI Widget Manager - centralized widget management
    val widgetPrefs = remember(context) {
        context.getSharedPreferences(MAP_PREFS_NAME, Context.MODE_PRIVATE)
    }
    val widgetManager = remember(mapState, widgetPrefs) {
        MapUIWidgetManager(mapState, widgetPrefs)
    }

    // G£à Profile ViewModel
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
    // G£à TaskScreenManager - Centralized task screen handling
    val taskScreenManager = remember(mapState, taskManager) {
        MapTaskScreenManager(mapState, taskManager)
    }

    // G£à CameraManager - Centralized camera handling
    val cameraManager = remember(mapState, mapStateReader, mapViewModel) {
        MapCameraManager(mapState, mapStateReader, mapViewModel)
    }

    // G£à LocationManager - Centralized location handling
    val locationManager = remember(
        mapState,
        coroutineScope,
        mapStateReader,
        mapViewModel.qnhPreferencesRepository,
        mapViewModel.varioServiceManager,
        context
    ) {
        LocationManager(
            context = context,
            mapState = mapState,
            mapStateReader = mapStateReader,
            stateActions = mapViewModel,
            coroutineScope = coroutineScope,
            qnhPreferencesRepository = mapViewModel.qnhPreferencesRepository,
            varioServiceManager = mapViewModel.varioServiceManager
        )
    }

    // G£à LifecycleManager - Centralized lifecycle handling
    val lifecycleManager = remember(
        mapState,
        orientationManager,
        locationManager,
        mapViewModel.igcReplayController
    ) {
        MapLifecycleManager(mapState, orientationManager, locationManager, mapViewModel.igcReplayController)
    }

    // G£à ModalManager - Centralized modal handling
    val modalManager = remember(mapState) {
        MapModalManager(mapState)
    }

    // G£à Backward compatibility variables (using locationManager)

    // Map Initializer
    val mapInitializer = remember(
        mapState,
        mapStateReader,
        orientationManager,
        taskManager,
        mapViewModel.unifiedSensorManager,
        context
    ) {
        MapInitializer(
            context = context,
            mapState = mapState,
            mapStateReader = mapStateReader,
            stateActions = mapViewModel,
            orientationManager = orientationManager,
            taskManager = taskManager,
            unifiedSensorManager = mapViewModel.unifiedSensorManager
        )
    }
    val gpsStatus by mapViewModel.gpsStatusFlow.collectAsStateWithLifecycle()

    // G?? Location state through LocationManager
    val showRecenterButton by mapStateReader.showRecenterButton.collectAsStateWithLifecycle()
    val showReturnButton by mapStateReader.showReturnButton.collectAsStateWithLifecycle()
    val currentMode by mapStateReader.currentMode.collectAsStateWithLifecycle()
    val currentZoom by mapStateReader.currentZoom.collectAsStateWithLifecycle()
    val replaySession by mapViewModel.replaySessionState.collectAsStateWithLifecycle()
    val suppressLiveGps by mapViewModel.suppressLiveGps.collectAsStateWithLifecycle()
    val allowSensorStart by mapViewModel.allowSensorStart.collectAsStateWithLifecycle()
    val locationForUi by mapViewModel.mapLocation.collectAsStateWithLifecycle()

    // G£à AAT Edit Mode State - Track when AAT pin editing is active
    val isAATEditMode by mapViewModel.isAATEditMode.collectAsStateWithLifecycle()
    
    // G£à CRITICAL FIX: Reset AAT edit mode when task type changes
    LaunchedEffect(taskManager.taskType, isAATEditMode) {
        if (taskManager.taskType != TaskType.AAT && isAATEditMode) {
            Log.d(TAG, "=ƒöº Task type changed to ${taskManager.taskType} - resetting AAT edit mode")
            mapViewModel.exitAATEditMode()
        }
    }

    // G£à Control drawer gestures based on task type and edit mode
    // Uses MapTaskIntegration to determine if drawer should be blocked
    LaunchedEffect(isAATEditMode, taskManager.taskType) {
        val shouldBlock = MapTaskIntegration.shouldBlockDrawerGestures(
            taskType = taskManager.taskType,
            isAATEditMode = isAATEditMode
        )

        if (shouldBlock) {
            // Close drawer if it's open and prevent it from opening
            if (drawerState.isOpen) {
                drawerState.close()
            }
            Log.d(TAG, "=ƒÜ½ Task-specific drawer blocking active (${taskManager.taskType})")
        } else {
            Log.d(TAG, "G£à Drawer gestures enabled")
        }
    }
    val savedLocation by mapStateReader.savedLocation.collectAsStateWithLifecycle()
    val savedZoom by mapStateReader.savedZoom.collectAsStateWithLifecycle()
    val savedBearing by mapStateReader.savedBearing.collectAsStateWithLifecycle()
    val hasInitiallyCentered by mapStateReader.hasInitiallyCentered.collectAsStateWithLifecycle()
    val showDistanceCircles by mapStateReader.showDistanceCircles.collectAsStateWithLifecycle()
    val cardHydrationReady by mapViewModel.cardHydrationReady.collectAsStateWithLifecycle()

    // G£à Location Permission Launcher through LocationManager
    val locationPermissionLauncher = rememberLocationPermissionLauncher(locationManager)

    // G£à Map FlightMode to FlightModeSelection using FlightDataManager
    val currentFlightModeSelection = flightDataManager.currentFlightMode
    LaunchedEffect(currentFlightModeSelection) {
        orientationManager.setFlightMode(currentFlightModeSelection)
    }

    // G£à Variometer test state for debug effects
    // G£à CENTRALIZED EFFECTS - Replace all individual LaunchedEffect blocks
    // G£à REFACTORED: Removed cardStates parameter - no longer needed
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
        suppressLiveGps = suppressLiveGps,
        allowSensorStart = allowSensorStart
    )

    // G£à CENTRALIZED LIFECYCLE EFFECTS - Replace individual DisposableEffect blocks
    MapLifecycleEffects.LifecycleObserverEffect(lifecycleManager)
    DisposableEffect(lifecycleManager) {
        onDispose { lifecycleManager.cleanup() }
    }


    // Load widget positions using existing widgetManager and density
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val widgetPositions = remember(screenWidthPx, screenHeightPx) {
        widgetManager.loadWidgetPositions(screenWidthPx, screenHeightPx, density)
    }

    // If the card layer hasn't reported its safe bounds yet, seed the size from the screen metrics
    // so MapScreenViewModel can mark cardHydrationReady once flight data arrives. The card grid
    // will overwrite this fallback as soon as its own onGloballyPositioned callback fires.
    LaunchedEffect(screenWidthPx, screenHeightPx) {
        if (safeContainerSize == IntSize.Zero) {
            val fallbackWidth = screenWidthPx.roundToInt().coerceAtLeast(1)
            val fallbackHeight = screenHeightPx.roundToInt().coerceAtLeast(1)
            if (fallbackWidth > 0 && fallbackHeight > 0) {
                safeContainerSizeState.value = IntSize(fallbackWidth, fallbackHeight)
            }
        }
    }

    val hamburgerOffsetState = remember { mutableStateOf(widgetPositions.sideHamburgerOffset) }
    val flightModeOffsetState = remember { mutableStateOf(widgetPositions.flightModeOffset) }
    val ballastOffsetState = remember { mutableStateOf(widgetPositions.ballastOffset) }

    val variometerUiState by mapViewModel.variometerUiState.collectAsStateWithLifecycle()
    val minVariometerSizePx = with(density) { 60.dp.toPx() }
    val maxVariometerSizePx = min(screenWidthPx, screenHeightPx)
    val defaultVariometerSizePx = with(density) { 150.dp.toPx() }

    LaunchedEffect(screenWidthPx, screenHeightPx) {
        mapViewModel.ensureVariometerLayout(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            defaultSizePx = defaultVariometerSizePx,
            minSizePx = minVariometerSizePx,
            maxSizePx = maxVariometerSizePx
        )
    }

    // G£à CENTRALIZED CAMERA EFFECTS - Replace camera animation and orientation effects
    MapCameraEffects.AllCameraEffects(
        cameraManager = cameraManager,
        bearing = orientationData.bearing,
        orientationMode = orientationData.mode,
        bearingSource = orientationData.bearingSource,
        replayPlaying = replaySession.status == com.example.xcpro.replay.SessionStatus.PLAYING
    )

    val mapRuntimeController = remember(overlayManager) {
        MapRuntimeController(overlayManager)
    }

    LaunchedEffect(mapRuntimeController) {
        mapViewModel.mapCommands.collect { command ->
            mapRuntimeController.apply(command)
        }
    }


    MapScreenScaffold(
        drawerState = drawerState,
        navController = navController,
        profileExpanded = profileExpanded,
        mapStyleExpanded = mapStyleExpanded,
        settingsExpanded = settingsExpanded,
        initialMapStyle = initialMapStyle,
        onDrawerItemSelected = { item ->
            Log.d(TAG, "Navigation drawer item selected: $item")
            coroutineScope.launch {
                drawerState.close()
                taskScreenManager.handleNavigationTaskSelection(item)
            }
        },
        onMapStyleSelected = { style ->
            mapViewModel.setMapStyle(style)
            saveConfig(context, style, emptyMap(), profileExpanded.value, mapStyleExpanded.value)
            Log.d(TAG, "Map style selected: $style")
            onMapStyleSelected(style)
        },
        gpsStatus = gpsStatus,
        isLoadingWaypoints = mapUiState.isLoadingWaypoints,
        density = density,
        mapState = mapState,
        mapInitializer = mapInitializer,
        onMapReady = mapRuntimeController::onMapReady,
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
        onModeChange = mapViewModel::setFlightMode,
        currentLocation = locationForUi,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton,
        showDistanceCircles = showDistanceCircles,
        isUiEditMode = mapUiState.isUiEditMode,
        onEditModeChange = { enabled -> mapViewModel.onEvent(MapUiEvent.SetUiEditMode(enabled)) },
        isAATEditMode = isAATEditMode,
        onSetAATEditMode = mapViewModel::setAATEditMode,
        onExitAATEditMode = mapViewModel::exitAATEditMode,
        safeContainerSize = safeContainerSizeState,
        overlayManager = overlayManager,
        modalManager = modalManager,
        widgetManager = widgetManager,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        variometerUiState = variometerUiState,
        minVariometerSizePx = minVariometerSizePx,
        maxVariometerSizePx = maxVariometerSizePx,
        onVariometerOffsetChange = { offset ->
            mapViewModel.onVariometerOffsetCommitted(
                offset = offset,
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
        taskScreenManager = taskScreenManager,
        waypointData = mapUiState.waypoints,
        unitsPreferences = mapUiState.unitsPreferences,
        ballastUiState = mapViewModel.ballastUiState,
        isBallastPillHidden = mapUiState.hideBallastPill,
        onBallastCommand = mapViewModel::submitBallastCommand,
        onHamburgerTap = { mapViewModel.onEvent(MapUiEvent.ToggleDrawer) },
        onHamburgerLongPress = { mapViewModel.onEvent(MapUiEvent.ToggleUiEditMode) },
        cardStyle = cardStyle,
        replayState = mapViewModel.replaySessionState,
        onReplayPlayPause = mapViewModel::onReplayPlayPause,
        onReplayStop = mapViewModel::onReplayStop,
        onReplaySpeedChange = mapViewModel::onReplaySpeedChanged,
        onReplaySeek = mapViewModel::onReplaySeek,
        showReplayDevFab = mapViewModel.showReplayDebugFab,
        showVarioDemoFab = mapViewModel.showVarioDemoFab,
        onVarioDemoClick = mapViewModel::onVarioDemoReplay,
        onReplayPickFileClick = {
            replayFilePicker.launch(
                arrayOf(
                    "text/plain",
                    "application/octet-stream",
                    "application/*",
                    "*/*"
                )
            )
        }
    )
}




