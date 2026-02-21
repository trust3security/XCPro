package com.example.xcpro.map.replay

import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.replay.IgcLog
import com.example.xcpro.replay.IgcMetadata
import com.example.xcpro.replay.IgcPoint
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import kotlin.math.ceil
import kotlin.math.max

/**
 * Builds a deterministic synthetic replay path for a racing task.
 * This is intended for debug validation and uses simple straight-line legs.
 */
class RacingReplayLogBuilder(
    private val stepMillis: Long = DEFAULT_STEP_MS,
    private val altitudeMeters: Double = DEFAULT_ALTITUDE_M,
    private val targetSpeedKmh: Double = DEFAULT_TARGET_SPEED_KMH,
    private val epsilonPolicy: RacingBoundaryEpsilonPolicy = RacingBoundaryEpsilonPolicy()
) {

    private val anchorBuilder = RacingReplayAnchorBuilder(epsilonPolicy = epsilonPolicy)

    fun build(
        task: SimpleRacingTask,
        startTimestampMillis: Long = DEFAULT_START_TIME_MS,
        stepMillisOverride: Long? = null,
        targetSpeedKmhOverride: Double? = null,
        logPoints: Boolean = false
    ): IgcLog {
        val waypoints = task.waypoints
        require(waypoints.size >= 2) { "Racing replay requires at least 2 waypoints" }

        val stepMillis = stepMillisOverride ?: this.stepMillis
        require(stepMillis > 0L) { "Replay step must be > 0ms" }
        val speedKmh = targetSpeedKmhOverride ?: targetSpeedKmh
        require(speedKmh.isFinite() && speedKmh > 0.0) { "Replay speed must be > 0 km/h" }

        val anchors = mutableListOf<Anchor>()
        fun addAnchor(lat: Double, lon: Double) {
            anchors += Anchor(lat, lon)
        }

        val start = waypoints.first()
        val next = waypoints.getOrNull(1)
        if (next != null) {
            anchorBuilder.appendStartSegment(start, next, ::addAnchor)
        }

        if (waypoints.size > 2) {
            for (index in 1 until waypoints.lastIndex) {
                val prev = waypoints[index - 1]
                val current = waypoints[index]
                val upcoming = waypoints.getOrNull(index + 1)
                anchorBuilder.appendTurnpointSegment(prev, current, upcoming, ::addAnchor)
            }
        }

        val finish = waypoints.last()
        val prev = waypoints[waypoints.lastIndex - 1]
        anchorBuilder.appendFinishSegment(prev, finish, ::addAnchor)

        if (anchors.isEmpty()) {
            throw IllegalStateException("Racing replay contains no anchor points")
        }

        val points = mutableListOf<IgcPoint>()
        var timestamp = startTimestampMillis
        val speedMs = speedKmh / 3.6

        fun addPoint(lat: Double, lon: Double) {
            points += IgcPoint(
                timestampMillis = timestamp,
                latitude = lat,
                longitude = lon,
                gpsAltitude = altitudeMeters,
                pressureAltitude = altitudeMeters
            )
        }

        val first = anchors.first()
        addPoint(first.lat, first.lon)

        for (index in 1 until anchors.size) {
            timestamp = appendSegmentPoints(
                from = anchors[index - 1],
                to = anchors[index],
                startTimestampMillis = timestamp,
                speedMs = speedMs,
                stepMillis = stepMillis,
                points = points
            )
        }

        if (logPoints && BuildConfig.DEBUG) {
            logReplayPoints(points, startTimestampMillis, speedKmh, stepMillis)
        }

        return IgcLog(
            metadata = IgcMetadata(qnhHpa = DEFAULT_QNH_HPA),
            points = points
        )
    }

    private fun appendSegmentPoints(
        from: Anchor,
        to: Anchor,
        startTimestampMillis: Long,
        speedMs: Double,
        stepMillis: Long,
        points: MutableList<IgcPoint>
    ): Long {
        val distanceMeters = RacingGeometryUtils.haversineDistance(
            from.lat,
            from.lon,
            to.lat,
            to.lon
        ) * 1000.0
        val safeDistance = distanceMeters.coerceAtLeast(0.0)
        val expectedDurationMs = if (safeDistance == 0.0) {
            stepMillis.toDouble()
        } else {
            (safeDistance / speedMs * 1000.0).coerceAtLeast(stepMillis.toDouble())
        }
        val steps = max(1, ceil(expectedDurationMs / stepMillis.toDouble()).toInt())
        val bearing = RacingGeometryUtils.calculateBearing(from.lat, from.lon, to.lat, to.lon)
        var timestamp = startTimestampMillis
        for (step in 1..steps) {
            val fraction = step.toDouble() / steps.toDouble()
            val (lat, lon) = if (step == steps) {
                to.lat to to.lon
            } else {
                val distanceAlong = safeDistance * fraction
                RacingGeometryUtils.calculateDestinationPoint(from.lat, from.lon, bearing, distanceAlong)
            }
            timestamp += stepMillis
            points += IgcPoint(
                timestampMillis = timestamp,
                latitude = lat,
                longitude = lon,
                gpsAltitude = altitudeMeters,
                pressureAltitude = altitudeMeters
            )
        }
        return timestamp
    }

    private fun logReplayPoints(
        points: List<IgcPoint>,
        startTimestampMillis: Long,
        speedKmh: Double,
        stepMillis: Long
    ) {
        AppLogger.i(TAG, "Racing replay log points=${points.size} speed=${"%.1f".format(speedKmh)}km/h step=${stepMillis}ms")
        points.forEachIndexed { index, point ->
            val offset = point.timestampMillis - startTimestampMillis
            AppLogger.i(
                TAG,
                "REPLAY_FIX idx=$index t=${offset}ms lat=${point.latitude} lon=${point.longitude}"
            )
        }
    }

    private companion object {
        private const val TAG = "RACING_REPLAY"
        private const val DEFAULT_ALTITUDE_M = 1000.0
        private const val DEFAULT_STEP_MS = 1_000L
        private const val DEFAULT_TARGET_SPEED_KMH = 400.0
        private const val DEFAULT_START_TIME_MS = 1735689600000L // 2025-01-01T00:00:00Z
        private const val DEFAULT_QNH_HPA = 1013.25
    }

    private data class Anchor(val lat: Double, val lon: Double)
}
