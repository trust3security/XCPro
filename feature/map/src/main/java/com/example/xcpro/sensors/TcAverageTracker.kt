package com.example.xcpro.sensors

import com.example.xcpro.sensors.addSamplesForElapsedSeconds

/**
 * TC 30s tracker. Holds the last 30 one-second samples gathered only while circling.
 * When circling stops, the buffer is retained so the last average remains visible until circling resumes.
 */
internal class TcAverageTracker(
    capacitySeconds: Int = 30
) {
    private val window = FixedSampleAverageWindow(capacitySeconds)
    private var lastTimestamp = 0L
    private var lastIsCircling = false

    fun reset() {
        window.clear()
        lastTimestamp = 0L
        lastIsCircling = false
    }

    fun update(timestampMillis: Long, sample: Double, isCircling: Boolean) {
        val timeWentBack = timestampMillis < lastTimestamp
        val toggled = isCircling != lastIsCircling

        if (timeWentBack || toggled) {
            if (isCircling) {
                window.seed(sample)
            }
            lastTimestamp = timestampMillis
            lastIsCircling = isCircling
            return
        }

        if (!isCircling) {
            lastTimestamp = timestampMillis
            lastIsCircling = false
            return
        }

        lastTimestamp = addSamplesForElapsedSeconds(
            window = window,
            lastTimestamp = lastTimestamp,
            currentTime = timestampMillis,
            sampleValue = sample
        )
        lastIsCircling = true
    }

    fun average(): Double = window.average()

    fun isValid(): Boolean = !window.isEmpty()
}
