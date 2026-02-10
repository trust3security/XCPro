package com.example.xcpro.adsb

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.adsb.ui.openSkyCategoryLabel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdsbMarkerDetailsSheet(
    target: AdsbTrafficUiModel,
    unitsPreferences: UnitsPreferences,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = target.callsign?.takeIf { it.isNotBlank() } ?: target.id.raw.uppercase(Locale.US),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            DetailRow("ICAO24", target.id.raw.uppercase(Locale.US))
            DetailRow("Type", openSkyCategoryLabel(target.category))
            DetailRow("Category", target.category?.toString() ?: "--")
            DetailRow("Altitude", target.altitudeM?.let { UnitsFormatter.altitude(AltitudeM(it), unitsPreferences).text } ?: "--")
            DetailRow("Speed", target.speedMps?.let { UnitsFormatter.speed(SpeedMs(it), unitsPreferences).text } ?: "--")
            DetailRow("Track", target.trackDeg?.let { "${it.roundToOneDecimal()}\u00B0" } ?: "--")
            DetailRow(
                "Vertical Rate",
                target.climbMps?.let { UnitsFormatter.verticalSpeed(VerticalSpeedMs(it), unitsPreferences).text } ?: "--"
            )
            DetailRow("Age", "${target.ageSec}s")
            DetailRow("Distance", UnitsFormatter.distance(DistanceM(target.distanceMeters), unitsPreferences).text)
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

private fun Double.roundToOneDecimal(): String = String.format(Locale.US, "%.1f", this)
