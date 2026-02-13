package com.example.xcpro.map.ui

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.components.MapActionButtons
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.task.MapTaskScreenUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

@Composable
internal fun OgnDebugPanel(
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
        Column(
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
internal fun AdsbDebugPanel(
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
        Column(
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
internal fun ReplayDiagnosticsLogger(
    replayState: StateFlow<SessionState>,
    flightDataManager: FlightDataManager,
    unitsPreferences: UnitsPreferences
) {
    if (!BuildConfig.DEBUG) return

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
internal fun MapTaskManagerLayer(
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
internal fun MapActionButtonsLayer(
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
internal fun QnhDialogHost(
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
