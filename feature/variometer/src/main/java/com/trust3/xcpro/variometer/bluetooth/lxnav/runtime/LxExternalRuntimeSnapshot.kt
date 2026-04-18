package com.trust3.xcpro.variometer.bluetooth.lxnav.runtime

import com.trust3.xcpro.variometer.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxDeviceInfo

internal data class LxTimedValue<T>(
    val value: T,
    val receivedMonoMs: Long
)

internal data class LxExternalRuntimeSnapshot(
    val activeDeviceAddress: String? = null,
    val activeDeviceName: String? = null,
    val sessionOrdinal: Long = 0L,
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.Disconnected,
    val pressureAltitudeM: LxTimedValue<Double>? = null,
    val totalEnergyVarioMps: LxTimedValue<Double>? = null,
    val airspeedKph: LxTimedValue<Double>? = null,
    val deviceInfo: LxDeviceInfo? = null,
    val lastAcceptedMonoMs: Long? = null,
    val diagnostics: LxBluetoothRuntimeDiagnostics = LxBluetoothRuntimeDiagnostics()
)
