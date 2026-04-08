package com.example.xcpro.core.flight.filters

import kotlin.math.abs
import kotlin.math.pow

class AdaptiveVarianceTracker(capacity: Int) {
    private val buffer = DoubleArray(capacity.coerceAtLeast(1))
    private var size = 0
    private var index = 0

    fun add(value: Double) {
        buffer[index] = value
        index = (index + 1) % buffer.size
        if (size < buffer.size) {
            size++
        }
    }

    fun variance(): Double {
        if (size == 0) return 0.0

        val snapshot = DoubleArray(size) { offset ->
            buffer[(index - size + offset + buffer.size) % buffer.size]
        }
        snapshot.sort()

        val median = if (snapshot.size % 2 == 0) {
            val mid = snapshot.size / 2
            (snapshot[mid - 1] + snapshot[mid]) / 2.0
        } else {
            snapshot[snapshot.size / 2]
        }

        val deviations = DoubleArray(size) { i -> abs(snapshot[i] - median) }
        deviations.sort()
        val mad = if (deviations.size % 2 == 0) {
            val mid = deviations.size / 2
            (deviations[mid - 1] + deviations[mid]) / 2.0
        } else {
            deviations[deviations.size / 2]
        }

        val sigma = 1.4826 * mad
        return sigma.pow(2.0)
    }

    fun reset() {
        size = 0
        index = 0
    }
}

class HighPassFilter(private val tauSeconds: Double) {
    private var initialized = false
    private var lastInput = 0.0
    private var lastOutput = 0.0

    fun update(value: Double, deltaTime: Double): Double {
        val dt = deltaTime.coerceAtLeast(1e-3)
        if (!initialized) {
            initialized = true
            lastInput = value
            lastOutput = 0.0
            return 0.0
        }

        val alpha = tauSeconds / (tauSeconds + dt)
        lastOutput = alpha * (lastOutput + value - lastInput)
        lastInput = value
        return lastOutput
    }
}
