package com.example.xcpro.ogn

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
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OgnThermalDetailsSheet(
    hotspot: OgnThermalHotspot,
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
            DetailRow("Start Height", formatAltitude(hotspot.startAltitudeMeters, unitsPreferences))
            DetailRow("Max Height", formatAltitude(hotspot.maxAltitudeMeters, unitsPreferences))
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
