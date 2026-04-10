package com.example.xcpro.variometer.bluetooth.lxnav.control

import com.example.xcpro.variometer.bluetooth.BluetoothConnectionState
import com.example.xcpro.variometer.bluetooth.BondedBluetoothDevice
import com.example.xcpro.variometer.bluetooth.lxnav.runtime.LxExternalRuntimeSnapshot

internal data class ControlStateInputs(
    val permissionRequired: Boolean,
    val bondedDevices: List<BondedBluetoothDevice>,
    val selectedDevice: PersistedLxBluetoothDevice?,
    val connectionState: BluetoothConnectionState
)

internal fun buildLxBluetoothControlState(
    permissionRequired: Boolean,
    bondedDevices: List<BondedBluetoothDevice>,
    selectedDevice: PersistedLxBluetoothDevice?,
    connectionState: BluetoothConnectionState,
    runtimeSnapshot: LxExternalRuntimeSnapshot,
    reconnectState: LxBluetoothReconnectState,
    reconnectCount: Int,
    lastDisconnectReason: LxBluetoothDisconnectReason?,
    nowMonoMs: Long,
    streamStaleMs: Long
): LxBluetoothControlState {
    val selectedBondedDevice = selectedDevice?.address?.let { address ->
        bondedDevices.firstOrNull { it.address == address }
    }
    val selectedDisplayName =
        selectedBondedDevice?.displayName ?: selectedDevice?.displayNameSnapshot
    val activeDevice = when (connectionState) {
        is BluetoothConnectionState.Connected -> connectionState.device
        is BluetoothConnectionState.Connecting -> connectionState.device
        is BluetoothConnectionState.Error -> connectionState.device
        BluetoothConnectionState.Disconnected -> null
    }
    val lastError = (connectionState as? BluetoothConnectionState.Error)?.error
    val lastReceivedMonoMs = runtimeSnapshot.diagnostics.lastReceivedMonoMs
    val lastReceivedAgeMs =
        lastReceivedMonoMs?.let { (nowMonoMs - it).coerceAtLeast(0L) }
    val streamAlive =
        connectionState is BluetoothConnectionState.Connected &&
            lastReceivedAgeMs != null &&
            lastReceivedAgeMs <= streamStaleMs
    val reconnectActive =
        reconnectState is LxBluetoothReconnectState.Waiting ||
            reconnectState is LxBluetoothReconnectState.Attempting
    val canConnect =
        !permissionRequired &&
            selectedDevice != null &&
            selectedBondedDevice != null &&
            connectionState !is BluetoothConnectionState.Connecting &&
            connectionState !is BluetoothConnectionState.Connected &&
            !reconnectActive
    val canDisconnect =
        reconnectActive || connectionState !is BluetoothConnectionState.Disconnected

    return LxBluetoothControlState(
        permissionRequired = permissionRequired,
        bondedDevices = bondedDevices.map { bondedDevice ->
            BluetoothBondedDeviceItem(
                address = bondedDevice.address,
                displayName = bondedDevice.displayName
            )
        },
        selectedDeviceAddress = selectedDevice?.address,
        selectedDeviceDisplayName = selectedDisplayName,
        selectedDeviceAvailable = selectedDevice != null && selectedBondedDevice != null,
        activeDeviceAddress = activeDevice?.address,
        activeDeviceName = activeDevice?.displayName,
        connectionState = connectionState,
        lastError = lastError,
        lastDisconnectReason = lastDisconnectReason,
        reconnectState = reconnectState,
        reconnectCount = reconnectCount,
        streamAlive = streamAlive,
        lastReceivedMonoMs = lastReceivedMonoMs,
        lastReceivedAgeMs = lastReceivedAgeMs,
        rollingSentenceRatePerSecond = runtimeSnapshot.diagnostics.rollingSentenceRatePerSecond,
        canConnect = canConnect,
        canDisconnect = canDisconnect
    )
}
