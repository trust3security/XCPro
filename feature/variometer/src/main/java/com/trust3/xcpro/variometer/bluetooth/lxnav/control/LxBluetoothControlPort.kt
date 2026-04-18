package com.trust3.xcpro.variometer.bluetooth.lxnav.control

import kotlinx.coroutines.flow.StateFlow

interface LxBluetoothControlPort {
    val state: StateFlow<LxBluetoothControlState>

    suspend fun refresh()

    suspend fun selectDevice(address: String)

    suspend fun connectSelected()

    suspend fun disconnect()

    suspend fun onPermissionResult(granted: Boolean)
}
