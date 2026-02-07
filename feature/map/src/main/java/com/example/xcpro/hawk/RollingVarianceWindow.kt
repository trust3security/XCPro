package com.example.xcpro.hawk

import kotlin.math.max

internal class RollingVarianceWindow(maxSamples: Int) {
    private val capacity = max(1, maxSamples)
    private val values = ArrayDeque<Double>(capacity)
    private var sum = 0.0
    private var sumSquares = 0.0

    fun add(value: Double) {
        if (!value.isFinite()) return
        if (values.size == capacity) {
            val removed = values.removeFirst()
            sum -= removed
            sumSquares -= removed * removed
        }
        values.addLast(value)
        sum += value
        sumSquares += value * value
    }

    fun variance(): Double? {
        val count = values.size
        if (count < 2) return null
        val mean = sum / count
        val raw = (sumSquares / count) - (mean * mean)
        return raw.coerceAtLeast(0.0)
    }

    fun clear() {
        values.clear()
        sum = 0.0
        sumSquares = 0.0
    }

    fun size(): Int = values.size
}
