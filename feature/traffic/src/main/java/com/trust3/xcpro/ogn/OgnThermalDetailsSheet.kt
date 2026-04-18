package com.trust3.xcpro.ogn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.DistanceM
import com.trust3.xcpro.common.units.UnitsFormatter
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.units.VerticalSpeedMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OgnThermalDetailsSheet(
    hotspot: OgnThermalHotspot,
    context: SelectedOgnThermalContext?,
    distanceMeters: Double?,
    unitsPreferences: UnitsPreferences,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = hotspot.sourceLabel.ifBlank { "Thermal" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Thermal details",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            DetailRow("Distance", formatDistance(distanceMeters, unitsPreferences))
            DetailRow("Age", formatThermalAge(context?.ageMs))
            DetailRow("Duration", formatThermalDuration(context?.durationMs))
            DetailRow("Drift", formatThermalDrift(context, unitsPreferences))
            DetailRow("Start Height", formatAltitude(hotspot.startAltitudeMeters, unitsPreferences))
            DetailRow("Max Height", formatAltitude(hotspot.maxAltitudeMeters, unitsPreferences))
            DetailRow("Altitude Gain", formatThermalAltitudeDelta(context?.altitudeGainMeters, unitsPreferences))
            DetailRow("Max Climb", formatClimbRate(hotspot.maxClimbRateMps, unitsPreferences))
            DetailRow(
                "Average Climb",
                formatClimbRate(
                    hotspot.averageBottomToTopClimbRateMps ?: hotspot.averageClimbRateMps,
                    unitsPreferences
                )
            )
            DetailRow("State", hotspot.state.name.lowercase().replaceFirstChar { it.uppercase() })

            Text(
                text = "Informational only. Not for collision avoidance or separation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatAltitude(altitudeMeters: Double?, unitsPreferences: UnitsPreferences): String =
    altitudeMeters
        ?.takeIf { it.isFinite() }
        ?.let { UnitsFormatter.altitude(AltitudeM(it), unitsPreferences).text }
        ?: "--"

private fun formatClimbRate(climbRateMps: Double?, unitsPreferences: UnitsPreferences): String =
    climbRateMps
        ?.takeIf { it.isFinite() }
        ?.let { UnitsFormatter.verticalSpeed(VerticalSpeedMs(it), unitsPreferences).text }
        ?: "--"

private fun formatDistance(distanceMeters: Double?, unitsPreferences: UnitsPreferences): String =
    distanceMeters
        ?.takeIf { it.isFinite() }
        ?.let { UnitsFormatter.distance(DistanceM(it), unitsPreferences).text }
        ?: "--"

internal fun formatThermalAge(ageMs: Long?): String = compactElapsedLabel(ageMs)

internal fun formatThermalDuration(durationMs: Long?): String = compactElapsedLabel(durationMs)

internal fun compactElapsedLabel(durationMs: Long?): String {
    val safeDurationMs = durationMs?.coerceAtLeast(0L) ?: return "--"
    if (safeDurationMs < 1_000L) return "<1s"
    val totalSeconds = safeDurationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes.toString().padStart(2, '0')}m"
        minutes > 0L -> "${minutes}m ${seconds.toString().padStart(2, '0')}s"
        else -> "${seconds}s"
    }
}

internal fun formatThermalDrift(
    context: SelectedOgnThermalContext?,
    unitsPreferences: UnitsPreferences
): String {
    val bearing = context?.driftBearingDeg?.takeIf { it.isFinite() } ?: return "--"
    val distance = context.driftDistanceMeters?.takeIf { it.isFinite() } ?: return "--"
    return "${cardinalDirectionLabel(bearing)}, ${formatDistance(distance, unitsPreferences)}"
}

internal fun cardinalDirectionLabel(bearingDeg: Double): String {
    val normalized = ((bearingDeg % 360.0) + 360.0) % 360.0
    val cards = arrayOf(
        "N",
        "NNE",
        "NE",
        "ENE",
        "E",
        "ESE",
        "SE",
        "SSE",
        "S",
        "SSW",
        "SW",
        "WSW",
        "W",
        "WNW",
        "NW",
        "NNW"
    )
    val index = (((normalized + 11.25) / 22.5).toInt()) % cards.size
    return cards[index]
}

internal fun formatThermalAltitudeDelta(
    altitudeDeltaMeters: Double?,
    unitsPreferences: UnitsPreferences
): String {
    val delta = altitudeDeltaMeters?.takeIf { it.isFinite() } ?: return "--"
    val sign = if (delta > 0.0) "+" else if (delta < 0.0) "-" else ""
    val formatted = UnitsFormatter.altitude(AltitudeM(abs(delta)), unitsPreferences).text
    return "$sign$formatted"
}
