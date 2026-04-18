package com.trust3.xcpro.common.orientation

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
    val timestamp: Long = 0L
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
    val timestamp: Long = 0L
)

data class OrientationFlightDataSnapshot(
    val track: Double = 0.0,
    val groundSpeed: Double = 0.0,
    val windDirectionFrom: Double = 0.0,
    val windSpeed: Double = 0.0,
    val windValid: Boolean = false,
    val accuracy: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
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
    fun updateFromFlightData(flightData: OrientationFlightDataSnapshot)
}
