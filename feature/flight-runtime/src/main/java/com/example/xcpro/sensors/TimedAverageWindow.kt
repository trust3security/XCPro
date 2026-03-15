package com.example.xcpro.sensors

import java.util.ArrayDeque

/**
 * Time-based moving average window. Maintains samples for the most recent
 * [windowMillis] period and returns their arithmetic mean.
 */
class TimedAverageWindow(
    private val windowMillis: Long
) {

    private val samples = ArrayDeque<Pair<Long, Double>>()
    private var sum = 0.0

    fun addSample(timestampMillis: Long, value: Double) {
        samples.addLast(timestampMillis to value)
        sum += value
        trim(timestampMillis)
    }

    fun average(): Double {
        return if (samples.isEmpty()) 0.0 else sum / samples.size
    }

    fun isEmpty(): Boolean = samples.isEmpty()

    fun clear() {
        samples.clear()
        sum = 0.0
    }

    private fun trim(currentTimestamp: Long) {
        val threshold = currentTimestamp - windowMillis
        while (samples.isNotEmpty() && samples.first().first < threshold) {
            val removed = samples.removeFirst()
            sum -= removed.second
        }
    }
}
