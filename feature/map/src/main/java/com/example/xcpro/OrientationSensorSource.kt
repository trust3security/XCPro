package com.example.xcpro

import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.OrientationSensorData
import kotlinx.coroutines.flow.StateFlow

interface OrientationSensorSource {
    val orientationFlow: StateFlow<OrientationSensorData>

    fun getCurrentData(): OrientationSensorData
    fun updateFromFlightData(flightData: RealTimeFlightData)
    fun updateMinSpeedThreshold(thresholdMs: Double)
    fun start()
    fun stop()
}
