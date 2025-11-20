package com.example.xcpro.sensors

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Mirrors XCSoar's circling detector heuristics (turn-rate threshold with
 * enter/exit timers) so calculations that depend on circling mode (thermal
 * averages, wind, etc.) can share a single source of truth.
 */
internal class CirclingDetector {

    private var lastTrackRad: Double? = null
    private var lastTrackTimestamp: Long = 0L
    private var lastSampleTimestamp: Long = 0L
    private var accumulatorMs = 0.0
    private var isCircling = false

    fun reset() {
        lastTrackRad = null
        lastTrackTimestamp = 0L
        lastSampleTimestamp = 0L
        accumulatorMs = 0.0
        isCircling = false
    }

    fun update(trackDegrees: Double, timestampMillis: Long, groundSpeed: Double): Boolean {
        val trackRad = Math.toRadians(trackDegrees)
        val turnRate = computeTurnRate(trackRad, timestampMillis)
        val instantCircling = turnRate != null &&
            abs(turnRate) >= MIN_TURN_RATE &&
            groundSpeed >= MIN_GROUND_SPEED

        val previousSample = lastSampleTimestamp
        lastSampleTimestamp = timestampMillis
        val deltaMs = if (previousSample == 0L || timestampMillis <= previousSample) {
            0.0
        } else {
            (timestampMillis - previousSample).toDouble()
        }

        accumulatorMs = if (instantCircling) {
            min(accumulatorMs + deltaMs, MAX_ACCUM_MS)
        } else {
            max(0.0, accumulatorMs - deltaMs)
        }

        isCircling = if (isCircling) {
            accumulatorMs >= EXIT_THRESHOLD_MS
        } else {
            accumulatorMs >= ENTER_THRESHOLD_MS
        }

        return isCircling
    }

    private fun computeTurnRate(trackRad: Double, timestampMillis: Long): Double? {
        val previousTrack = lastTrackRad
        val previousTimestamp = lastTrackTimestamp
        lastTrackRad = trackRad
        lastTrackTimestamp = timestampMillis

        if (previousTrack == null || previousTimestamp == 0L) {
            return null
        }

        val dt = (timestampMillis - previousTimestamp) / 1000.0
        if (dt <= 0.01) {
            return null
        }

        var delta = trackRad - previousTrack
        while (delta <= -PI) delta += 2 * PI
        while (delta > PI) delta -= 2 * PI
        return delta / dt
    }

    companion object {
        private const val MIN_GROUND_SPEED = 8.0  // m/s
        private const val MIN_TURN_RATE = 0.15    // rad/s (~8.6 deg/s)
        private const val ENTER_THRESHOLD_MS = 3_500.0
        private const val EXIT_THRESHOLD_MS = 1_500.0
        private const val MAX_ACCUM_MS = 8_000.0
    }
}
