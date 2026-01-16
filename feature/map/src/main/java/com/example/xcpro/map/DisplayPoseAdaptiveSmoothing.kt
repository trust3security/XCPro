package com.example.xcpro.map

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Adaptive UI-only smoothing: scale the base config by speed + accuracy.
 * This keeps SSOT/navigation untouched while reducing lag at high speed.
 */
internal object DisplayPoseAdaptiveSmoothing {

    fun effectiveConfig(
        base: DisplayPoseSmoothingConfig,
        speedMs: Double,
        accuracyM: Double
    ): DisplayPoseSmoothingConfig {
        if (!speedMs.isFinite() || !accuracyM.isFinite()) return base

        val speedNorm = normalize(speedMs, SPEED_SLOW_MS, SPEED_FAST_MS)
        val accuracyNorm = normalize(accuracyM, ACCURACY_GOOD_M, ACCURACY_BAD_M)

        val speedScale = lerp(1.0, MIN_SPEED_SCALE, speedNorm)
        val accuracyScale = lerp(1.0, MAX_ACCURACY_SCALE, accuracyNorm)

        val combinedScale = clamp(speedScale * accuracyScale, MIN_SCALE, MAX_SCALE)
        val posSmoothMs = (base.posSmoothMs * combinedScale).coerceAtLeast(MIN_POS_SMOOTH_MS)
        val headingSmoothMs = (base.headingSmoothMs * combinedScale).coerceAtLeast(MIN_HEADING_SMOOTH_MS)

        val deadReckonScale = lerp(1.0, MIN_DEAD_RECKON_SCALE, accuracyNorm)
        val deadReckonLimitMs = (base.deadReckonLimitMs * deadReckonScale)
            .roundToLong()
            .coerceAtLeast(MIN_DEAD_RECKON_MS)

        return base.copy(
            posSmoothMs = posSmoothMs,
            headingSmoothMs = headingSmoothMs,
            deadReckonLimitMs = deadReckonLimitMs
        )
    }

    private fun normalize(value: Double, minValue: Double, maxValue: Double): Double {
        if (value <= minValue) return 0.0
        if (value >= maxValue) return 1.0
        return (value - minValue) / (maxValue - minValue)
    }

    private fun clamp(value: Double, minValue: Double, maxValue: Double): Double {
        return max(minValue, min(maxValue, value))
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private const val SPEED_SLOW_MS = 8.0    // ~15 kt
    private const val SPEED_FAST_MS = 60.0   // ~117 kt
    private const val ACCURACY_GOOD_M = 3.0
    private const val ACCURACY_BAD_M = 15.0

    private const val MIN_SPEED_SCALE = 0.5
    private const val MAX_ACCURACY_SCALE = 1.6
    private const val MIN_SCALE = 0.45
    private const val MAX_SCALE = 1.8

    private const val MIN_DEAD_RECKON_SCALE = 0.5
    private const val MIN_POS_SMOOTH_MS = 80.0
    private const val MIN_HEADING_SMOOTH_MS = 60.0
    private const val MIN_DEAD_RECKON_MS = 120L
}
