package com.trust3.xcpro.variometer.bluetooth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only Bluetooth transport seam for bonded-device input.
 *
 * Phase 1 owns only the contract. Concrete Android Bluetooth transport
 * implementation and DI binding are deferred to a later phase.
 */
interface BluetoothTransport {
    suspend fun listBondedDevices(): List<BondedBluetoothDevice>

    val connectionState: StateFlow<BluetoothConnectionState>

    /**
     * Opens a single read session for the provided bonded device.
     *
     * Contract rules:
     * - no auto reconnect
     * - no write path
     * - only one open session at a time
     */
    fun open(device: BondedBluetoothDevice): Flow<BluetoothReadChunk>

    suspend fun close()
}
