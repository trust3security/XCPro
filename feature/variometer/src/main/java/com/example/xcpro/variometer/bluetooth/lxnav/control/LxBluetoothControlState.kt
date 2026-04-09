package com.example.xcpro.variometer.bluetooth.lxnav.control

import com.example.xcpro.variometer.bluetooth.BluetoothConnectionError
import com.example.xcpro.variometer.bluetooth.BluetoothConnectionState

data class BluetoothBondedDeviceItem(
    val address: String,
    val displayName: String?
)

data class LxBluetoothControlState(
    val permissionRequired: Boolean = false,
    val bondedDevices: List<BluetoothBondedDeviceItem> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val selectedDeviceDisplayName: String? = null,
    val selectedDeviceAvailable: Boolean = false,
    val activeDeviceAddress: String? = null,
    val activeDeviceName: String? = null,
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.Disconnected,
    val lastError: BluetoothConnectionError? = null,
    val canConnect: Boolean = false,
    val canDisconnect: Boolean = false
)
