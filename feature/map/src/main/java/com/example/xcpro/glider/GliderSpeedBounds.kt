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

    data class PolarRangeKmh(val minKmh: Double, val maxKmh: Double)

    fun hasPolar(model: GliderModel?, config: GliderConfig): Boolean =
        resolvePolarRangeKmh(model, config) != null

    fun resolveIasBoundsMs(model: GliderModel?, config: GliderConfig): SpeedBoundsMs? {
        val polarRange = resolvePolarRangeKmh(model, config) ?: return null
        val minCandidate = config.iasMinKmh ?: polarRange.minKmh
        val maxCandidate = config.iasMaxKmh ?: polarRange.maxKmh
        val clampedMax = clampMaxToSpeedLimits(maxCandidate, model?.speedLimits)
        val minKmh = minCandidate.coerceAtLeast(0.0)
        val maxKmh = clampedMax.coerceAtLeast(0.0)
        if (maxKmh <= 0.0 || minKmh <= 0.0 || minKmh >= maxKmh) return null
        return SpeedBoundsMs(
            minMs = UnitsConverter.kmhToMs(minKmh),
            maxMs = UnitsConverter.kmhToMs(maxKmh)
        )
    }

    fun resolvePolarRangeKmh(model: GliderModel?, config: GliderConfig): PolarRangeKmh? {
        config.threePointPolar?.let { polar ->
            val minKmh = min(polar.lowKmh, min(polar.midKmh, polar.highKmh))
            val maxKmh = max(polar.lowKmh, max(polar.midKmh, polar.highKmh))
            if (minKmh > 0.0 && maxKmh > minKmh) {
                return PolarRangeKmh(minKmh, maxKmh)
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
            val minKmh = coeff.minKmh
            val maxKmh = coeff.maxKmh
            if (minKmh > 0.0 && maxKmh > minKmh) {
                return PolarRangeKmh(minKmh, maxKmh)
            }
        }

        return null
    }

    private fun pointsRange(points: List<PolarPoint>): PolarRangeKmh {
        var minKmh = Double.POSITIVE_INFINITY
        var maxKmh = Double.NEGATIVE_INFINITY
        points.forEach { point ->
            minKmh = min(minKmh, point.kmh)
            maxKmh = max(maxKmh, point.kmh)
        }
        val safeMin = if (minKmh.isFinite()) minKmh else 0.0
        val safeMax = if (maxKmh.isFinite()) maxKmh else 0.0
        return PolarRangeKmh(safeMin, safeMax)
    }

    private fun clampMaxToSpeedLimits(maxKmh: Double, limits: SpeedLimits?): Double {
        val limit = limits?.let {
            listOfNotNull(
                it.vneKmh?.toDouble(),
                it.vraKmh?.toDouble(),
                it.vaKmh?.toDouble(),
                it.vwKmh?.toDouble(),
                it.vtKmh?.toDouble()
            ).minOrNull()
        }
        return if (limit != null && limit > 0.0) {
            min(maxKmh, limit)
        } else {
            maxKmh
        }
    }
}
