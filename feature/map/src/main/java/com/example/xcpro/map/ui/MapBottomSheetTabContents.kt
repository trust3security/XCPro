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
internal fun Map4MapControlsContent(
    showDistanceCircles: Boolean,
    currentQnhLabel: String,
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
    androidx.compose.material3.Button(
        onClick = onOpenQnhDialog,
        modifier = Modifier.testTag(MAP4_QNH_BUTTON_TAG)
    ) {
        Text("Set QNH")
    }
    Text(
        text = "These controls replace the map FABs for QNH and circles.",
        style = MaterialTheme.typography.bodySmall
    )
}

internal const val MAP4_DISTANCE_SWITCH_TAG = "map4_distance_switch"
internal const val MAP4_QNH_BUTTON_TAG = "map4_qnh_button"
