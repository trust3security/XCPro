package com.example.xcpro.map.ui

import com.example.xcpro.tasks.TaskMapOverlay
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.components.AirspaceSettingsContent
import com.example.xcpro.profiles.FlightModeIndicator
import com.example.xcpro.profiles.ProfileIndicator
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.CompassWidget
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.BlueLocationOverlay
import com.example.xcpro.map.DistanceCirclesOverlay
import com.example.xcpro.map.DistanceCirclesCanvas
import com.example.xcpro.map.components.MapActionButtons
import com.example.xcpro.map.ui.effects.MapComposeEffects
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.MapCameraEffects
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapLifecycleEffects
import com.example.xcpro.map.MapLifecycleManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapModalUI
import com.example.xcpro.map.MapGestureSetup
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapUiEffect
import com.example.xcpro.map.MapUiEvent
import com.example.xcpro.map.MapStateStore
import com.example.dfcards.CardDefinition
import com.example.ui1.icons.Task
import com.example.ui1.icons.LocationSailplane
import com.example.dfcards.CardCategory
import com.example.dfcards.CardLibrary
import com.example.dfcards.dfcards.CardContainer
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.dfcards.FlightTemplate
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.CardLibraryModal
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.PressureUnit
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.UnitsFormatter
import com.example.dfcards.FlightDataProvider
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.sensors.GPSData
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.collect
import com.example.xcpro.tasks.BottomSheetState
import com.example.xcpro.FileWaypointRepo
import com.example.xcpro.saveConfig
// ✅ REMOVED DataQuality - no longer used
import com.example.ui1.UIVariometer
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.io.File
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToInt

private const val TAG = "MapScreen"
private const val MAP_PREFS_NAME = "MapPrefs"
const val INITIAL_LATITUDE = -30.87

