package com.example.xcpro.sensors.domain

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import kotlin.math.abs

internal data class SpeedToFlyResult(
    val targetIasMs: Double,
    val deltaIasMs: Double,
    val valid: Boolean,
    val mcSourceAuto: Boolean
)

internal class SpeedToFlyCalculator(
    private val sinkProvider: StillAirSinkProvider
) {
    private var lastOutputMs: Double? = null
    private var lastUpdateMs: Long = 0L

    fun update(input: SpeedToFlyInput): SpeedToFlyResult {
        val bounds = input.iasBounds ?: return SpeedToFlyResult(
            targetIasMs = 0.0,
            deltaIasMs = 0.0,
            valid = false,
            mcSourceAuto = input.mcSourceAuto
        )

        val baseMc = input.mcBaseMs.coerceAtLeast(0.0)
        val glideNettoUsed = resolveGlideNetto(input, baseMc)
        val mcEff = (baseMc - glideNettoUsed).coerceIn(MC_MIN_EFF, MC_MAX)

        val targetRaw = findBestSpeed(bounds, mcEff)
        if (!targetRaw.isFinite()) {
            return SpeedToFlyResult(
                targetIasMs = 0.0,
                deltaIasMs = 0.0,
                valid = false,
                mcSourceAuto = input.mcSourceAuto
            )
        }

        val smoothed = smooth(targetRaw, input.currentTimeMillis)
        val delta = if (input.currentIasMs.isFinite()) {
            smoothed - input.currentIasMs
        } else {
            0.0
        }

        return SpeedToFlyResult(
            targetIasMs = smoothed,
            deltaIasMs = delta,
            valid = true,
            mcSourceAuto = input.mcSourceAuto
        )
    }

    fun reset() {
        lastOutputMs = null
        lastUpdateMs = 0L
    }

    private fun resolveGlideNetto(input: SpeedToFlyInput, mcBase: Double): Double {
        if (input.flightMode == FlightMode.FINAL_GLIDE) return 0.0
        if (!input.glideNettoValid) return 0.0

        val alpha = when {
            input.windConfidence <= CONF_LOW -> 0.0
            input.windConfidence >= CONF_HIGH -> 1.0
            else -> (input.windConfidence - CONF_LOW) / (CONF_HIGH - CONF_LOW)
        }
        if (alpha <= 0.0) return 0.0
        return input.glideNettoMs * alpha
    }

    private fun findBestSpeed(bounds: SpeedBoundsMs, mcEff: Double): Double {
        val stepMs = UnitsConverter.knotsToMs(1.0)
        var speed = bounds.minMs
        var bestSpeed = bounds.minMs
        var bestScore = Double.POSITIVE_INFINITY
        val useBestLd = mcEff <= MC_MIN_EFF + 1e-3

        while (speed <= bounds.maxMs + 1e-6) {
            val sink = sinkProvider.sinkAtSpeed(speed)
                ?.takeIf { it.isFinite() }
                ?: return Double.NaN
            val sinkDown = sink.coerceAtLeast(0.0)
            val score = if (useBestLd) {
                sinkDown / speed.coerceAtLeast(0.1)
            } else {
                (1.0 / speed.coerceAtLeast(0.1)) * (1.0 + sinkDown / mcEff)
            }
            if (score < bestScore) {
                bestScore = score
                bestSpeed = speed
            }
            speed += stepMs
        }

        return bestSpeed
    }

    private fun smooth(target: Double, nowMs: Long): Double {
        val last = lastOutputMs
        if (last == null || lastUpdateMs <= 0L || nowMs <= lastUpdateMs) {
            lastOutputMs = target
            lastUpdateMs = nowMs
            return target
        }
        val dt = (nowMs - lastUpdateMs) / 1000.0
        val tau = OUTPUT_TAU_SECONDS
        val alpha = (dt / (tau + dt)).coerceIn(0.0, 1.0)
        val smoothed = last + alpha * (target - last)
        val maxStep = RATE_LIMIT_MS_PER_S * dt
        val limited = if (abs(smoothed - last) > maxStep) {
            last + maxStep * kotlin.math.sign(smoothed - last)
        } else {
            smoothed
        }
        lastOutputMs = limited
        lastUpdateMs = nowMs
        return limited
    }

    companion object {
        private const val MC_MIN_EFF = 0.1
        private const val MC_MAX = 4.0
        private const val CONF_LOW = 0.35
        private const val CONF_HIGH = 0.70
        private const val OUTPUT_TAU_SECONDS = 3.0
        private val RATE_LIMIT_MS_PER_S = UnitsConverter.knotsToMs(2.0)
    }
}

internal data class SpeedToFlyInput(
    val currentTimeMillis: Long,
    val currentIasMs: Double,
    val mcBaseMs: Double,
    val mcSourceAuto: Boolean,
    val glideNettoMs: Double,
    val glideNettoValid: Boolean,
    val windConfidence: Double,
    val flightMode: FlightMode,
    val iasBounds: SpeedBoundsMs?
)
