package com.example.xcpro.map.ui
/**
 * Map screen body content used inside MapScreenScaffold.
 * Invariants: UI renders state only and routes mutations through the ViewModel.
 */


import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.example.xcpro.tasks.TaskManagerCoordinator
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

@Composable
internal fun MapScreenContent(
    density: Density,
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (MapLibreMap) -> Unit,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    taskManager: TaskManagerCoordinator,
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
    onSetAATEditMode: (Boolean) -> Unit,
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
    var showQnhDialog by remember { mutableStateOf(false) }
    var qnhInput by remember { mutableStateOf("") }
    var qnhError by remember { mutableStateOf<String?>(null) }
    var showQnhFab by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.border(2.dp, Color.Yellow)
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
                        taskManager = taskManager,
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
                        isAATEditMode = isAATEditMode,
                        isUiEditMode = isUiEditMode,
                        onEditModeChange = onEditModeChange,
                        onSetAATEditMode = onSetAATEditMode,
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
            currentLocation = currentLocation
        )

        MapActionButtonsLayer(
            taskScreenManager = taskScreenManager,
            currentLocation = currentLocation,
            showRecenterButton = showRecenterButton,
            showReturnButton = showReturnButton,
            showDistanceCircles = showDistanceCircles,
            showOgnTraffic = ognOverlayEnabled,
            showAdsbTraffic = adsbOverlayEnabled,
            showQnhFab = showQnhFab,
            showVarioDemoFab = showVarioDemoFab,
            showRacingReplayFab = showRacingReplayFab,
            onRecenter = locationManager::recenterOnCurrentLocation,
            onToggleDistanceCircles = { overlayManager.toggleDistanceCircles() },
            onToggleOgnTraffic = onToggleOgnTraffic,
            onToggleAdsbTraffic = onToggleAdsbTraffic,
            onReturn = { locationManager.returnToSavedLocation() },
            onShowQnhDialog = {
                val currentQnh = flightDataManager.liveFlightData?.qnh ?: 1013.25
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

        OgnDebugPanel(
            visible = BuildConfig.DEBUG && ognOverlayEnabled,
            snapshot = ognSnapshot,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 24.dp)
        )

        AdsbDebugPanel(
            visible = BuildConfig.DEBUG && adsbOverlayEnabled,
            snapshot = adsbSnapshot,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 132.dp)
        )

        QnhDialogHost(
            visible = showQnhDialog,
            qnhInput = qnhInput,
            qnhError = qnhError,
            unitsPreferences = unitsPreferences,
            liveData = flightDataManager.liveFlightData,
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
    }

    ReplayDiagnosticsLogger(
        replayState = replayState,
        flightDataManager = flightDataManager,
        unitsPreferences = unitsPreferences
    )
}

