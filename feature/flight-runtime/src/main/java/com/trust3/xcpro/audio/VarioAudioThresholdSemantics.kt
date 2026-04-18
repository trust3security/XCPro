package com.trust3.xcpro.audio

/**
 * Canonical threshold semantics shared by audio runtime, settings migration,
 * and future UI simplification work.
 *
 * These helpers preserve the current mapper behavior exactly:
 * - lift starts at the highest positive-side boundary
 * - sink starts at the lowest negative-side boundary
 */
const val LEGACY_DEFAULT_LIFT_THRESHOLD: Double = 0.1
const val LEGACY_DEFAULT_SINK_SILENCE_THRESHOLD: Double = 0.0
const val LEGACY_DEFAULT_DEADBAND_MIN: Double = -0.3
const val LEGACY_DEFAULT_DEADBAND_MAX: Double = 0.1

fun VarioAudioSettings.effectiveLiftStartThreshold(): Double = liftStartThreshold

fun VarioAudioSettings.effectiveSinkStartThreshold(): Double = sinkStartThreshold

fun legacyEffectiveLiftStartThreshold(
    liftThreshold: Double,
    deadbandMin: Double,
    deadbandMax: Double
): Double = maxOf(liftThreshold, deadbandMax, deadbandMin)

fun legacyEffectiveSinkStartThreshold(
    sinkSilenceThreshold: Double,
    deadbandMin: Double
): Double = minOf(sinkSilenceThreshold, deadbandMin)

fun canonicalAudioSettingsFromLegacyThresholdPairs(
    enabled: Boolean,
    volume: Float,
    liftThreshold: Double,
    sinkSilenceThreshold: Double,
    dutyCycle: Double,
    deadbandMin: Double,
    deadbandMax: Double
): VarioAudioSettings =
    VarioAudioSettings(
        enabled = enabled,
        volume = volume,
        liftStartThreshold = legacyEffectiveLiftStartThreshold(
            liftThreshold = liftThreshold,
            deadbandMin = deadbandMin,
            deadbandMax = deadbandMax
        ),
        sinkStartThreshold = legacyEffectiveSinkStartThreshold(
            sinkSilenceThreshold = sinkSilenceThreshold,
            deadbandMin = deadbandMin
        ),
        dutyCycle = dutyCycle
    )

fun VarioAudioSettings.withEffectiveLiftStartThreshold(threshold: Double): VarioAudioSettings =
    copy(liftStartThreshold = threshold)

fun VarioAudioSettings.withEffectiveSinkStartThreshold(threshold: Double): VarioAudioSettings =
    copy(sinkStartThreshold = threshold)
