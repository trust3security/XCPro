package com.example.xcpro

import com.example.xcpro.tasks.TaskMapOverlay
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.skysight.SkysightMapOverlay
import com.example.xcpro.skysight.SkysightClient
import com.example.xcpro.skysight.addSkysightLayerToMap
import com.example.xcpro.skysight.removeSkysightLayerFromMap
import com.example.xcpro.components.AirspaceSettingsContent
import com.example.xcpro.screens.overlays.getMapStyleUrl
import com.example.xcpro.profiles.ProfileIndicator
import com.example.xcpro.profiles.FlightModeIndicator
import com.example.xcpro.CompassWidget
import com.example.xcpro.MapOrientationMode
import com.example.xcpro.map.BlueLocationOverlay
import com.example.xcpro.map.DistanceCirclesOverlay
import com.example.xcpro.map.DistanceCirclesCanvas
import com.example.xcpro.map.TemplateChangeNotifier
import com.example.xcpro.map.components.MapActionButtons
import com.example.xcpro.map.MapComposeEffects
import com.example.xcpro.map.MapUIWidgetManager
import com.example.xcpro.map.MapUIWidgets
import com.example.xcpro.map.MapTaskScreenUI
import com.example.xcpro.map.MapCameraEffects
import com.example.xcpro.map.MapLifecycleEffects
import com.example.xcpro.map.MapModalUI
import com.example.xcpro.map.MapGestureSetup
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.WaypointData
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
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.dfcards.FlightDataProvider
import com.example.dfcards.FlightModeSelection
import androidx.lifecycle.viewmodel.compose.viewModel
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
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
import com.example.xcpro.tasks.BottomSheetState
// ✅ REMOVED DataQuality - no longer used
import com.example.ui1.UIVariometer
import com.example.xcpro.navdrawer.NavigationDrawer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "MapScreen"
private const val PREFS_NAME = "MapScreenPrefs"
const val INITIAL_LATITUDE = -30.87

/**
 * ✅ PHASE 2: Convert CompleteFlightData (from FlightDataCalculator) to RealTimeFlightData (for cards)
 *
 * This adapter function maintains backward compatibility with existing card system
 * while migrating to the new unified sensor architecture.
 */
private fun convertToRealTimeFlightData(completeData: com.example.xcpro.sensors.CompleteFlightData): RealTimeFlightData {
    val gps = completeData.gps
    val baro = completeData.baro

    // Calculate flight time (simple implementation - starts from app launch)
    val flightTimeMs = System.currentTimeMillis() - completeData.timestamp
    val flightTimeMinutes = flightTimeMs / 60000
    val hours = flightTimeMinutes / 60
    val minutes = flightTimeMinutes % 60
    val formattedFlightTime = "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"

    return RealTimeFlightData(
        // GPS data
        latitude = gps?.latLng?.latitude ?: 0.0,
        longitude = gps?.latLng?.longitude ?: 0.0,
        gpsAltitude = gps?.altitude ?: 0.0,
        groundSpeed = gps?.speed ?: 0.0,
        track = gps?.bearing ?: 0.0,
        accuracy = gps?.accuracy?.toDouble() ?: 0.0,
        satelliteCount = 0, // Not available from new system (phones don't expose this)

        // Barometric data
        baroAltitude = completeData.baroAltitude,
        currentPressureHPa = baro?.pressureHPa ?: 1013.25,
        qnh = completeData.qnh,
        isQNHCalibrated = completeData.isQNHCalibrated,

        // Calculated values
        verticalSpeed = completeData.verticalSpeed,
        agl = completeData.agl,
        pressureAltitude = completeData.pressureAltitude,
        baroGpsDelta = completeData.baroGpsDelta,
        baroConfidence = completeData.baroConfidence,
        qnhCalibrationAgeSeconds = completeData.qnhCalibrationAgeSeconds,

        // Performance data
        windSpeed = completeData.windSpeed,
        windDirection = completeData.windDirection,
        thermalAverage = completeData.thermalAverage,
        currentLD = completeData.currentLD,
        netto = completeData.netto,

        // NEW: Vario variants for side-by-side testing (VARIO_IMPROVEMENTS.md)
        varioOptimized = completeData.varioOptimized,
        varioLegacy = completeData.varioLegacy,
        varioRaw = completeData.varioRaw,
        varioGPS = completeData.varioGPS,
        varioComplementary = completeData.varioComplementary,

        // Metadata
        flightTime = formattedFlightTime,
        timestamp = completeData.timestamp,
        lastUpdateTime = System.currentTimeMillis(),
        calculationSource = completeData.dataQuality
    )
}

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
    showTaskScreen: MutableState<Boolean>
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val mapViewModel: MapScreenViewModel = viewModel(
        factory = MapScreenViewModel.provideFactory(application, initialMapStyle)
    )
    val unitsRepository = remember(context.applicationContext) {
        UnitsRepository(context.applicationContext)
    }
    val unitsPreferences by unitsRepository.unitsFlow.collectAsState(initial = UnitsPreferences())
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ?o. Centralized state management via ViewModel
    val mapState = mapViewModel.mapState
    val modes = FlightMode.values()

    // ?o. Map Orientation Manager
    val orientationManager = mapViewModel.orientationManager
    val orientationData by orientationManager.orientationFlow.collectAsState()
    val taskManager = mapViewModel.taskManager  // ?o. Using coordinator for task management
    // 🔄 Load waypoints once
    val waypointData by produceState(initialValue = emptyList<WaypointData>(), key1 = context) {
        value = withContext(Dispatchers.IO) {
            val (waypointFiles, _) = loadWaypointFiles(context)
            waypointFiles.flatMap { file ->
                try {
                    WaypointParser.parseWaypointFile(context, file)
                } catch (e: Exception) {
                    Log.e("MapScreen", "Error parsing waypoint file: ${e.message}")
                    emptyList()
                }
            }
        }
    }

