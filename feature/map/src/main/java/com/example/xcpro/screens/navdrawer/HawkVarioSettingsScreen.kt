package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.ui1.UIVariometer
import com.example.ui1.VarioDialConfig
import com.example.ui1.VarioDialLabel
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.common.units.VerticalSpeedUnit
import com.example.xcpro.hawk.HawkConfidence
import com.example.xcpro.hawk.HawkVarioUiState
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HawkVarioSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: HawkVarioSettingsViewModel = hiltViewModel()
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "HAWK Vario",
                onNavigateUp = { navController.navigateUp() },
                onSecondaryNavigate = {
                    scope.launch {
                        navController.popBackStack("map", inclusive = false)
                        drawerState.open()
                    }
                },
                onNavigateToMap = {
                    scope.launch {
                        drawerState.close()
                        navController.popBackStack("map", inclusive = false)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "General",
                style = MaterialTheme.typography.titleMedium
            )

            HawkVarioDisplayOptionsCard(
                enableHawkUi = uiState.enableHawkUi,
                onEnableHawkUiChange = viewModel::setEnableHawkUi,
                showHawkCard = uiState.showHawkCard,
                onShowHawkCardChange = viewModel::setShowHawkCard
            )

            HawkNeedleTuningCard(
                omegaMinHz = uiState.needleOmegaMinHz,
                omegaMaxHz = uiState.needleOmegaMaxHz,
                targetTauSec = uiState.needleTargetTauSec,
                driftTauMinSec = uiState.needleDriftTauMinSec,
                driftTauMaxSec = uiState.needleDriftTauMaxSec,
                onOmegaMinChange = viewModel::setNeedleOmegaMinHz,
                onOmegaMaxChange = viewModel::setNeedleOmegaMaxHz,
                onTargetTauChange = viewModel::setNeedleTargetTauSec,
                onDriftTauMinChange = viewModel::setNeedleDriftTauMinSec,
                onDriftTauMaxChange = viewModel::setNeedleDriftTauMaxSec
            )

            if (uiState.enableHawkUi) {
                HawkVarioPreviewCard(
                    state = uiState.hawkVarioUiState,
                    unitsPreferences = uiState.unitsPreferences,
                    omegaMinHz = uiState.needleOmegaMinHz,
                    omegaMaxHz = uiState.needleOmegaMaxHz,
                    targetTauSec = uiState.needleTargetTauSec,
                    driftTauMinSec = uiState.needleDriftTauMinSec,
                    driftTauMaxSec = uiState.needleDriftTauMaxSec
                )
                HawkLiveDataCard(uiState.hawkVarioUiState)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "HAWK live data",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Enable HAWK UI to view live HAWK figures.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HawkVarioDisplayOptionsCard(
    enableHawkUi: Boolean,
    onEnableHawkUiChange: (Boolean) -> Unit,
    showHawkCard: Boolean,
    onShowHawkCardChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "HAWK display",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Enable HAWK UI",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Show live HAWK figures on this screen",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = enableHawkUi,
                    onCheckedChange = onEnableHawkUiChange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "HAWK Vario card",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Show the HAWK vario card in Variometers",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = showHawkCard,
                    onCheckedChange = onShowHawkCardChange
                )
            }
        }
    }
}

