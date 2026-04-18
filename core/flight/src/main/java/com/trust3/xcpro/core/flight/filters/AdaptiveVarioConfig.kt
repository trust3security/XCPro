package com.trust3.xcpro.core.flight.filters

data class AdaptiveVarioConfig(
    val baseBaroMeasurementNoise: Double = 0.5,
    val baroVarianceScale: Double = 8.0,
    val baroVarianceWindowSize: Int = 32,
    val measurementNoiseMin: Double = 0.1,
    val measurementNoiseMax: Double = 6.0,
    val baseProcessNoise: Double = 0.1,
    val processNoiseBoost: Double = 0.25,
    val accelHighPassTauSeconds: Double = 0.4,
    val accelBoostThreshold: Double = 0.12,
    val tauBaseSeconds: Double = 0.7,
    val tauMinSeconds: Double = 0.25,
    val tauMaxSeconds: Double = 1.6
)
