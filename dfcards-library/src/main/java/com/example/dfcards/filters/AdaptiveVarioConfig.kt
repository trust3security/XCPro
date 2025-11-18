package com.example.dfcards.filters

/**
 * Tunable parameters for adaptive variometer filtering.
 *
 * Values are conservative defaults that match the guidance discussed in
 * CODING_POLICY plus the adaptive sensor weighting proposal.
 */
data class AdaptiveVarioConfig(
    val baseBaroMeasurementNoise: Double = 0.5,    // m
    val baroVarianceScale: Double = 8.0,
    val baroVarianceWindowSize: Int = 32,          // ~1s @ 30Hz
    val measurementNoiseMin: Double = 0.1,
    val measurementNoiseMax: Double = 6.0,

    val baseProcessNoise: Double = 0.1,
    val processNoiseBoost: Double = 0.25,
    val accelHighPassTauSeconds: Double = 0.4,
    val accelBoostThreshold: Double = 0.12,        // g-equivalent (after HP filter)

    val tauBaseSeconds: Double = 0.7,
    val tauMinSeconds: Double = 0.25,
    val tauMaxSeconds: Double = 1.6
)
