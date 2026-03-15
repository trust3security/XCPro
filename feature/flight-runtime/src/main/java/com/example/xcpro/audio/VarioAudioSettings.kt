package com.example.xcpro.audio

/**
 * Shared runtime audio settings for live and replay sensor-fusion owners.
 *
 * The concrete audio engine still lives in `feature:variometer`; this model is
 * kept in `feature:flight-runtime` so the fusion pipeline does not depend
 * directly on variometer implementation code.
 */
data class VarioAudioSettings(
    val enabled: Boolean = true,
    val volume: Float = 0.8f,
    val liftThreshold: Double = 0.1,
    val sinkSilenceThreshold: Double = 0.0,
    val dutyCycle: Double = 2.0 / 3.0,
    val deadbandMin: Double = -0.3,
    val deadbandMax: Double = 0.1
)
