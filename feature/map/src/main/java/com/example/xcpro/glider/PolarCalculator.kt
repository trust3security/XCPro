package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.PolarPoint
import com.example.xcpro.common.glider.ThreePointPolar
import kotlin.math.max

object PolarCalculator {
    /**
     * Computes still-air sink rate (m/s) for a given indicated airspeed (m/s).
     * Uses parabolic coefficients when available. Applies simple bug/ballast adjustments (placeholder).
     */
    fun sinkMs(airspeedMs: Double, model: GliderModel, config: GliderConfig): Double {
        val v = max(airspeedMs, 0.0)
        val vKmh = v * 3.6
        // If user provided 3-point polar, prefer its fitted coefficients
        config.threePointPolar?.let { tpp ->
            val coeff = coefficientsFromThreePoints(
                v1Ms = tpp.lowKmh / 3.6, y1 = tpp.lowSinkMs,
                v2Ms = tpp.midKmh / 3.6, y2 = tpp.midSinkMs,
                v3Ms = tpp.highKmh / 3.6, y3 = tpp.highSinkMs
            )
            val base = coeff.a + coeff.b * v + coeff.c * v * v
            val adjusted = applyAdjustments(base, config)
            return adjusted
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
                val alpha = if (maxWL > minWL) ((wl - minWL) / (maxWL - minWL)).coerceIn(0.0, 1.0) else 0.0
                val sinkLight = sinkFromPoints(model.pointsLight!!, vKmh)
                val sinkHeavy = sinkFromPoints(model.pointsHeavy!!, vKmh)
                (1 - alpha) * sinkLight + alpha * sinkHeavy
            }
            model.points != null && model.points!!.size >= 2 -> sinkFromPoints(model.points!!, vKmh)
            polynomialSink != null -> polynomialSink
            else -> {
                val refV = 27.78 // 100 km/h
                val refSink = 0.65
                val k = 0.0006 // rough growth factor
                refSink + k * (v - refV) * (v - refV)
            }
        }

        return applyAdjustments(baseSink, config)
    }

    private fun applyAdjustments(baseSink: Double, config: GliderConfig): Double {
        val bugsFactor = 1.0 + (config.bugsPercent.coerceIn(0, 50) * 0.005)
        val ballastPenalty = (config.waterBallastKg.coerceAtLeast(0.0) / 50.0) * 0.02
        return (baseSink * bugsFactor) + ballastPenalty
    }

    data class Coeff(val a: Double, val b: Double, val c: Double)

    fun coefficientsFromThreePoints(v1Ms: Double, y1: Double, v2Ms: Double, y2: Double, v3Ms: Double, y3: Double): Coeff {
        // Solve for a + b*v + c*v^2 matching three points using Cramer's rule
        val x1 = v1Ms; val x2 = v2Ms; val x3 = v3Ms
        val d = det(1.0, x1, x1 * x1, 1.0, x2, x2 * x2, 1.0, x3, x3 * x3)
        val da = det(y1, x1, x1 * x1, y2, x2, x2 * x2, y3, x3, x3 * x3)
        val db = det(1.0, y1, x1 * x1, 1.0, y2, x2 * x2, 1.0, y3, x3 * x3)
        val dc = det(1.0, x1, y1 * x1, 1.0, x2, y2 * x2, 1.0, x3, y3 * x3)
        return Coeff(a = da / d, b = db / d, c = dc / d)
    }

    private fun det(a: Double, b: Double, c: Double, d: Double, e: Double, f: Double, g: Double, h: Double, i: Double): Double {
        return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    }

    private fun wingLoading(model: GliderModel, config: GliderConfig): Double {
        val area = model.wingAreaM2 ?: return 0.0
        val empty = (model.emptyWeightKg ?: 0).toDouble()
        val gross = empty + config.pilotAndGearKg + config.waterBallastKg
        return gross / area
    }

    private fun sinkFromPoints(points: List<PolarPoint>, vKmh: Double): Double {
        val sorted = points.sortedBy { it.kmh }
        val first = sorted.first()
        val last = sorted.last()
        return when {
            vKmh <= first.kmh -> first.sinkMs
            vKmh >= last.kmh -> last.sinkMs
            else -> {
                val idx = sorted.indexOfLast { it.kmh <= vKmh }.coerceAtLeast(0)
                val p0 = sorted[idx]
                val p1 = sorted[idx + 1]
                val t = ((vKmh - p0.kmh) / (p1.kmh - p0.kmh)).coerceIn(0.0, 1.0)
                p0.sinkMs + t * (p1.sinkMs - p0.sinkMs)
            }
        }
    }
}
