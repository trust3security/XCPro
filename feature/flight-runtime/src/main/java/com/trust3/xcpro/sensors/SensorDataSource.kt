package com.trust3.xcpro.sensors

import kotlinx.coroutines.flow.StateFlow

interface SensorDataSource {
    val gpsFlow: StateFlow<GPSData?>
    val baroFlow: StateFlow<BaroData?>
    val compassFlow: StateFlow<CompassData?>
    val rawAccelFlow: StateFlow<RawAccelData?>
    val accelFlow: StateFlow<AccelData?>
    val attitudeFlow: StateFlow<AttitudeData?>
}
