package com.example.xcpro.map.ui
/**
 * Map screen body content used inside MapScreenScaffold.
 * Invariants: UI renders state only and routes mutations through the ViewModel.
 */


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.AdsbMarkerDetailsSheet
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastOverlayViewModel
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.map.components.MapActionButtons
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.ui.MapOverlayStack
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.ui.task.MapTaskScreenUi
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnMarkerDetailsSheet
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalDetailsSheet
import com.example.xcpro.ogn.OgnThermalHotspot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.util.Log
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.xcpro.seedQnhInputValue
import com.example.xcpro.convertQnhInputToHpa
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.weather.rain.WeatherOverlayViewModel
import kotlin.math.roundToInt
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.qnh.QnhCalibrationState
import java.util.Locale

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
    currentLocation: MapLocationUiModel?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    ognSnapshot: OgnTrafficSnapshot,
    ognOverlayEnabled: Boolean,
    ognThermalHotspots: List<OgnThermalHotspot>,
    showOgnThermalsEnabled: Boolean,
    showOgnGliderTrailsEnabled: Boolean,
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
    ballastOffset: MutableState<Offset>,
    onHamburgerOffsetChange: (Offset) -> Unit,
    onFlightModeOffsetChange: (Offset) -> Unit,
    onBallastOffsetChange: (Offset) -> Unit,
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    qnhCalibrationState: QnhCalibrationState,
    onAutoCalibrateQnh: () -> Unit,
    onSetManualQnh: (Double) -> Unit,
    onToggleOgnTraffic: () -> Unit,
    onToggleOgnThermals: () -> Unit,
    onToggleOgnGliderTrails: () -> Unit,
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
    val currentQnhLabel = remember(liveFlightData?.qnh) {
        val qnh = liveFlightData?.qnh ?: 1013.25
        String.format(Locale.US, "%.1f hPa", qnh)
    }

    var showQnhDialog by remember { mutableStateOf(false) }
    var qnhInput by remember { mutableStateOf("") }
    var qnhError by remember { mutableStateOf<String?>(null) }
    var showQnhFab by remember { mutableStateOf(true) }
    var showForecastSheet by remember { mutableStateOf(false) }
    var tappedWindArrowCallout by remember { mutableStateOf<WindArrowTapCallout?>(null) }
    var windTapLabelSize by remember { mutableStateOf(IntSize.Zero) }
    var overlayViewportSize by remember { mutableStateOf(IntSize.Zero) }
    val isForecastWindArrowOverlayActive = forecastOverlayState.windOverlayEnabled &&
        forecastOverlayState.windDisplayMode == ForecastWindDisplayMode.ARROW &&
        forecastOverlayState.windTileSpec?.format == ForecastTileFormat.VECTOR_WIND_POINTS

    LaunchedEffect(
        mapState.mapLibreMap,
        forecastOverlayState.enabled,
        forecastOverlayState.primaryTileSpec,
        forecastOverlayState.primaryLegend,
        forecastOverlayState.secondaryPrimaryOverlayEnabled,
        forecastOverlayState.secondaryPrimaryTileSpec,
        forecastOverlayState.secondaryPrimaryLegend,
        forecastOverlayState.windOverlayEnabled,
        forecastOverlayState.windTileSpec,
        forecastOverlayState.windLegend,
        forecastOverlayState.opacity,
        forecastOverlayState.windOverlayScale,
        forecastOverlayState.windDisplayMode
    ) {
        val hasPrimaryOverlay = forecastOverlayState.enabled &&
            forecastOverlayState.primaryTileSpec != null
        val hasWindOverlay = forecastOverlayState.windOverlayEnabled &&
            forecastOverlayState.windTileSpec != null
        if (!hasPrimaryOverlay && !hasWindOverlay) {
            overlayManager.clearForecastOverlay()
            return@LaunchedEffect
        }
        overlayManager.setForecastOverlay(
            enabled = forecastOverlayState.enabled,
            primaryTileSpec = forecastOverlayState.primaryTileSpec,
            primaryLegendSpec = forecastOverlayState.primaryLegend,
            secondaryPrimaryOverlayEnabled = forecastOverlayState.secondaryPrimaryOverlayEnabled,
            secondaryPrimaryTileSpec = forecastOverlayState.secondaryPrimaryTileSpec,
            secondaryPrimaryLegendSpec = forecastOverlayState.secondaryPrimaryLegend,
            windOverlayEnabled = forecastOverlayState.windOverlayEnabled,
            windTileSpec = forecastOverlayState.windTileSpec,
            windLegendSpec = forecastOverlayState.windLegend,
            opacity = forecastOverlayState.opacity,
            windOverlayScale = forecastOverlayState.windOverlayScale,
            windDisplayMode = forecastOverlayState.windDisplayMode,
        )
    }

    LaunchedEffect(isForecastWindArrowOverlayActive) {
        if (!isForecastWindArrowOverlayActive) {
            tappedWindArrowCallout = null
            windTapLabelSize = IntSize.Zero
        }
    }

    LaunchedEffect(tappedWindArrowCallout) {
        if (tappedWindArrowCallout != null) {
            delay(WIND_ARROW_SPEED_TAP_DISPLAY_MS)
            tappedWindArrowCallout = null
        }
    }

    val showOgnDebugPanel = rememberTrafficDebugPanelVisibility(
        enabled = BuildConfig.DEBUG && ognOverlayEnabled,
        readyForAutoDismiss = isOgnReadyForAutoDismiss(ognSnapshot),
        autoDismissDelayMs = TRAFFIC_DEBUG_PANEL_AUTO_DISMISS_MS
    )
    val showAdsbDebugPanel = rememberTrafficDebugPanelVisibility(
        enabled = BuildConfig.DEBUG && adsbOverlayEnabled,
        readyForAutoDismiss = isAdsbReadyForAutoDismiss(adsbSnapshot),
        autoDismissDelayMs = TRAFFIC_DEBUG_PANEL_AUTO_DISMISS_MS
    )

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                overlayViewportSize = size
            }
    ) {
        Scaffold(
            modifier = Modifier
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
                        ballastOffset = ballastOffset,
                        onHamburgerOffsetChange = onHamburgerOffsetChange,
                        onFlightModeOffsetChange = onFlightModeOffsetChange,
                        onBallastOffsetChange = onBallastOffsetChange,
                        widgetManager = widgetManager,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        modalManager = modalManager,
                        ballastUiState = ballastUiState,
                        hideBallastPill = isBallastPillHidden,
                        onBallastCommand = onBallastCommand,
                        onHamburgerTap = onHamburgerTap,
                        onHamburgerLongPress = onHamburgerLongPress,
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
            currentLocation = currentLocation,
            currentQnh = currentQnhLabel
        )

        MapActionButtonsLayer(
            taskScreenManager = taskScreenManager,
            currentLocation = currentLocation,
            showRecenterButton = showRecenterButton,
            showReturnButton = showReturnButton,
            showDistanceCircles = showDistanceCircles,
            showOgnTraffic = ognOverlayEnabled,
            showOgnThermals = showOgnThermalsEnabled,
            showOgnGliderTrails = showOgnGliderTrailsEnabled,
            showAdsbTraffic = adsbOverlayEnabled,
            showForecastOverlay = forecastOverlayState.enabled || forecastOverlayState.windOverlayEnabled,
            showQnhFab = showQnhFab,
            showVarioDemoFab = showVarioDemoFab,
            showAatEditFab = isAATEditMode && taskType == TaskType.AAT,
            showRacingReplayFab = showRacingReplayFab,
            onRecenter = locationManager::recenterOnCurrentLocation,
            onToggleDistanceCircles = { overlayManager.toggleDistanceCircles() },
            onToggleOgnTraffic = onToggleOgnTraffic,
            onToggleOgnThermals = onToggleOgnThermals,
            onToggleOgnGliderTrails = onToggleOgnGliderTrails,
            onToggleAdsbTraffic = onToggleAdsbTraffic,
            onShowForecastSheet = { showForecastSheet = true },
            onReturn = { locationManager.returnToSavedLocation() },
            onShowQnhDialog = {
                val currentQnh = liveFlightData?.qnh ?: 1013.25
                qnhInput = seedQnhInputValue(currentQnh, unitsPreferences)
                qnhError = null
                showQnhDialog = true
            },
            onDismissQnhFab = { showQnhFab = false },
            onVarioDemoReferenceClick = onVarioDemoReferenceClick,
            onVarioDemoSimClick = onVarioDemoSimClick,
            onVarioDemoSim2Click = onVarioDemoSim2Click,
            onVarioDemoSim3Click = onVarioDemoSim3Click,
            onRacingReplayClick = onRacingReplayClick
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OgnDebugPanel(
                visible = showOgnDebugPanel,
                snapshot = ognSnapshot
            )

            AdsbDebugPanel(
                visible = showAdsbDebugPanel,
                snapshot = adsbSnapshot
            )
        }

        tappedWindArrowCallout?.let { callout ->
            val map = mapState.mapLibreMap
            val screenPoint = map?.projection?.toScreenLocation(callout.tapLatLng)
            if (screenPoint != null) {
                val edgePaddingPx = with(density) { WIND_TAP_LABEL_EDGE_PADDING_DP.dp.toPx() }
                val anchorGapPx = with(density) { WIND_TAP_LABEL_ANCHOR_GAP_DP.dp.toPx() }
                val estimatedWidthPx = with(density) { WIND_TAP_LABEL_ESTIMATED_WIDTH_DP.dp.toPx() }
                val estimatedHeightPx = with(density) { WIND_TAP_LABEL_ESTIMATED_HEIGHT_DP.dp.toPx() }
                val labelWidthPx = if (windTapLabelSize.width > 0) {
                    windTapLabelSize.width.toFloat()
                } else {
                    estimatedWidthPx
                }
                val labelHeightPx = if (windTapLabelSize.height > 0) {
                    windTapLabelSize.height.toFloat()
                } else {
                    estimatedHeightPx
                }
                val maxX = (overlayViewportSize.width.toFloat() - labelWidthPx - edgePaddingPx)
                    .coerceAtLeast(edgePaddingPx)
                val maxY = (overlayViewportSize.height.toFloat() - labelHeightPx - edgePaddingPx)
                    .coerceAtLeast(edgePaddingPx)
                val targetX = (screenPoint.x - (labelWidthPx / 2f))
                    .coerceIn(edgePaddingPx, maxX)
                val targetY = (screenPoint.y - labelHeightPx - anchorGapPx)
                    .coerceIn(edgePaddingPx, maxY)

                WindArrowSpeedTapLabel(
                    speedKt = callout.speedKt,
                    unitLabel = forecastOverlayState.windLegend?.unitLabel
                        ?.takeIf { label -> label.isNotBlank() }
                        ?: DEFAULT_WIND_SPEED_UNIT_LABEL,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = targetX.roundToInt(),
                                y = targetY.roundToInt()
                            )
                        }
                        .onSizeChanged { size ->
                            windTapLabelSize = size
                        }
                )
            }
        }

        forecastPointCallout?.let { callout ->
            ForecastPointCalloutCard(
                callout = callout,
                regionCode = forecastOverlayState.selectedRegionCode,
                onDismiss = forecastViewModel::clearPointCallout,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp)
            )
        }

        forecastQueryStatus?.let { status ->
            ForecastQueryStatusChip(
                message = status,
                onDismiss = forecastViewModel::clearQueryStatus,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp, start = 16.dp, end = 16.dp)
            )
        }

        WeatherMapConfidenceChip(
            runtimeState = weatherOverlayState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 16.dp)
        )

        QnhDialogHost(
            visible = showQnhDialog,
            qnhInput = qnhInput,
            qnhError = qnhError,
            unitsPreferences = unitsPreferences,
            liveData = liveFlightData,
            calibrationState = qnhCalibrationState,
            onQnhInputChange = {
                qnhInput = it
                qnhError = null
            },
            onConfirm = { parsed ->
                val qnhHpa = convertQnhInputToHpa(parsed, unitsPreferences)
                onSetManualQnh(qnhHpa)
                showQnhDialog = false
                qnhError = null
            },
            onInvalidInput = { error ->
                qnhError = error
            },
            onAutoCalibrate = {
                onAutoCalibrateQnh()
                showQnhDialog = false
                qnhError = null
            },
            onDismiss = {
                showQnhDialog = false
                qnhError = null
            }
        )

        when {
            selectedOgnTarget != null -> {
                OgnMarkerDetailsSheet(
                    target = selectedOgnTarget,
                    unitsPreferences = unitsPreferences,
                    onDismiss = onDismissOgnTargetDetails
                )
            }

            selectedOgnThermal != null -> {
                OgnThermalDetailsSheet(
                    hotspot = selectedOgnThermal,
                    unitsPreferences = unitsPreferences,
                    onDismiss = onDismissOgnThermalDetails
                )
            }

            selectedAdsbTarget != null -> {
                AdsbMarkerDetailsSheet(
                    target = selectedAdsbTarget,
                    unitsPreferences = unitsPreferences,
                    onDismiss = onDismissAdsbTargetDetails
                )
            }
        }

        if (showForecastSheet) {
            ForecastOverlayBottomSheet(
                uiState = forecastOverlayState,
                onDismiss = { showForecastSheet = false },
                onEnabledChanged = forecastViewModel::setEnabled,
                onPrimaryParameterToggled = forecastViewModel::togglePrimaryOverlayParameter,
                onWindOverlayEnabledChanged = forecastViewModel::setWindOverlayEnabled,
                onWindParameterSelected = forecastViewModel::selectWindParameter,
                onAutoTimeEnabledChanged = forecastViewModel::setAutoTimeEnabled,
                onFollowTimeOffsetChanged = forecastViewModel::setFollowTimeOffsetMinutes,
                onJumpToNow = forecastViewModel::jumpToNow,
                onTimeSelected = forecastViewModel::selectTime,
                onOpacityChanged = forecastViewModel::setOpacity,
                onWindOverlayScaleChanged = forecastViewModel::setWindOverlayScale,
                onWindDisplayModeChanged = forecastViewModel::setWindDisplayMode
            )
        }
    }

    ReplayDiagnosticsLogger(
        replayState = replayState,
        flightDataManager = flightDataManager,
        unitsPreferences = unitsPreferences
    )
}

