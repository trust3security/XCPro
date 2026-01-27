package com.example.xcpro.map.trail.domain

import com.example.xcpro.sensors.CirclingDetector
import com.example.xcpro.sensors.CompleteFlightData

/**
 * Determines circling state for trail rendering, including replay fallbacks.
 */
internal class ResolveCirclingUseCase(
    private val detector: CirclingDetectorAdapter = DefaultCirclingDetectorAdapter()
) {
    private var lastReplayTimestamp: Long = Long.MIN_VALUE

    fun reset() {
        lastReplayTimestamp = Long.MIN_VALUE
        detector.reset()
    }

    fun resolve(data: CompleteFlightData, isReplay: Boolean): Boolean {
        if (!isReplay) return data.isCircling
        if (data.isCircling) return true
        if (data.currentThermalValid || data.thermalAverageValid) return true

        val timestamp = data.timestamp
        val track = data.gps?.bearing ?: Double.NaN
        if (timestamp <= 0L || !track.isFinite()) return false

        if (timestamp < lastReplayTimestamp) {
            detector.reset()
        }
        lastReplayTimestamp = timestamp
        return detector.update(
            trackDegrees = track,
            timestampMillis = timestamp,
            isFlying = true
        )
    }
}

internal interface CirclingDetectorAdapter {
    fun reset()
    fun update(trackDegrees: Double, timestampMillis: Long, isFlying: Boolean): Boolean
}

internal class DefaultCirclingDetectorAdapter(
    private val detector: CirclingDetector = CirclingDetector()
) : CirclingDetectorAdapter {
    override fun reset() {
        detector.reset()
    }

    override fun update(trackDegrees: Double, timestampMillis: Long, isFlying: Boolean): Boolean {
        return detector.update(trackDegrees, timestampMillis, isFlying).isCircling
    }
}
