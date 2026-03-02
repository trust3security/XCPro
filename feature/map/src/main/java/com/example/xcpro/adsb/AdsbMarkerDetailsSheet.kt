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
import com.example.xcpro.adsb.metadata.domain.MetadataAvailability
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState
import com.example.xcpro.adsb.ui.openSkyCategoryLabel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdsbMarkerDetailsSheet(
    target: AdsbSelectedTargetDetails,
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

            Text(
                text = "Live state",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            DetailRow("ICAO24", target.id.raw.uppercase(Locale.US))
            DetailRow("Altitude", target.altitudeM?.let { UnitsFormatter.altitude(AltitudeM(it), unitsPreferences).text } ?: "--")
            DetailRow(
                distanceLabelForDetails(target.usesOwnshipReference),
                UnitsFormatter.distance(DistanceM(target.distanceMeters), unitsPreferences).text
            )
            DetailRow("Ownship reference", ownshipReferenceText(target.usesOwnshipReference))
            DetailRow("Proximity tier", proximityTierText(target.proximityTier))
            DetailRow("Trend", proximityTrendText(target))
            DetailRow("Range rate (+ closing)", closingRateText(target.closingRateMps))
            DetailRow("Proximity reason", proximityReasonText(target))
            DetailRow("Speed", target.speedMps?.let { UnitsFormatter.speed(SpeedMs(it), unitsPreferences).text } ?: "--")
            DetailRow("Track", target.trackDeg?.let { "${it.roundToOneDecimal()}\u00B0" } ?: "--")
            DetailRow(
                "Vertical Rate",
                AdsbDetailsFormatter.formatVerticalRate(target.climbMps, unitsPreferences)
            )
            DetailRow("Age", "${target.ageSec}s")

            Text(
                text = "Aircraft identification",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            DetailRow("Registration", target.registration ?: "--")
            DetailRow("Typecode", target.typecode ?: "--")
            DetailRow("Model", target.model ?: "--")
            DetailRow("Manufacturer", target.manufacturerName ?: "--")
            DetailRow("Operator", target.operator ?: "--")
            DetailRow("Operator callsign", target.operatorCallsign ?: "--")
            DetailRow("ICAO aircraft type", target.icaoAircraftType ?: "--")
            DetailRow("Owner", target.owner ?: "--")
            Text(
                text = metadataStatusText(target.metadataAvailability, target.metadataSyncState),
                style = MaterialTheme.typography.bodySmall,
                color = when (target.metadataAvailability) {
                    MetadataAvailability.Ready -> MaterialTheme.colorScheme.primary
                    is MetadataAvailability.Unavailable -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Text(
                text = "Emitter category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            DetailRow("Emitter category", openSkyCategoryLabel(target.category))
            DetailRow("Category raw", target.category?.toString() ?: "--")
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

internal fun distanceLabelForDetails(usesOwnshipReference: Boolean): String =
    if (usesOwnshipReference) {
        "Distance from ownship"
    } else {
        "Distance (query-center fallback)"
    }

internal fun ownshipReferenceText(usesOwnshipReference: Boolean): String =
    if (usesOwnshipReference) {
        "Available"
    } else {
        "Unavailable (fallback active)"
    }

internal fun proximityTierText(tier: AdsbProximityTier): String = when (tier) {
    AdsbProximityTier.NEUTRAL -> "Neutral"
    AdsbProximityTier.GREEN -> "Green"
    AdsbProximityTier.AMBER -> "Amber"
    AdsbProximityTier.RED -> "Red"
    AdsbProximityTier.EMERGENCY -> "Emergency"
}

internal fun proximityTrendText(target: AdsbSelectedTargetDetails): String = when {
    !target.usesOwnshipReference -> "Unknown (no ownship reference)"
    target.isClosing -> "Closing"
    target.proximityTier == AdsbProximityTier.RED || target.proximityTier == AdsbProximityTier.AMBER ->
        "Recovery dwell active"

    else -> "Not closing"
}

internal fun closingRateText(closingRateMps: Double?): String {
    val rate = closingRateMps?.takeIf { it.isFinite() } ?: return "--"
    return String.format(Locale.US, "%+.1f m/s", rate)
}

internal fun proximityReasonText(target: AdsbSelectedTargetDetails): String = when {
    !target.usesOwnshipReference -> "Neutral fallback while ownship reference is unavailable"
    target.isEmergencyCollisionRisk -> "Emergency geometry and active closing"
    target.isClosing -> "Approaching ownship"
    target.proximityTier == AdsbProximityTier.RED || target.proximityTier == AdsbProximityTier.AMBER ->
        "Holding alert during recovery dwell"

    else -> "Steady or diverging"
}

private fun metadataStatusText(
    availability: MetadataAvailability,
    syncState: MetadataSyncState
): String {
    return when (availability) {
        MetadataAvailability.Ready -> "Metadata loaded"
        MetadataAvailability.SyncInProgress -> "Metadata sync in progress"
        MetadataAvailability.Missing -> when (syncState) {
            MetadataSyncState.Idle -> "Metadata not available"
            MetadataSyncState.Scheduled -> "Metadata sync scheduled"
            MetadataSyncState.Running -> "Metadata sync running"
            is MetadataSyncState.PausedByUser -> "Metadata sync paused"
            is MetadataSyncState.Success ->
                "Metadata not found for this ICAO24 (last sync ${formatWallTime(syncState.lastSuccessWallMs)})"

            is MetadataSyncState.Failed ->
                "Metadata sync failed: ${syncState.reason}"
        }

        is MetadataAvailability.Unavailable -> "Metadata unavailable: ${availability.errorSummary}"
    }
}

private fun formatWallTime(wallMs: Long?): String {
    if (wallMs == null || wallMs <= 0L) {
        return "--"
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return formatter.format(wallMs)
}
