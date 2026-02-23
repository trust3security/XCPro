package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossing
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlanner
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryTransition
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import kotlin.math.abs
import kotlin.math.max

internal fun normalizeNavigationState(
    state: RacingNavigationState,
    task: SimpleRacingTask,
    signature: String
): RacingNavigationState {
    val normalized = if (state.taskSignature.isNotEmpty() && state.taskSignature != signature) {
        RacingNavigationState(taskSignature = signature)
    } else {
        state
    }
    if (task.waypoints.isEmpty()) {
        return normalized.copy(currentLegIndex = 0, taskSignature = signature)
    }
    return normalized.copy(
        currentLegIndex = normalized.currentLegIndex.coerceIn(0, task.waypoints.lastIndex),
        taskSignature = signature
    )
}

internal fun buildTaskSignature(task: SimpleRacingTask): String {
    if (task.waypoints.isEmpty()) return "empty"
    return task.waypoints.joinToString("|") { waypoint ->
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
    val limit = radiusMeters + margin
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
    currentFix: RacingNavigationFix
): RacingBoundaryCrossing? {
    if (previousFix == null || nextWaypoint == null) return null
    val bearingToNext = RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
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

internal fun detectFinishLineCrossing(
    crossingPlanner: RacingBoundaryCrossingPlanner,
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    previousFix: RacingNavigationFix?,
    currentFix: RacingNavigationFix
): RacingBoundaryCrossing? {
    if (previousFix == null || previousWaypoint == null) return null
    val inboundBearing = RacingGeometryUtils.calculateBearing(previousWaypoint.lat, previousWaypoint.lon, waypoint.lat, waypoint.lon)
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
    currentFix: RacingNavigationFix
): RacingBoundaryCrossing? {
    if (previousFix == null || nextWaypoint == null) return null
    val bearingToNext = RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
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
