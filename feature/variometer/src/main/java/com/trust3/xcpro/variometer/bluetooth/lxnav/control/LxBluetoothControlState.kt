package com.trust3.xcpro.variometer.bluetooth.lxnav.control

import com.trust3.xcpro.bluetooth.BluetoothConnectionError
import com.trust3.xcpro.bluetooth.BluetoothConnectionState

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
    val lastDisconnectReason: LxBluetoothDisconnectReason? = null,
    val reconnectState: LxBluetoothReconnectState = LxBluetoothReconnectState.Idle,
    val reconnectCount: Int = 0,
    val streamAlive: Boolean = false,
    val lastReceivedMonoMs: Long? = null,
    val lastReceivedAgeMs: Long? = null,
    val rollingSentenceRatePerSecond: Double = 0.0,
    val canConnect: Boolean = false,
    val canDisconnect: Boolean = false
)

