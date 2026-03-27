package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.PolarPoint
import com.example.xcpro.common.glider.ThreePointPolar
import kotlin.math.abs
import kotlin.math.max

object PolarCalculator {
    /**
     * Computes still-air sink rate (m/s) for a given indicated airspeed (IAS).
     *
     * Phase 4 release contract:
     * - manual three-point polar is authoritative when present
     * - otherwise the selected glider model polar is authoritative
     * - bugs and water ballast are the only active General Polar modifiers
     * - reference weight and user coefficients remain stored-only and do not
     *   affect authoritative runtime math
     */
    fun sinkMs(airspeedMs: Double, model: GliderModel, config: GliderConfig): Double {
        val v = max(airspeedMs, 0.0)
        config.threePointPolar?.let { tpp ->
            val threePointSink = sinkFromThreePoint(speedMs = v, tpp = tpp)
            if (threePointSink != null) {
                return applyAdjustments(threePointSink, config)
            }
        }
        val coeff = model.polar
        val polynomialSink = coeff?.let { params ->
            val a = params.a
            val b = params.b
            val c = params.c
            if (a != null && b != null && c != null) {
                a + b * v + c * v * v
            } else {
                null
            }
        }
        val baseSink = when {
            model.pointsLight != null && model.pointsHeavy != null -> {
                val wl = wingLoading(model, config)
                val minWL = model.minWingLoadingKgM2 ?: wl
                val maxWL = model.maxWingLoadingKgM2 ?: wl
                val alpha = if (maxWL > minWL) {
                    ((wl - minWL) / (maxWL - minWL)).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                val sinkLight = sinkFromPoints(model.pointsLight, v)
                val sinkHeavy = sinkFromPoints(model.pointsHeavy, v)
                if (sinkLight != null && sinkHeavy != null) {
                    (1 - alpha) * sinkLight + alpha * sinkHeavy
                } else {
                    null
                }
            }
            model.points != null -> sinkFromPoints(model.points, v)
            polynomialSink != null -> polynomialSink
            else -> null
        } ?: run {
            val refV = 27.78
            val refSink = 0.65
            val k = 0.0006
            refSink + k * (v - refV) * (v - refV)
        }

        return applyAdjustments(baseSink, config)
    }

    private fun applyAdjustments(baseSink: Double, config: GliderConfig): Double {
        val bugsFactor = 1.0 + (config.bugsPercent.coerceIn(0, 50) * 0.005)
        val ballastPenalty = (config.waterBallastKg.coerceAtLeast(0.0) / 50.0) * 0.02
        return (baseSink * bugsFactor) + ballastPenalty
    }

    data class Coeff(val a: Double, val b: Double, val c: Double)

    fun coefficientsFromThreePoints(
        v1Ms: Double,
        y1: Double,
        v2Ms: Double,
        y2: Double,
        v3Ms: Double,
        y3: Double
    ): Coeff {
        val x1 = v1Ms
        val x2 = v2Ms
        val x3 = v3Ms
        val d = det(1.0, x1, x1 * x1, 1.0, x2, x2 * x2, 1.0, x3, x3 * x3)
        if (!d.isFinite() || abs(d) < DETERMINANT_EPSILON) {
            return Coeff(Double.NaN, Double.NaN, Double.NaN)
        }
        val da = det(y1, x1, x1 * x1, y2, x2, x2 * x2, y3, x3, x3 * x3)
        val db = det(1.0, y1, x1 * x1, 1.0, y2, x2 * x2, 1.0, y3, x3 * x3)
        val dc = det(1.0, x1, y1, 1.0, x2, y2, 1.0, x3, y3)
        return Coeff(a = da / d, b = db / d, c = dc / d)
    }

    private fun det(
        a: Double,
        b: Double,
        c: Double,
        d: Double,
        e: Double,
        f: Double,
        g: Double,
        h: Double,
        i: Double
    ): Double {
        return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    }

    private fun wingLoading(model: GliderModel, config: GliderConfig): Double {
        val area = model.wingAreaM2 ?: return 0.0
        val empty = (model.emptyWeightKg ?: 0).toDouble()
        val gross = empty + config.pilotAndGearKg + config.waterBallastKg
        return gross / area
    }

    private fun sinkFromThreePoint(speedMs: Double, tpp: ThreePointPolar): Double? {
        val speedsAndSinks = listOf(
            tpp.lowMs to tpp.lowSinkMs,
            tpp.midMs to tpp.midSinkMs,
            tpp.highMs to tpp.highSinkMs
        )
        val validInputs = speedsAndSinks.all { (speed, sink) ->
            speed.isFinite() && speed > 0.0 && sink.isFinite()
        }
        if (!validInputs) return null

        val coeff = coefficientsFromThreePoints(
            v1Ms = tpp.lowMs,
            y1 = tpp.lowSinkMs,
            v2Ms = tpp.midMs,
            y2 = tpp.midSinkMs,
            v3Ms = tpp.highMs,
            y3 = tpp.highSinkMs
        )
        if (!coeff.a.isFinite() || !coeff.b.isFinite() || !coeff.c.isFinite()) {
            return null
        }
        val base = coeff.a + coeff.b * speedMs + coeff.c * speedMs * speedMs
        return base.takeIf { it.isFinite() }
    }

    private fun sinkFromPoints(points: List<PolarPoint>?, speedMs: Double): Double? {
        if (points.isNullOrEmpty()) return null
        val sorted = points
            .asSequence()
            .filter { it.speedMs.isFinite() && it.speedMs > 0.0 && it.sinkMs.isFinite() }
            .sortedBy { it.speedMs }
            .toList()
        if (sorted.isEmpty()) return null
        val first = sorted.first()
        if (sorted.size == 1) return first.sinkMs
        val last = sorted.last()
        return when {
            speedMs <= first.speedMs -> first.sinkMs
            speedMs >= last.speedMs -> last.sinkMs
            else -> {
                val idx = sorted.indexOfLast { it.speedMs <= speedMs }.coerceIn(0, sorted.lastIndex)
                val nextIdx = (idx + 1).coerceAtMost(sorted.lastIndex)
                if (nextIdx == idx) {
                    return sorted[idx].sinkMs
                }
                val p0 = sorted[idx]
                val p1 = sorted[nextIdx]
                val speedDelta = p1.speedMs - p0.speedMs
                if (!speedDelta.isFinite() || abs(speedDelta) < DETERMINANT_EPSILON) {
                    return p0.sinkMs
                }
                val t = ((speedMs - p0.speedMs) / speedDelta).coerceIn(0.0, 1.0)
                val interpolated = p0.sinkMs + t * (p1.sinkMs - p0.sinkMs)
                interpolated.takeIf { it.isFinite() }
            }
        }
    }

    private const val DETERMINANT_EPSILON = 1e-9
}
