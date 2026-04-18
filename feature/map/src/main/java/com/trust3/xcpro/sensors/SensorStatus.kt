package com.trust3.xcpro.sensors

data class SensorStatus(
    val gpsAvailable: Boolean,
    val gpsStarted: Boolean,
    val baroAvailable: Boolean,
    val baroStarted: Boolean,
    val compassAvailable: Boolean,
    val compassStarted: Boolean,
    val accelAvailable: Boolean,
    val accelStarted: Boolean,
    val rotationAvailable: Boolean,
    val rotationStarted: Boolean,
    val hasLocationPermissions: Boolean
) {
    val allSensorsAvailable: Boolean
        get() = gpsAvailable && baroAvailable && compassAvailable && accelAvailable && rotationAvailable

    val allSensorsStarted: Boolean
        get() = gpsStarted && baroStarted && compassStarted && accelStarted && rotationStarted
}
