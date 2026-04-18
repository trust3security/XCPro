package com.trust3.xcpro.weather.wind.model

data class GpsSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val groundSpeedMs: Double,
    val trackRad: Double,
    val timestampMillis: Long,
    val clockMillis: Long
)

data class PressureSample(
    val pressureHpa: Double,
    val altitudeMeters: Double,
    val timestampMillis: Long,
    val clockMillis: Long
)

data class AirspeedSample(
    val trueMs: Double,
    val indicatedMs: Double,
    val timestampMillis: Long,
    val clockMillis: Long,
    val valid: Boolean
) {
    val hasIndicatedAirspeed: Boolean
        get() = indicatedMs.isFinite() && indicatedMs > 0.0

    companion object {
        fun tasOnly(
            trueMs: Double,
            clockMillis: Long,
            timestampMillis: Long = 0L,
            valid: Boolean = true
        ): AirspeedSample = AirspeedSample(
            trueMs = trueMs,
            indicatedMs = Double.NaN,
            timestampMillis = timestampMillis,
            clockMillis = clockMillis,
            valid = valid
        )
    }
}

data class HeadingSample(
    val headingDeg: Double,
    val timestampMillis: Long,
    val clockMillis: Long
)

data class GLoadSample(
    val gLoad: Double,
    val timestampMillis: Long,
    val clockMillis: Long,
    val isReliable: Boolean
)

enum class WindInputSource {
    LIVE,
    REPLAY
}
