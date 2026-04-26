package com.trust3.xcpro.screens.navdrawer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.trust3.xcpro.livesource.DesiredLiveMode
import com.trust3.xcpro.simulator.CondorTransportKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CondorBridgeSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: CondorBridgeSettingsViewModel = hiltViewModel(),
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refresh()
    }
    val navigateUpAction: () -> Unit = onNavigateUp ?: {
        navController.navigateUp()
        Unit
    }
    val secondaryNavigateAction: () -> Unit = onSecondaryNavigate ?: {
        scope.launch {
            navController.popBackStack("map", inclusive = false)
            drawerState.open()
        }
        Unit
    }
    val navigateToMapAction: () -> Unit = onNavigateToMap ?: {
        scope.launch {
            drawerState.close()
            navController.popBackStack("map", inclusive = false)
        }
        Unit
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "Bridge",
                onNavigateUp = navigateUpAction,
                onSecondaryNavigate = secondaryNavigateAction,
                onNavigateToMap = navigateToMapAction
            )
        }
    ) { padding ->
        CondorBridgeSettingsContent(
            uiState = uiState,
            onRequestPermission = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    viewModel.refresh()
                } else {
                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            },
            onSelectTransport = viewModel::selectTransport,
            onUpdateTcpListenPort = viewModel::updateTcpListenPort,
            onUpdateTcpIpAddress = viewModel::updateTcpIpAddress,
            onSelectBridge = viewModel::selectBridge,
            onSelectLiveMode = viewModel::setDesiredLiveMode,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
            onClearSelection = viewModel::clearSelectedBridge,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

@Composable
internal fun CondorBridgeSettingsContent(
    uiState: CondorBridgeSettingsUiState,
    onRequestPermission: () -> Unit,
    onSelectTransport: (CondorTransportKind) -> Unit,
    onUpdateTcpListenPort: (Int) -> Unit,
    onUpdateTcpIpAddress: (String?) -> Unit,
    onSelectBridge: (String) -> Unit,
    onSelectLiveMode: (DesiredLiveMode) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tcpPortText by rememberSaveable(uiState.selectedTransport, uiState.tcpListenPort) {
        mutableStateOf(uiState.tcpListenPort.toString())
    }
    var tcpIpAddressText by rememberSaveable(uiState.selectedTransport, uiState.tcpIpAddress) {
        mutableStateOf(uiState.tcpIpAddress.orEmpty())
    }
    val tcpPortError =
        if (uiState.selectedTransport == CondorTransportKind.TCP_LISTENER) {
            tcpPortValidationError(tcpPortText)
        } else {
            null
        }
    val tcpIpAddressError =
        if (uiState.selectedTransport == CondorTransportKind.TCP_LISTENER) {
            tcpIpAddressValidationError(tcpIpAddressText)
        } else {
            null
        }
    val tcpInputsValid = tcpPortError == null && tcpIpAddressError == null
    val connectEnabled =
        uiState.connectEnabled &&
            (uiState.selectedTransport != CondorTransportKind.TCP_LISTENER || tcpInputsValid)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    text = "Live source",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Choose which live source XCPro uses.",
                    style = MaterialTheme.typography.bodyMedium
                )
                CondorBridgeLiveModeRow(
                    mode = DesiredLiveMode.PHONE_ONLY,
                    selectedMode = uiState.desiredLiveMode,
                    title = "Phone only",
                    subtitle = "Use Android live sensors and GPS.",
                    onSelect = onSelectLiveMode
                )
                CondorBridgeLiveModeRow(
                    mode = DesiredLiveMode.CONDOR2_FULL,
                    selectedMode = uiState.desiredLiveMode,
                    title = "Condor 2",
                    subtitle = "Use the Condor bridge without falling back to phone GPS.",
                    onSelect = onSelectLiveMode
                )
            }
        }

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
                    text = "Connection transport",
                    style = MaterialTheme.typography.titleSmall
                )
                CondorBridgeTransportRow(
                    kind = CondorTransportKind.BLUETOOTH,
                    selectedKind = uiState.selectedTransport,
                    title = "Bluetooth",
                    subtitle = "Use the paired Condor bridge device.",
                    onSelect = onSelectTransport
                )
                CondorBridgeTransportRow(
                    kind = CondorTransportKind.TCP_LISTENER,
                    selectedKind = uiState.selectedTransport,
                    title = "Wi-Fi / TCP",
                    subtitle = "Listen on this device and let the PC bridge connect in.",
                    onSelect = onSelectTransport
                )
            }
        }

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
                    text = "Connection status",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Status: ${uiState.statusText}",
                    style = MaterialTheme.typography.bodyMedium
                )
                uiState.healthText?.let { healthText ->
                    Text(
                        text = healthText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                uiState.reconnectText?.let { reconnectText ->
                    Text(
                        text = reconnectText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = "Selected: ${uiState.selectedEndpointLabel}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Active: ${uiState.activeEndpointLabel}",
                    style = MaterialTheme.typography.bodyMedium
                )
                uiState.selectedBridgeWarningText?.let { warningText ->
                    Text(
                        text = warningText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                uiState.failureText?.let { failureText ->
                    Text(
                        text = failureText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        when (uiState.selectedTransport) {
            CondorTransportKind.BLUETOOTH -> {
                if (uiState.bluetoothPermissionRequired) {
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
                                text = "Bluetooth permission",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Grant Bluetooth permission to list bonded bridges and connect.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = onRequestPermission,
                                modifier = Modifier.testTag(CONDOR_BRIDGE_TAG_PERMISSION_BUTTON)
                            ) {
                                Text("Grant Bluetooth permission")
                            }
                        }
                    }
                } else {
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
                                text = "Bonded bridges",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (uiState.bondedBridges.isEmpty()) {
                                Text(
                                    text = "No bonded bridges found.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                uiState.bondedBridges.forEach { bridge ->
                                    CondorBridgeDeviceRow(
                                        bridge = bridge,
                                        onSelect = { onSelectBridge(bridge.address) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CondorTransportKind.TCP_LISTENER -> {
                CondorTcpListenerSettingsCard(
                    localIpAddress = uiState.tcpLocalIpAddress,
                    tcpIpAddressText = tcpIpAddressText,
                    tcpIpAddressError = tcpIpAddressError,
                    onTcpIpAddressTextChange = { filtered ->
                        tcpIpAddressText = filtered
                        if (tcpIpAddressValidationError(filtered) == null) {
                            onUpdateTcpIpAddress(filtered.ifBlank { null })
                        }
                    },
                    tcpPortText = tcpPortText,
                    tcpPortError = tcpPortError,
                    onTcpPortTextChange = { filtered ->
                        tcpPortText = filtered
                        filtered.toIntOrNull()
                            ?.takeIf(::isValidTcpPort)
                            ?.let(onUpdateTcpListenPort)
                    },
                    fieldsEnabled = uiState.disconnectEnabled.not()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (uiState.selectedTransport == CondorTransportKind.TCP_LISTENER) {
                                if (tcpIpAddressValidationError(tcpIpAddressText) == null) {
                                    onUpdateTcpIpAddress(tcpIpAddressText.ifBlank { null })
                                }
                                tcpPortText.toIntOrNull()
                                    ?.takeIf(::isValidTcpPort)
                                    ?.let(onUpdateTcpListenPort)
                            }
                            onConnect()
                        },
                        enabled = connectEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(CONDOR_BRIDGE_TAG_CONNECT_BUTTON)
                    ) {
                        Text("Connect")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        enabled = uiState.disconnectEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(CONDOR_BRIDGE_TAG_DISCONNECT_BUTTON)
                    ) {
                        Text("Disconnect")
                    }
                }
                OutlinedButton(
                    onClick = onClearSelection,
                    enabled = uiState.clearSelectionEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CONDOR_BRIDGE_TAG_CLEAR_BUTTON)
                ) {
                    Text("Clear saved Bluetooth bridge")
                }
            }
        }
    }
}
