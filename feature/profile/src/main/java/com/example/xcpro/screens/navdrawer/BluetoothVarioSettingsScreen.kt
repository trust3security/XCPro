package com.example.xcpro.screens.navdrawer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothVarioSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: BluetoothVarioSettingsViewModel = hiltViewModel(),
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
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
                title = "Bluetooth Vario",
                onNavigateUp = navigateUpAction,
                onSecondaryNavigate = secondaryNavigateAction,
                onNavigateToMap = navigateToMapAction
            )
        }
    ) { padding ->
        BluetoothVarioSettingsContent(
            uiState = uiState,
            onRequestPermission = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    viewModel.onPermissionResult(true)
                } else {
                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            },
            onSelectDevice = viewModel::selectDevice,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

@Composable
internal fun BluetoothVarioSettingsContent(
    uiState: BluetoothVarioSettingsUiState,
    onRequestPermission: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    text = "Selected: ${uiState.selectedDeviceLabel}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Active: ${uiState.activeDeviceLabel}",
                    style = MaterialTheme.typography.bodyMedium
                )
                uiState.selectedDeviceWarningText?.let { warningText ->
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

        if (uiState.permissionRequired) {
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
                        text = "Grant Bluetooth permission to list bonded devices and connect.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.testTag(BLUETOOTH_VARIO_TAG_PERMISSION_BUTTON)
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
                        text = "Bonded devices",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (uiState.bondedDevices.isEmpty()) {
                        Text(
                            text = "No bonded devices found.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        uiState.bondedDevices.forEach { bondedDevice ->
                            BluetoothVarioDeviceRow(
                                bondedDevice = bondedDevice,
                                onSelect = { onSelectDevice(bondedDevice.address) }
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onConnect,
                    enabled = uiState.connectEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(BLUETOOTH_VARIO_TAG_CONNECT_BUTTON)
                ) {
                    Text("Connect")
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = uiState.disconnectEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(BLUETOOTH_VARIO_TAG_DISCONNECT_BUTTON)
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
private fun BluetoothVarioDeviceRow(
    bondedDevice: BluetoothBondedDeviceRowUiState,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp)
            .testTag(bluetoothVarioDeviceRowTag(bondedDevice.address)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = bondedDevice.isSelected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = bondedDevice.title,
                style = MaterialTheme.typography.bodyMedium
            )
            bondedDevice.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