@Composable
private fun OgnDebugPanel(
    visible: Boolean,
    snapshot: OgnTrafficSnapshot,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Surface(
        modifier = modifier,
        color = Color(0xCC111827),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.small
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = "OGN ${snapshot.connectionState.toDebugLabel()}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Targets: ${snapshot.targets.size}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Center: ${formatCoord(snapshot.subscriptionCenterLat)}, ${formatCoord(snapshot.subscriptionCenterLon)}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Radius: ${snapshot.receiveRadiusKm} km",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "DDB age: ${formatAge(snapshot.ddbCacheAgeMs)}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Backoff: ${formatBackoff(snapshot.reconnectBackoffMs)}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            snapshot.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun OgnConnectionState.toDebugLabel(): String = when (this) {
    OgnConnectionState.DISCONNECTED -> "DISCONNECTED"
    OgnConnectionState.CONNECTING -> "CONNECTING"
    OgnConnectionState.CONNECTED -> "CONNECTED"
    OgnConnectionState.ERROR -> "ERROR"
}

@Composable
private fun AdsbDebugPanel(
    visible: Boolean,
    snapshot: AdsbTrafficSnapshot,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Surface(
        modifier = modifier,
        color = Color(0xCC1F2937),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.small
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = "ADS-B ${snapshot.connectionState.toDebugLabel()}",
                color = Color(0xFFF9FAFB),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Active displayed: ${snapshot.displayedCount}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Counts (fetched/within/displayed): ${snapshot.fetchedCount}/${snapshot.withinRadiusCount}/${snapshot.displayedCount}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Center: ${formatCoord(snapshot.centerLat)}, ${formatCoord(snapshot.centerLon)}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Radius: ${snapshot.receiveRadiusKm} km",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "HTTP: ${snapshot.lastHttpStatus ?: "--"} | Credits: ${snapshot.remainingCredits ?: "--"}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            snapshot.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun AdsbConnectionState.toDebugLabel(): String = when (this) {
    AdsbConnectionState.Disabled -> "DISABLED"
    AdsbConnectionState.Active -> "ACTIVE"
    is AdsbConnectionState.BackingOff -> "BACKOFF ${retryAfterSec}s"
    is AdsbConnectionState.Error -> "ERROR"
}

private fun formatCoord(value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    return String.format(java.util.Locale.US, "%.4f", value)
}

private fun formatAge(ageMs: Long?): String {
    if (ageMs == null || ageMs < 0L) return "--"
    val seconds = ageMs / 1000L
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3600L -> "${seconds / 60L}m"
        else -> "${seconds / 3600L}h"
    }
}

private fun formatBackoff(backoffMs: Long?): String {
    if (backoffMs == null || backoffMs <= 0L) return "--"
    return "${backoffMs / 1000L}s"
}

@Composable
private fun ReplayDiagnosticsLogger(
    replayState: StateFlow<SessionState>,
    flightDataManager: FlightDataManager,
    unitsPreferences: UnitsPreferences
) {
    if (!com.example.xcpro.map.BuildConfig.DEBUG) return

    val replaySession by replayState.collectAsStateWithLifecycle()

    LaunchedEffect(replaySession.status, unitsPreferences) {
        Log.d("REPLAY_UI", "status=${replaySession.status} speed=${replaySession.speedMultiplier}")
        if (replaySession.status != SessionStatus.PLAYING) return@LaunchedEffect

        while (isActive && replayState.value.status == SessionStatus.PLAYING) {
            val live = flightDataManager.liveFlightData
            val displayMs = live?.displayVario ?: Double.NaN
            val displayUnits = if (displayMs.isFinite()) {
                unitsPreferences.verticalSpeed.fromSi(VerticalSpeedMs(displayMs))
            } else {
                Double.NaN
            }
            val label = if (displayMs.isFinite()) {
                UnitsFormatter.verticalSpeed(
                    VerticalSpeedMs(displayMs),
                    unitsPreferences
                ).text
            } else {
                "--"
            }
            Log.d(
                "REPLAY_UI",
                "displayMs=${"%.3f".format(displayMs)} displayUi=${"%.3f".format(displayUnits)} " +
                    "label=$label units=${unitsPreferences.verticalSpeed} " +
                    "valid=${live?.varioValid} src=${live?.varioSource} baseDisp=${live?.baselineDisplayVario}"
            )
            delay(1_000L)
        }
    }
}

@Composable
private fun MapTaskManagerLayer(
    taskScreenManager: MapTaskScreenManager,
    waypointData: List<WaypointData>,
    currentLocation: MapLocationUiModel?
) {
    MapTaskScreenUi.AllTaskScreenComponents(
        taskScreenManager = taskScreenManager,
        allWaypoints = waypointData,
        currentQNH = "1013 hPa",
        currentLocation = currentLocation
    )
}

@Composable
private fun MapActionButtonsLayer(
    taskScreenManager: MapTaskScreenManager,
    currentLocation: MapLocationUiModel?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    showOgnTraffic: Boolean,
    showAdsbTraffic: Boolean,
    showQnhFab: Boolean,
    showVarioDemoFab: Boolean,
    showRacingReplayFab: Boolean,
    onRecenter: () -> Unit,
    onToggleDistanceCircles: () -> Unit,
    onToggleOgnTraffic: () -> Unit,
    onToggleAdsbTraffic: () -> Unit,
    onReturn: () -> Unit,
    onShowQnhDialog: () -> Unit,
    onDismissQnhFab: () -> Unit,
    onVarioDemoReferenceClick: () -> Unit,
    onVarioDemoSimClick: () -> Unit,
    onVarioDemoSim2Click: () -> Unit,
    onVarioDemoSim3Click: () -> Unit,
    onRacingReplayClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    MapActionButtons(
        taskScreenManager = taskScreenManager,
        currentLocation = currentLocation,
        showRecenterButton = showRecenterButton,
        showReturnButton = showReturnButton,
        showDistanceCircles = showDistanceCircles,
        showOgnTraffic = showOgnTraffic,
        showAdsbTraffic = showAdsbTraffic,
        onRecenter = onRecenter,
        onToggleDistanceCircles = onToggleDistanceCircles,
        onToggleOgnTraffic = onToggleOgnTraffic,
        onToggleAdsbTraffic = onToggleAdsbTraffic,
        onReturn = onReturn,
        onShowQnhDialog = onShowQnhDialog,
        showQnhFab = showQnhFab,
        onDismissQnhFab = onDismissQnhFab,
        showVarioDemoFab = showVarioDemoFab,
        showRacingReplayFab = showRacingReplayFab,
        onVarioDemoReferenceClick = onVarioDemoReferenceClick,
        onVarioDemoSimClick = onVarioDemoSimClick,
        onVarioDemoSim2Click = onVarioDemoSim2Click,
        onVarioDemoSim3Click = onVarioDemoSim3Click,
        onRacingReplayClick = onRacingReplayClick,
        modifier = modifier
    )
}

@Composable
private fun QnhDialogHost(
    visible: Boolean,
    qnhInput: String,
    qnhError: String?,
    unitsPreferences: UnitsPreferences,
    liveData: RealTimeFlightData?,
    calibrationState: QnhCalibrationState,
    onQnhInputChange: (String) -> Unit,
    onConfirm: (Double) -> Unit,
    onInvalidInput: (String) -> Unit,
    onAutoCalibrate: () -> Unit,
    onDismiss: () -> Unit
) {
    QnhDialog(
        visible = visible,
        qnhInput = qnhInput,
        qnhError = qnhError,
        unitsPreferences = unitsPreferences,
        liveData = liveData,
        calibrationState = calibrationState,
        onQnhInputChange = onQnhInputChange,
        onConfirm = onConfirm,
        onInvalidInput = onInvalidInput,
        onAutoCalibrate = onAutoCalibrate,
        onDismiss = onDismiss
    )
}
