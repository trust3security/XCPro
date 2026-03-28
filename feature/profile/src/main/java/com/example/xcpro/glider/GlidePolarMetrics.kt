package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel

internal data class GlidePolarDerivedMetrics(
    val bestLd: Double?,
    val bestLdSpeedMs: Double?
)

internal object GlidePolarMetricsResolver {
    private const val MIN_SCAN_STEP_MS = 0.25
    private const val MAX_SCAN_STEP_MS = 1.0
    private const val TARGET_SCAN_SAMPLES = 240.0

    fun ldAtSpeed(
        indicatedAirspeedMs: Double,
        model: GliderModel,
        config: GliderConfig
    ): Double? {
        val bounds = GliderSpeedBoundsResolver.resolveIasBoundsMs(model, config) ?: return null
        val clampedSpeed = bounds.clamp(indicatedAirspeedMs)
        if (!clampedSpeed.isFinite() || clampedSpeed <= 0.0) return null
        val sink = PolarCalculator.sinkMs(clampedSpeed, model, config)
        return ldFrom(clampedSpeed, sink)
    }

    fun deriveBestLd(
        model: GliderModel,
        config: GliderConfig
    ): GlidePolarDerivedMetrics {
        val bounds = GliderSpeedBoundsResolver.resolveIasBoundsMs(model, config)
            ?: return GlidePolarDerivedMetrics(bestLd = null, bestLdSpeedMs = null)
        val rangeMs = (bounds.maxMs - bounds.minMs).coerceAtLeast(0.0)
        val stepMs = (rangeMs / TARGET_SCAN_SAMPLES).coerceIn(MIN_SCAN_STEP_MS, MAX_SCAN_STEP_MS)

        var bestLd: Double? = null
        var bestLdSpeedMs: Double? = null
        var speedMs = bounds.minMs

        while (speedMs <= bounds.maxMs + (stepMs * 0.5)) {
            val clampedSpeed = bounds.clamp(speedMs)
            val sink = PolarCalculator.sinkMs(clampedSpeed, model, config)
            val candidateLd = ldFrom(clampedSpeed, sink)
            if (candidateLd != null && (bestLd == null || candidateLd > bestLd)) {
                bestLd = candidateLd
                bestLdSpeedMs = clampedSpeed
            }
            speedMs += stepMs
        }

        return GlidePolarDerivedMetrics(
            bestLd = bestLd,
            bestLdSpeedMs = bestLdSpeedMs
        )
    }

    private fun ldFrom(speedMs: Double, sinkMs: Double): Double? {
        if (!speedMs.isFinite() || speedMs <= 0.0) return null
        if (!sinkMs.isFinite() || sinkMs <= 0.0) return null
        val ld = speedMs / sinkMs
        return ld.takeIf { it.isFinite() && it > 0.0 }
    }
}
