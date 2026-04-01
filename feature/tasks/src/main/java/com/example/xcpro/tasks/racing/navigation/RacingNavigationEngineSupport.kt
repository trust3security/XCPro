package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryGeometry
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossing
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlanner
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryTransition
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import kotlin.math.abs
import kotlin.math.max

internal const val TURNPOINT_NEAR_MISS_DISTANCE_METERS = 500.0

internal fun normalizeNavigationState(
    state: RacingNavigationState,
    taskWaypoints: List<RacingWaypoint>,
    signature: String
): RacingNavigationState {
    val normalized = if (state.taskSignature.isNotEmpty() && state.taskSignature != signature) {
        RacingNavigationState(taskSignature = signature)
    } else {
        state
    }
    if (taskWaypoints.isEmpty()) {
        return normalized.copy(currentLegIndex = 0, taskSignature = signature)
    }
    return normalized.copy(
        currentLegIndex = normalized.currentLegIndex.coerceIn(0, taskWaypoints.lastIndex),
        taskSignature = signature
    )
}

internal fun normalizeNavigationState(
    state: RacingNavigationState,
    task: SimpleRacingTask,
    signature: String
): RacingNavigationState = normalizeNavigationState(state, task.waypoints, signature)

internal fun buildTaskSignature(taskWaypoints: List<RacingWaypoint>): String {
    if (taskWaypoints.isEmpty()) return "empty"
    return taskWaypoints.joinToString("|") { waypoint ->
        buildString {
            append(waypoint.id)
            append(":")
            append(waypoint.role.name)
            append(":")
            append(waypoint.startPointType.name)
            append(":")
            append(waypoint.finishPointType.name)
            append(":")
            append(waypoint.turnPointType.name)
            append(":")
            append(waypoint.gateWidthMeters)
            append(":")
            append(waypoint.keyholeInnerRadiusMeters)
            append(":")
            append(waypoint.keyholeAngle)
            append(":")
            append(waypoint.faiQuadrantOuterRadiusMeters)
            append(":")
            append(waypoint.lat)
            append(":")
            append(waypoint.lon)
        }
    }
}

internal fun buildTaskSignature(task: SimpleRacingTask): String = buildTaskSignature(task.waypoints)

internal fun buildCreditedBoundaryHit(
    legIndex: Int,
    waypointRole: RacingWaypointRole,
    transitionTimeMillis: Long,
    crossing: RacingBoundaryCrossing,
    altitudeSourceFix: RacingNavigationFix? = null,
    altitudeReference: RacingAltitudeReference? = null
): RacingCreditedBoundaryHit = RacingCreditedBoundaryHit(
    legIndex = legIndex,
    waypointRole = waypointRole,
    timestampMillis = transitionTimeMillis,
    crossingEvidence = crossing.toEventEvidence(),
    altitudeSourceFix = altitudeSourceFix,
    altitudeReference = altitudeReference
)

internal fun shouldEvaluateTransitions(
    activeWaypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    nextWaypoint: RacingWaypoint?,
    fix: RacingNavigationFix,
    lastFix: RacingNavigationFix?,
    epsilonPolicy: RacingBoundaryEpsilonPolicy
): Boolean {
    val radiusMeters = proximityRadiusMeters(activeWaypoint, previousWaypoint, nextWaypoint) ?: return true
    val margin = epsilonPolicy.epsilonMeters(fix)
    val nearMissAllowance = if (
        activeWaypoint.role == RacingWaypointRole.TURNPOINT &&
        activeWaypoint.turnPointType == RacingTurnPointType.TURN_POINT_CYLINDER
    ) {
        TURNPOINT_NEAR_MISS_DISTANCE_METERS
    } else {
        0.0
    }
    val limit = radiusMeters + margin + nearMissAllowance
    val currentDistance = waypointDistanceMeters(activeWaypoint, fix)
    if (currentDistance <= limit) return true
    val lastDistance = lastFix?.let { waypointDistanceMeters(activeWaypoint, it) } ?: currentDistance
    return lastDistance <= limit
}

private fun proximityRadiusMeters(
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    nextWaypoint: RacingWaypoint?
): Double? {
    return when (waypoint.role) {
        RacingWaypointRole.START -> when (waypoint.startPointType) {
            RacingStartPointType.START_LINE -> lineRadiusMeters(waypoint)
            RacingStartPointType.START_CYLINDER -> cylinderRadiusMeters(waypoint)
            RacingStartPointType.FAI_START_SECTOR -> sectorRadiusMeters(waypoint, nextWaypoint)
        }
        RacingWaypointRole.TURNPOINT -> when (waypoint.turnPointType) {
            RacingTurnPointType.TURN_POINT_CYLINDER -> cylinderRadiusMeters(waypoint)
            RacingTurnPointType.KEYHOLE -> keyholeRadiusMeters(waypoint, previousWaypoint, nextWaypoint)
            RacingTurnPointType.FAI_QUADRANT -> faiQuadrantRadiusMeters(waypoint, previousWaypoint, nextWaypoint)
        }
        RacingWaypointRole.FINISH -> when (waypoint.finishPointType) {
            RacingFinishPointType.FINISH_LINE -> lineRadiusMeters(waypoint)
            RacingFinishPointType.FINISH_CYLINDER -> cylinderRadiusMeters(waypoint)
        }
    }?.takeIf { it > 0.0 }
}

