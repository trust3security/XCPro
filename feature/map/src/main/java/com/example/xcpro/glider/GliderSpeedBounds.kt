package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.PolarPoint
import com.example.xcpro.common.glider.SpeedLimits
import com.example.xcpro.common.units.UnitsConverter
import kotlin.math.max
import kotlin.math.min

data class SpeedBoundsMs(
    val minMs: Double,
    val maxMs: Double
) {
    fun clamp(value: Double): Double = value.coerceIn(minMs, maxMs)
}

internal object GliderSpeedBoundsResolver {

    data class PolarRangeMs(val minMs: Double, val maxMs: Double)

    fun hasPolar(model: GliderModel?, config: GliderConfig): Boolean =
        resolvePolarRangeMs(model, config) != null

    fun resolveIasBoundsMs(model: GliderModel?, config: GliderConfig): SpeedBoundsMs? {
        val polarRange = resolvePolarRangeMs(model, config) ?: return null
        val minCandidate = config.iasMinMs ?: polarRange.minMs
        val maxCandidate = config.iasMaxMs ?: polarRange.maxMs
        val clampedMax = clampMaxToSpeedLimitsMs(maxCandidate, model?.speedLimits)
        val minMs = minCandidate.coerceAtLeast(0.0)
        val maxMs = clampedMax.coerceAtLeast(0.0)
        if (maxMs <= 0.0 || minMs <= 0.0 || minMs >= maxMs) return null
        return SpeedBoundsMs(minMs = minMs, maxMs = maxMs)
    }

    fun resolvePolarRangeMs(model: GliderModel?, config: GliderConfig): PolarRangeMs? {
        config.threePointPolar?.let { polar ->
            val minMs = min(polar.lowMs, min(polar.midMs, polar.highMs))
            val maxMs = max(polar.lowMs, max(polar.midMs, polar.highMs))
            if (minMs > 0.0 && maxMs > minMs) {
                return PolarRangeMs(minMs, maxMs)
            }
        }

        val modelValue = model ?: return null

        val points = buildList {
            modelValue.points?.let { addAll(it) }
            modelValue.pointsLight?.let { addAll(it) }
            modelValue.pointsHeavy?.let { addAll(it) }
        }
        if (points.isNotEmpty()) {
            return pointsRange(points)
        }

        val coeff = modelValue.polar
        if (coeff != null && coeff.a != null && coeff.b != null && coeff.c != null) {
            val minMs = coeff.minMs
            val maxMs = coeff.maxMs
            if (minMs > 0.0 && maxMs > minMs) {
                return PolarRangeMs(minMs, maxMs)
            }
        }

        return null
    }

    private fun pointsRange(points: List<PolarPoint>): PolarRangeMs {
        var minMs = Double.POSITIVE_INFINITY
        var maxMs = Double.NEGATIVE_INFINITY
        points.forEach { point ->
            minMs = min(minMs, point.speedMs)
            maxMs = max(maxMs, point.speedMs)
        }
        val safeMin = if (minMs.isFinite()) minMs else 0.0
        val safeMax = if (maxMs.isFinite()) maxMs else 0.0
        return PolarRangeMs(safeMin, safeMax)
    }

    private fun clampMaxToSpeedLimitsMs(maxMs: Double, limits: SpeedLimits?): Double {
        val limit = limits?.let {
            listOfNotNull(
                it.vneKmh?.toDouble()?.let(UnitsConverter::kmhToMs),
                it.vraKmh?.toDouble()?.let(UnitsConverter::kmhToMs),
                it.vaKmh?.toDouble()?.let(UnitsConverter::kmhToMs),
                it.vwKmh?.toDouble()?.let(UnitsConverter::kmhToMs),
                it.vtKmh?.toDouble()?.let(UnitsConverter::kmhToMs)
            ).minOrNull()
        }
        return if (limit != null && limit > 0.0) {
            min(maxMs, limit)
        } else {
            maxMs
        }
    }
}
