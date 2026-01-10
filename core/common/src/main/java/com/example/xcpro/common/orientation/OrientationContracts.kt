package com.example.xcpro.common.orientation

import com.example.dfcards.RealTimeFlightData
import kotlinx.coroutines.flow.StateFlow

enum class MapOrientationMode {
    NORTH_UP,
    TRACK_UP,
    HEADING_UP
}

enum class BearingSource {
    COMPASS,
    WIND,
    TRACK,
    LAST_KNOWN,
    NONE
}

data class HeadingSolution(
    val bearingDeg: Double = 0.0,
    val source: BearingSource = BearingSource.NONE,
    val isValid: Boolean = false
)

data class OrientationData(
    val bearing: Double = 0.0,
    val mode: MapOrientationMode = MapOrientationMode.NORTH_UP,
    val isValid: Boolean = true,
    val bearingSource: BearingSource = BearingSource.NONE,
    val headingDeg: Double = 0.0,
    val headingValid: Boolean = false,
    val headingSource: BearingSource = BearingSource.NONE,
    val timestamp: Long = System.currentTimeMillis()
)

data class OrientationSensorData(
    val track: Double = 0.0,
    val magneticHeading: Double = 0.0,
    val groundSpeed: Double = 0.0,
    val isGPSValid: Boolean = false,
    val hasValidHeading: Boolean = false,
    val compassReliable: Boolean = false,
    val windDirectionFrom: Double = 0.0,
    val windSpeed: Double = 0.0,
    val headingSolution: HeadingSolution = HeadingSolution(),
    val timestamp: Long = System.currentTimeMillis()
)

interface OrientationController {
    val orientationFlow: StateFlow<OrientationData>

    fun setOrientationMode(mode: MapOrientationMode)
    fun onUserInteraction()
    fun resetUserOverride()
    fun getCurrentMode(): MapOrientationMode
    fun getCurrentBearing(): Double
    fun isOrientationValid(): Boolean
    fun start()
    fun stop()
    fun updateFromFlightData(flightData: RealTimeFlightData)
}