private fun lineRadiusMeters(waypoint: RacingWaypoint): Double {
    val lengthMeters = waypoint.gateWidthMeters
    return lengthMeters / 2.0
}

private fun cylinderRadiusMeters(waypoint: RacingWaypoint): Double {
    return waypoint.gateWidthMeters
}

private fun sectorRadiusMeters(
    waypoint: RacingWaypoint,
    nextWaypoint: RacingWaypoint?
): Double {
    if (nextWaypoint == null) return 0.0
    return waypoint.gateWidthMeters
}

private fun keyholeRadiusMeters(
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    nextWaypoint: RacingWaypoint?
): Double {
    if (previousWaypoint == null || nextWaypoint == null) return 0.0
    val outer = waypoint.gateWidthMeters
    val inner = waypoint.keyholeInnerRadiusMeters
    return max(outer, inner)
}

private fun faiQuadrantRadiusMeters(
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    nextWaypoint: RacingWaypoint?
): Double {
    if (previousWaypoint == null || nextWaypoint == null) return 0.0
    return waypoint.faiQuadrantOuterRadiusMeters
}

private fun waypointDistanceMeters(waypoint: RacingWaypoint, fix: RacingNavigationFix): Double {
    return abs(RacingGeometryUtils.haversineDistanceMeters(waypoint.lat, waypoint.lon, fix.lat, fix.lon))
}

internal fun detectCylinderCrossing(
    crossingPlanner: RacingBoundaryCrossingPlanner,
    waypoint: RacingWaypoint,
    previousFix: RacingNavigationFix?,
    currentFix: RacingNavigationFix,
    transition: RacingBoundaryTransition
): RacingBoundaryCrossing? {
    if (previousFix == null) return null
    val radiusMeters = waypoint.gateWidthMeters
    if (radiusMeters <= 0.0) return null
    return crossingPlanner.detectCylinderCrossing(
        center = RacingBoundaryPoint(waypoint.lat, waypoint.lon),
        radiusMeters = radiusMeters,
        previousFix = previousFix,
        currentFix = currentFix,
        transition = transition
    )
}

internal fun detectStartLineCrossing(
    crossingPlanner: RacingBoundaryCrossingPlanner,
    waypoint: RacingWaypoint,
    nextWaypoint: RacingWaypoint?,
    previousFix: RacingNavigationFix?,
    currentFix: RacingNavigationFix,
    directionOverrideDegrees: Double? = null
): RacingBoundaryCrossing? {
    if (previousFix == null) return null
    val bearingToNext = directionOverrideDegrees
        ?: nextWaypoint?.let {
            RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, it.lat, it.lon)
        }
        ?: return null
    val lineBearing = (bearingToNext + 90.0) % 360.0
    val sectorBearing = (bearingToNext + 180.0) % 360.0
    return crossingPlanner.detectLineCrossing(
        center = RacingBoundaryPoint(waypoint.lat, waypoint.lon),
        lineLengthMeters = waypoint.gateWidthMeters,
        lineBearingDegrees = lineBearing,
        sectorBearingDegrees = sectorBearing,
        previousFix = previousFix,
        currentFix = currentFix,
        transition = RacingBoundaryTransition.EXIT
    )
}

internal fun detectStartLineWrongDirectionCrossing(
    crossingPlanner: RacingBoundaryCrossingPlanner,
    waypoint: RacingWaypoint,
    nextWaypoint: RacingWaypoint?,
    previousFix: RacingNavigationFix?,
    currentFix: RacingNavigationFix,
    directionOverrideDegrees: Double? = null
): RacingBoundaryCrossing? {
    if (previousFix == null) return null
    val bearingToNext = directionOverrideDegrees
        ?: nextWaypoint?.let {
            RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, it.lat, it.lon)
        }
        ?: return null
    val lineBearing = (bearingToNext + 90.0) % 360.0
    val sectorBearing = (bearingToNext + 180.0) % 360.0
    return crossingPlanner.detectLineCrossing(
        center = RacingBoundaryPoint(waypoint.lat, waypoint.lon),
        lineLengthMeters = waypoint.gateWidthMeters,
        lineBearingDegrees = lineBearing,
        sectorBearingDegrees = sectorBearing,
        previousFix = previousFix,
        currentFix = currentFix,
        transition = RacingBoundaryTransition.ENTER
    )
}

