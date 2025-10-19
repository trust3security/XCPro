package com.example.xcpro.xcprov1.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.xcprov1.audio.XcproV1AudioEngine
import com.example.xcpro.xcprov1.model.FlightDataV1Snapshot
import com.example.xcpro.xcprov1.viewmodel.HawkDashboardViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

private const val HISTORY_CAP = 60

private enum class FlightPhase {
    Staging,
    Cruise,
    Thermal,
    Glide
}

private fun determinePhase(snapshot: FlightDataV1Snapshot?): FlightPhase {
    if (snapshot == null) return FlightPhase.Staging
    if (snapshot.confidence < 0.3) return FlightPhase.Staging
    return when {
        snapshot.actualClimb > 1.2 || snapshot.netto > 1.5 -> FlightPhase.Thermal
        snapshot.actualClimb < -2.0 && snapshot.netto < -1.5 -> FlightPhase.Glide
        else -> FlightPhase.Cruise
    }
}

private fun FlightPhase.presentationText(): Pair<String, String> = when (this) {
    FlightPhase.Staging -> "Initialising sensors" to "Hold level flight while the filter settles."
    FlightPhase.Cruise -> "Cruise mode" to "Energy guidance suits inter-thermal cruise."
    FlightPhase.Thermal -> "Thermal mode" to "Center the core using dual needles and trend."
    FlightPhase.Glide -> "Final glide" to "Watch sink pockets and protect arrival height."
}

private data class PhaseAdvice(
    val title: String,
    val message: String
)

private fun FlightPhase.advice(snapshot: FlightDataV1Snapshot?): PhaseAdvice {
    val climb = snapshot?.actualClimb ?: Double.NaN
    val delta = snapshot?.let { it.potentialClimb - it.actualClimb } ?: Double.NaN
    return when (this) {
        FlightPhase.Staging -> PhaseAdvice(
            title = "Standby",
            message = "Confidence is low. Keep the device steady to finish initialisation."
        )
        FlightPhase.Cruise -> PhaseAdvice(
            title = "Cruise guidance",
            message = if (!climb.isNaN() && !delta.isNaN()) {
                when {
                    delta > 0.5 -> "Reduce IAS slightly; lift ahead is still improving."
                    delta < -0.5 -> "Push to best L/D - energy is decaying."
                    else -> "Hold course and monitor the wind cue."
                }
            } else {
                "Waiting for stable airmass estimate."
            }
        )
        FlightPhase.Thermal -> PhaseAdvice(
            title = "Thermal strategy",
            message = if (!climb.isNaN()) {
                when {
                    climb > 2.5 -> "Tighten the turn and stay with the core."
                    climb < 1.0 -> "Widen the circle and probe for fresher lift."
                    else -> "Hold bank and keep the needles aligned."
                }
            } else {
                "Follow the dual needles to lock into the core."
            }
        )
        FlightPhase.Glide -> PhaseAdvice(
            title = "Arrival guard",
            message = if (!climb.isNaN()) {
                String.format(Locale.US, "Sink %.1f m/s - adjust speed to remain above final glide.", climb)
            } else {
                "Monitor sink bands and protect arrival height."
            }
        )
    }
}

@Composable
fun HawkDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: HawkDashboardViewModel
) {
    val snapshot by viewModel.snapshotFlow.collectAsState()
    val garminStatus by viewModel.garminStatus.collectAsState()
    val audioEnabled by viewModel.audioEnabled.collectAsState(initial = true)
    val audioStats by viewModel.audioTelemetry.collectAsState()

    val phase = remember(snapshot) { determinePhase(snapshot) }
    val history = rememberClimbHistory(snapshot)
    var diagnosticsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PhaseBanner(phase, snapshot)
        PrimaryInstrumentRow(snapshot, phase, history)
        PhaseAdviceCard(phase, snapshot)
        EngagementRow(snapshot)
        BottomCards(
            snapshot = snapshot,
            audioEnabled = audioEnabled,
            audioStats = audioStats,
            onAudioToggle = viewModel::setAudioEnabled,
            garminStatus = garminStatus,
            onConnect = viewModel::connectGarmin,
            onDisconnect = viewModel::disconnectGarmin
        )
        DiagnosticsCard(snapshot, diagnosticsExpanded) { diagnosticsExpanded = !diagnosticsExpanded }
    }
}

@Composable
private fun PhaseBanner(
    phase: FlightPhase,
    snapshot: FlightDataV1Snapshot?
) {
    val colors = MaterialTheme.colorScheme
    val accent = when (phase) {
        FlightPhase.Staging -> colors.secondary
        FlightPhase.Cruise -> colors.primary
        FlightPhase.Thermal -> colors.tertiary
        FlightPhase.Glide -> colors.error
    }
    val (title, subtitle) = phase.presentationText()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            snapshot?.let {
                Text(
                    text = "Source: ${it.sourceLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrimaryInstrumentRow(
    snapshot: FlightDataV1Snapshot?,
    phase: FlightPhase,
    history: List<Double>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                HawkGauge(
                    actualClimb = snapshot?.actualClimb,
                    potentialClimb = snapshot?.potentialClimb,
                    confidence = snapshot?.confidence ?: 0.0,
                    aoaDeg = snapshot?.aoaDeg,
                    sideslipDeg = snapshot?.sideslipDeg,
                    gaugeSize = 300.dp
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KeyStatCard(
                label = "Actual climb",
                value = snapshot?.actualClimb,
                unit = "m/s",
                accent = when (phase) {
                    FlightPhase.Thermal -> MaterialTheme.colorScheme.primary
                    FlightPhase.Glide -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Potential",
                    value = snapshot?.potentialClimb
                )
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Netto",
                    value = snapshot?.netto
                )
            }
            ConfidenceMeter(snapshot?.confidence ?: 0.0)
            Sparkline(history)
        }
    }
}

