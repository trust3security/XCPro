package com.example.xcpro.ogn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.adsb.AdsbDetailsFormatter
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OgnMarkerDetailsSheet(
    target: OgnTrafficTarget,
    unitsPreferences: UnitsPreferences,
    onDismiss: () -> Unit
) {
    val competitionId = target.identity?.competitionNumber?.takeIf { it.isNotBlank() }
        ?: target.displayLabel.ifBlank { target.callsign }
    val aircraftModel = target.identity?.aircraftModel?.takeIf { it.isNotBlank() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = competitionId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (aircraftModel != null) {
                    Text(
                        text = aircraftModel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Flight state",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            DetailRow("Altitude", formatAltitude(target.altitudeMeters, unitsPreferences))
            DetailRow("Distance", formatDistance(target.distanceMeters, unitsPreferences))
            DetailRow("Speed", formatSpeed(target.groundSpeedMps, unitsPreferences))
            DetailRow(
                "Vertical Rate",
                AdsbDetailsFormatter.formatVerticalRate(target.verticalSpeedMps, unitsPreferences)
            )
            DetailRow("Track", formatTrack(target.trackDegrees))

            Text(
                text = "Identity",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            DetailRow("Target ID", target.id)
            DetailRow("Callsign", target.callsign.ifBlank { "--" })
            DetailRow("Destination", target.destination.ifBlank { "--" })
            DetailRow("Device ID (HEX)", target.deviceIdHex ?: "--")
            DetailRow("Registration", target.identity?.registration ?: "--")
            DetailRow("Competition ID", target.identity?.competitionNumber ?: "--")
            DetailRow("Aircraft model", target.identity?.aircraftModel ?: "--")
            DetailRow("Aircraft type code", target.identity?.aircraftTypeCode?.toString() ?: "--")
            DetailRow("Tracked", formatBoolean(target.identity?.tracked))
            DetailRow("Identified", formatBoolean(target.identity?.identified))

            Text(
                text = "Raw OGN payload",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            DetailRow("Comment", sanitizeOgnRawText(target.rawComment, RAW_COMMENT_MAX_LEN))
            DetailRow("Line", sanitizeOgnRawText(target.rawLine, RAW_LINE_MAX_LEN))

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
    altitudeMeters?.let { UnitsFormatter.altitude(AltitudeM(it), unitsPreferences).text } ?: "--"

private fun formatSpeed(speedMps: Double?, unitsPreferences: UnitsPreferences): String =
    speedMps?.let { UnitsFormatter.speed(SpeedMs(it), unitsPreferences).text } ?: "--"

private fun formatDistance(distanceMeters: Double?, unitsPreferences: UnitsPreferences): String =
    distanceMeters
        ?.takeIf { it.isFinite() }
        ?.let { UnitsFormatter.distance(DistanceM(it), unitsPreferences).text }
        ?: "--"

private fun formatTrack(trackDegrees: Double?): String =
    trackDegrees?.let { String.format(Locale.US, "%.1f\u00B0", it) } ?: "--"

private fun formatBoolean(value: Boolean?): String = when (value) {
    true -> "Yes"
    false -> "No"
    null -> "--"
}

internal fun sanitizeOgnRawText(raw: String?, maxLen: Int): String {
    val normalized = raw?.trim().orEmpty()
    if (normalized.isEmpty()) return "--"

    val output = StringBuilder(normalized.length.coerceAtMost(maxLen) + 3)
    normalized.forEach { char ->
        if (output.length >= maxLen) return@forEach
        val safeChar = when {
            char == '\n' || char == '\r' || char == '\t' -> ' '
            char.code in 32..126 -> char
            else -> '?'
        }
        output.append(safeChar)
    }

    return if (normalized.length > maxLen) {
        output.append("...").toString()
    } else {
        output.toString()
    }
}

private const val RAW_COMMENT_MAX_LEN = 220
private const val RAW_LINE_MAX_LEN = 320