internal fun detectFinishLineCrossing(
    crossingPlanner: RacingBoundaryCrossingPlanner,
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    previousFix: RacingNavigationFix?,
    currentFix: RacingNavigationFix,
    directionOverrideDegrees: Double? = null
): RacingBoundaryCrossing? {
    if (previousFix == null || previousWaypoint == null) return null
    val inboundBearing = directionOverrideDegrees
        ?: RacingGeometryUtils.calculateBearing(previousWaypoint.lat, previousWaypoint.lon, waypoint.lat, waypoint.lon)
    val lineBearing = (inboundBearing + 90.0) % 360.0
    val sectorBearing = inboundBearing % 360.0
    return crossingPlanner.detectLineCrossing(
        center = RacingBoundaryPoint(waypoint.lat, waypoint.lon),
        lineLengthMeters = waypoint.gateWidthMeters,
        lineBearingDegrees = lineBearing,
        sectorBearingDegrees = sectorBearing,
        previousFix = previousFix,
        currentFix = currentFix,
        transition = RacingBoundaryTransition.ENTER
    )
}

internal fun detectStartSectorCrossing(
    crossingPlanner: RacingBoundaryCrossingPlanner,
    waypoint: RacingWaypoint,
    nextWaypoint: RacingWaypoint?,
    previousFix: RacingNavigationFix?,
    currentFix: RacingNavigationFix,
    directionOverrideDegrees: Double? = null
): RacingBoundaryCrossing? {
    if (previousFix == null) return null
    val bearingToNext = directionOverrideDegrees
        ?: nextWaypoint?.let {
            RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, it.lat, it.lon)
        }
        ?: return null
    val halfAngle = 45.0
    return crossingPlanner.detectSectorCrossing(
        center = RacingBoundaryPoint(waypoint.lat, waypoint.lon),
        radiusMeters = waypoint.gateWidthMeters,
        sectorBearingDegrees = bearingToNext,
        halfAngleDegrees = halfAngle,
        previousFix = previousFix,
        currentFix = currentFix,
        transition = RacingBoundaryTransition.EXIT
    )
}

internal fun detectKeyholeCrossing(
    crossingPlanner: RacingBoundaryCrossingPlanner,
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    nextWaypoint: RacingWaypoint?,
    previousFix: RacingNavigationFix?,
    currentFix: RacingNavigationFix
): RacingBoundaryCrossing? {
    if (previousFix == null || previousWaypoint == null || nextWaypoint == null) return null
    val innerRadiusMeters = waypoint.keyholeInnerRadiusMeters
    val innerCrossing = if (innerRadiusMeters > 0.0) {
        crossingPlanner.detectCylinderCrossing(
            center = RacingBoundaryPoint(waypoint.lat, waypoint.lon),
            radiusMeters = innerRadiusMeters,
            previousFix = previousFix,
            currentFix = currentFix,
            transition = RacingBoundaryTransition.ENTER
        )
    } else {
        null
    }
    if (innerCrossing != null) return innerCrossing

    val sectorBearing = calculateFAISectorBisector(waypoint, previousWaypoint, nextWaypoint)
    val halfAngle = waypoint.normalizedKeyholeAngle / 2.0
    return crossingPlanner.detectSectorCrossing(
        center = RacingBoundaryPoint(waypoint.lat, waypoint.lon),
        radiusMeters = waypoint.gateWidthMeters,
        sectorBearingDegrees = sectorBearing,
        halfAngleDegrees = halfAngle,
        previousFix = previousFix,
        currentFix = currentFix,
        transition = RacingBoundaryTransition.ENTER
    )
}

internal fun detectFaiQuadrantCrossing(
    crossingPlanner: RacingBoundaryCrossingPlanner,
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    nextWaypoint: RacingWaypoint?,
    previousFix: RacingNavigationFix?,
    currentFix: RacingNavigationFix
): RacingBoundaryCrossing? {
    if (previousFix == null || previousWaypoint == null || nextWaypoint == null) return null
    val sectorBearing = calculateFAISectorBisector(waypoint, previousWaypoint, nextWaypoint)
    return crossingPlanner.detectSectorCrossing(
        center = RacingBoundaryPoint(waypoint.lat, waypoint.lon),
        radiusMeters = waypoint.faiQuadrantOuterRadiusMeters,
        sectorBearingDegrees = sectorBearing,
        halfAngleDegrees = 45.0,
        previousFix = previousFix,
        currentFix = currentFix,
        transition = RacingBoundaryTransition.ENTER
    )
}

