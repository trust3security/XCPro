package com.trust3.xcpro.screens.navdrawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.livesource.DesiredLiveMode
import com.trust3.xcpro.simulator.CondorTransportKind

@Composable
internal fun CondorBridgeLiveModeRow(
    mode: DesiredLiveMode,
    selectedMode: DesiredLiveMode,
    title: String,
    subtitle: String,
    onSelect: (DesiredLiveMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(vertical = 6.dp)
            .testTag(condorBridgeLiveModeTag(mode)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selectedMode == mode,
            onClick = { onSelect(mode) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun CondorBridgeTransportRow(
    kind: CondorTransportKind,
    selectedKind: CondorTransportKind,
    title: String,
    subtitle: String,
    onSelect: (CondorTransportKind) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(kind) }
            .padding(vertical = 6.dp)
            .testTag(condorBridgeTransportTag(kind)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selectedKind == kind,
            onClick = { onSelect(kind) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun CondorBridgeDeviceRow(
    bridge: CondorBondedBridgeRowUiState,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp)
            .testTag(condorBridgeDeviceRowTag(bridge.address)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = bridge.isSelected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = bridge.title,
                style = MaterialTheme.typography.bodyMedium
            )
            bridge.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

internal fun tcpPortValidationError(text: String): String? {
    if (text.isBlank()) return "Enter a TCP port."
    val port = text.toIntOrNull() ?: return "Port must be numeric."
    if (!isValidTcpPort(port)) {
        return "Port must be between 1 and 65535."
    }
    return null
}

internal fun isValidTcpPort(port: Int): Boolean = port in 1..65_535
