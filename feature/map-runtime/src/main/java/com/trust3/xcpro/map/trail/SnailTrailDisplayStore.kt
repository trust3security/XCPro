// Role: Keep recent display-pose points for live visual-only snail trail rendering.
// Invariants: Never mutates authoritative TrailStore or replay data.
package com.trust3.xcpro.map.trail

import com.trust3.xcpro.map.trail.domain.TrailTimeBase

internal class SnailTrailDisplayStore(
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    private val maxPoints: Int = DEFAULT_MAX_POINTS,
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
        if (isReplay || rawTimeBase == TrailTimeBase.REPLAY_IGC || trailLength == TrailLength.OFF) {
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
        if (poseTimeBase == TrailTimeBase.REPLAY_IGC) {
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
            } else if (dt < minStepMillis && distance < minDistanceMeters) {
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
        while (points.size > maxPoints) {
            points.removeAt(0)
        }
    }

    fun clear() {
        points.clear()
        latestRawPoint = null
        lastFrameId = null
        timeBase = null
    }

    private companion object {
        private const val DEFAULT_MAX_AGE_MILLIS = 60_000L
        private const val DEFAULT_MAX_POINTS = 600
        private const val DEFAULT_MAX_GAP_MILLIS = 2_000L
        private const val DEFAULT_MAX_JUMP_METERS = 250.0
    }
}
