package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
internal fun WeGlideSettingsContent(
    uiState: WeGlideSettingsUiState,
    onSetAutoUploadFinishedFlights: (Boolean) -> Unit,
    onSetUploadOnWifiOnly: (Boolean) -> Unit,
    onSetRetryOnMobileData: (Boolean) -> Unit,
    onSetShowCompletionNotification: (Boolean) -> Unit,
    onSetDebugEnabled: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshAircraft: () -> Unit,
    onSetProfileAircraftMapping: (String, Long) -> Unit,
    onClearProfileAircraftMapping: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pickerProfileId by remember { mutableStateOf<String?>(null) }
    var aircraftSearchQuery by remember { mutableStateOf("") }
    val pickerProfile = uiState.profileMappings.firstOrNull { item -> item.profileId == pickerProfileId }
    val filteredAircraft = uiState.aircraftOptions.filter { item ->
        if (aircraftSearchQuery.isBlank()) {
            true
        } else {
            val query = aircraftSearchQuery.trim()
            item.name.contains(query, ignoreCase = true) ||
                (item.secondaryLabel?.contains(query, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WeGlideSectionCard {
            Text("WeGlide", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (uiState.isConnected) "Connected account" else "Connection pending",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.accountDisplayName ?: "Not connected",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            uiState.accountEmail?.let { email ->
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = uiState.authModeLabel ?: "OAuth connection lands in the next slice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.isConnected) {
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = uiState.oauthConfigured
                ) {
                    Text("Connect WeGlide")
                }
            }
        }

        WeGlideSectionCard {
            Text("Upload behavior", style = MaterialTheme.typography.titleMedium)
            PreferenceSwitchRow(
                label = "Auto-upload finished flights",
                checked = uiState.autoUploadFinishedFlights,
                onCheckedChange = onSetAutoUploadFinishedFlights
            )
            PreferenceSwitchRow(
                label = "Upload on Wi-Fi only",
                checked = uiState.uploadOnWifiOnly,
                onCheckedChange = onSetUploadOnWifiOnly
            )
            PreferenceSwitchRow(
                label = "Retry on mobile data",
                checked = uiState.retryOnMobileData,
                onCheckedChange = onSetRetryOnMobileData
            )
            PreferenceSwitchRow(
                label = "Show completion notifications",
                checked = uiState.showCompletionNotification,
                onCheckedChange = onSetShowCompletionNotification
            )
            PreferenceSwitchRow(
                label = "Advanced debug",
                checked = uiState.debugEnabled,
                onCheckedChange = onSetDebugEnabled
            )
        }

        WeGlideSectionCard {
            Text("Aircraft mapping", style = MaterialTheme.typography.titleMedium)
            Text(
                "This is now driven by the new WeGlide state module. Aircraft sync and picker UI land next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRefreshAircraft,
                    enabled = uiState.isConnected && !uiState.isAircraftSyncInProgress,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.isAircraftSyncInProgress) "Syncing..." else "Refresh aircraft")
                }
                Text(
                    text = uiState.lastAircraftSyncMessage ?: "No aircraft sync yet",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.profileMappings.isEmpty()) {
                Text(
                    "No XCPro profiles available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uiState.profileMappings.forEach { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (item.isActive) "${item.profileName} (active)" else item.profileName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Local aircraft: ${item.localAircraftLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "WeGlide: ${item.remoteAircraftLabel}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    pickerProfileId = item.profileId
                                    aircraftSearchQuery = ""
                                },
                                enabled = uiState.aircraftOptions.isNotEmpty()
                            ) {
                                Text("Choose")
                            }
                            TextButton(
                                onClick = { onClearProfileAircraftMapping(item.profileId) },
                                enabled = item.remoteAircraftId != null
                            ) {
                                Text("Clear")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        WeGlideSectionCard {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            Text("Cached aircraft: ${uiState.cachedAircraftCount}")
            Text("Pending uploads: ${uiState.pendingQueueCount}")
            Text("Uploaded items: ${uiState.uploadedCount}")
            Text(
                text = uiState.lastFailureMessage ?: "No recent upload failure recorded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (pickerProfile != null) {
        AlertDialog(
            onDismissRequest = { pickerProfileId = null },
            title = { Text("Select WeGlide aircraft") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Profile: ${pickerProfile.profileName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = aircraftSearchQuery,
                        onValueChange = { value -> aircraftSearchQuery = value },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search aircraft") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None
                        )
                    )
                    LazyColumn(
                        modifier = Modifier.height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredAircraft) { aircraft ->
                            TextButton(
                                onClick = {
                                    onSetProfileAircraftMapping(
                                        pickerProfile.profileId,
                                        aircraft.aircraftId
                                    )
                                    pickerProfileId = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        aircraft.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    aircraft.secondaryLabel?.let { secondary ->
                                        Text(
                                            secondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerProfileId = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PreferenceSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WeGlideSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}
