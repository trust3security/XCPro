package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AdsbEmergencyAudioSection(
    emergencyAudioMasterEnabled: Boolean,
    onEmergencyAudioMasterEnabledChanged: (Boolean) -> Unit,
    emergencyAudioShadowMode: Boolean,
    onEmergencyAudioShadowModeChanged: (Boolean) -> Unit,
    emergencyFlashEnabled: Boolean,
    onEmergencyFlashEnabledChanged: (Boolean) -> Unit,
    emergencyAudioEnabled: Boolean,
    onEmergencyAudioEnabledChanged: (Boolean) -> Unit,
    emergencyAudioRollbackLatched: Boolean,
    emergencyAudioRollbackReason: String?,
    onClearEmergencyAudioRollback: () -> Unit
) {
    Text(
        text = "Optional one-shot alert for EMERGENCY risk only.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    RolloutToggleRow(
        title = "Master rollout gate",
        subtitle = "Global emergency audio enable.",
        checked = emergencyAudioMasterEnabled,
        onCheckedChanged = onEmergencyAudioMasterEnabledChanged
    )

    Spacer(modifier = Modifier.height(8.dp))

    RolloutToggleRow(
        title = "Shadow mode",
        subtitle = "Track FSM telemetry without sound.",
        checked = emergencyAudioShadowMode,
        onCheckedChanged = onEmergencyAudioShadowModeChanged
    )

    Spacer(modifier = Modifier.height(8.dp))

    RolloutToggleRow(
        title = "Emergency audio alerts",
        subtitle = "Plays only for EMERGENCY risk (never RED).",
        checked = emergencyAudioEnabled,
        onCheckedChanged = onEmergencyAudioEnabledChanged
    )

    Spacer(modifier = Modifier.height(12.dp))

    RolloutToggleRow(
        title = "Emergency icon flash",
        subtitle = "Pulse EMERGENCY markers on the map.",
        checked = emergencyFlashEnabled,
        onCheckedChanged = onEmergencyFlashEnabledChanged
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (emergencyAudioRollbackLatched) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.75f)) {
                Text(
                    text = "Rollback latched",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = emergencyAudioRollbackReason ?: "Auto rollback triggered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClearEmergencyAudioRollback) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun RolloutToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChanged
        )
    }
}
