package com.trust3.xcpro.bluetooth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only Bluetooth transport seam for bonded-device input.
 *
 * Contract rules:
 * - no auto reconnect
 * - no write path
 * - only one open session at a time
 */
interface BluetoothTransport {
    suspend fun listBondedDevices(): List<BondedBluetoothDevice>

    val connectionState: StateFlow<BluetoothConnectionState>

    fun open(device: BondedBluetoothDevice): Flow<BluetoothReadChunk>

    suspend fun close()
}