// ✅ keep repo if you still need it
    val waypointRepo = remember(waypointData) { FileWaypointRepo(waypointData) }

    // ✅ SIMPLIFIED: Remove permission dialog variables, always enable everything
    var safeContainerSize by remember { mutableStateOf(IntSize.Zero) }

    // ✅ DEBUG: Track container size changes
    LaunchedEffect(safeContainerSize) {
        Log.d("MapScreen", "🔍 CONTAINER SIZE CHANGED: $safeContainerSize")
    }

    // ✅ Flight Cards ViewModel
    val flightViewModel: FlightDataViewModel = viewModel()
    // ✅ REFACTORED: No longer collect cardStates here - CardContainer handles it directly
    val selectedCardIds by flightViewModel.selectedCardIds.collectAsState()
    val cardPreferences = mapViewModel.cardPreferences

    // ✅ Initialize FlightDataManager
    val flightDataManager = mapViewModel.flightDataManager
    LaunchedEffect(unitsPreferences) {
        flightDataManager.updateUnitsPreferences(unitsPreferences)
    }
    var showQnhDialog by remember { mutableStateOf(false) }
    var qnhInput by remember { mutableStateOf("") }
    var qnhError by remember { mutableStateOf<String?>(null) }
    var showQnhFab by remember { mutableStateOf(true) }
    // ✅ NEW: Register template change callback for communication with FlightDataMgmt
    DisposableEffect(Unit) {
        TemplateChangeNotifier.registerCallback {
            flightDataManager.incrementTemplateVersion()
        }
        onDispose {
            TemplateChangeNotifier.unregisterCallback()
        }
    }

    // Map Overlay Manager - centralized overlay management
    val overlayManager = mapViewModel.overlayManager

    // ✅ UI Widget Manager - centralized widget management
    val widgetManager = remember {
        MapUIWidgetManager(mapState, mapState.sharedPrefs)
    }

    // ✅ Profile ViewModel
    val profileViewModel: com.example.xcpro.profiles.ProfileViewModel = viewModel()
    val uiState by profileViewModel.uiState.collectAsState()
    // ✅ TaskScreenManager - Centralized task screen handling
    val taskScreenManager = mapViewModel.taskScreenManager

    // ✅ CameraManager - Centralized camera handling
    val cameraManager = mapViewModel.cameraManager

    // ✅ LocationManager - Centralized location handling
    val locationManager = mapViewModel.locationManager

    // ✅ LifecycleManager - Centralized lifecycle handling
    val lifecycleManager = mapViewModel.lifecycleManager

    // ✅ ModalManager - Centralized modal handling
    val modalManager = mapViewModel.modalManager

    // ✅ Backward compatibility variables (using locationManager)
    val currentUserLocation by remember { derivedStateOf { locationManager.currentUserLocation } }
    val unifiedSensorManager = locationManager.unifiedSensorManager

    // Map Initializer
    val mapInitializer = mapViewModel.mapInitializer
    val currentLocation by unifiedSensorManager.gpsFlow.collectAsState()
    val isGpsActive = unifiedSensorManager.isGpsEnabled()

    // ✅ Location state through LocationManager
    val showRecenterButton by remember { derivedStateOf { locationManager.showRecenterButton } }
    val isTrackingLocation by remember { derivedStateOf { locationManager.isTrackingLocation } }
    val lastUserPanTime by remember { derivedStateOf { locationManager.lastUserPanTime } }
    val showReturnButton by remember { derivedStateOf { locationManager.showReturnButton } }

    // ✅ AAT Edit Mode State - Track when AAT pin editing is active
    val isAATEditMode by mapViewModel.isAATEditMode.collectAsState()
    
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
    val savedLocation by remember { derivedStateOf { locationManager.savedLocation } }
    val savedZoom by remember { derivedStateOf { locationManager.savedZoom } }
    val savedBearing by remember { derivedStateOf { locationManager.savedBearing } }
    val hasInitiallyCentered by remember { derivedStateOf { locationManager.hasInitiallyCentered } }

    // ✅ Location Permission Launcher through LocationManager
    val locationPermissionLauncher = locationManager.LocationPermissionHandler()

    // ✅ Map FlightMode to FlightModeSelection using FlightDataManager
    val currentFlightModeSelection by remember { derivedStateOf { flightDataManager.currentFlightMode } }

    // ✅ Variometer test state for debug effects
    // ✅ CENTRALIZED EFFECTS - Replace all individual LaunchedEffect blocks
    // ✅ REFACTORED: Removed cardStates parameter - no longer needed
    MapComposeEffects.AllMapEffects(
        locationManager = locationManager,
        locationPermissionLauncher = locationPermissionLauncher,
        currentLocation = currentLocation,
        orientationData = orientationData,
        uiState = uiState,
        flightDataManager = flightDataManager,
        mapState = mapState,
        currentFlightModeSelection = currentFlightModeSelection,
        safeContainerSize = safeContainerSize,
        flightViewModel = flightViewModel,
        cardPreferences = cardPreferences,
        initialMapStyle = initialMapStyle,
        onMapStyleSelected = onMapStyleSelected
    )

    // ✅ CENTRALIZED LIFECYCLE EFFECTS - Replace individual DisposableEffect blocks
    MapLifecycleEffects.AllLifecycleEffects(
        lifecycleManager = lifecycleManager,
        styleUrl = mapState.mapStyleUrl,
        onStyleLoaded = {
            overlayManager.onMapStyleChanged(mapState.mapLibreMap)
        }
    )

    // Load widget positions using existing widgetManager and density
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val widgetPositions = remember(screenWidthPx, screenHeightPx) {
        widgetManager.loadWidgetPositions(screenWidthPx, screenHeightPx, density)
    }

    var variometerOffset by remember { mutableStateOf(widgetPositions.variometerOffset) }
    var variometerSizePx by remember { mutableStateOf(widgetPositions.variometerSizePx) }
    var hamburgerOffset by remember { mutableStateOf(widgetPositions.hamburgerOffset) }

    // ✅ CENTRALIZED CAMERA EFFECTS - Replace camera animation and orientation effects
    MapCameraEffects.AllCameraEffects(
        cameraManager = cameraManager,
        bearing = orientationData.bearing,
        orientationMode = orientationData.mode,
        isOrientationValid = orientationData.isValid
    )


    NavigationDrawer(
        drawerState = drawerState,
        navController = navController,
        profileExpanded = profileExpanded,
        mapStyleExpanded = mapStyleExpanded,
        settingsExpanded = settingsExpanded,
        initialMapStyle = initialMapStyle,
        onItemSelected = { item ->
            Log.d(TAG, "Navigation drawer item selected: $item")
            coroutineScope.launch {
                drawerState.close()
                taskScreenManager.handleNavigationTaskSelection(item)
            }
        },
        onMapStyleSelected = { style ->
            mapState.mapStyleUrl = getMapStyleUrl(style)
            saveConfig(context, style, emptyMap(), profileExpanded.value, mapStyleExpanded.value)
            Log.d(TAG, "Map style selected: $style, URL: ${mapState.mapStyleUrl}")
            onMapStyleSelected(style)
        },
        content = {

            // ---------- WRAP CONTENT IN A ROOT BOX SO WE CAN OVERLAY A MANUAL FAB ----------
            Box(Modifier.fillMaxSize()) {

                // --- Your existing Scaffold (no floatingActionButton here) ---
                Scaffold(
                    modifier = Modifier
                        .border(2.dp, Color.Yellow)
                ) { padding ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        val maxWidthPx = with(density) { maxWidth.toPx() }
                        val maxHeightPx = with(density) { maxHeight.toPx() }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .size(maxWidth, maxHeight)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    MapView(ctx).apply {
                                        mapState.mapView = this
                                        getMapAsync { map: MapLibreMap ->
                                            coroutineScope.launch {
                                                try {
                                                    mapInitializer.initializeMap(map)
                                                    Log.d(TAG, "Map initialization completed via MapInitializer")

                                                    // Overlays are now managed through LocationManager via mapState
                                                    Log.d(TAG, "Map overlays initialized through LocationManager")
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error during MapInitializer setup: ${e.message}", e)
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            // ✅ PHASE 2: Flight data provider using new FlightDataCalculator
                            FlightDataProvider(
                                dataProvider = { onDataReceived ->
                                    locationManager.flightDataCalculator.flightDataFlow.collect { completeData ->
                                        if (completeData != null) {
                                            // Convert CompleteFlightData to RealTimeFlightData
                                            val realTimeData = convertToRealTimeFlightData(completeData)
                                            onDataReceived(realTimeData)
                                        }
                                    }
                                }
                            ) { liveData ->
                                flightDataManager.updateLiveFlightData(liveData)
                                println(
                                    "DEBUG: MapScreen received live data - Baro: ${liveData.baroAltitude}m, GPS: ${liveData.gpsAltitude}m, Pressure: ${liveData.currentPressureHPa}hPa"
                                )
                            }

                            // ✅ REFACTORED: Cards - now use independent StateFlows per card
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(2f)
                            ) {
                                CardContainer(
                                    onContainerSizeChanged = { size -> safeContainerSize = size },
                                    statusBarOffset = 0f,
                                    onFlightTemplateClick = { flightDataManager.showCardLibrary() },
                                    viewModel = flightViewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // ✅ Task Map Overlay (SEPARATE Box - ABOVE cards so turnpoints show)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(3f)
                            ) {
                                TaskMapOverlay(
                                    taskManager = taskManager,
                                    mapLibreMap = mapState.mapLibreMap,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // ✅ AAT Long Press functionality now integrated into CustomMapGestureHandler
                            }

                            // TODO: AAT Interactive Turnpoint Overlay - will be re-implemented incrementally

                            // Skysight Weather Overlay (above task overlay)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(3.5f)
                            ) {
                                SkysightMapOverlay(
                                    onOpenSettings = {
                                        navController.navigate("skysight_settings")
                                    },
                                    mapLibreMap = mapState.mapLibreMap,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // ✅ Custom Gesture Handling - Extracted to MapGestureSetup module
                            MapGestureSetup.GestureHandlerOverlay(
                                mapState = mapState,
                                taskManager = taskManager,
                                flightDataManager = flightDataManager,
                                locationManager = locationManager,
                                cameraManager = cameraManager,
                                currentLocation = currentLocation,
                                showReturnButton = showReturnButton,
                                isAATEditMode = isAATEditMode,
                                onAATEditModeChange = mapViewModel::setAATEditMode
                            )

                            // ✅ Modern Flight Mode Indicator (Top Left - Unified UI)
                            FlightModeIndicator(
                                currentMode = mapState.currentMode, // Display current mode (controlled by gesture handler)
                                onModeChange = { newMode ->
                                    mapState.updateFlightMode(newMode)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopStart) // Move to top-left
                                    .padding(top = mapState.iconSize.dp + 8.dp, start = 16.dp) // Position below hamburger menu
                                    .zIndex(5f)
                            )

                            // ✅ Compass Widget (Top Right) - Always visible for mode switching
                            androidx.compose.animation.AnimatedVisibility(
                                visible = true, // Always show so user can switch orientation modes
                                enter = androidx.compose.animation.fadeIn(
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                ) + androidx.compose.animation.scaleIn(
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                ),
                                exit = androidx.compose.animation.fadeOut(
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                ) + androidx.compose.animation.scaleOut(
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                ),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 80.dp, end = 16.dp)
                                    .zIndex(5f)
                            ) {
                                CompassWidget(
                                    orientation = orientationData,
                                    onModeToggle = {
                                        // Cycle through orientation modes: North Up → Track Up → Heading Up → North Up
                                        val nextMode = when (orientationManager.getCurrentMode()) {
                                            MapOrientationMode.NORTH_UP -> MapOrientationMode.TRACK_UP
                                            MapOrientationMode.TRACK_UP -> MapOrientationMode.HEADING_UP
                                            MapOrientationMode.HEADING_UP -> MapOrientationMode.NORTH_UP
                                        }
                                        orientationManager.setOrientationMode(nextMode)
                                    }
                                )
                            }

                            // ✅ CardLibraryModal - TODO: Restore after fixing dependencies
                            // Temporarily removed to fix compilation issues

                            // ✅ Other UI (variometer) - animation managed by centralized effects
                            // Note: sharedPrefs is accessed via mapState.sharedPrefs
                            val targetVario = (flightDataManager.rawVerticalSpeed ?: flightDataManager.liveFlightData?.verticalSpeed)?.toFloat() ?: 0f
                            val displayNumericVario = (flightDataManager.smoothedVerticalSpeed ?: flightDataManager.liveFlightData?.verticalSpeed)?.toFloat() ?: 0f
                            val animatedVario by animateFloatAsState(
                                targetValue = targetVario,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                ),
                                label = "vario"
                            )

                            // Draggable Variometer using centralized widget manager
                            val minSizePx = with(density) { 60.dp.toPx() }
                            val maxSizePx = with(density) { 200.dp.toPx() }

                            MapUIWidgets.DraggableVariometer(
                                variometerNeedleValue = animatedVario,
                                variometerDisplayValue = displayNumericVario,
                                variometerOffset = variometerOffset,
                                variometerSizePx = variometerSizePx,
                                screenWidthPx = screenWidthPx,
                                screenHeightPx = screenHeightPx,
                                minSizePx = minSizePx,
                                maxSizePx = maxSizePx,
                                widgetManager = widgetManager,
                                density = density,
                                onOffsetChange = { variometerOffset = it },
                                onSizeChange = { variometerSizePx = it },
                                modifier = Modifier.zIndex(if (mapState.isUIEditMode) 4f else 1f)
                            )

                            // Aircraft icon is now drawn on the map at actual GPS position
                            // via BlueLocationOverlay to prevent the icon from moving with camera pan

                            // ✅ Fixed Distance Circles Overlay - Non-moving implementation
                            // Circles centered on fixed aircraft icon, only size changes with zoom
                            DistanceCirclesCanvas(
                                mapZoom = mapState.mapLibreMap?.cameraPosition?.zoom?.toFloat() ?: 10f,
                                mapLatitude = flightDataManager.liveFlightData?.latitude ?: 0.0,
                                isVisible = mapState.showDistanceCircles,
                                modifier = Modifier.zIndex(3.7f)  // Just below aircraft icon
                            )

                            // ✅ Task-Specific UI - Extracted to MapTaskIntegration module
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .zIndex(11f) // Fix: Higher than gesture handler to receive clicks
                            ) {
                                MapTaskIntegration.AATEditModeFAB(
                                    isAATEditMode = isAATEditMode,
                                    taskManager = taskManager,
                                    cameraManager = cameraManager,
                                    onExitEditMode = mapViewModel::exitAATEditMode
                                )
                            }

                            // Draggable Hamburger Menu using centralized widget manager
                            MapUIWidgets.DraggableHamburgerMenu(
                                hamburgerOffset = hamburgerOffset,
                                iconSize = mapState.iconSize,
                                screenWidthPx = screenWidthPx,
                                screenHeightPx = screenHeightPx,
                                widgetManager = widgetManager,
                                density = density,
                                drawerState = drawerState,
                                coroutineScope = coroutineScope,
                                onOffsetChange = { hamburgerOffset = it },
                                onSizeChange = { size -> mapState.iconSize = size },
                                modifier = Modifier.zIndex(4f)
                            )

                            // ✅ Airspace Settings Modal - Centralized through MapModalUI
                            MapModalUI.AirspaceSettingsModalOverlay(
                                modalManager = modalManager
                            )
                        }
                    }
                }

                // ✅ Task Screen UI Components - Centralized management
                MapTaskScreenUI.AllTaskScreenComponents(
                    taskScreenManager = taskScreenManager,
                    allWaypoints = waypointData,
                    currentQNH = "1013 hPa",
                    onWaypointGoto = { wp ->
                        cameraManager.moveToWaypoint(wp.latitude, wp.longitude)
                    }
                )

                // ✅ Use extracted MapActionButtons component
                MapActionButtons(
                    mapState = mapState,
                    taskManager = taskManager,
                    taskScreenManager = taskScreenManager,
                    currentLocation = currentLocation,
                    showReturnButton = showReturnButton,
                    onToggleDistanceCircles = {
                        // ✅ Use MapOverlayManager for centralized distance circles management
                        overlayManager.toggleDistanceCircles()
                    },
                    onReturn = {
                        locationManager.returnToSavedLocation()
                    },
                    onShowQnhDialog = {
                        val currentQnh = flightDataManager.liveFlightData?.qnh ?: 1013.25
                        qnhInput = seedQnhInputValue(currentQnh, unitsPreferences)
                        qnhError = null
                        showQnhDialog = true
                    },
                    showQnhFab = showQnhFab,
                    onDismissQnhFab = { showQnhFab = false },
                    modifier = Modifier.fillMaxSize()
                )
                if (showQnhDialog) {
                    val liveData = flightDataManager.liveFlightData
                    val pressureLabel = unitsPreferences.pressure.abbreviation
                    val pressureDecimals = pressurePrecision(unitsPreferences)
                    AlertDialog(
                        onDismissRequest = {
                            showQnhDialog = false
                            qnhError = null
                        },
                        title = { Text("Manual QNH") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Enter the local QNH in $pressureLabel.")
                                OutlinedTextField(
                                    value = qnhInput,
                                    onValueChange = {
                                        qnhInput = it
                                        qnhError = null
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("QNH ($pressureLabel)") },
                                    isError = qnhError != null
                                )
                                qnhError?.let {
                                    Text(
                                        text = it,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                liveData?.let { data ->
                                    val status = if (data.isQNHCalibrated) "Calibrated" else "Standard"
                                    Text("Current: ${formatQnhDisplay(data.qnh, unitsPreferences, pressureDecimals)} ($status)")
                                    data.baroGpsDelta?.let { delta ->
                                        Text("Baro vs GPS: ${formatBaroGpsDelta(delta, unitsPreferences)}")
                                    }
                                    val ageSeconds = data.qnhCalibrationAgeSeconds
                                    if (ageSeconds >= 0) {
                                        val ageLabel = when {
                                            ageSeconds >= 3600 -> "${ageSeconds / 3600}h"
                                            ageSeconds >= 60 -> "${ageSeconds / 60}m"
                                            else -> "${ageSeconds}s"
                                        }
                                        Text("Last calibration: $ageLabel ago")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val parsed = qnhInput.trim().toDoubleOrNull()
                                    if (parsed != null) {
                                        val qnhHpa = convertQnhInputToHpa(parsed, unitsPreferences)
                                        locationManager.setManualQnh(qnhHpa)
                                        showQnhDialog = false
                                        qnhError = null
                                    } else {
                                        qnhError = "Enter a numeric value"
                                    }
                                }
                            ) {
                                Text("Set QNH")
                            }
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        locationManager.resetQnhToStandard()
                                        showQnhDialog = false
                                        qnhError = null
                                    }
                                ) {
                                    Text("Auto Cal")
                                }
                                TextButton(
                                    onClick = {
                                        showQnhDialog = false
                                        qnhError = null
                                    }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    )
                }
            }
        }
    )
}






internal fun pressurePrecision(preferences: UnitsPreferences): Int =
    if (preferences.pressure == PressureUnit.INHG) 2 else 1

internal fun seedQnhInputValue(qnhHpa: Double, preferences: UnitsPreferences): String {
    val decimals = pressurePrecision(preferences)
    val displayValue = preferences.pressure.fromSi(PressureHpa(qnhHpa))
    return "%.${decimals}f".format(Locale.US, displayValue)
}

internal fun formatQnhDisplay(
    qnhHpa: Double,
    preferences: UnitsPreferences,
    decimals: Int = pressurePrecision(preferences)
): String {
    return UnitsFormatter.pressure(PressureHpa(qnhHpa), preferences, decimals).text
}

internal fun convertQnhInputToHpa(
    inputValue: Double,
    preferences: UnitsPreferences
): Double {
    return preferences.pressure.toSi(inputValue).value
}

internal fun formatBaroGpsDelta(
    deltaMeters: Double,
    preferences: UnitsPreferences
): String {
    val unit = preferences.altitude
    val converted = unit.fromSi(AltitudeM(deltaMeters))
    val rounded = converted.roundToInt()
    val sign = if (rounded >= 0) "+" else ""
    return "$sign$rounded ${unit.abbreviation}"
}

