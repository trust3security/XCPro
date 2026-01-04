package com.example.xcpro.weather.wind.model

data class GpsSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val groundSpeedMs: Double,
    val trackRad: Double,
    val timestampMillis: Long
)

data class PressureSample(
    val pressureHpa: Double,
    val altitudeMeters: Double,
    val timestampMillis: Long
)

data class AirspeedSample(
    val trueMs: Double,
    val indicatedMs: Double,
    val timestampMillis: Long,
    val valid: Boolean
)

data class HeadingSample(
    val headingDeg: Double,
    val timestampMillis: Long
)

enum class WindInputSource {
    LIVE,
    REPLAY
}
