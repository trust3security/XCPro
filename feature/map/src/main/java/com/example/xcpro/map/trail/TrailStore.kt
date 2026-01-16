// Role: Store and thin snail-trail points for rendering.
// Invariants: Points are time-ordered, thinned to a bounded size, and sampled at >= 2s.
package com.example.xcpro.map.trail

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class TrailSample(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val altitudeMeters: Double,
    val varioMs: Double
)

data class TrailPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val altitudeMeters: Double,
    val varioMs: Double,
    val driftFactor: Double
)

class TrailStore(
    private val maxSize: Int = 1024,
    private val minDeltaMillis: Long = 2_000L,
    private val noThinMillis: Long = 2 * 60_000L
) {
    private val points = ArrayList<TrailPoint>(maxSize)

    private val optSize: Int = (3 * maxSize) / 4
    private val clearThresholdMillis: Long = 3 * 60_000L
    private val fixThresholdMillis: Long = 10_000L

    fun clear() {
        points.clear()
    }

    fun snapshot(): List<TrailPoint> = points.toList()

    fun latestTimestampMillis(): Long? = points.lastOrNull()?.timestampMillis

    fun addSample(sample: TrailSample): Boolean {
        if (!TrailGeo.isValidCoordinate(sample.latitude, sample.longitude)) {
            return false
        }
        if (!sample.timestampMillis.isFiniteTimestamp()) {
            return false
        }

        val last = points.lastOrNull()
        if (last != null) {
            val dt = sample.timestampMillis - last.timestampMillis
            if (dt < 0) {
                if (sample.timestampMillis + clearThresholdMillis < last.timestampMillis) {
                    clear()
                    return false
                }
                trimLaterThan(sample.timestampMillis - fixThresholdMillis)
            } else if (dt < minDeltaMillis) {
                return false
            }
        }

        if (points.size >= maxSize) {
            thin()
        }

        val driftFactor = sigmoid(sample.altitudeMeters / 100.0)
        points.add(
            TrailPoint(
                latitude = sample.latitude,
                longitude = sample.longitude,
                timestampMillis = sample.timestampMillis,
                altitudeMeters = sample.altitudeMeters,
                varioMs = sample.varioMs,
                driftFactor = driftFactor
            )
        )
        return true
    }

    private fun trimLaterThan(minTimestamp: Long) {
        if (points.isEmpty()) return
        var index = points.size - 1
        while (index >= 0 && points[index].timestampMillis > minTimestamp) {
            points.removeAt(index)
            index--
        }
    }

    private fun thin() {
        if (points.size <= optSize) return
        val recentCutoff = points.lastOrNull()?.timestampMillis?.minus(noThinMillis)
        while (points.size > optSize && removeBestCandidate(recentCutoff)) {
            // continue trimming
        }
        if (points.size > optSize) {
            while (points.size > optSize && removeBestCandidate(null)) {
                // continue trimming
            }
        }
    }

    private fun removeBestCandidate(recentCutoff: Long?): Boolean {
        if (points.size <= 2) return false
        var bestIndex = -1
        var bestDistance = Double.MAX_VALUE
        var bestTime = Double.MAX_VALUE
        var bestTimestamp = Long.MAX_VALUE

        for (i in 1 until points.size - 1) {
            val point = points[i]
            if (recentCutoff != null && point.timestampMillis >= recentCutoff) {
                continue
            }
            val prev = points[i - 1]
            val next = points[i + 1]
            val distanceMetric = distanceMetric(prev, point, next)
            val timeMetric = timeMetric(prev, point, next)
            val timestamp = point.timestampMillis
            if (distanceMetric < bestDistance ||
                (distanceMetric == bestDistance && timeMetric < bestTime) ||
                (distanceMetric == bestDistance && timeMetric == bestTime && timestamp < bestTimestamp)
            ) {
                bestIndex = i
                bestDistance = distanceMetric
                bestTime = timeMetric
                bestTimestamp = timestamp
            }
        }

        if (bestIndex >= 0) {
            points.removeAt(bestIndex)
            return true
        }
        return false
    }

    private fun distanceMetric(prev: TrailPoint, point: TrailPoint, next: TrailPoint): Double {
        val distPrev = TrailGeo.distanceMeters(prev.latitude, prev.longitude, point.latitude, point.longitude)
        val distNext = TrailGeo.distanceMeters(point.latitude, point.longitude, next.latitude, next.longitude)
        val distSkip = TrailGeo.distanceMeters(prev.latitude, prev.longitude, next.latitude, next.longitude)
        return abs((distPrev + distNext) - distSkip)
    }

    private fun timeMetric(prev: TrailPoint, point: TrailPoint, next: TrailPoint): Double {
        val total = (next.timestampMillis - prev.timestampMillis).toDouble()
        val left = (point.timestampMillis - prev.timestampMillis).toDouble()
        val right = (next.timestampMillis - point.timestampMillis).toDouble()
        return max(0.0, total - min(left, right))
    }

    private fun sigmoid(x: Double): Double {
        return 2.0 / (1.0 + exp(-x)) - 1.0
    }

    private fun Long.isFiniteTimestamp(): Boolean = this > 0L && this < Long.MAX_VALUE
}
