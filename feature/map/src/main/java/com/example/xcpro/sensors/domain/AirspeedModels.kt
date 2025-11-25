package com.example.xcpro.sensors.domain

internal data class AirspeedEstimate(
    val indicatedMs: Double,
    val trueMs: Double,
    val source: AirspeedSource
)

internal enum class AirspeedSource(val label: String) {
    WIND_VECTOR("WIND"),
    POLAR_SINK("POLAR"),
    GPS_GROUND("GPS")
}
