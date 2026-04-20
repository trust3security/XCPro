package com.trust3.xcpro.variometer.bluetooth.lxnav.runtime

import com.trust3.xcpro.bluetooth.BluetoothConnectionError

internal data class LxBluetoothRuntimeDiagnostics(
    val sessionStartMonoMs: Long? = null,
    val lastReceivedMonoMs: Long? = null,
    val rollingSentenceRatePerSecond: Double = 0.0,
    val acceptedSentenceCount: Int = 0,
    val rejectedSentenceCount: Int = 0,
    val checksumFailureCount: Int = 0,
    val parseFailureCount: Int = 0,
    val lastTransportError: BluetoothConnectionError? = null
)

