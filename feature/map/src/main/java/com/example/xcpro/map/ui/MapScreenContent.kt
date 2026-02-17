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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.adsb.AdsbConnectionState
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
import com.example.xcpro.ogn.OgnTrafficSnapshot
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
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.qnh.QnhCalibrationState
import java.util.Locale

@Composable
internal fun MapScreenContent(
    density: Density,
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (MapLibreMap) -> Unit,
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
    adsbSnapshot: AdsbTrafficSnapshot,
    adsbOverlayEnabled: Boolean,
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
    onToggleAdsbTraffic: () -> Unit,
    onAdsbTargetSelected: (Icao24) -> Unit,
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
    val currentQnhLabel = remember(liveFlightData?.qnh) {
        val qnh = liveFlightData?.qnh ?: 1013.25
        String.format(Locale.US, "%.1f hPa", qnh)
    }

    var showQnhDialog by remember { mutableStateOf(false) }
    var qnhInput by remember { mutableStateOf("") }
    var qnhError by remember { mutableStateOf<String?>(null) }
    var showQnhFab by remember { mutableStateOf(true) }
    var showForecastSheet by remember { mutableStateOf(false) }
    var tappedWindArrowSpeedKt by remember { mutableStateOf<Double?>(null) }
    val isForecastWindArrowOverlayActive = forecastOverlayState.enabled &&
        forecastOverlayState.windDisplayMode == ForecastWindDisplayMode.ARROW &&
        forecastOverlayState.tileSpec?.format == ForecastTileFormat.VECTOR_WIND_POINTS

    LaunchedEffect(
        mapState.mapLibreMap,
        forecastOverlayState.enabled,
        forecastOverlayState.tileSpec,
        forecastOverlayState.legend,
        forecastOverlayState.opacity,
        forecastOverlayState.windOverlayScale,
        forecastOverlayState.windDisplayMode,
        forecastOverlayState.errorMessage
    ) {
        if (!forecastOverlayState.enabled || forecastOverlayState.errorMessage != null) {
            overlayManager.clearForecastOverlay()
            return@LaunchedEffect
        }
        val tileSpec = forecastOverlayState.tileSpec ?: return@LaunchedEffect
        overlayManager.setForecastOverlay(
            enabled = true,
            tileSpec = tileSpec,
            opacity = forecastOverlayState.opacity,
            windOverlayScale = forecastOverlayState.windOverlayScale,
            windDisplayMode = forecastOverlayState.windDisplayMode,
            legendSpec = forecastOverlayState.legend
        )
    }

    LaunchedEffect(isForecastWindArrowOverlayActive) {
        if (!isForecastWindArrowOverlayActive) {
            tappedWindArrowSpeedKt = null
        }
    }

    LaunchedEffect(tappedWindArrowSpeedKt) {
        if (tappedWindArrowSpeedKt != null) {
            delay(WIND_ARROW_SPEED_TAP_DISPLAY_MS)
            tappedWindArrowSpeedKt = null
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                        overlayManager = overlayManager,
                        onAdsbTargetSelected = onAdsbTargetSelected,
                        onForecastWindArrowSpeedTap = { speedKt ->
                            if (isForecastWindArrowOverlayActive) {
                                tappedWindArrowSpeedKt = speedKt
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
            showAdsbTraffic = adsbOverlayEnabled,
            showForecastOverlay = forecastOverlayState.enabled,
            showQnhFab = showQnhFab,
            showVarioDemoFab = showVarioDemoFab,
            showRacingReplayFab = showRacingReplayFab,
            onRecenter = locationManager::recenterOnCurrentLocation,
            onToggleDistanceCircles = { overlayManager.toggleDistanceCircles() },
            onToggleOgnTraffic = onToggleOgnTraffic,
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
                visible = BuildConfig.DEBUG && ognOverlayEnabled,
                snapshot = ognSnapshot
            )

            AdsbDebugPanel(
                visible = BuildConfig.DEBUG && adsbOverlayEnabled,
                snapshot = adsbSnapshot
            )
        }

        tappedWindArrowSpeedKt?.let { speedKt ->
            WindArrowSpeedTapLabel(
                speedKt = speedKt,
                unitLabel = forecastOverlayState.legend?.unitLabel
                    ?.takeIf { label -> label.isNotBlank() }
                    ?: DEFAULT_WIND_SPEED_UNIT_LABEL,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
            )
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

        selectedAdsbTarget?.let { target ->
            AdsbMarkerDetailsSheet(
                target = target,
                unitsPreferences = unitsPreferences,
                onDismiss = onDismissAdsbTargetDetails
            )
        }

        if (showForecastSheet) {
            ForecastOverlayBottomSheet(
                uiState = forecastOverlayState,
                onDismiss = { showForecastSheet = false },
                onEnabledChanged = forecastViewModel::setEnabled,
                onParameterSelected = forecastViewModel::selectParameter,
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

private const val WIND_ARROW_SPEED_TAP_DISPLAY_MS = 4_000L
private const val DEFAULT_WIND_SPEED_UNIT_LABEL = "kt"
