// Role: Keep bounded display-pose points for visual-only snail trail rendering.
// Invariants: Never mutates authoritative TrailStore or replay data.
package com.trust3.xcpro.map.trail

import com.trust3.xcpro.map.trail.domain.TrailTimeBase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class SnailTrailDisplayStore(
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    private val maxPoints: Int = DEFAULT_MAX_POINTS,
    private val optPoints: Int = (3 * maxPoints) / 4,
    private val noThinMillis: Long = DEFAULT_NO_THIN_MILLIS,
    private val maxGapMillis: Long = DEFAULT_MAX_GAP_MILLIS,
    private val maxJumpMeters: Double = DEFAULT_MAX_JUMP_METERS
) {
    private val points = ArrayList<RenderPoint>(maxPoints.coerceAtLeast(0))
    private var timeBase: TrailTimeBase? = null
    private var latestRawPoint: TrailPoint? = null
    private var lastFrameId: Long? = null

    fun updateRawState(
        rawPoints: List<TrailPoint>,
        rawTimeBase: TrailTimeBase,
        isReplay: Boolean,
        trailLength: TrailLength
    ) {
        if (trailLength == TrailLength.OFF) {
            clear()
            return
        }

        if (timeBase != null && timeBase != rawTimeBase) {
            clear()
        }
        timeBase = rawTimeBase

        val rawPoint = rawPoints.lastOrNull()
        if (rawPoint == null) {
            clear()
            timeBase = rawTimeBase
            return
        }
        latestRawPoint = rawPoint
        applyRetention(trailLength, rawPoint.timestampMillis)
    }

    fun appendDisplayPose(
        location: TrailGeoPoint,
        timestampMillis: Long,
        poseTimeBase: TrailTimeBase,
        trailLength: TrailLength,
        frameId: Long?,
        minStepMillis: Long,
        minDistanceMeters: Double
    ): Boolean {
        if (trailLength == TrailLength.OFF) {
            clear()
            return false
        }
        if (timeBase != null && timeBase != poseTimeBase) {
            clear()
            timeBase = poseTimeBase
            return false
        }
        if (!TrailGeo.isValidCoordinate(location.latitude, location.longitude)) {
            return false
        }
        if (timestampMillis <= 0L) {
            return false
        }
        if (frameId != null && lastFrameId == frameId) {
            return false
        }

        val rawPoint = latestRawPoint ?: return false
        val lastPoint = points.lastOrNull()
        if (lastPoint != null) {
            val dt = timestampMillis - lastPoint.timestampMillis
            if (dt < 0L) {
                clear()
                return false
            }
            val distance = TrailGeo.distanceMeters(
                lastPoint.latitude,
                lastPoint.longitude,
                location.latitude,
                location.longitude
            )
            if (dt > maxGapMillis || distance > maxJumpMeters) {
                points.clear()
            } else if (dt < minStepMillis && (minDistanceMeters <= 0.0 || distance < minDistanceMeters)) {
                return false
            }
        }

        points.add(
            RenderPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = rawPoint.altitudeMeters,
                varioMs = rawPoint.varioMs,
                timestampMillis = timestampMillis
            )
        )
        if (frameId != null) {
            lastFrameId = frameId
        }
        timeBase = poseTimeBase
        applyRetention(trailLength, timestampMillis)
        return true
    }

    fun snapshot(): List<RenderPoint> = points.toList()

    fun latestPoint(): RenderPoint? = points.lastOrNull()

    fun applyRetention(trailLength: TrailLength, nowMillis: Long) {
        val settingsMinTimestamp = when (trailLength) {
            TrailLength.FULL -> null
            TrailLength.LONG -> nowMillis - 60 * 60_000L
            TrailLength.MEDIUM -> nowMillis - 30 * 60_000L
            TrailLength.SHORT -> nowMillis - 10 * 60_000L
            TrailLength.OFF -> {
                points.clear()
                return
            }
        }
        val displayMinTimestamp = if (maxAgeMillis > 0L) {
            nowMillis - maxAgeMillis
        } else {
            null
        }
        val minTimestamp = listOfNotNull(settingsMinTimestamp, displayMinTimestamp).maxOrNull()
        if (minTimestamp != null) {
            while (points.isNotEmpty() && points.first().timestampMillis < minTimestamp) {
                points.removeAt(0)
            }
        }
        if (maxPoints <= 0) {
            points.clear()
            return
        }
        if (points.size > maxPoints) {
            thin(nowMillis)
        }
    }

    fun clear() {
        points.clear()
        latestRawPoint = null
        lastFrameId = null
        timeBase = null
    }

    private fun thin(nowMillis: Long) {
        val targetSize = optPoints.coerceIn(2, maxPoints.coerceAtLeast(2))
        val protectedCutoff = nowMillis - noThinMillis
        while (points.size > targetSize && removeBestCandidate(protectedCutoff)) {
            // Keep newest points at full fidelity where possible.
        }
        while (points.size > targetSize && removeBestCandidate(null)) {
            // Fall back to whole-list thinning when the protected window is larger than the cap.
        }
        while (points.size > maxPoints) {
            points.removeAt(0)
        }
    }

    private fun removeBestCandidate(protectedCutoff: Long?): Boolean {
        if (points.size <= 2) return false
        var bestIndex = -1
        var bestDistance = Double.MAX_VALUE
        var bestTime = Double.MAX_VALUE
        var bestTimestamp = Long.MAX_VALUE

        for (index in 1 until points.size - 1) {
            val point = points[index]
            if (protectedCutoff != null && point.timestampMillis >= protectedCutoff) {
                continue
            }
            val prev = points[index - 1]
            val next = points[index + 1]
            val distanceMetric = distanceMetric(prev, point, next)
            val timeMetric = timeMetric(prev, point, next)
            val timestamp = point.timestampMillis
            if (distanceMetric < bestDistance ||
                (distanceMetric == bestDistance && timeMetric < bestTime) ||
                (distanceMetric == bestDistance && timeMetric == bestTime && timestamp < bestTimestamp)
            ) {
                bestIndex = index
                bestDistance = distanceMetric
                bestTime = timeMetric
                bestTimestamp = timestamp
            }
        }

        if (bestIndex < 0) return false
        points.removeAt(bestIndex)
        return true
    }

    private fun distanceMetric(prev: RenderPoint, point: RenderPoint, next: RenderPoint): Double {
        val distPrev = TrailGeo.distanceMeters(prev.latitude, prev.longitude, point.latitude, point.longitude)
        val distNext = TrailGeo.distanceMeters(point.latitude, point.longitude, next.latitude, next.longitude)
        val distSkip = TrailGeo.distanceMeters(prev.latitude, prev.longitude, next.latitude, next.longitude)
        return abs((distPrev + distNext) - distSkip)
    }

    private fun timeMetric(prev: RenderPoint, point: RenderPoint, next: RenderPoint): Double {
        val total = (next.timestampMillis - prev.timestampMillis).toDouble()
        val left = (point.timestampMillis - prev.timestampMillis).toDouble()
        val right = (next.timestampMillis - point.timestampMillis).toDouble()
        return max(0.0, total - min(left, right))
    }

    private companion object {
        private const val DEFAULT_MAX_AGE_MILLIS = 0L
        private const val DEFAULT_MAX_POINTS = 1024
        private const val DEFAULT_NO_THIN_MILLIS = 2 * 60_000L
        private const val DEFAULT_MAX_GAP_MILLIS = 2_000L
        private const val DEFAULT_MAX_JUMP_METERS = 250.0
    }
}
