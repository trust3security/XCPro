package com.trust3.xcpro.sensors.domain

import kotlin.math.abs

internal data class AutoMcResult(
    val valueMs: Double,
    val valid: Boolean
)

internal class AutoMcCalculator {
    private val recentThermals = ArrayDeque<Double>()
    private var lastUpdateMs: Long = 0L
    private var lastValueMs: Double = 0.0
    private var lastCircling = false

    fun update(input: AutoMcInput): AutoMcResult {
        val isExit = lastCircling && !input.isCircling
        lastCircling = input.isCircling

        if (isExit && input.currentThermalValid && input.currentThermalLiftRate.isFinite()) {
            val lift = input.currentThermalLiftRate.coerceAtLeast(0.0)
            recordThermal(lift)
            val target = median(recentThermals)
            val limited = rateLimit(target, input.currentTimeMillis)
            lastValueMs = limited
            lastUpdateMs = input.currentTimeMillis
        }

        return AutoMcResult(
            valueMs = lastValueMs,
            valid = recentThermals.isNotEmpty()
        )
    }

    fun reset() {
        recentThermals.clear()
        lastUpdateMs = 0L
        lastValueMs = 0.0
        lastCircling = false
    }

    private fun recordThermal(value: Double) {
        recentThermals.addLast(value)
        while (recentThermals.size > MAX_THERMALS) {
            recentThermals.removeFirst()
        }
    }

    private fun median(values: Collection<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            0.5 * (sorted[mid - 1] + sorted[mid])
        }
    }

    private fun rateLimit(target: Double, nowMs: Long): Double {
        if (lastUpdateMs <= 0L || nowMs <= lastUpdateMs) return target
        val dtSeconds = (nowMs - lastUpdateMs) / 1000.0
        val maxDelta = MAX_DELTA_PER_SEC * dtSeconds
        val delta = target - lastValueMs
        return if (abs(delta) <= maxDelta) {
            target
        } else {
            lastValueMs + maxDelta * kotlin.math.sign(delta)
        }
    }

    companion object {
        private const val MAX_THERMALS = 6
        private const val MAX_DELTA_PER_SEC = 0.006 // 0.36 m/s per minute
    }
}

internal data class AutoMcInput(
    val currentTimeMillis: Long,
    val isCircling: Boolean,
    val currentThermalLiftRate: Double,
    val currentThermalValid: Boolean
)
