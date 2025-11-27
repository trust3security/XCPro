package com.example.xcpro.sensors

/**
 * Circular buffer that mirrors XCSoar's WindowFilter<30> semantics.
 * Keeps a fixed number of samples and overwrites the oldest entry
 * once the buffer is full, exposing the arithmetic mean of the window.
 */
class FixedSampleAverageWindow(
    private val capacity: Int
) {

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val samples = DoubleArray(capacity)
    private var count = 0
    private var headIndex = 0
    private var sum = 0.0

    fun addSample(value: Double) {
        if (count < capacity) {
            samples[count] = value
            sum += value
            count++
            return
        }

        val replaced = samples[headIndex]
        samples[headIndex] = value
        sum += value - replaced
        headIndex = (headIndex + 1) % capacity
    }

    fun seed(value: Double) {
        clear()
        repeat(capacity) { index ->
            samples[index] = value
        }
        count = capacity
        headIndex = 0
        sum = value * capacity
    }

    fun average(): Double = if (count == 0) 0.0 else sum / count

    fun isEmpty(): Boolean = count == 0

    fun count(): Int = count

    fun clear() {
        count = 0
        headIndex = 0
        sum = 0.0
    }
}
