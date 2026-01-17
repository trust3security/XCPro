package com.example.xcpro.map.replay

import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.replay.IgcLog
import com.example.xcpro.replay.IgcMetadata
import com.example.xcpro.replay.IgcPoint
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryGeometry
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.turnpoints.CylinderCalculator
import com.example.xcpro.tasks.racing.turnpoints.KeyholeCalculator
import com.example.xcpro.tasks.racing.turnpoints.TaskContext
import kotlin.math.abs
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

    private val cylinderCalculator = CylinderCalculator()
    private val keyholeCalculator = KeyholeCalculator()

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
            appendStartSegment(start, next, ::addAnchor)
        }

        if (waypoints.size > 2) {
            for (index in 1 until waypoints.lastIndex) {
                val prev = waypoints[index - 1]
                val current = waypoints[index]
                val upcoming = waypoints.getOrNull(index + 1)
                appendTurnpointSegment(prev, current, upcoming, ::addAnchor)
            }
        }

        val finish = waypoints.last()
        val prev = waypoints[waypoints.lastIndex - 1]
        appendFinishSegment(prev, finish, ::addAnchor)

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

    private fun appendStartSegment(
        start: RacingWaypoint,
        next: RacingWaypoint,
        addPoint: (Double, Double) -> Unit
    ) {
        val bearingToNext = RacingGeometryUtils.calculateBearing(start.lat, start.lon, next.lat, next.lon)
        when (start.startPointType) {
            RacingStartPointType.START_LINE -> {
                val offset = lineCrossOffsetMeters(start.gateWidth)
                val pre = destination(start, bearingToNext + 180.0, offset)
                val post = destination(start, bearingToNext, offset)
                addPoint(pre.first, pre.second)
                addPoint(post.first, post.second)
            }
            RacingStartPointType.START_CYLINDER -> {
                val (inside, outside) = cylinderAnchors(
                    waypoint = start,
                    boundaryPoint = cylinderBoundaryPointForStart(start, next)
                )
                addPoint(inside.lat, inside.lon)
                addPoint(outside.lat, outside.lon)
            }
            RacingStartPointType.FAI_START_SECTOR -> {
                val inside = start.lat to start.lon
                val outsideDistance = outsideRadiusMeters(start.gateWidth)
                val outside = destination(start, bearingToNext, outsideDistance)
                addPoint(inside.first, inside.second)
                addPoint(outside.first, outside.second)
            }
        }
    }

    private fun appendTurnpointSegment(
        previous: RacingWaypoint,
        turn: RacingWaypoint,
        next: RacingWaypoint?,
        addPoint: (Double, Double) -> Unit
    ) {
        val inboundBearing = RacingGeometryUtils.calculateBearing(previous.lat, previous.lon, turn.lat, turn.lon)
        if (turn.turnPointType == RacingTurnPointType.TURN_POINT_CYLINDER && next != null) {
            val (inside, outside) = cylinderAnchors(
                waypoint = turn,
                boundaryPoint = cylinderBoundaryPointForTurn(turn, previous, next)
            )
            addPoint(outside.lat, outside.lon)
            addPoint(inside.lat, inside.lon)
        } else if (turn.turnPointType == RacingTurnPointType.KEYHOLE && next != null) {
            val boundaryPoint = keyholeBoundaryPointForTurn(turn, previous, next)
            val (inside, outside) = neighborAnchors(boundaryPoint, previous, next)
            addPoint(outside.lat, outside.lon)
            addPoint(inside.lat, inside.lon)
        } else {
            val inside = turnInsidePoint(turn, previous, next)
            val outside = turnOutsidePoint(turn, previous, next, inboundBearing)
            addPoint(outside.first, outside.second)
            addPoint(inside.first, inside.second)
        }
    }

    private fun appendFinishSegment(
        previous: RacingWaypoint,
        finish: RacingWaypoint,
        addPoint: (Double, Double) -> Unit
    ) {
        val inboundBearing = RacingGeometryUtils.calculateBearing(previous.lat, previous.lon, finish.lat, finish.lon)
        when (finish.finishPointType) {
            RacingFinishPointType.FINISH_LINE -> {
                val offset = lineCrossOffsetMeters(finish.gateWidth)
                val pre = destination(finish, inboundBearing + 180.0, offset)
                val post = destination(finish, inboundBearing, offset)
                addPoint(pre.first, pre.second)
                addPoint(post.first, post.second)
            }
            RacingFinishPointType.FINISH_CYLINDER -> {
                val (inside, outside) = cylinderAnchors(
                    waypoint = finish,
                    boundaryPoint = cylinderBoundaryPointForFinish(finish, previous)
                )
                addPoint(outside.lat, outside.lon)
                addPoint(inside.lat, inside.lon)
            }
        }
    }

    private fun turnInsidePoint(
        turn: RacingWaypoint,
        previous: RacingWaypoint,
        next: RacingWaypoint?
    ): Pair<Double, Double> {
        return when (turn.turnPointType) {
            RacingTurnPointType.TURN_POINT_CYLINDER,
            RacingTurnPointType.KEYHOLE -> turn.lat to turn.lon
            RacingTurnPointType.FAI_QUADRANT -> {
                if (next == null) return turn.lat to turn.lon
                val sectorBearing = faiSectorBearing(turn, previous, next)
                destination(turn, sectorBearing, 150.0)
            }
        }
    }

    private fun cylinderBoundaryPointForStart(
        start: RacingWaypoint,
        next: RacingWaypoint
    ): Pair<Double, Double> {
        val context = TaskContext(
            waypointIndex = 0,
            allWaypoints = listOf(start, next),
            previousWaypoint = null,
            nextWaypoint = next
        )
        return cylinderCalculator.calculateOptimalTouchPoint(start, context)
    }

    private fun cylinderBoundaryPointForTurn(
        turn: RacingWaypoint,
        previous: RacingWaypoint,
        next: RacingWaypoint
    ): Pair<Double, Double> {
        val context = TaskContext(
            waypointIndex = 0,
            allWaypoints = listOf(previous, turn, next),
            previousWaypoint = previous,
            nextWaypoint = next
        )
        return cylinderCalculator.calculateOptimalTouchPoint(turn, context)
    }

    private fun cylinderBoundaryPointForFinish(
        finish: RacingWaypoint,
        previous: RacingWaypoint
    ): Pair<Double, Double> {
        return cylinderCalculator.calculateOptimalEntryPoint(finish, previous)
    }

    private fun keyholeBoundaryPointForTurn(
        turn: RacingWaypoint,
        previous: RacingWaypoint,
        next: RacingWaypoint
    ): Pair<Double, Double> {
        val context = TaskContext(
            waypointIndex = 0,
            allWaypoints = listOf(previous, turn, next),
            previousWaypoint = previous,
            nextWaypoint = next
        )
        return keyholeCalculator.calculateOptimalTouchPoint(turn, context)
    }

    private fun cylinderAnchors(
        waypoint: RacingWaypoint,
        boundaryPoint: Pair<Double, Double>
    ): Pair<RacingBoundaryPoint, RacingBoundaryPoint> {
        val center = RacingBoundaryPoint(waypoint.lat, waypoint.lon)
        val boundary = RacingBoundaryPoint(boundaryPoint.first, boundaryPoint.second)
        val radiusMeters = waypoint.gateWidth * 1000.0
        val epsilonMeters = epsilonPolicy.epsilonMeters()
        return RacingBoundaryGeometry.anchorsForBoundaryPoint(
            center = center,
            boundaryPoint = boundary,
            radiusMeters = radiusMeters,
            epsilonMeters = epsilonMeters
        )
    }

    private fun neighborAnchors(
        boundaryPoint: Pair<Double, Double>,
        previous: RacingWaypoint,
        next: RacingWaypoint
    ): Pair<RacingBoundaryPoint, RacingBoundaryPoint> {
        val epsilonMeters = epsilonPolicy.epsilonMeters()
        val boundary = RacingBoundaryPoint(boundaryPoint.first, boundaryPoint.second)
        val bearingToPrev = RacingGeometryUtils.calculateBearing(boundary.lat, boundary.lon, previous.lat, previous.lon)
        val bearingToNext = RacingGeometryUtils.calculateBearing(boundary.lat, boundary.lon, next.lat, next.lon)
        val outside = RacingBoundaryGeometry.pointOnBearing(boundary, bearingToPrev, epsilonMeters)
        val inside = RacingBoundaryGeometry.pointOnBearing(boundary, bearingToNext, epsilonMeters)
        return inside to outside
    }

    private fun turnOutsidePoint(
        turn: RacingWaypoint,
        previous: RacingWaypoint,
        next: RacingWaypoint?,
        inboundBearing: Double
    ): Pair<Double, Double> {
        return when (turn.turnPointType) {
            RacingTurnPointType.TURN_POINT_CYLINDER -> {
                destination(turn, inboundBearing + 180.0, outsideRadiusMeters(turn.gateWidth))
            }
            RacingTurnPointType.KEYHOLE -> {
                val distance = outsideRadiusMeters(turn.gateWidth)
                destination(turn, inboundBearing + 180.0, distance)
            }
            RacingTurnPointType.FAI_QUADRANT -> {
                if (next == null) {
                    destination(turn, inboundBearing + 180.0, outsideRadiusMeters(turn.gateWidth))
                } else {
                    val sectorBearing = faiSectorBearing(turn, previous, next)
                    destination(turn, sectorBearing + 180.0, 500.0)
                }
            }
        }
    }

    private fun faiSectorBearing(
        turn: RacingWaypoint,
        previous: RacingWaypoint,
        next: RacingWaypoint
    ): Double {
        val inbound = RacingGeometryUtils.calculateBearing(previous.lat, previous.lon, turn.lat, turn.lon)
        val outbound = RacingGeometryUtils.calculateBearing(turn.lat, turn.lon, next.lat, next.lon)
        val bisector = RacingGeometryUtils.calculateAngleBisector(inbound, outbound)
        val turnDirection = RacingGeometryUtils.calculateTurnDirection(inbound, outbound)
        val raw = if (turnDirection > 0) bisector - 90.0 else bisector + 90.0
        return (raw + 360.0) % 360.0
    }

    private fun destination(
        waypoint: RacingWaypoint,
        bearingDeg: Double,
        distanceMeters: Double
    ): Pair<Double, Double> = RacingGeometryUtils.calculateDestinationPoint(
        waypoint.lat,
        waypoint.lon,
        normalizeBearing(bearingDeg),
        distanceMeters
    )

    private fun lineCrossOffsetMeters(gateWidthKm: Double): Double {
        val lineLengthMeters = max(0.0, gateWidthKm) * 1000.0
        if (lineLengthMeters <= 0.0) return 0.0

        val radiusMeters = lineLengthMeters / 2.0
        val epsilonMeters = epsilonPolicy.epsilonMeters()
        val safeMax = (radiusMeters - epsilonMeters).coerceAtLeast(0.0)

        val raw = lineLengthMeters * 0.2
        val base = raw.coerceIn(MIN_LINE_OFFSET_METERS, MAX_LINE_OFFSET_METERS)
        return if (safeMax > 0.0) {
            base.coerceAtMost(safeMax)
        } else {
            0.0
        }
    }

    private fun outsideRadiusMeters(radiusKm: Double): Double {
        val radiusMeters = max(0.0, radiusKm) * 1000.0
        return radiusMeters + OUTSIDE_MARGIN_METERS
    }

    private fun normalizeBearing(bearingDeg: Double): Double {
        val normalized = (bearingDeg % 360.0 + 360.0) % 360.0
        return if (abs(normalized - 360.0) < 1e-6) 0.0 else normalized
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
        val steps = max(1, kotlin.math.ceil(expectedDurationMs / stepMillis.toDouble()).toInt())
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
        private const val OUTSIDE_MARGIN_METERS = 200.0
        private const val MIN_LINE_OFFSET_METERS = 200.0
        private const val MAX_LINE_OFFSET_METERS = 2_000.0
    }

    private data class Anchor(val lat: Double, val lon: Double)
}
