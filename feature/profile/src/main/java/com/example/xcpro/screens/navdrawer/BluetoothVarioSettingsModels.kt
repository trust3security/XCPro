package com.example.xcpro.screens.navdrawer

internal const val BLUETOOTH_VARIO_TAG_PERMISSION_BUTTON = "bluetooth_vario_permission_button"
internal const val BLUETOOTH_VARIO_TAG_CONNECT_BUTTON = "bluetooth_vario_connect_button"
internal const val BLUETOOTH_VARIO_TAG_DISCONNECT_BUTTON = "bluetooth_vario_disconnect_button"

internal fun bluetoothVarioDeviceRowTag(address: String): String =
    "bluetooth_vario_device_$address"

data class BluetoothBondedDeviceRowUiState(
    val address: String,
    val title: String,
    val subtitle: String?,
    val isSelected: Boolean
)

data class BluetoothVarioSettingsUiState(
    val permissionRequired: Boolean = false,
    val bondedDevices: List<BluetoothBondedDeviceRowUiState> = emptyList(),
    val selectedDeviceLabel: String = "No device selected",
    val selectedDeviceWarningText: String? = null,
    val activeDeviceLabel: String = "No active device",
    val statusText: String = "Disconnected",
    val failureText: String? = null,
    val connectEnabled: Boolean = false,
    val disconnectEnabled: Boolean = false
)
