package com.example.xcpro.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.util.Log
import com.example.xcpro.CompassWidget
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.common.units.VerticalSpeedUnit
import com.example.xcpro.map.DistanceCirclesCanvas
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapUIWidgets
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.sensors.GPSData
import com.example.ui1.VarioDialConfig
import com.example.ui1.VarioDialLabel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun CompassPanel(
    orientationData: OrientationData,
    orientationManager: MapOrientationManager,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        CompassWidget(
            orientation = orientationData,
            onModeToggle = {
                val nextMode = when (orientationManager.getCurrentMode()) {
                    MapOrientationMode.NORTH_UP -> MapOrientationMode.TRACK_UP
                    MapOrientationMode.TRACK_UP -> MapOrientationMode.HEADING_UP
                    MapOrientationMode.HEADING_UP -> MapOrientationMode.NORTH_UP
                }
                orientationManager.setOrientationMode(nextMode)
            }
        )
    }
}

@Composable
internal fun BallastPanel(
    ballastUiState: StateFlow<BallastUiState>,
    hideBallastPill: Boolean,
    widgetManager: MapUIWidgetManager,
    ballastOffset: androidx.compose.runtime.MutableState<Offset>,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onBallastCommand: (BallastCommand) -> Unit,
    isUiEditMode: Boolean,
    modifier: Modifier = Modifier
) {
    val ballastState by ballastUiState.collectAsStateWithLifecycle()
    val showBallastPill =
        !hideBallastPill && (
            ballastState.isAnimating ||
                ballastState.snapshot.hasBallast ||
                ballastState.snapshot.currentKg > 0.0
            )

    AnimatedVisibility(
        visible = showBallastPill,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        MapUIWidgets.BallastWidget(
            widgetManager = widgetManager,
            ballastState = ballastState,
            onCommand = onBallastCommand,
            ballastOffset = ballastOffset.value,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            onOffsetChange = { offset -> ballastOffset.value = offset },
            isEditMode = isUiEditMode
        )
    }
}

@Composable
internal fun VariometerPanel(
    flightDataManager: FlightDataManager,
    widgetManager: MapUIWidgetManager,
    variometerUiState: VariometerUiState,
    minVariometerSizePx: Float,
    maxVariometerSizePx: Float,
    onVariometerOffsetChange: (Offset) -> Unit,
    onVariometerSizeChange: (Float) -> Unit,
    onVariometerLongPress: () -> Unit,
    onVariometerEditFinished: () -> Unit,
    screenWidthPx: Float,
    screenHeightPx: Float,
    isUiEditMode: Boolean,
    replayState: StateFlow<SessionState>
) {
    val displayNumericVario by flightDataManager.displayVarioFlow.collectAsStateWithLifecycle()
    val xcSoarDisplayVario by flightDataManager.xcSoarDisplayVarioFlow.collectAsStateWithLifecycle()
    val unitsPreferences = flightDataManager.unitsPreferences
    val displayVarioUnits by remember(displayNumericVario, unitsPreferences) {
        derivedStateOf {
            unitsPreferences.verticalSpeed.fromSi(VerticalSpeedMs(displayNumericVario.toDouble()))
        }
    }
    val dialConfig by remember(unitsPreferences) {
        derivedStateOf { buildVarioDialConfig(unitsPreferences) }
    }
    val replaySession by replayState.collectAsStateWithLifecycle()
    val animationSpec: AnimationSpec<Float> = if (replaySession.status == SessionStatus.PLAYING) {
        // AI-NOTE: During replay we want a critically damped response to avoid overshoot on 10 Hz updates.
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
    } else {
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    }
    val animatedVario by animateFloatAsState(
        targetValue = displayNumericVario,
        animationSpec = animationSpec,
        label = "vario"
    )
    val animatedVarioState = rememberUpdatedState(animatedVario)
    val targetVarioState = rememberUpdatedState(displayNumericVario)
    if (com.example.xcpro.map.BuildConfig.DEBUG) {
        LaunchedEffect(replaySession.status) {
            if (replaySession.status != SessionStatus.PLAYING) return@LaunchedEffect
            // Higher cadence logging during replay to diagnose needle jitter.
            // AI-NOTE: keep this DEBUG-only to avoid log spam in prod.
            var lastNeedle = animatedVarioState.value
            var lastTarget = targetVarioState.value
            while (isActive && replayState.value.status == SessionStatus.PLAYING) {
                val needleValue = animatedVarioState.value
                val targetValue = targetVarioState.value
                val maxNeedle = dialConfig.maxValueSi.coerceAtLeast(0.1f)
                val clamped = needleValue.coerceIn(-maxNeedle, maxNeedle)
                val angle = clamped * (dialConfig.sweepDegrees / (maxNeedle * 2f)) - 90f
                val delta = needleValue - targetValue
                val targetStep = targetValue - lastTarget
                val needleStep = needleValue - lastNeedle
                val overshoot = if (kotlin.math.abs(delta) > 0.5f) "overshoot" else "ok"
                Log.d(
                    "REPLAY_NEEDLE",
                    "needle=${"%.3f".format(needleValue)} target=${"%.3f".format(targetValue)} " +
                        "delta=${"%.3f".format(delta)} stepN=${"%.3f".format(needleStep)} " +
                        "stepT=${"%.3f".format(targetStep)} angle=${"%.1f".format(angle)} " +
                        "state=$overshoot"
                )
                lastNeedle = needleValue
                lastTarget = targetValue
                delay(200L)
            }
        }
    }
    val varioFormatted by remember(displayNumericVario, unitsPreferences) {
        derivedStateOf {
            UnitsFormatter.verticalSpeed(
                VerticalSpeedMs(displayNumericVario.toDouble()),
                unitsPreferences
            )
        }
    }
    val xcSoarFormatted by remember(xcSoarDisplayVario, unitsPreferences) {
        derivedStateOf {
            UnitsFormatter.verticalSpeed(
                VerticalSpeedMs(xcSoarDisplayVario.toDouble()),
                unitsPreferences
            )
        }
    }
    MapUIWidgets.VariometerWidget(
        widgetManager = widgetManager,
        variometerState = variometerUiState,
        needleValue = animatedVario,
        displayValue = displayVarioUnits.toFloat(),
        displayLabel = stripUnit(varioFormatted),
        secondaryLabel = stripUnit(xcSoarFormatted),
        dialConfig = dialConfig,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        minSizePx = minVariometerSizePx,
        maxSizePx = maxVariometerSizePx,
        isEditMode = isUiEditMode,
        onOffsetChange = onVariometerOffsetChange,
        onSizeChange = onVariometerSizeChange,
        onLongPress = onVariometerLongPress,
        onEditFinished = onVariometerEditFinished
    )
}

