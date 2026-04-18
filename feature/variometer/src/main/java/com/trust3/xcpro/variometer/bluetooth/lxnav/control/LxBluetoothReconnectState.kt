package com.trust3.xcpro.variometer.bluetooth.lxnav.control

import com.trust3.xcpro.variometer.bluetooth.BluetoothConnectionError

enum class LxBluetoothReconnectBlockReason {
    PERMISSION_REQUIRED,
    DEVICE_NOT_BONDED,
    SELECTION_CHANGED,
    DIFFERENT_DEVICE_REQUESTED
}

sealed interface LxBluetoothReconnectState {
    data object Idle : LxBluetoothReconnectState

    data class Waiting(
        val attemptNumber: Int,
        val maxAttempts: Int,
        val delayMs: Long
    ) : LxBluetoothReconnectState

    data class Attempting(
        val attemptNumber: Int,
        val maxAttempts: Int
    ) : LxBluetoothReconnectState

    data class Blocked(
        val reason: LxBluetoothReconnectBlockReason
    ) : LxBluetoothReconnectState

    data class Exhausted(
        val attempts: Int
    ) : LxBluetoothReconnectState
}

enum class LxBluetoothDisconnectReason {
    PERMISSION_REQUIRED,
    DEVICE_NOT_BONDED,
    CONNECT_FAILED,
    STREAM_CLOSED,
    READ_FAILED,
    RETRIES_EXHAUSTED
}

internal fun BluetoothConnectionError.toDisconnectReasonOrNull(): LxBluetoothDisconnectReason? =
    when (this) {
        BluetoothConnectionError.PERMISSION_REQUIRED -> LxBluetoothDisconnectReason.PERMISSION_REQUIRED
        BluetoothConnectionError.DEVICE_NOT_BONDED -> LxBluetoothDisconnectReason.DEVICE_NOT_BONDED
        BluetoothConnectionError.CONNECT_FAILED -> LxBluetoothDisconnectReason.CONNECT_FAILED
        BluetoothConnectionError.STREAM_CLOSED -> LxBluetoothDisconnectReason.STREAM_CLOSED
        BluetoothConnectionError.READ_FAILED -> LxBluetoothDisconnectReason.READ_FAILED
        BluetoothConnectionError.ALREADY_OPEN,
        BluetoothConnectionError.CANCELLED -> null
    }
