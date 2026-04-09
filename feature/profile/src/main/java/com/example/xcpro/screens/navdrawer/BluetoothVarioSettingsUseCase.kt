package com.example.xcpro.screens.navdrawer

import com.example.xcpro.variometer.bluetooth.BluetoothConnectionError
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionState
import com.example.xcpro.variometer.bluetooth.lxnav.control.BluetoothBondedDeviceItem
import com.example.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothControlPort
import com.example.xcpro.variometer.bluetooth.lxnav.control.LxBluetoothControlState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BluetoothVarioSettingsUseCase @Inject constructor(
    private val controlPort: LxBluetoothControlPort
) {
    val uiState: Flow<BluetoothVarioSettingsUiState> =
        controlPort.state.map(::mapControlState)

    suspend fun refresh() {
        controlPort.refresh()
    }

    suspend fun selectDevice(address: String) {
        controlPort.selectDevice(address)
    }

    suspend fun connect() {
        controlPort.connectSelected()
    }

    suspend fun disconnect() {
        controlPort.disconnect()
    }

    suspend fun onPermissionResult(granted: Boolean) {
        controlPort.onPermissionResult(granted)
    }

    private fun mapControlState(
        state: LxBluetoothControlState
    ): BluetoothVarioSettingsUiState {
        return BluetoothVarioSettingsUiState(
            permissionRequired = state.permissionRequired,
            bondedDevices = state.bondedDevices.map { bondedDevice ->
                bondedDevice.toUiState(selectedAddress = state.selectedDeviceAddress)
            },
            selectedDeviceLabel = formatDeviceLabel(
                address = state.selectedDeviceAddress,
                displayName = state.selectedDeviceDisplayName,
                emptyLabel = "No device selected"
            ),
            selectedDeviceWarningText = when {
                state.selectedDeviceAddress == null -> null
                !state.selectedDeviceAvailable -> "Selected device is not currently bonded."
                else -> null
            },
            activeDeviceLabel = formatDeviceLabel(
                address = state.activeDeviceAddress,
                displayName = state.activeDeviceName,
                emptyLabel = "No active device"
            ),
            statusText = state.toStatusText(),
            failureText = state.lastError?.toFailureText(),
            connectEnabled = state.canConnect,
            disconnectEnabled = state.canDisconnect
        )
    }

    private fun BluetoothBondedDeviceItem.toUiState(
        selectedAddress: String?
    ): BluetoothBondedDeviceRowUiState =
        BluetoothBondedDeviceRowUiState(
            address = address,
            title = displayName ?: address,
            subtitle = if (displayName == null) null else address,
            isSelected = address == selectedAddress
        )

    private fun LxBluetoothControlState.toStatusText(): String {
        if (permissionRequired) return "Bluetooth permission required"
        return when (connectionState) {
            is BluetoothConnectionState.Connecting -> "Connecting"
            is BluetoothConnectionState.Connected -> "Connected"
            is BluetoothConnectionState.Error -> "Connection failed"
            BluetoothConnectionState.Disconnected -> when {
                selectedDeviceAddress != null && !selectedDeviceAvailable ->
                    "Selected device unavailable"
                bondedDevices.isEmpty() -> "No bonded devices"
                else -> "Disconnected"
            }
        }
    }

    private fun BluetoothConnectionError.toFailureText(): String =
        when (this) {
            BluetoothConnectionError.PERMISSION_REQUIRED -> "Bluetooth permission is required."
            BluetoothConnectionError.DEVICE_NOT_BONDED -> "Selected device is not bonded."
            BluetoothConnectionError.ALREADY_OPEN -> "A Bluetooth session is already open."
            BluetoothConnectionError.CONNECT_FAILED -> "Could not connect to the selected device."
            BluetoothConnectionError.STREAM_CLOSED -> "Bluetooth stream closed."
            BluetoothConnectionError.READ_FAILED -> "Bluetooth read failed."
            BluetoothConnectionError.CANCELLED -> "Bluetooth session cancelled."
        }

    private fun formatDeviceLabel(
        address: String?,
        displayName: String?,
        emptyLabel: String
    ): String {
        if (address == null) return emptyLabel
        return when {
            displayName.isNullOrBlank() -> address
            else -> "$displayName ($address)"
        }
    }
}
