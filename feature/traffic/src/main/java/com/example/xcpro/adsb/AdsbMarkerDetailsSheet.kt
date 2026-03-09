package com.example.xcpro.adsb

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ADSB_DETAILS_SHEET_SCROLL_TAG),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = target.callsign?.takeIf { it.isNotBlank() } ?: target.id.raw.uppercase(Locale.US),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Text(
                    text = "Live state",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item { DetailRow("ICAO24", target.id.raw.uppercase(Locale.US)) }
            item {
                DetailRow(
                    "Altitude",
                    target.altitudeM?.let { UnitsFormatter.altitude(AltitudeM(it), unitsPreferences).text }
                        ?: "--"
                )
            }
            item {
                DetailRow(
                    distanceLabelForDetails(target.usesOwnshipReference),
                    UnitsFormatter.distance(DistanceM(target.distanceMeters), unitsPreferences).text
                )
            }
            item { DetailRow("Ownship reference", ownshipReferenceText(target.usesOwnshipReference)) }
            item { DetailRow("Proximity tier", proximityTierText(target.proximityTier)) }
            item { DetailRow("Trend", proximityTrendText(target)) }
            item { DetailRow("Range rate (+ closing)", closingRateText(target.closingRateMps)) }
            item { DetailRow("Proximity reason", proximityReasonText(target)) }
            item { DetailRow("Emergency rule source", emergencyRuleSourceText(target)) }
            item { DetailRow("Emergency audio eligible", emergencyAudioEligibilityText(target)) }
            item { DetailRow("Emergency audio reason", emergencyAudioIneligibilityText(target)) }
            item {
                DetailRow(
                    "Speed",
                    target.speedMps?.let { UnitsFormatter.speed(SpeedMs(it), unitsPreferences).text } ?: "--"
                )
            }
            item { DetailRow("Track", target.trackDeg?.let { "${it.roundToOneDecimal()}\u00B0" } ?: "--") }
            item {
                DetailRow(
                    "Vertical Rate",
                    AdsbDetailsFormatter.formatVerticalRate(target.climbMps, unitsPreferences)
                )
            }
            item { DetailRow("Age", "${target.ageSec}s") }

            item {
                Text(
                    text = "Aircraft identification",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item { DetailRow("Registration", target.registration ?: "--") }
            item { DetailRow("Typecode", target.typecode ?: "--") }
            item { DetailRow("Model", target.model ?: "--") }
            item { DetailRow("Manufacturer", target.manufacturerName ?: "--") }
            item { DetailRow("Operator", target.operator ?: "--") }
            item { DetailRow("Operator callsign", target.operatorCallsign ?: "--") }
            item { DetailRow("ICAO aircraft type", target.icaoAircraftType ?: "--") }
            item { DetailRow("Owner", target.owner ?: "--") }
            item {
                Text(
                    text = metadataStatusText(target.metadataAvailability, target.metadataSyncState),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (target.metadataAvailability) {
                        MetadataAvailability.Ready -> MaterialTheme.colorScheme.primary
                        is MetadataAvailability.Unavailable -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            item {
                Text(
                    text = "Emitter category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item { DetailRow("Emitter category", openSkyCategoryLabel(target.category)) }
            item { DetailRow("Category raw", target.category?.toString() ?: "--") }
            item {
                Text(
                    text = "Informational only. Not for collision avoidance or separation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
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

internal const val ADSB_DETAILS_SHEET_SCROLL_TAG = "adsb_details_sheet_scroll"

internal fun proximityTrendText(target: AdsbSelectedTargetDetails): String = when {
    !target.usesOwnshipReference -> "Unknown (no ownship reference)"
    target.isClosing -> "Closing"
    target.proximityReason == AdsbProximityReason.RECOVERY_DWELL ->
        "Recovery dwell active"

    else -> "Not closing"
}

internal fun closingRateText(closingRateMps: Double?): String {
    val rate = closingRateMps?.takeIf { it.isFinite() } ?: return "--"
    return String.format(Locale.US, "%+.1f m/s", rate)
}

internal fun proximityReasonText(target: AdsbSelectedTargetDetails): String = when {
    target.proximityReason == AdsbProximityReason.NO_OWNSHIP_REFERENCE ->
        "Neutral fallback while ownship reference is unavailable"
    target.proximityReason == AdsbProximityReason.CIRCLING_RULE_APPLIED ->
        "Circling emergency rule applied (1 km + vertical cap + closing)"
    target.proximityReason == AdsbProximityReason.GEOMETRY_EMERGENCY_APPLIED ->
        "Emergency geometry and active closing"
    target.proximityReason == AdsbProximityReason.APPROACH_CLOSING ->
        "Approaching ownship"
    target.proximityReason == AdsbProximityReason.RECOVERY_DWELL ->
        "Holding alert during recovery dwell"
    else -> "Steady or diverging"
}

internal fun emergencyRuleSourceText(target: AdsbSelectedTargetDetails): String = when {
    target.isCirclingEmergencyRedRule -> "Circling emergency RED rule"
    target.isEmergencyCollisionRisk -> "Geometry emergency rule"
    else -> "No emergency rule active"
}

internal fun emergencyAudioEligibilityText(target: AdsbSelectedTargetDetails): String =
    if (target.isEmergencyAudioEligible) {
        "Eligible"
    } else {
        "Not eligible"
    }

internal fun emergencyAudioIneligibilityText(target: AdsbSelectedTargetDetails): String {
    if (target.isEmergencyAudioEligible) return "Eligible"
    return when (target.emergencyAudioIneligibilityReason) {
        AdsbEmergencyAudioIneligibilityReason.NO_OWNSHIP_REFERENCE ->
            "No ownship reference"
        AdsbEmergencyAudioIneligibilityReason.NOT_CLOSING ->
            "Not closing"
        AdsbEmergencyAudioIneligibilityReason.TREND_STALE_WAITING_FOR_FRESH_SAMPLE ->
            "Trend stale, waiting for fresh sample"
        AdsbEmergencyAudioIneligibilityReason.STALE_TARGET_SAMPLE ->
            "Target sample stale"
        AdsbEmergencyAudioIneligibilityReason.DISTANCE_OUTSIDE_EMERGENCY_RANGE ->
            "Outside emergency range"
        AdsbEmergencyAudioIneligibilityReason.RELATIVE_ALTITUDE_UNAVAILABLE ->
            "Relative altitude unavailable"
        AdsbEmergencyAudioIneligibilityReason.OUTSIDE_VERTICAL_GATE ->
            "Outside vertical gate"
        AdsbEmergencyAudioIneligibilityReason.TARGET_TRACK_UNAVAILABLE ->
            "Target track unavailable"
        AdsbEmergencyAudioIneligibilityReason.HEADING_GATE_FAILED ->
            "Heading gate failed"
        AdsbEmergencyAudioIneligibilityReason.MOTION_CONFIDENCE_LOW ->
            "Motion confidence low"
        AdsbEmergencyAudioIneligibilityReason.PROJECTED_CONFLICT_NOT_LIKELY ->
            "Projected conflict not likely"
        AdsbEmergencyAudioIneligibilityReason.LOW_MOTION_SPEED ->
            "Low motion speed"
        AdsbEmergencyAudioIneligibilityReason.VERTICAL_NON_THREAT ->
            "Large vertical separation (non-threat)"
        null -> "Unknown"
    }
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
