package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
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
fun MapTrafficOgnTabContent(
    ognEnabled: Boolean,
    showSciaEnabled: Boolean,
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
        Text(text = "Show Scia")
        Switch(
            checked = showSciaEnabled,
            onCheckedChange = onShowSciaEnabledChanged
        )
    }
    if (!ognEnabled) {
        Text(
            text = "Enable OGN traffic in General - OGN to manage aircraft trail visibility.",
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
fun MapTrafficMap4ControlsContent(
    adsbTrafficEnabled: Boolean,
    showOgnThermalsEnabled: Boolean,
    onAdsbTrafficEnabledChanged: (Boolean) -> Unit,
    onShowOgnThermalsEnabledChanged: (Boolean) -> Unit
) {
    Text(
        text = "Traffic controls",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TRAFFIC_MAP4_ADSB_SWITCH_TAG)
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
            .testTag(TRAFFIC_MAP4_THERMALS_SWITCH_TAG)
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
    Text(
        text = "These controls replace the traffic FABs for ADS-B and hotspots.",
        style = MaterialTheme.typography.bodySmall
    )
}

private const val TRAFFIC_MAP4_ADSB_SWITCH_TAG = "map4_adsb_switch"
private const val TRAFFIC_MAP4_THERMALS_SWITCH_TAG = "map4_thermals_switch"