/**
 * ✅ PHASE 2: Convert CompleteFlightData (from FlightDataCalculator) to RealTimeFlightData (for cards)
 *
 * This adapter function maintains backward compatibility with existing card system
 * while migrating to the new unified sensor architecture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
fun MapScreen(
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
    val modes = FlightMode.values()

    // ?o. Map Orientation Manager
    val orientationManager = mapViewModel.orientationManager
    val orientationData by orientationManager.orientationFlow.collectAsStateWithLifecycle()
    val taskManager = mapViewModel.taskManager  // ?o. Using coordinator for task management
    val waypointRepo = remember(mapUiState.waypoints) { FileWaypointRepo(mapUiState.waypoints) }

    // ✅ SIMPLIFIED: Remove permission dialog variables, always enable everything
    val safeContainerSizeState = remember { mutableStateOf(IntSize.Zero) }
    var safeContainerSize by safeContainerSizeState

    // ✅ DEBUG: Track container size changes
    LaunchedEffect(safeContainerSize) {
        Log.d("MapScreen", "🔍 CONTAINER SIZE CHANGED: $safeContainerSize")
        if (safeContainerSize.width > 0 && safeContainerSize.height > 0) {
            mapViewModel.updateSafeContainerSize(
            MapStateStore.MapSize(
                widthPx = safeContainerSize.width,
                heightPx = safeContainerSize.height
            )
        )
        }
    }

    // ✅ Flight Cards ViewModel
    val flightViewModel: FlightDataViewModel = viewModel()
    // ✅ REFACTORED: No longer collect cardStates here - CardContainer handles it directly
    val selectedCardIds by flightViewModel.selectedCardIds.collectAsStateWithLifecycle()
    val profileModeCards by flightViewModel.profileModeCards.collectAsStateWithLifecycle()
    val profileModeTemplates by flightViewModel.profileModeTemplates.collectAsStateWithLifecycle()
    val activeTemplateId by flightViewModel.activeTemplateId.collectAsStateWithLifecycle()
    val cardPreferences = mapViewModel.cardPreferences

    // ✅ Initialize FlightDataManager
    val flightDataManager = mapViewModel.flightDataManager
    // Map Overlay Manager - centralized overlay management
    val overlayManager = remember(mapState, taskManager, context, mapViewModel.mapStateStore) {
        MapOverlayManager(context, mapState, mapViewModel.mapStateStore, taskManager)
    }

    // ✅ UI Widget Manager - centralized widget management
    val widgetPrefs = remember(context) {
        context.getSharedPreferences(MAP_PREFS_NAME, Context.MODE_PRIVATE)
    }
    val widgetManager = remember(mapState, widgetPrefs) {
        MapUIWidgetManager(mapState, widgetPrefs)
    }

    // ✅ Profile ViewModel
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
    // ✅ TaskScreenManager - Centralized task screen handling
    val taskScreenManager = remember(mapState, taskManager) {
        MapTaskScreenManager(mapState, taskManager)
    }

    // ✅ CameraManager - Centralized camera handling
    val cameraManager = remember(mapState, mapViewModel.mapStateStore) {
        MapCameraManager(mapState, mapViewModel.mapStateStore)
    }

    // ✅ LocationManager - Centralized location handling
    val locationManager = remember(
        mapState,
        coroutineScope,
        mapViewModel.mapStateStore,
        mapViewModel.qnhPreferencesRepository,
        mapViewModel.varioServiceManager,
        context
    ) {
        LocationManager(
            context = context,
            mapState = mapState,
            mapStateStore = mapViewModel.mapStateStore,
            coroutineScope = coroutineScope,
            qnhPreferencesRepository = mapViewModel.qnhPreferencesRepository,
            varioServiceManager = mapViewModel.varioServiceManager
        )
    }

    // ✅ LifecycleManager - Centralized lifecycle handling
    val lifecycleManager = remember(
        mapState,
        orientationManager,
        locationManager,
        mapViewModel.igcReplayController
    ) {
        MapLifecycleManager(mapState, orientationManager, locationManager, mapViewModel.igcReplayController)
    }

    // ✅ ModalManager - Centralized modal handling
    val modalManager = remember(mapState) {
        MapModalManager(mapState)
    }

    // ✅ Backward compatibility variables (using locationManager)
    val unifiedSensorManager = locationManager.unifiedSensorManager

    // Map Initializer
    val mapInitializer = remember(
        mapState,
        mapViewModel.mapStateStore,
        orientationManager,
        taskManager,
        mapViewModel.unifiedSensorManager,
        context
    ) {
        MapInitializer(
            context = context,
            mapState = mapState,
            mapStateStore = mapViewModel.mapStateStore,
            orientationManager = orientationManager,
            taskManager = taskManager,
            unifiedSensorManager = mapViewModel.unifiedSensorManager
        )
    }
    val currentGpsLocation by unifiedSensorManager.gpsFlow.collectAsStateWithLifecycle()
    val isGpsActive = unifiedSensorManager.isGpsEnabled()
    val gpsStatus by mapViewModel.gpsStatusFlow.collectAsStateWithLifecycle()

    // ✅ Location state through LocationManager
    val showRecenterButton by mapViewModel.mapStateStore.showRecenterButton.collectAsStateWithLifecycle()
    val showReturnButton by mapViewModel.mapStateStore.showReturnButton.collectAsStateWithLifecycle()
    val currentMode by mapViewModel.mapStateStore.currentMode.collectAsStateWithLifecycle()
    val currentZoom by mapViewModel.mapStateStore.currentZoom.collectAsStateWithLifecycle()
    val replaySession by mapViewModel.replaySessionState.collectAsStateWithLifecycle()
    val suppressLiveGps = replaySession.selection != null
    val allowSensorStart = replaySession.selection == null ||
        replaySession.status == com.example.xcpro.replay.IgcReplayController.SessionStatus.IDLE
    val replayFlightData by flightDataManager.liveFlightDataFlow.collectAsStateWithLifecycle()
    val replayGpsLocation by remember(replayFlightData) {
        derivedStateOf {
            replayFlightData?.let { sample ->
                if (sample.latitude == 0.0 && sample.longitude == 0.0) {
                    null
                } else {
                    GPSData(
                        latLng = LatLng(sample.latitude, sample.longitude),
                        altitude = AltitudeM(sample.gpsAltitude),
                        speed = SpeedMs(sample.groundSpeed),
                        bearing = sample.track,
                        accuracy = sample.accuracy.toFloat(),
                        timestamp = sample.timestamp
                    )
                }
            }
        }
    }
    val locationForUi = if (suppressLiveGps) replayGpsLocation else currentGpsLocation

    // ✅ AAT Edit Mode State - Track when AAT pin editing is active
    val isAATEditMode by mapViewModel.isAATEditMode.collectAsStateWithLifecycle()
    
    // ✅ CRITICAL FIX: Reset AAT edit mode when task type changes
    LaunchedEffect(taskManager.taskType, isAATEditMode) {
        if (taskManager.taskType != TaskType.AAT && isAATEditMode) {
            Log.d(TAG, "🔧 Task type changed to ${taskManager.taskType} - resetting AAT edit mode")
            mapViewModel.exitAATEditMode()
        }
    }

    // ✅ Control drawer gestures based on task type and edit mode
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
            Log.d(TAG, "🚫 Task-specific drawer blocking active (${taskManager.taskType})")
        } else {
            Log.d(TAG, "✅ Drawer gestures enabled")
        }
    }
    val savedLocation by mapViewModel.mapStateStore.savedLocation.collectAsStateWithLifecycle()
    val savedZoom by mapViewModel.mapStateStore.savedZoom.collectAsStateWithLifecycle()
    val savedBearing by mapViewModel.mapStateStore.savedBearing.collectAsStateWithLifecycle()
    val hasInitiallyCentered by mapViewModel.mapStateStore.hasInitiallyCentered.collectAsStateWithLifecycle()
    val showDistanceCircles by mapViewModel.mapStateStore.showDistanceCircles.collectAsStateWithLifecycle()
    val cardHydrationReady by mapViewModel.cardHydrationReady.collectAsStateWithLifecycle()

    // ✅ Location Permission Launcher through LocationManager
    val locationPermissionLauncher = rememberLocationPermissionLauncher(locationManager)

    // ✅ Map FlightMode to FlightModeSelection using FlightDataManager
    val currentFlightModeSelection = flightDataManager.currentFlightMode
    LaunchedEffect(currentFlightModeSelection) {
        orientationManager.setFlightMode(currentFlightModeSelection)
    }

    // ✅ Variometer test state for debug effects
    // ✅ CENTRALIZED EFFECTS - Replace all individual LaunchedEffect blocks
    // ✅ REFACTORED: Removed cardStates parameter - no longer needed
    MapComposeEffects.AllMapEffects(
        locationManager = locationManager,
        locationPermissionLauncher = locationPermissionLauncher,
        currentLocation = currentGpsLocation,
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

    // ✅ CENTRALIZED LIFECYCLE EFFECTS - Replace individual DisposableEffect blocks
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

    // ✅ CENTRALIZED CAMERA EFFECTS - Replace camera animation and orientation effects
    MapCameraEffects.AllCameraEffects(
        cameraManager = cameraManager,
        bearing = orientationData.bearing,
        orientationMode = orientationData.mode,
        bearingSource = orientationData.bearingSource,
        replayPlaying = replaySession.status == com.example.xcpro.replay.IgcReplayController.SessionStatus.PLAYING
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




