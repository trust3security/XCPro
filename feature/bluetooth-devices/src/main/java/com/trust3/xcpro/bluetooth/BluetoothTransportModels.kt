package com.trust3.xcpro.bluetooth

data class BondedBluetoothDevice(
    val address: String,
    val displayName: String?
)

data class BluetoothReadChunk(
    val bytes: ByteArray,
    val receivedMonoMs: Long
)

enum class BluetoothConnectionError {
    PERMISSION_REQUIRED,
    DEVICE_NOT_BONDED,
    ALREADY_OPEN,
    CONNECT_FAILED,
    STREAM_CLOSED,
    READ_FAILED,
    CANCELLED
}

sealed interface BluetoothConnectionState {
    data object Disconnected : BluetoothConnectionState

    data class Connecting(
        val device: BondedBluetoothDevice
    ) : BluetoothConnectionState

    data class Connected(
        val device: BondedBluetoothDevice
    ) : BluetoothConnectionState

    data class Error(
        val device: BondedBluetoothDevice?,
        val error: BluetoothConnectionError
    ) : BluetoothConnectionState
}
