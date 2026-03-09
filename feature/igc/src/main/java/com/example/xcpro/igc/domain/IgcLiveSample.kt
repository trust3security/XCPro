package com.example.xcpro.igc.domain

/**
 * Normalized live sample used by the IGC B-record mapper.
 *
 * All values are SI units and nullable where sensor data may be unavailable.
 */
data class IgcLiveSample(
    val sampleWallTimeMs: Long,
    val gpsWallTimeMs: Long?,
    val baroWallTimeMs: Long?,
    val latitudeDegrees: Double?,
    val longitudeDegrees: Double?,
    val horizontalAccuracyMeters: Double?,
    val pressureAltitudeMeters: Double?,
    val gnssAltitudeMeters: Double?,
    val indicatedAirspeedMs: Double?,
    val trueAirspeedMs: Double?
)
