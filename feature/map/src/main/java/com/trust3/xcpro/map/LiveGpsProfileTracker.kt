package com.trust3.xcpro.map

import com.trust3.xcpro.sensors.GPSData
import kotlin.math.roundToLong

internal data class LiveGpsProfile(
    val stepMs: Long,
    val accuracyM: Float,
    val sampleCount: Int
)

internal class LiveGpsProfileTracker(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val minSamples: Int = DEFAULT_MIN_SAMPLES,
    private val minStepMs: Long = DEFAULT_MIN_STEP_MS,
    private val maxStepMs: Long = DEFAULT_MAX_STEP_MS
) {
    private val stepSamples = LongArray(capacity)
    private val accuracySamples = FloatArray(capacity)
    private var stepCount = 0
    private var accuracyCount = 0
    private var stepIndex = 0
    private var accuracyIndex = 0
    private var lastTimestampMs: Long? = null

    fun record(gps: GPSData) {
        val timestampMs = gps.timeForCalculationsMillis
        lastTimestampMs?.let { previous ->
            val dt = (timestampMs - previous).coerceAtLeast(0L)
            if (dt in minStepMs..maxStepMs) {
                stepSamples[stepIndex] = dt
                stepIndex = (stepIndex + 1) % capacity
                if (stepCount < capacity) stepCount += 1
            }
        }
        lastTimestampMs = timestampMs

        val accuracy = gps.accuracy
        if (accuracy.isFinite() && accuracy > 0f) {
            accuracySamples[accuracyIndex] = accuracy
            accuracyIndex = (accuracyIndex + 1) % capacity
            if (accuracyCount < capacity) accuracyCount += 1
        }
    }

    fun snapshot(): LiveGpsProfile? {
        if (stepCount < minSamples) return null
        val stepMs = medianLong(stepSamples, stepCount)
        val accuracy = if (accuracyCount > 0) medianFloat(accuracySamples, accuracyCount) else DEFAULT_ACCURACY_M
        val clampedAccuracy = accuracy.coerceIn(MIN_ACCURACY_M, MAX_ACCURACY_M)
        return LiveGpsProfile(
            stepMs = stepMs,
            accuracyM = clampedAccuracy,
            sampleCount = stepCount
        )
    }

    private fun medianLong(samples: LongArray, count: Int): Long {
        val copy = LongArray(count)
        for (i in 0 until count) copy[i] = samples[i]
        copy.sort()
        val mid = count / 2
        return if (count % 2 == 1) {
            copy[mid]
        } else {
            ((copy[mid - 1] + copy[mid]) / 2.0).roundToLong()
        }
    }

    private fun medianFloat(samples: FloatArray, count: Int): Float {
        val copy = FloatArray(count)
        for (i in 0 until count) copy[i] = samples[i]
        copy.sort()
        val mid = count / 2
        return if (count % 2 == 1) {
            copy[mid]
        } else {
            (copy[mid - 1] + copy[mid]) / 2f
        }
    }

    companion object {
        private const val DEFAULT_CAPACITY = 60
        private const val DEFAULT_MIN_SAMPLES = 12
        private const val DEFAULT_MIN_STEP_MS = 100L
        private const val DEFAULT_MAX_STEP_MS = 5_000L
        private const val DEFAULT_ACCURACY_M = 5f
        private const val MIN_ACCURACY_M = 1f
        private const val MAX_ACCURACY_M = 50f
    }
}
