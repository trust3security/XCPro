package com.trust3.xcpro.variometer.bluetooth.lxnav

data class LxDeviceSnapshot(
    val airspeedKph: Double? = null,
    val pressureAltitudeM: Double? = null,
    val totalEnergyVarioMps: Double? = null,
    val externalVarioMps: Double? = null,
    val deviceInfo: LxDeviceInfo? = null,
    val lastAcceptedSentenceId: LxSentenceId? = null,
    val lastAcceptedMonoMs: Long? = null
)