private fun stripUnit(formatted: UnitsFormatter.FormattedValue): String =
    formatted.text.replace(formatted.unitLabel, "").trim()

private fun buildVarioDialConfig(unitsPreferences: com.example.xcpro.common.units.UnitsPreferences): VarioDialConfig {
    val maxSi = 5f
    val unit = unitsPreferences.verticalSpeed
    val stepUser = when (unit) {
        VerticalSpeedUnit.METERS_PER_SECOND -> 1.0
        VerticalSpeedUnit.KNOTS -> 2.0
        VerticalSpeedUnit.FEET_PER_MINUTE -> 200.0
    }
    val maxUserRaw = unit.fromSi(VerticalSpeedMs(maxSi.toDouble()))
    val maxUserRounded = when (unit) {
        VerticalSpeedUnit.METERS_PER_SECOND -> maxUserRaw
        else -> kotlin.math.round(maxUserRaw / stepUser) * stepUser
    }.coerceAtLeast(stepUser)
    val labels = buildList {
        var value = -maxUserRounded
        while (value <= maxUserRounded + 1e-6) {
            val valueSi = unit.toSi(value).value.toFloat().coerceIn(-maxSi, maxSi)
            add(VarioDialLabel(valueSi, formatVarioLabel(value)))
            value += stepUser
        }
    }
    return VarioDialConfig(
        maxValueSi = maxSi,
        labelValues = labels
    )
}

private fun formatVarioLabel(value: Double): String =
    kotlin.math.round(value).toInt().toString()

@Composable
internal fun DistanceCirclesLayer(
    currentZoom: Float,
    currentLocation: GPSData?,
    showDistanceCircles: Boolean
) {
    val zoom = currentZoom
    val latitude = currentLocation?.position?.latitude ?: 0.0
    AnimatedVisibility(
        visible = showDistanceCircles,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .zIndex(1f)
    ) {
        DistanceCirclesCanvas(
            mapZoom = zoom,
            mapLatitude = latitude,
            modifier = Modifier.fillMaxSize()
        )
    }
}
