package com.trust3.xcpro.screens.navdrawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
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

@Composable
internal fun CondorTcpListenerSettingsCard(
    localIpAddress: String?,
    tcpIpAddressText: String,
    tcpIpAddressError: String?,
    onTcpIpAddressTextChange: (String) -> Unit,
    tcpPortText: String,
    tcpPortError: String?,
    onTcpPortTextChange: (String) -> Unit,
    fieldsEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Wi-Fi / TCP listener",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Configure the PC bridge endpoint.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Detected IP: ${localIpAddress ?: "Unavailable"}",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = tcpIpAddressText,
                onValueChange = { input ->
                    onTcpIpAddressTextChange(filterTcpIpAddressInput(input))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CONDOR_BRIDGE_TAG_TCP_IP_ADDRESS),
                enabled = fieldsEnabled,
                singleLine = true,
                label = {
                    Text("IP address")
                },
                isError = tcpIpAddressError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    if (tcpIpAddressError != null) {
                        Text(tcpIpAddressError)
                    } else {
                        Text("Example: 192.168.1.2")
                    }
                }
            )
            OutlinedTextField(
                value = tcpPortText,
                onValueChange = { input ->
                    onTcpPortTextChange(input.filter(Char::isDigit).take(5))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CONDOR_BRIDGE_TAG_TCP_PORT),
                enabled = fieldsEnabled,
                singleLine = true,
                label = {
                    Text("Listen port")
                },
                isError = tcpPortError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    if (tcpPortError != null) {
                        Text(tcpPortError)
                    } else {
                        Text("Default compatible port: 4353")
                    }
                }
            )
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

internal fun filterTcpIpAddressInput(input: String): String =
    input.filter { char -> char.isDigit() || char == '.' }.take(15)

internal fun tcpIpAddressValidationError(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null
    val parts = trimmed.split('.')
    if (parts.size != 4) return "Enter a valid IPv4 address."
    return if (parts.all(::isValidIpv4Part)) {
        null
    } else {
        "Enter a valid IPv4 address."
    }
}

private fun isValidIpv4Part(part: String): Boolean =
    part.isNotEmpty() &&
        part.length <= 3 &&
        part.all(Char::isDigit) &&
        part.toIntOrNull()?.let { it in 0..255 } == true
