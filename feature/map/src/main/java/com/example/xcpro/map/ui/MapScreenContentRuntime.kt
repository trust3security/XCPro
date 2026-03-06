package com.example.xcpro.map.ui
/**
 * Map screen body content used inside MapScreenScaffold.
 * Invariants: UI renders state only and routes mutations through the ViewModel.
 */
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.forecast.ForecastOverlayViewModel
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnTrailSelectionViewModel
import com.example.xcpro.ogn.buildOgnSelectionLookup
import com.example.xcpro.ogn.selectionLookupContainsOgnKey
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.replay.SessionState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.seedQnhInputValue
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.weather.rain.WeatherOverlayViewModel
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.maps.MapLibreMap

@Composable
internal fun MapScreenContent(
    density: Density,
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (MapLibreMap) -> Unit,
    onMapViewBound: () -> Unit,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    taskType: TaskType,
    createTaskGestureHandler: (TaskGestureCallbacks) -> TaskGestureHandler,
    windArrowState: WindArrowUiState,
    showWindSpeedOnVario: Boolean,
    cameraManager: MapCameraManager,
    currentMode: FlightMode,
    currentZoom: Float,
    onModeChange: (FlightMode) -> Unit,
    currentMapStyleName: String,
    onTransientMapStyleSelected: (String) -> Unit,
    currentLocation: MapLocationUiModel?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    ognSnapshot: OgnTrafficSnapshot,
    ognOverlayEnabled: Boolean,
    ognTargetEnabled: Boolean,
    ognTargetAircraftKey: String?,
    ognThermalHotspots: List<OgnThermalHotspot>,
    showOgnSciaEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    adsbSnapshot: AdsbTrafficSnapshot,
    adsbOverlayEnabled: Boolean,
    selectedOgnTarget: OgnTrafficTarget?,
    selectedOgnThermal: OgnThermalHotspot?,
    selectedAdsbTarget: AdsbSelectedTargetDetails?,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    isAATEditMode: Boolean,
    onEnterAATEditMode: (Int) -> Unit,
    onUpdateAATTargetPoint: (Int, Double, Double) -> Unit,
    onExitAATEditMode: () -> Unit,
    safeContainerSize: MutableState<IntSize>,
    overlayManager: MapOverlayManager,
    modalManager: MapModalManager,
    widgetManager: MapUIWidgetManager,
    screenWidthPx: Float,
    screenHeightPx: Float,
    variometerUiState: VariometerUiState,
    minVariometerSizePx: Float,
    maxVariometerSizePx: Float,
    onVariometerOffsetChange: (Offset) -> Unit,
    onVariometerSizeChange: (Float) -> Unit,
    onVariometerLongPress: () -> Unit,
    onVariometerEditFinished: () -> Unit,
    hamburgerOffset: MutableState<Offset>,
    flightModeOffset: MutableState<Offset>,
    settingsOffset: MutableState<Offset>,
    ballastOffset: MutableState<Offset>,
    hamburgerSizePx: MutableState<Float>,
    settingsSizePx: MutableState<Float>,
    onHamburgerOffsetChange: (Offset) -> Unit,
    onFlightModeOffsetChange: (Offset) -> Unit,
    onSettingsOffsetChange: (Offset) -> Unit,
    onBallastOffsetChange: (Offset) -> Unit,
    onHamburgerSizeChange: (Float) -> Unit,
    onSettingsSizeChange: (Float) -> Unit,
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    qnhCalibrationState: QnhCalibrationState,
    onAutoCalibrateQnh: () -> Unit,
    onSetManualQnh: (Double) -> Unit,
    onToggleOgnTraffic: () -> Unit,
    onToggleOgnScia: () -> Unit,
    onToggleOgnThermals: () -> Unit,
    onSetOgnTarget: (String, Boolean) -> Unit,
    onToggleAdsbTraffic: () -> Unit,
    onOgnTargetSelected: (String) -> Unit,
    onOgnThermalSelected: (String) -> Unit,
    onAdsbTargetSelected: (Icao24) -> Unit,
    onDismissOgnTargetDetails: () -> Unit,
    onDismissOgnThermalDetails: () -> Unit,
    onDismissAdsbTargetDetails: () -> Unit,
    ballastUiState: StateFlow<BallastUiState>,
    isBallastPillHidden: Boolean,
    onBallastCommand: (BallastCommand) -> Unit,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    onSettingsTap: () -> Unit,
    cardStyle: CardStyle,
    hiddenCardIds: Set<String>,
    replayState: StateFlow<SessionState>,
    showVarioDemoFab: Boolean,
    onVarioDemoReferenceClick: () -> Unit,
    onVarioDemoSimClick: () -> Unit,
    onVarioDemoSim2Click: () -> Unit,
    onVarioDemoSim3Click: () -> Unit,
    showRacingReplayFab: Boolean,
    onRacingReplayClick: () -> Unit
) {
    val liveFlightData by flightDataManager.liveFlightDataFlow.collectAsStateWithLifecycle()
    val forecastViewModel: ForecastOverlayViewModel = hiltViewModel()
    val forecastOverlayState by forecastViewModel.overlayState.collectAsStateWithLifecycle()
    val forecastPointCallout by forecastViewModel.pointCallout.collectAsStateWithLifecycle()
    val forecastQueryStatus by forecastViewModel.queryStatus.collectAsStateWithLifecycle()
    val weatherOverlayViewModel: WeatherOverlayViewModel = hiltViewModel()
    val weatherOverlayState by weatherOverlayViewModel.overlayState.collectAsStateWithLifecycle()
    val ognTrailSelectionViewModel: OgnTrailSelectionViewModel = hiltViewModel()
    val forecastRuntimeWarning by overlayManager.forecastRuntimeWarningMessage.collectAsStateWithLifecycle()
    val skySightSatelliteRuntimeError by overlayManager.skySightSatelliteRuntimeErrorMessage.collectAsStateWithLifecycle()
    val selectedOgnTrailAircraftKeys by ognTrailSelectionViewModel.selectedTrailAircraftKeys
        .collectAsStateWithLifecycle()
    val trailSelectionLookup = remember(selectedOgnTrailAircraftKeys) { buildOgnSelectionLookup(selectedOgnTrailAircraftKeys) }
    val ognTrailAircraftRows = remember(ognSnapshot.targets, trailSelectionLookup) { buildOgnTrailAircraftRows(ognSnapshot.targets, trailSelectionLookup) }
    val currentQnhLabel = remember(liveFlightData?.qnh) {
        val qnh = liveFlightData?.qnh ?: 1013.25
        String.format(Locale.US, "%.1f hPa", qnh)
    }

    var showQnhDialog by remember { mutableStateOf(false) }
    var qnhInput by remember { mutableStateOf("") }
    var qnhError by remember { mutableStateOf<String?>(null) }
    var selectedBottomTabName by rememberSaveable { mutableStateOf(MapBottomTab.SKYSIGHT.name) }
    var isBottomTabsSheetVisible by rememberSaveable { mutableStateOf(false) }
    var lastNonSatelliteMapStyleName by rememberSaveable { mutableStateOf<String?>(null) }
    var tappedWindArrowCallout by remember { mutableStateOf<WindArrowTapCallout?>(null) }
    var windTapLabelSize by remember { mutableStateOf(IntSize.Zero) }
    var overlayViewportSize by remember { mutableStateOf(IntSize.Zero) }
    val selectedBottomTab = remember(selectedBottomTabName) { runCatching { MapBottomTab.valueOf(selectedBottomTabName) }.getOrDefault(MapBottomTab.SKYSIGHT) }
    val taskPanelState by taskScreenManager.taskPanelState.collectAsStateWithLifecycle()
    val isTaskPanelVisible = taskPanelState != MapTaskScreenManager.TaskPanelState.HIDDEN
    val skySightRegionCoverageWarning = computeSkySightRegionCoverageWarning(mapState.mapLibreMap, currentLocation, forecastOverlayState.selectedRegionCode)
    val skySightRainArbitrationWarning = computeSkySightRainSuppressionWarning(forecastOverlayState, weatherOverlayState.enabled)
    val skySightUiMessages = resolveSkySightUiMessages(
        repositoryWarningMessage = forecastOverlayState.warningMessage, regionCoverageWarningMessage = skySightRegionCoverageWarning,
        runtimeWarningMessage = forecastRuntimeWarning, runtimeArbitrationWarningMessage = skySightRainArbitrationWarning,
        repositoryErrorMessage = forecastOverlayState.errorMessage, runtimeErrorMessage = skySightSatelliteRuntimeError
    )
    val skySightWarningMessage = skySightUiMessages.warningMessage
    val skySightErrorMessage = skySightUiMessages.errorMessage
    // Temporarily suppress replay/debug FABs on MapScreen (REF/SIM/SIM2/SIM3/TASK).
    val hideReplayDebugFabs = true
    val isForecastWindArrowOverlayActive = forecastOverlayState.windOverlayEnabled &&
        forecastOverlayState.windDisplayMode == ForecastWindDisplayMode.ARROW &&
        forecastOverlayState.windTileSpec?.format == ForecastTileFormat.VECTOR_WIND_POINTS
    val skySightSatViewEnabled = currentMapStyleName.equals(SATELLITE_MAP_STYLE_NAME, ignoreCase = true)
    val openQnhDialog: () -> Unit = {
        val currentQnh = liveFlightData?.qnh ?: 1013.25
        qnhInput = seedQnhInputValue(currentQnh, unitsPreferences)
        qnhError = null
        showQnhDialog = true
    }
    LaunchedEffect(currentMapStyleName) {
        if (!currentMapStyleName.equals(SATELLITE_MAP_STYLE_NAME, ignoreCase = true)) {
            lastNonSatelliteMapStyleName = currentMapStyleName
        }
    }
    ForecastOverlayRuntimeEffects(
        mapLibreMap = mapState.mapLibreMap,
        forecastOverlayState = forecastOverlayState,
        rainViewerEnabled = weatherOverlayState.enabled,
        overlayManager = overlayManager
    )
    WindArrowTapRuntimeEffects(
        isForecastWindArrowOverlayActive = isForecastWindArrowOverlayActive,
        tappedWindArrowCallout = tappedWindArrowCallout,
        onClearTapCallout = { tappedWindArrowCallout = null },
        onResetWindTapLabelSize = { windTapLabelSize = IntSize.Zero }
    )
    val hasTrafficDetailsOpen = selectedOgnTarget != null ||
        selectedOgnThermal != null ||
        selectedAdsbTarget != null
    val selectedOgnTargetSciaEnabled = remember(selectedOgnTarget, trailSelectionLookup) {
        selectedOgnTarget?.let { target ->
            selectionLookupContainsOgnKey(
                lookup = trailSelectionLookup,
                candidateKey = target.canonicalKey
            )
        } ?: false
    }
    val targetSelectionLookup = remember(ognTargetAircraftKey) {
        val selectedKeys = ognTargetAircraftKey?.let(::setOf) ?: emptySet()
        buildOgnSelectionLookup(selectedKeys)
    }
    val selectedOgnTargetTargetEnabled = remember(
        selectedOgnTarget,
        ognTargetEnabled,
        targetSelectionLookup
    ) {
        if (!ognTargetEnabled) return@remember false
        val target = selectedOgnTarget ?: return@remember false
        selectionLookupContainsOgnKey(
            lookup = targetSelectionLookup,
            candidateKey = target.canonicalKey
        )
    }
    val selectedOgnTargetIsGliderClass = remember(selectedOgnTarget) {
        isTargetableOgnAircraftTypeCode(selectedOgnTarget?.identity?.aircraftTypeCode)
    }
    LaunchedEffect(isTaskPanelVisible, hasTrafficDetailsOpen) {
        if (isTaskPanelVisible || hasTrafficDetailsOpen) {
            isBottomTabsSheetVisible = false
        }
    }
    val trafficPanelVisibility = rememberTrafficDebugPanelVisibility(
        adsbOverlayEnabled = adsbOverlayEnabled,
        adsbSnapshot = adsbSnapshot,
        ognOverlayEnabled = ognOverlayEnabled,
        ognSnapshot = ognSnapshot
    )
    val showOgnDebugPanel = trafficPanelVisibility.showOgnDebugPanel
    val showAdsbDebugPanel = trafficPanelVisibility.showAdsbDebugPanel
    val showAdsbIssueFlash = trafficPanelVisibility.showAdsbIssueFlash
    val showAdsbPersistentStatus = trafficPanelVisibility.showAdsbPersistentStatus

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                overlayViewportSize = size
            }
    ) {
        Scaffold(modifier = Modifier) { padding ->
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
                    MapOverlayStack(
                        mapState = mapState,
                        mapInitializer = mapInitializer,
                        onMapReady = onMapReady,
                        onMapViewBound = onMapViewBound,
                        locationManager = locationManager,
                        flightDataManager = flightDataManager,
                        flightViewModel = flightViewModel,
                        taskType = taskType,
                        createTaskGestureHandler = createTaskGestureHandler,
                        windArrowState = windArrowState,
                        showWindSpeedOnVario = showWindSpeedOnVario,
                        cameraManager = cameraManager,
                        currentMode = currentMode,
                        currentZoom = currentZoom,
                        unitsPreferences = unitsPreferences,
                        onModeChange = onModeChange,
                        currentLocation = currentLocation,
                        showReturnButton = showReturnButton,
                        showDistanceCircles = showDistanceCircles,
                        ognOverlayEnabled = ognOverlayEnabled,
                        showOgnThermalsEnabled = showOgnThermalsEnabled,
                        overlayManager = overlayManager,
                        onOgnTargetSelected = onOgnTargetSelected,
                        onOgnThermalSelected = onOgnThermalSelected,
                        onAdsbTargetSelected = onAdsbTargetSelected,
                        onForecastWindArrowSpeedTap = { tapLatLng, speedKt ->
                            if (isForecastWindArrowOverlayActive) {
                                tappedWindArrowCallout = WindArrowTapCallout(
                                    tapLatLng = tapLatLng,
                                    speedKt = speedKt
                                )
                            }
                        },
                        onMapLongPress = { point ->
                            if (!isAATEditMode && forecastOverlayState.enabled) {
                                forecastViewModel.queryPointValue(
                                    latitude = point.latitude,
                                    longitude = point.longitude
                                )
                            }
                        },
                        isAATEditMode = isAATEditMode,
                        isUiEditMode = isUiEditMode,
                        onEditModeChange = onEditModeChange,
                        onEnterAATEditMode = onEnterAATEditMode,
                        onUpdateAATTargetPoint = onUpdateAATTargetPoint,
                        onExitAATEditMode = onExitAATEditMode,
                        safeContainerSize = safeContainerSize,
                        variometerUiState = variometerUiState,
                        minVariometerSizePx = minVariometerSizePx,
                        maxVariometerSizePx = maxVariometerSizePx,
                        onVariometerOffsetChange = onVariometerOffsetChange,
                        onVariometerSizeChange = onVariometerSizeChange,
                        onVariometerLongPress = onVariometerLongPress,
                        onVariometerEditFinished = onVariometerEditFinished,
                        hamburgerOffset = hamburgerOffset,
                        flightModeOffset = flightModeOffset,
                        settingsOffset = settingsOffset,
                        ballastOffset = ballastOffset,
                        hamburgerSizePx = hamburgerSizePx,
                        settingsSizePx = settingsSizePx,
                        onHamburgerOffsetChange = onHamburgerOffsetChange,
                        onFlightModeOffsetChange = onFlightModeOffsetChange,
                        onSettingsOffsetChange = onSettingsOffsetChange,
                        onBallastOffsetChange = onBallastOffsetChange,
                        onHamburgerSizeChange = onHamburgerSizeChange,
                        onSettingsSizeChange = onSettingsSizeChange,
                        widgetManager = widgetManager,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        modalManager = modalManager,
                        ballastUiState = ballastUiState,
                        hideBallastPill = isBallastPillHidden,
                        onBallastCommand = onBallastCommand,
                        onHamburgerTap = onHamburgerTap,
                        onHamburgerLongPress = onHamburgerLongPress,
                        onSettingsTap = onSettingsTap,
                        cardStyle = cardStyle,
                        hiddenCardIds = hiddenCardIds,
                        replayState = replayState
                    )
        }
            }
        }
        MapTaskManagerLayer(
            taskScreenManager = taskScreenManager,
            waypointData = waypointData,
            unitsPreferences = unitsPreferences,
            currentLocation = currentLocation,
            currentQnh = currentQnhLabel
        )

        MapActionButtonsLayer(
            currentLocation = currentLocation,
            showRecenterButton = showRecenterButton,
            showReturnButton = showReturnButton,
            showVarioDemoFab = showVarioDemoFab && !hideReplayDebugFabs,
            showAatEditFab = isAATEditMode && taskType == TaskType.AAT,
            showRacingReplayFab = showRacingReplayFab && !hideReplayDebugFabs,
            onRecenter = locationManager::recenterOnCurrentLocation,
            onReturn = { locationManager.returnToSavedLocation() },
            onVarioDemoReferenceClick = onVarioDemoReferenceClick,
            onVarioDemoSimClick = onVarioDemoSimClick,
            onVarioDemoSim2Click = onVarioDemoSim2Click,
            onVarioDemoSim3Click = onVarioDemoSim3Click,
            onRacingReplayClick = onRacingReplayClick
        )

        MapBottomTabsSection(
            selectedBottomTab = selectedBottomTab,
            isBottomTabsSheetVisible = isBottomTabsSheetVisible,
            isTaskPanelVisible = isTaskPanelVisible,
            hasTrafficDetailsOpen = hasTrafficDetailsOpen,
            setSelectedBottomTabName = { selectedBottomTabName = it },
            setBottomTabsSheetVisible = { isBottomTabsSheetVisible = it },
            onDismissOgnTargetDetails = onDismissOgnTargetDetails,
            onDismissOgnThermalDetails = onDismissOgnThermalDetails,
            onDismissAdsbTargetDetails = onDismissAdsbTargetDetails,
            weatherEnabled = weatherOverlayState.enabled,
            ognOverlayEnabled = ognOverlayEnabled,
            showOgnSciaEnabled = showOgnSciaEnabled,
            onToggleOgnScia = onToggleOgnScia,
            adsbOverlayEnabled = adsbOverlayEnabled,
            showOgnThermalsEnabled = showOgnThermalsEnabled,
            showDistanceCircles = showDistanceCircles,
            currentQnhLabel = currentQnhLabel,
            onToggleAdsbTraffic = onToggleAdsbTraffic,
            onToggleOgnThermals = onToggleOgnThermals,
            overlayManager = overlayManager,
            openQnhDialog = openQnhDialog,
            ognTrailAircraftRows = ognTrailAircraftRows,
            ognTrailSelectionViewModel = ognTrailSelectionViewModel,
            forecastOverlayState = forecastOverlayState,
            forecastViewModel = forecastViewModel,
            skySightWarningMessage = skySightWarningMessage,
            skySightErrorMessage = skySightErrorMessage,
            skySightSatViewEnabled = skySightSatViewEnabled,
            currentMapStyleName = currentMapStyleName,
            lastNonSatelliteMapStyleName = lastNonSatelliteMapStyleName,
            setLastNonSatelliteMapStyleName = { lastNonSatelliteMapStyleName = it },
            onTransientMapStyleSelected = onTransientMapStyleSelected
        )

        MapOverlayPanelsAndSheetsSection(
            adsbSnapshot = adsbSnapshot,
            ognSnapshot = ognSnapshot,
            showAdsbPersistentStatus = showAdsbPersistentStatus,
            showAdsbIssueFlash = showAdsbIssueFlash,
            showAdsbDebugPanel = showAdsbDebugPanel,
            showOgnDebugPanel = showOgnDebugPanel,
            mapState = mapState,
            density = density,
            tappedWindArrowCallout = tappedWindArrowCallout,
            forecastOverlayState = forecastOverlayState,
            windTapLabelSize = windTapLabelSize,
            setWindTapLabelSize = { windTapLabelSize = it },
            overlayViewportSize = overlayViewportSize,
            forecastPointCallout = forecastPointCallout,
            forecastQueryStatus = forecastQueryStatus,
            forecastViewModel = forecastViewModel,
            showQnhDialog = showQnhDialog,
            qnhInput = qnhInput,
            qnhError = qnhError,
            unitsPreferences = unitsPreferences,
            liveFlightData = liveFlightData,
            qnhCalibrationState = qnhCalibrationState,
            setQnhInput = { qnhInput = it },
            setQnhError = { qnhError = it },
            setShowQnhDialog = { showQnhDialog = it },
            onSetManualQnh = onSetManualQnh,
            onAutoCalibrateQnh = onAutoCalibrateQnh,
            selectedOgnTarget = selectedOgnTarget,
            selectedOgnTargetSciaEnabled = selectedOgnTargetSciaEnabled,
            selectedOgnTargetTargetEnabled = selectedOgnTargetTargetEnabled,
            selectedOgnTargetTargetToggleEnabled = selectedOgnTargetIsGliderClass,
            ognTrailSelectionViewModel = ognTrailSelectionViewModel,
            showOgnSciaEnabled = showOgnSciaEnabled,
            ognOverlayEnabled = ognOverlayEnabled,
            onToggleOgnScia = onToggleOgnScia,
            onToggleOgnTraffic = onToggleOgnTraffic,
            onSetOgnTarget = onSetOgnTarget,
            onDismissOgnTargetDetails = onDismissOgnTargetDetails,
            selectedOgnThermal = selectedOgnThermal,
            currentLocation = currentLocation,
            onDismissOgnThermalDetails = onDismissOgnThermalDetails,
            selectedAdsbTarget = selectedAdsbTarget,
            onDismissAdsbTargetDetails = onDismissAdsbTargetDetails
        )
    }
    ReplayDiagnosticsLogger(
        replayState = replayState,
        flightDataManager = flightDataManager,
        unitsPreferences = unitsPreferences
    )
}
