package com.example.xcpro.weather.wind.model

data class GpsSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val groundSpeedMs: Double,
    val trackDeg: Double,
    val timestampMillis: Long,
    val accuracyMeters: Double? = null
) {
    val trackRad: Double = Math.toRadians(trackDeg)
}

data class PressureSample(
    val pressureHpa: Double,
    val pressureAltitudeMeters: Double,
    val timestampMillis: Long
)

data class AirspeedSample(
    val trueMs: Double,
    val indicatedMs: Double,
    val timestampMillis: Long,
    val isInstrument: Boolean
) {
    val isValid: Boolean = isInstrument && trueMs.isFinite()
}

data class HeadingSample(
    val headingDeg: Double,
    val timestampMillis: Long,
    val isReliable: Boolean
) {
    val isValid: Boolean = headingDeg.isFinite() && isReliable
}
