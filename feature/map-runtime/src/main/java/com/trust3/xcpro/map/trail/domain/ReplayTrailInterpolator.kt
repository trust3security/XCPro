package com.trust3.xcpro.map.trail.domain

import com.trust3.xcpro.map.trail.TrailGeo
import com.trust3.xcpro.map.trail.TrailSample

/**
 * Expands replay samples to a steady cadence and enforces monotonic timestamps.
 */
internal class ReplayTrailInterpolator(
    private val stepMillis: Long = DEFAULT_STEP_MS,
    private val resetDistanceM: Double = DEFAULT_RESET_DISTANCE_M,
    private val resetBackstepMs: Long = DEFAULT_RESET_BACKSTEP_MS
) {
    private var lastSample: TrailSample? = null
    private var lastAdjustedTimestamp: Long? = null
    private var lastRawTimestamp: Long? = null

    fun reset() {
        lastSample = null
        lastAdjustedTimestamp = null
        lastRawTimestamp = null
    }

    fun shouldReset(sample: TrailSample): Boolean {
        val last = lastSample ?: return false
        val backstep = last.timestampMillis - sample.timestampMillis
        if (backstep > resetBackstepMs) return true
        val rawBackstep = lastRawTimestamp?.let { it - sample.timestampMillis } ?: 0L
        if (rawBackstep > resetBackstepMs) return true
        val distance = TrailGeo.distanceMeters(
            last.latitude,
            last.longitude,
            sample.latitude,
            sample.longitude
        )
        return distance >= resetDistanceM
    }

    fun expand(sample: TrailSample): List<TrailSample> {
        val adjusted = adjustSample(sample)
        val previous = lastSample
        val expanded = ArrayList<TrailSample>(4)

        if (previous != null && adjusted.timestampMillis > previous.timestampMillis) {
            val dt = adjusted.timestampMillis - previous.timestampMillis
            val distance = TrailGeo.distanceMeters(
                previous.latitude,
                previous.longitude,
                adjusted.latitude,
                adjusted.longitude
            )
            if (distance > INTERPOLATION_DISTANCE_MIN_M) {
                val steps = (dt / stepMillis).coerceAtLeast(1L).toInt()
                if (steps > 1) {
                    val total = steps.toFloat()
                    for (i in 1 until steps) {
                        val t = i / total
                        val ts = previous.timestampMillis + (dt * i / steps)
                        expanded.add(
                            TrailSample(
                                latitude = lerp(previous.latitude, adjusted.latitude, t),
                                longitude = lerp(previous.longitude, adjusted.longitude, t),
                                timestampMillis = ts,
                                altitudeMeters = lerp(previous.altitudeMeters, adjusted.altitudeMeters, t),
                                varioMs = lerp(previous.varioMs, adjusted.varioMs, t),
                                windSpeedMs = lerp(previous.windSpeedMs, adjusted.windSpeedMs, t),
                                windDirectionFromDeg = lerpAngleDeg(
                                    previous.windDirectionFromDeg,
                                    adjusted.windDirectionFromDeg,
                                    t
                                )
                            )
                        )
                    }
                }
            }
        }

        expanded.add(adjusted)
        lastSample = adjusted
        lastRawTimestamp = sample.timestampMillis
        return expanded
    }

    private fun adjustSample(sample: TrailSample): TrailSample {
        val lastAdjusted = lastAdjustedTimestamp
        val adjustedTimestamp = if (lastAdjusted == null) {
            sample.timestampMillis
        } else if (sample.timestampMillis <= lastAdjusted) {
            lastAdjusted + stepMillis
        } else {
            sample.timestampMillis
        }
        lastAdjustedTimestamp = adjustedTimestamp
        return if (adjustedTimestamp == sample.timestampMillis) {
            sample
        } else {
            sample.copy(timestampMillis = adjustedTimestamp)
        }
    }

    private fun lerp(start: Double, end: Double, t: Float): Double =
        start + (end - start) * t

    private fun lerpAngleDeg(start: Double, end: Double, t: Float): Double {
        if (!start.isFinite() || !end.isFinite()) {
            return if (start.isFinite()) start else end
        }
        val delta = ((end - start + 540.0) % 360.0) - 180.0
        return (start + delta * t + 360.0) % 360.0
    }

    private companion object {
        private const val DEFAULT_STEP_MS = 250L
        private const val DEFAULT_RESET_DISTANCE_M = 2_000.0
        private const val DEFAULT_RESET_BACKSTEP_MS = 2_000L
        private const val INTERPOLATION_DISTANCE_MIN_M = 0.5
    }
}