@Composable
private fun KeyStatCard(
    label: String,
    value: Double?,
    unit: String,
    accent: Color
) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label.uppercase(Locale.US),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
            Text(
                text = value.signedFormat(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: Double?
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.signedFormat(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ConfidenceMeter(confidence: Double) {
    val clamped = confidence.coerceIn(0.0, 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Sensor confidence",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "%.0f%%", clamped * 100),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = clamped.toFloat())
                    .height(10.dp)
                    .background(
                        color = when {
                            clamped < 0.4 -> MaterialTheme.colorScheme.error
                            clamped < 0.7 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

@Composable
private fun Sparkline(history: List<Double>) {
    if (history.isEmpty()) {
        Text(
            text = "History building...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val samples = history.takeLast(12)
    val maxMagnitude = max(samples.maxOf { abs(it) }, 0.3)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        samples.forEach { value ->
            val heightFraction = (abs(value) / maxMagnitude).coerceIn(0.0, 1.0)
            val barHeight = (32.dp * heightFraction.toFloat()).coerceAtLeast(4.dp)
            val barColor = if (value >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .align(if (value >= 0) Alignment.BottomCenter else Alignment.TopCenter)
                        .height(barHeight)
                        .width(12.dp)
                        .background(barColor, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun PhaseAdviceCard(phase: FlightPhase, snapshot: FlightDataV1Snapshot?) {
    val advice = phase.advice(snapshot)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = advice.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = advice.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EngagementRow(snapshot: FlightDataV1Snapshot?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Air mass summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val windSpeed = snapshot?.let { sqrt(it.windX * it.windX + it.windY * it.windY) }
            val windBearing = snapshot?.let {
                val angle = Math.toDegrees(atan2(it.windY, it.windX))
                (angle + 360.0) % 360.0
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Wind",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = windSpeed?.let { String.format(Locale.US, "%.1f m/s", it) } ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = windBearing?.let { String.format(Locale.US, "from %.0f deg", it) } ?: "--",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Trend",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = snapshot?.climbTrend.signedFormat(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Smoothed climb delta",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomCards(
    snapshot: FlightDataV1Snapshot?,
    audioEnabled: Boolean,
    audioStats: XcproV1AudioEngine.AudioTelemetry,
    onAudioToggle: (Boolean) -> Unit,
    garminStatus: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AudioCard(
            modifier = Modifier.weight(1f),
            audioEnabled = audioEnabled,
            telemetry = audioStats,
            onAudioEnabledChange = onAudioToggle
        )
        GarminCard(
            modifier = Modifier.weight(1f),
            status = garminStatus,
            onConnect = onConnect,
            onDisconnect = onDisconnect
        )
    }
}

@Composable
private fun AudioCard(
    modifier: Modifier,
    audioEnabled: Boolean,
    telemetry: XcproV1AudioEngine.AudioTelemetry,
    onAudioEnabledChange: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Audio vario",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (audioEnabled) "Lift cues active" else "Muted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = audioEnabled,
                    onCheckedChange = onAudioEnabledChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AudioStat("Tone", telemetry.frequencyHz, "Hz")
                AudioStat("Cycle", telemetry.cycleTimeMs, "ms")
                AudioStat("Duty", telemetry.dutyCycle * 100.0, "%")
            }
        }
    }
}

@Composable
private fun AudioStat(label: String, value: Double, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format(Locale.US, "%.1f %s", value, unit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GarminCard(
    modifier: Modifier,
    status: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Garmin GLO 2",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConnect) { Text("Connect") }
                Button(onClick = onDisconnect) { Text("Disconnect") }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    snapshot: FlightDataV1Snapshot?,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Sensor diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (expanded) "Tap to collapse" else "Tap to inspect residuals",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                snapshot?.diagnostics?.let { diagnostics ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DiagnosticRow("Covariance trace", diagnostics.covarianceTrace)
                        DiagnosticRow("Baro innovation", diagnostics.baroInnovation)
                        DiagnosticRow("Accel innovation", diagnostics.accelInnovation)
                        DiagnosticRow("GPS innovation", diagnostics.gpsInnovation)
                        DiagnosticRow("Residual RMS", diagnostics.residualRms)
                    }
                } ?: Text(
                    text = "Awaiting first solution fix.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format(Locale.US, "%.3f", value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun rememberClimbHistory(snapshot: FlightDataV1Snapshot?): List<Double> {
    val history = remember { mutableStateListOf<Double>() }
    LaunchedEffect(snapshot?.timestampMillis) {
        snapshot?.let {
            history.add(it.actualClimb)
            while (history.size > HISTORY_CAP) {
                history.removeAt(0)
            }
        }
    }
    return history
}

private fun Double?.signedFormat(): String =
    this?.let { String.format(Locale.US, "%+.1f", it) } ?: "--"
