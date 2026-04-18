package com.trust3.xcpro

import com.trust3.xcpro.core.flight.RealTimeFlightData
import com.trust3.xcpro.common.orientation.OrientationFlightDataSnapshot
import com.trust3.xcpro.common.orientation.OrientationSensorData
import kotlinx.coroutines.flow.StateFlow

interface OrientationSensorSource {
    val orientationFlow: StateFlow<OrientationSensorData>

    fun getCurrentData(): OrientationSensorData
    fun updateFromFlightData(flightData: OrientationFlightDataSnapshot)
    fun updateMinSpeedThreshold(thresholdMs: Double)
    fun start()
    fun stop()
}

fun RealTimeFlightData.toOrientationFlightDataSnapshot(): OrientationFlightDataSnapshot =
    OrientationFlightDataSnapshot(
        track = track,
        groundSpeed = groundSpeed,
        windDirectionFrom = windDirection.toDouble(),
        windSpeed = windSpeed.toDouble(),
        windValid = windValid,
        accuracy = accuracy,
        latitude = latitude,
        longitude = longitude
    )