internal fun turnpointNearMissDistanceMeters(
    waypoint: RacingWaypoint,
    previousFix: RacingNavigationFix?,
    currentFix: RacingNavigationFix
): Double? {
    if (waypoint.turnPointType != RacingTurnPointType.TURN_POINT_CYLINDER) {
        return null
    }
    val currentGap = cylinderBoundaryGapMeters(waypoint, currentFix)
    val previousGap = previousFix?.let { fix -> cylinderBoundaryGapMeters(waypoint, fix) }
    val minGap = if (previousGap != null) minOf(previousGap, currentGap) else currentGap
    return minGap.takeIf { gap ->
        gap > 0.0 && gap <= TURNPOINT_NEAR_MISS_DISTANCE_METERS
    }
}

internal fun distanceToStartBoundaryMeters(
    waypoint: RacingWaypoint,
    nextWaypoint: RacingWaypoint?,
    fix: RacingNavigationFix,
    directionOverrideDegrees: Double?
): Double? {
    return when (waypoint.startPointType) {
        RacingStartPointType.START_LINE -> {
            val courseBearing = directionOverrideDegrees
                ?: nextWaypoint?.let {
                    RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, it.lat, it.lon)
                }
                ?: return null
            val lineBearing = (courseBearing + 90.0) % 360.0
            val halfLength = waypoint.gateWidthMeters / 2.0
            val left = RacingGeometryUtils.calculateDestinationPoint(
                waypoint.lat,
                waypoint.lon,
                lineBearing,
                halfLength
            )
            val right = RacingGeometryUtils.calculateDestinationPoint(
                waypoint.lat,
                waypoint.lon,
                (lineBearing + 180.0) % 360.0,
                halfLength
            )
            val center = RacingBoundaryPoint(waypoint.lat, waypoint.lon)
            val fixLocal = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(fix.lat, fix.lon))
            val leftLocal = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(left.first, left.second))
            val rightLocal = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(right.first, right.second))
            distancePointToSegmentMeters(fixLocal, leftLocal, rightLocal)
        }
        RacingStartPointType.START_CYLINDER,
        RacingStartPointType.FAI_START_SECTOR -> {
            kotlin.math.abs(
                RacingGeometryUtils.haversineDistanceMeters(
                    waypoint.lat,
                    waypoint.lon,
                    fix.lat,
                    fix.lon
                ) - waypoint.gateWidthMeters
            )
        }
    }
}

private fun distancePointToSegmentMeters(
    point: Pair<Double, Double>,
    start: Pair<Double, Double>,
    end: Pair<Double, Double>
): Double {
    val dx = end.first - start.first
    val dy = end.second - start.second
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.0) {
        val ex = point.first - start.first
        val ey = point.second - start.second
        return kotlin.math.sqrt(ex * ex + ey * ey)
    }

    val t = (((point.first - start.first) * dx) + ((point.second - start.second) * dy)) / lengthSquared
    val clamped = t.coerceIn(0.0, 1.0)
    val projX = start.first + clamped * dx
    val projY = start.second + clamped * dy
    val ex = point.first - projX
    val ey = point.second - projY
    return kotlin.math.sqrt(ex * ex + ey * ey)
}

private fun calculateFAISectorBisector(
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint,
    nextWaypoint: RacingWaypoint
): Double {
    val inboundBearing = RacingGeometryUtils.calculateBearing(previousWaypoint.lat, previousWaypoint.lon, waypoint.lat, waypoint.lon)
    val outboundBearing = RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
    val trackBisector = RacingGeometryUtils.calculateAngleBisector(inboundBearing, outboundBearing)
    val turnDirection = RacingGeometryUtils.calculateTurnDirection(inboundBearing, outboundBearing)
    return if (turnDirection > 0) {
        (trackBisector - 90.0 + 360.0) % 360.0
    } else {
        (trackBisector + 90.0) % 360.0
    }
}

private fun cylinderBoundaryGapMeters(waypoint: RacingWaypoint, fix: RacingNavigationFix): Double {
    val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
        waypoint.lat,
        waypoint.lon,
        fix.lat,
        fix.lon
    )
    return abs(distanceMeters - waypoint.gateWidthMeters)
}

internal fun resolveNavigationAltitudeMeters(
    fix: RacingNavigationFix,
    reference: com.example.xcpro.tasks.core.RacingAltitudeReference
): Double? {
    return when (reference) {
        com.example.xcpro.tasks.core.RacingAltitudeReference.MSL -> fix.altitudeMslMeters
        com.example.xcpro.tasks.core.RacingAltitudeReference.QNH -> fix.altitudeQnhMeters ?: fix.altitudeMslMeters
    }
}
