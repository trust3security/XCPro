package com.example.xcpro.map.replay

import com.example.xcpro.tasks.racing.RacingGeometryUtils
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

internal class RacingReplayAnchorBuilder(
    private val epsilonPolicy: RacingBoundaryEpsilonPolicy,
    private val cylinderCalculator: CylinderCalculator = CylinderCalculator(),
    private val keyholeCalculator: KeyholeCalculator = KeyholeCalculator()
) {

    fun appendStartSegment(
        start: RacingWaypoint,
        next: RacingWaypoint,
        addPoint: (Double, Double) -> Unit
    ) {
        val bearingToNext = RacingGeometryUtils.calculateBearing(start.lat, start.lon, next.lat, next.lon)
        when (start.startPointType) {
            RacingStartPointType.START_LINE -> {
                val offset = lineCrossOffsetMeters(start.gateWidthMeters)
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
                val outsideDistance = outsideRadiusMeters(start.gateWidthMeters)
                val outside = destination(start, bearingToNext, outsideDistance)
                addPoint(inside.first, inside.second)
                addPoint(outside.first, outside.second)
            }
        }
    }

    fun appendTurnpointSegment(
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

    fun appendFinishSegment(
        previous: RacingWaypoint,
        finish: RacingWaypoint,
        addPoint: (Double, Double) -> Unit
    ) {
        val inboundBearing = RacingGeometryUtils.calculateBearing(previous.lat, previous.lon, finish.lat, finish.lon)
        when (finish.finishPointType) {
            RacingFinishPointType.FINISH_LINE -> {
                val offset = lineCrossOffsetMeters(finish.gateWidthMeters)
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
        val radiusMeters = waypoint.gateWidthMeters
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
                destination(turn, inboundBearing + 180.0, outsideRadiusMeters(turn.gateWidthMeters))
            }
            RacingTurnPointType.KEYHOLE -> {
                val distance = outsideRadiusMeters(turn.gateWidthMeters)
                destination(turn, inboundBearing + 180.0, distance)
            }
            RacingTurnPointType.FAI_QUADRANT -> {
                if (next == null) {
                    destination(turn, inboundBearing + 180.0, outsideRadiusMeters(turn.gateWidthMeters))
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

    private fun lineCrossOffsetMeters(gateWidthMeters: Double): Double {
        val lineLengthMeters = max(0.0, gateWidthMeters)
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

    private fun outsideRadiusMeters(radiusMeters: Double): Double {
        return max(0.0, radiusMeters) + OUTSIDE_MARGIN_METERS
    }

    private fun normalizeBearing(bearingDeg: Double): Double {
        val normalized = (bearingDeg % 360.0 + 360.0) % 360.0
        return if (abs(normalized - 360.0) < 1e-6) 0.0 else normalized
    }

    private companion object {
        private const val OUTSIDE_MARGIN_METERS = 200.0
        private const val MIN_LINE_OFFSET_METERS = 200.0
        private const val MAX_LINE_OFFSET_METERS = 2_000.0
    }
}