@Composable
private fun HawkVarioPreviewCard(
    state: HawkVarioUiState,
    unitsPreferences: UnitsPreferences,
    omegaMinHz: Double,
    omegaMaxHz: Double,
    targetTauSec: Double,
    driftTauMinSec: Double,
    driftTauMaxSec: Double
) {
    val dialConfig = remember(unitsPreferences) {
        buildVarioDialConfig(unitsPreferences)
    }
    val smoothedSi = state.varioSmoothedMps
    val pneumaticSi = rememberPneumaticNeedle(
        smoothedSi,
        state.confidence,
        state.confidenceScore,
        omegaMinHz,
        omegaMaxHz,
        targetTauSec,
        driftTauMinSec,
        driftTauMaxSec
    )
    val displayValueUser = smoothedSi?.let {
        unitsPreferences.verticalSpeed.fromSi(VerticalSpeedMs(it.toDouble())).toFloat()
    } ?: 0f
    val displayLabel = smoothedSi?.let {
        stripUnit(UnitsFormatter.verticalSpeed(VerticalSpeedMs(it.toDouble()), unitsPreferences))
    } ?: "--.-"
    val pneumaticLabel = pneumaticSi?.let {
        stripUnit(UnitsFormatter.verticalSpeed(VerticalSpeedMs(it.toDouble()), unitsPreferences))
    } ?: "--.-"
    val secondaryColor = if (!state.accelOk || !state.baroOk) {
        MaterialTheme.colorScheme.error
    } else {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "HAWK variometer preview",
                style = MaterialTheme.typography.titleSmall
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val maxSize = minOf(maxWidth, 280.dp)
                Box(modifier = Modifier.size(maxSize)) {
                    UIVariometer(
                        needleValue = smoothedSi ?: 0f,
                        fastNeedleValue = null,
                        averageNeedleValue = null,
                        dampedNeedleValue = pneumaticSi,
                        showPrimaryNeedle = false,
                        microArcEnabled = state.confidence == HawkConfidence.LEVEL6,
                        displayValue = displayValueUser,
                        valueLabel = displayLabel,
                        secondaryLabel = "PNEU $pneumaticLabel",
                        secondaryLabelColor = secondaryColor,
                        dialConfig = dialConfig,
                        windDirectionScreenDeg = null,
                        windIsValid = false,
                        windSpeedLabel = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = "Blue = smoothed, green = pneumatic (smoothed)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HawkNeedleTuningCard(
    omegaMinHz: Double,
    omegaMaxHz: Double,
    targetTauSec: Double,
    driftTauMinSec: Double,
    driftTauMaxSec: Double,
    onOmegaMinChange: (Double) -> Unit,
    onOmegaMaxChange: (Double) -> Unit,
    onTargetTauChange: (Double) -> Unit,
    onDriftTauMinChange: (Double) -> Unit,
    onDriftTauMaxChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Green needle tuning",
                style = MaterialTheme.typography.titleSmall
            )

            SliderRow(
                title = "Response speed (low confidence)",
                valueText = String.format(Locale.US, "%.2f Hz", omegaMinHz),
                description = "Lower = heavier, more pneumatic feel when confidence is mid.",
                value = omegaMinHz.toFloat(),
                valueRange = 0.5f..2.5f,
                onValueChange = { onOmegaMinChange(it.toDouble()) }
            )

            SliderRow(
                title = "Response speed (high confidence)",
                valueText = String.format(Locale.US, "%.2f Hz", omegaMaxHz),
                description = "Upper limit for how fast the green needle can react.",
                value = omegaMaxHz.toFloat(),
                valueRange = 1.0f..4.0f,
                onValueChange = { onOmegaMaxChange(it.toDouble()) }
            )

            SliderRow(
                title = "Input smoothing",
                valueText = String.format(Locale.US, "%.2f s", targetTauSec),
                description = "Low-pass on the green needle input; higher = less jitter but more lag.",
                value = targetTauSec.toFloat(),
                valueRange = 0.2f..2.0f,
                onValueChange = { onTargetTauChange(it.toDouble()) }
            )

            SliderRow(
                title = "Drift to zero (low confidence)",
                valueText = String.format(Locale.US, "%.1f s", driftTauMinSec),
                description = "How quickly the needle relaxes toward zero at mid confidence.",
                value = driftTauMinSec.toFloat(),
                valueRange = 1.0f..8.0f,
                onValueChange = { onDriftTauMinChange(it.toDouble()) }
            )

            SliderRow(
                title = "Drift to zero (high confidence)",
                valueText = String.format(Locale.US, "%.1f s", driftTauMaxSec),
                description = "How quickly it relaxes near high confidence (usually slower).",
                value = driftTauMaxSec.toFloat(),
                valueRange = 2.0f..15.0f,
                onValueChange = { onDriftTauMaxChange(it.toDouble()) }
            )
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun rememberPneumaticNeedle(
    target: Float?,
    confidence: HawkConfidence,
    confidenceScore: Float,
    omegaMinHz: Double,
    omegaMaxHz: Double,
    targetTauSec: Double,
    driftTauMinSec: Double,
    driftTauMaxSec: Double
): Float? {
    val targetState by rememberUpdatedState(target)
    val confidenceState by rememberUpdatedState(confidence)
    val scoreState by rememberUpdatedState(confidenceScore)
    var position by remember { mutableStateOf(0.0) }
    var velocity by remember { mutableStateOf(0.0) }
    var gain by remember { mutableStateOf(1.0) }
    var filteredTarget by remember { mutableStateOf(0.0) }
    var wasEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        if (target == null) {
            position = 0.0
            velocity = 0.0
            gain = 1.0
            filteredTarget = 0.0
        }
    }

    LaunchedEffect(Unit) {
        var lastFrameNs = 0L
        val gainFloor = 0.25f
        while (true) {
            val nowNs = withFrameNanos { it }
            if (lastFrameNs != 0L) {
                val dt = ((nowNs - lastFrameNs) / 1_000_000_000.0).coerceIn(0.0, 0.05)
                val currentTarget = targetState ?: 0f
                val currentConfidence = confidenceState
                val safeTargetTau = targetTauSec.coerceAtLeast(0.01)
                val targetAlpha = dt / (safeTargetTau + dt)
                filteredTarget += targetAlpha * (currentTarget - filteredTarget)
                val isEnabled = currentConfidence != HawkConfidence.UNKNOWN &&
                    currentConfidence != HawkConfidence.LEVEL1
                val normalized = if (isEnabled) {
                    ((scoreState - gainFloor) / (1f - gainFloor)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val smoothStep = normalized * normalized * (3f - 2f * normalized)
                val inputGain = (smoothStep * smoothStep).toDouble()
                val omegaHz = lerp(omegaMinHz, omegaMaxHz, normalized).coerceAtLeast(0.1)
                val omega = 2.0 * Math.PI * omegaHz
                val tauDrift = lerp(driftTauMinSec, driftTauMaxSec, normalized).coerceAtLeast(0.1)
                if (!isEnabled) {
                    gain = 0.0
                } else {
                    if (!wasEnabled) {
                        gain = 1.0
                    }
                    if (currentConfidence == HawkConfidence.LEVEL6) {
                        gain = 1.0
                    } else {
                        gain *= exp(-dt / tauDrift)
                    }
                }
                val targetWithGain = filteredTarget * gain * inputGain
                val acceleration = omega * omega * (targetWithGain - position) - 2.0 * omega * velocity
                velocity += acceleration * dt
                position += velocity * dt
                wasEnabled = isEnabled
            }
            lastFrameNs = nowNs
        }
    }

    return if (targetState == null) 0f else position.toFloat()
}

private fun lerp(min: Double, max: Double, t: Float): Double =
    min + (max - min) * t.toDouble()

@Composable
private fun HawkLiveDataCard(state: HawkVarioUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "HAWK live data",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = state.formatCenterValue(),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Raw: ${formatHawkValue(state.varioRawMps)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = state.statusLine,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Accel variance: ${formatOptional(state.accelVariance, "%.3f")}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Baro innovation: ${formatOptional(state.baroInnovationMps, "%+.2f m/s")}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Baro rate: ${formatOptional(state.baroHz, "%.1f Hz")}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Last update: ${state.lastUpdateElapsedRealtimeMs ?: 0L} ms",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun stripUnit(formatted: UnitsFormatter.FormattedValue): String =
    formatted.text.replace(formatted.unitLabel, "").trim()

private fun formatHawkValue(value: Float?): String {
    if (value == null || !value.isFinite()) return "--.- m/s"
    val clamped = if (abs(value) < 0.05f) 0f else value
    return String.format(Locale.US, "%+.1f m/s", clamped)
}

private fun formatOptional(value: Float?, format: String): String {
    if (value == null || !value.isFinite()) return "--"
    return String.format(Locale.US, format, value)
}

private fun buildVarioDialConfig(unitsPreferences: UnitsPreferences): VarioDialConfig {
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
