package com.example.xcpro.sensors.domain

internal data class AirspeedEstimate(
    val indicatedMs: Double,
    val trueMs: Double,
    val source: AirspeedSource
)

internal enum class AirspeedSource(
    val label: String,
    val energyHeightEligible: Boolean
) {
    EXTERNAL("SENSOR", true),
    WIND_VECTOR("WIND", true),
    POLAR_SINK("POLAR", false),
    GPS_GROUND("GPS", false)
}