@Composable
private fun WindArrowSpeedTapLabel(
    speedKt: Double,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = MaterialTheme.typography.headlineSmall.fontSize * 0.5f,
        fontWeight = FontWeight.SemiBold
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Text(
            text = "Wind ${formatWindSpeedForTap(speedKt)} $unitLabel",
            color = Color.Black,
            style = textStyle,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

private fun formatWindSpeedForTap(speedKt: Double): String =
    String.format(Locale.US, "%.0f", speedKt)

private data class WindArrowTapCallout(
    val tapLatLng: LatLng,
    val speedKt: Double
)

@Composable
private fun rememberTrafficDebugPanelVisibility(
    enabled: Boolean,
    readyForAutoDismiss: Boolean,
    autoDismissDelayMs: Long
): Boolean {
    var visible by remember(enabled) { mutableStateOf(enabled) }
    LaunchedEffect(enabled, readyForAutoDismiss, autoDismissDelayMs) {
        if (!enabled) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        if (!readyForAutoDismiss) return@LaunchedEffect
        delay(autoDismissDelayMs)
        if (isActive) {
            visible = false
        }
    }
    return visible
}

private const val WIND_ARROW_SPEED_TAP_DISPLAY_MS = 4_000L
private const val TRAFFIC_DEBUG_PANEL_AUTO_DISMISS_MS = 3_000L
private const val DEFAULT_WIND_SPEED_UNIT_LABEL = "kt"
private const val WIND_TAP_LABEL_EDGE_PADDING_DP = 8
private const val WIND_TAP_LABEL_ANCHOR_GAP_DP = 10
private const val WIND_TAP_LABEL_ESTIMATED_WIDTH_DP = 136
private const val WIND_TAP_LABEL_ESTIMATED_HEIGHT_DP = 42
