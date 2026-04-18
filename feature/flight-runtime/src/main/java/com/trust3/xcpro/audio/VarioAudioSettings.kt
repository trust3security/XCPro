package com.trust3.xcpro.audio

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
    val liftStartThreshold: Double = 0.1,
    val sinkStartThreshold: Double = -0.3,
    val dutyCycle: Double = 2.0 / 3.0,
)
