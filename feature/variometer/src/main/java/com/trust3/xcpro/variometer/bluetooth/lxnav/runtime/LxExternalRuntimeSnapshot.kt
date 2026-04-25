package com.trust3.xcpro.variometer.bluetooth.lxnav.runtime

import com.trust3.xcpro.bluetooth.BluetoothConnectionState
import com.trust3.xcpro.variometer.bluetooth.lxnav.LxDeviceInfo

internal data class LxTimedValue<T>(
    val value: T,
    val receivedMonoMs: Long
)

internal data class LxLiveSettingsOverrides(
    val macCreadyMps: LxTimedValue<Double>? = null,
    val bugsPercent: LxTimedValue<Int>? = null,
    val ballastOverloadFactor: LxTimedValue<Double>? = null,
    val qnhHpa: LxTimedValue<Double>? = null
)

internal data class LxEnvironmentStatus(
    val outsideAirTemperatureC: LxTimedValue<Double>? = null,
    val mode: LxTimedValue<Int>? = null,
    val voltageV: LxTimedValue<Double>? = null
)

internal data class LxDeviceConfigurationStatus(
    val polarA: LxTimedValue<Double>? = null,
    val polarB: LxTimedValue<Double>? = null,
    val polarC: LxTimedValue<Double>? = null,
    val audioVolume: LxTimedValue<Int>? = null,
    val altitudeOffsetFeet: LxTimedValue<Double>? = null,
    val scMode: LxTimedValue<Double>? = null,
    val varioFilter: LxTimedValue<Double>? = null,
    val teFilter: LxTimedValue<Double>? = null,
    val teLevel: LxTimedValue<Double>? = null,
    val varioAverage: LxTimedValue<Double>? = null,
    val varioRange: LxTimedValue<Double>? = null,
    val scTab: LxTimedValue<Double>? = null,
    val scLow: LxTimedValue<Double>? = null,
    val scSpeed: LxTimedValue<Double>? = null,
    val smartDiff: LxTimedValue<Double>? = null,
    val gliderName: LxTimedValue<String>? = null,
    val timeOffsetMinutes: LxTimedValue<Int>? = null
)

internal data class LxExternalRuntimeSnapshot(
    val activeDeviceAddress: String? = null,
    val activeDeviceName: String? = null,
    val sessionOrdinal: Long = 0L,
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.Disconnected,
    val pressureAltitudeM: LxTimedValue<Double>? = null,
    val totalEnergyVarioMps: LxTimedValue<Double>? = null,
    val externalVarioMps: LxTimedValue<Double>? = null,
    val airspeedKph: LxTimedValue<Double>? = null,
    val plxvfIasKph: LxTimedValue<Double>? = null,
    val deviceInfo: LxDeviceInfo? = null,
    val liveSettingsOverrides: LxLiveSettingsOverrides = LxLiveSettingsOverrides(),
    val environmentStatus: LxEnvironmentStatus = LxEnvironmentStatus(),
    val deviceConfiguration: LxDeviceConfigurationStatus = LxDeviceConfigurationStatus(),
    val lastAcceptedMonoMs: Long? = null,
    val diagnostics: LxBluetoothRuntimeDiagnostics = LxBluetoothRuntimeDiagnostics()
)


