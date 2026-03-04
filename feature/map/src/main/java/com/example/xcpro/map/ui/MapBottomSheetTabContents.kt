package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun OgnTabContent(
    ognEnabled: Boolean,
    showSciaEnabled: Boolean,
    onOgnEnabledChanged: (Boolean) -> Unit,
    onShowSciaEnabledChanged: (Boolean) -> Unit,
    aircraftRows: List<OgnTrailAircraftRowUi>,
    onAircraftTrailToggled: (String, Boolean) -> Unit
) {
    Text(
        text = "Scia (trail/wake)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "OGN Traffic")
        Switch(
            checked = ognEnabled,
            onCheckedChange = onOgnEnabledChanged,
            enabled = !showSciaEnabled
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Show Scia")
        Switch(
            checked = showSciaEnabled,
            onCheckedChange = onShowSciaEnabledChanged
        )
    }
    if (!ognEnabled) {
        Text(
            text = "Enable OGN traffic to manage aircraft trail visibility.",
            style = MaterialTheme.typography.bodySmall
        )
    } else if (!showSciaEnabled) {
        Text(
            text = "Enable Show Scia to display OGN trails/wake.",
            style = MaterialTheme.typography.bodySmall
        )
    } else if (aircraftRows.isEmpty()) {
        Text(
            text = "No OGN aircraft currently available.",
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        Text(
            text = "Aircraft trail visibility",
            style = MaterialTheme.typography.labelLarge
        )
        aircraftRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = row.trailsEnabled,
                    onCheckedChange = { enabled ->
                        onAircraftTrailToggled(row.key, enabled)
                    },
                    enabled = ognEnabled
                )
            }
        }
    }
}

@Composable
internal fun Map4ControlsContent(
    adsbTrafficEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    showDistanceCircles: Boolean,
    currentQnhLabel: String,
    onAdsbTrafficEnabledChanged: (Boolean) -> Unit,
    onShowOgnThermalsEnabledChanged: (Boolean) -> Unit,
    onShowDistanceCirclesChanged: (Boolean) -> Unit,
    onOpenQnhDialog: () -> Unit
) {
    Text(
        text = "Map controls",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MAP4_ADSB_SWITCH_TAG)
            .toggleable(
                value = adsbTrafficEnabled,
                role = Role.Switch,
                onValueChange = onAdsbTrafficEnabledChanged
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "ADS-B traffic")
        Switch(
            checked = adsbTrafficEnabled,
            onCheckedChange = null
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MAP4_THERMALS_SWITCH_TAG)
            .toggleable(
                value = showOgnThermalsEnabled,
                role = Role.Switch,
                onValueChange = onShowOgnThermalsEnabledChanged
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Hotspots (TH)")
        Switch(
            checked = showOgnThermalsEnabled,
            onCheckedChange = null
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MAP4_DISTANCE_SWITCH_TAG)
            .toggleable(
                value = showDistanceCircles,
                role = Role.Switch,
                onValueChange = onShowDistanceCirclesChanged
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Distance circles")
        Switch(
            checked = showDistanceCircles,
            onCheckedChange = null
        )
    }
    Text(
        text = "QNH $currentQnhLabel",
        style = MaterialTheme.typography.bodyMedium
    )
    Button(
        onClick = onOpenQnhDialog,
        modifier = Modifier.testTag(MAP4_QNH_BUTTON_TAG)
    ) {
        Text("Set QNH")
    }
    Text(
        text = "These controls replace the map FABs for ADS-B, QNH, Hotspots and circles.",
        style = MaterialTheme.typography.bodySmall
    )
}

internal const val MAP4_ADSB_SWITCH_TAG = "map4_adsb_switch"
internal const val MAP4_THERMALS_SWITCH_TAG = "map4_thermals_switch"
internal const val MAP4_DISTANCE_SWITCH_TAG = "map4_distance_switch"
internal const val MAP4_QNH_BUTTON_TAG = "map4_qnh_button"
