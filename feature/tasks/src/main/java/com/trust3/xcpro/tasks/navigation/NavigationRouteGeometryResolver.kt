package com.trust3.xcpro.tasks.navigation

import com.trust3.xcpro.tasks.core.RacingFinishCustomParams
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.racing.RacingGeometryUtils
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryCrossingMath
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryGeometry
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.trust3.xcpro.tasks.racing.models.RacingFinishPointType
import com.trust3.xcpro.tasks.racing.models.RacingTurnPointType
import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationState
import com.trust3.xcpro.tasks.racing.toRacingWaypoints
import com.trust3.xcpro.tasks.racing.turnpoints.KeyholeGeometry
import com.trust3.xcpro.tasks.racing.turnpoints.TaskContext

internal fun Task.projectBoundaryAwareRemainingWaypoints(
    navigationState: RacingNavigationState
): List<NavigationRoutePoint> {
    val racingWaypoints = toRacingWaypoints()
    if (racingWaypoints.isEmpty()) return emptyList()

    val startIndex = navigationState.currentLegIndex.coerceIn(1, racingWaypoints.lastIndex)
    val routePoints = mutableListOf<NavigationRoutePoint>()

    for (index in startIndex..racingWaypoints.lastIndex) {
        val waypoint = racingWaypoints[index]
        val previousWaypoint = racingWaypoints.getOrNull(index - 1)
        val nextWaypoint = racingWaypoints.getOrNull(index + 1)
        val approachAnchor = when {
            routePoints.isNotEmpty() -> routePoints.last().toBoundaryPoint()
            index == startIndex -> navigationState.lastFix?.toBoundaryPoint()
                ?: previousWaypoint?.toBoundaryPoint()
                ?: waypoint.toBoundaryPoint()
            else -> previousWaypoint?.toBoundaryPoint() ?: waypoint.toBoundaryPoint()
        }

        routePoints += resolveBoundaryAwareRoutePoint(
            taskWaypoints = waypoints,
            waypointIndex = index,
            waypoint = waypoint,
            previousWaypoint = previousWaypoint,
            nextWaypoint = nextWaypoint,
            approachAnchor = approachAnchor
        )
    }

    return routePoints
}

private fun resolveBoundaryAwareRoutePoint(
    taskWaypoints: List<TaskWaypoint>,
    waypointIndex: Int,
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    nextWaypoint: RacingWaypoint?,
    approachAnchor: RacingBoundaryPoint
): NavigationRoutePoint {
    val resolvedPoint = when (waypoint.role) {
        com.trust3.xcpro.tasks.racing.models.RacingWaypointRole.FINISH -> resolveFinishTouchPoint(
            taskWaypoints = taskWaypoints,
            waypointIndex = waypointIndex,
            waypoint = waypoint,
            previousWaypoint = previousWaypoint,
            approachAnchor = approachAnchor
        )

        else -> when (waypoint.turnPointType) {
            RacingTurnPointType.TURN_POINT_CYLINDER -> resolveTurnpointCylinderTouchPoint(
                waypoint = waypoint,
                approachAnchor = approachAnchor,
                nextWaypoint = nextWaypoint
            )

            RacingTurnPointType.FAI_QUADRANT -> resolveSectorTouchPoint(
                waypoint = waypoint,
                approachAnchor = approachAnchor,
                previousWaypoint = previousWaypoint,
                nextWaypoint = nextWaypoint,
                radiusMeters = waypoint.faiQuadrantOuterRadiusMeters,
                halfAngleDegrees = 45.0
            )

            RacingTurnPointType.KEYHOLE -> resolveKeyholeTouchPoint(
                waypoint = waypoint,
                approachAnchor = approachAnchor,
                previousWaypoint = previousWaypoint,
                nextWaypoint = nextWaypoint
            )
        }
    }

    return NavigationRoutePoint(
        lat = resolvedPoint.lat,
        lon = resolvedPoint.lon,
        label = waypoint.title.ifBlank { waypoint.id }
    )
}

private fun resolveFinishTouchPoint(
    taskWaypoints: List<TaskWaypoint>,
    waypointIndex: Int,
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    approachAnchor: RacingBoundaryPoint
): RacingBoundaryPoint {
    return when (waypoint.finishPointType) {
        RacingFinishPointType.FINISH_CYLINDER -> resolveFinishCylinderTouchPoint(
            waypoint = waypoint,
            approachAnchor = approachAnchor
        )

        RacingFinishPointType.FINISH_LINE -> resolveFinishLineTouchPoint(
            taskWaypoint = taskWaypoints[waypointIndex],
            waypoint = waypoint,
            previousWaypoint = previousWaypoint,
            approachAnchor = approachAnchor
        )
    }
}

private fun resolveTurnpointCylinderTouchPoint(
    waypoint: RacingWaypoint,
    approachAnchor: RacingBoundaryPoint,
    nextWaypoint: RacingWaypoint?
): RacingBoundaryPoint {
    val target = nextWaypoint?.toBoundaryPoint() ?: waypoint.toBoundaryPoint()
    return resolveCylinderIntersection(
        center = waypoint.toBoundaryPoint(),
        radiusMeters = waypoint.gateWidthMeters,
        approachAnchor = approachAnchor,
        target = target
    ) ?: fallbackCylinderTouchPoint(waypoint, approachAnchor, nextWaypoint)
}

private fun resolveKeyholeTouchPoint(
    waypoint: RacingWaypoint,
    approachAnchor: RacingBoundaryPoint,
    previousWaypoint: RacingWaypoint?,
    nextWaypoint: RacingWaypoint?
): RacingBoundaryPoint {
    val target = nextWaypoint?.toBoundaryPoint() ?: waypoint.toBoundaryPoint()
    val sectorPoint = resolveSectorTouchPoint(
        waypoint = waypoint,
        approachAnchor = approachAnchor,
        previousWaypoint = previousWaypoint,
        nextWaypoint = nextWaypoint,
        radiusMeters = waypoint.gateWidthMeters,
        halfAngleDegrees = waypoint.normalizedKeyholeAngle / 2.0
    )
    val cylinderPoint = resolveCylinderIntersection(
        center = waypoint.toBoundaryPoint(),
        radiusMeters = waypoint.keyholeInnerRadiusMeters,
        approachAnchor = approachAnchor,
        target = target
    )

    val sectorDistance = routeDistanceMeters(approachAnchor, sectorPoint, target)
    val cylinderDistance = cylinderPoint?.let { routeDistanceMeters(approachAnchor, it, target) }

    return when {
        cylinderDistance == null -> sectorPoint
        cylinderDistance <= sectorDistance -> cylinderPoint
        else -> sectorPoint
    }
}

private fun resolveSectorTouchPoint(
    waypoint: RacingWaypoint,
    approachAnchor: RacingBoundaryPoint,
    previousWaypoint: RacingWaypoint?,
    nextWaypoint: RacingWaypoint?,
    radiusMeters: Double,
    halfAngleDegrees: Double
): RacingBoundaryPoint {
    if (nextWaypoint == null) {
        return waypoint.toBoundaryPoint()
    }

    val sectorBearing = KeyholeGeometry.calculateFAISectorBisector(
        waypoint = waypoint,
        previousWaypoint = previousWaypoint,
        nextWaypoint = nextWaypoint
    )
    val target = nextWaypoint.toBoundaryPoint()
    return resolveSectorIntersection(
        center = waypoint.toBoundaryPoint(),
        radiusMeters = radiusMeters,
        sectorBearingDegrees = sectorBearing,
        halfAngleDegrees = halfAngleDegrees,
        approachAnchor = approachAnchor,
        target = target
    ) ?: waypoint.toBoundaryPoint()
}

private fun resolveFinishCylinderTouchPoint(
    waypoint: RacingWaypoint,
    approachAnchor: RacingBoundaryPoint
): RacingBoundaryPoint {
    val center = waypoint.toBoundaryPoint()
    return resolveCylinderIntersection(
        center = center,
        radiusMeters = waypoint.gateWidthMeters,
        approachAnchor = approachAnchor,
        target = center
    ) ?: pointOnCircleToward(center, approachAnchor, waypoint.gateWidthMeters)
}

private fun resolveFinishLineTouchPoint(
    taskWaypoint: TaskWaypoint,
    waypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    approachAnchor: RacingBoundaryPoint
): RacingBoundaryPoint {
    val finishRules = RacingFinishCustomParams.from(taskWaypoint.customParameters)
    val inboundBearing = finishRules.directionOverrideDegrees
        ?: previousWaypoint?.let { previous ->
            RacingGeometryUtils.calculateBearing(previous.lat, previous.lon, waypoint.lat, waypoint.lon)
        }
        ?: RacingGeometryUtils.calculateBearing(approachAnchor.lat, approachAnchor.lon, waypoint.lat, waypoint.lon)
    val lineBearing = (inboundBearing + 90.0) % 360.0
    val center = waypoint.toBoundaryPoint()
    val halfLength = waypoint.gateWidthMeters / 2.0
    val lineStart = RacingBoundaryGeometry.pointOnBearing(center, lineBearing, halfLength)
    val lineEnd = RacingBoundaryGeometry.pointOnBearing(center, lineBearing + 180.0, halfLength)
    return closestPointOnSegment(
        center = center,
        point = approachAnchor,
        segmentStart = lineStart,
        segmentEnd = lineEnd
    )
}

private fun resolveCylinderIntersection(
    center: RacingBoundaryPoint,
    radiusMeters: Double,
    approachAnchor: RacingBoundaryPoint,
    target: RacingBoundaryPoint
): RacingBoundaryPoint? {
    if (radiusMeters <= 0.0) return null
    val previousFix = approachAnchor.toFix(timestampMillis = 0L)
    val currentFix = target.toFix(timestampMillis = 1_000L)
    val t = RacingBoundaryCrossingMath.intersectionParameter(
        center = center,
        radiusMeters = radiusMeters,
        previousFix = previousFix,
        currentFix = currentFix,
        transition = com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryTransition.ENTER
    ) ?: return null
    return RacingBoundaryCrossingMath.boundaryPointFromParameter(
        center = center,
        radiusMeters = radiusMeters,
        previousFix = previousFix,
        currentFix = currentFix,
        t = t
    )
}

private fun resolveSectorIntersection(
    center: RacingBoundaryPoint,
    radiusMeters: Double,
    sectorBearingDegrees: Double,
    halfAngleDegrees: Double,
    approachAnchor: RacingBoundaryPoint,
    target: RacingBoundaryPoint
): RacingBoundaryPoint? {
    if (radiusMeters <= 0.0) return null
    val p0 = RacingBoundaryGeometry.toLocalMeters(center, approachAnchor)
    val p1 = RacingBoundaryGeometry.toLocalMeters(center, target)
    val intersection = RacingBoundaryCrossingMath.sectorIntersectionParameter(
        center = center,
        radiusMeters = radiusMeters,
        sectorBearingDegrees = sectorBearingDegrees,
        halfAngleDegrees = halfAngleDegrees,
        p0 = p0,
        p1 = p1,
        transition = com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryTransition.ENTER
    ) ?: return null
    return RacingBoundaryGeometry.fromLocalMeters(center, intersection.x, intersection.y)
}

private fun fallbackCylinderTouchPoint(
    waypoint: RacingWaypoint,
    approachAnchor: RacingBoundaryPoint,
    nextWaypoint: RacingWaypoint?
): RacingBoundaryPoint {
    if (nextWaypoint == null) {
        return pointOnCircleToward(waypoint.toBoundaryPoint(), approachAnchor, waypoint.gateWidthMeters)
    }
    val syntheticPrevious = approachAnchor.toSyntheticWaypoint(id = "${waypoint.id}_approach")
    val context = TaskContext(
        waypointIndex = 0,
        allWaypoints = listOf(syntheticPrevious, waypoint, nextWaypoint),
        previousWaypoint = syntheticPrevious,
        nextWaypoint = nextWaypoint
    )
    val point = com.trust3.xcpro.tasks.racing.turnpoints.CylinderCalculator()
        .calculateOptimalTouchPoint(waypoint, context)
    return RacingBoundaryPoint(point.first, point.second)
}

private fun pointOnCircleToward(
    center: RacingBoundaryPoint,
    target: RacingBoundaryPoint,
    radiusMeters: Double
): RacingBoundaryPoint {
    val bearing = RacingGeometryUtils.calculateBearing(center.lat, center.lon, target.lat, target.lon)
    return RacingBoundaryGeometry.pointOnBearing(center, bearing, radiusMeters)
}

private fun closestPointOnSegment(
    center: RacingBoundaryPoint,
    point: RacingBoundaryPoint,
    segmentStart: RacingBoundaryPoint,
    segmentEnd: RacingBoundaryPoint
): RacingBoundaryPoint {
    val localPoint = RacingBoundaryGeometry.toLocalMeters(center, point)
    val localStart = RacingBoundaryGeometry.toLocalMeters(center, segmentStart)
    val localEnd = RacingBoundaryGeometry.toLocalMeters(center, segmentEnd)
    val dx = localEnd.first - localStart.first
    val dy = localEnd.second - localStart.second
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.0) {
        return segmentStart
    }
    val t = (
        ((localPoint.first - localStart.first) * dx) +
            ((localPoint.second - localStart.second) * dy)
        ) / lengthSquared
    val clampedT = t.coerceIn(0.0, 1.0)
    return RacingBoundaryGeometry.fromLocalMeters(
        center = center,
        xMeters = localStart.first + dx * clampedT,
        yMeters = localStart.second + dy * clampedT
    )
}

private fun routeDistanceMeters(
    start: RacingBoundaryPoint,
    touchPoint: RacingBoundaryPoint,
    end: RacingBoundaryPoint
): Double {
    return RacingGeometryUtils.haversineDistanceMeters(start.lat, start.lon, touchPoint.lat, touchPoint.lon) +
        RacingGeometryUtils.haversineDistanceMeters(touchPoint.lat, touchPoint.lon, end.lat, end.lon)
}

private fun RacingBoundaryPoint.toFix(timestampMillis: Long): RacingNavigationFix = RacingNavigationFix(
    lat = lat,
    lon = lon,
    timestampMillis = timestampMillis
)

private fun RacingBoundaryPoint.toSyntheticWaypoint(id: String): RacingWaypoint =
    RacingWaypoint.createWithStandardizedDefaults(
        id = id,
        title = id,
        subtitle = "",
        lat = lat,
        lon = lon,
        role = com.trust3.xcpro.tasks.racing.models.RacingWaypointRole.TURNPOINT,
        turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
    )

private fun NavigationRoutePoint.toBoundaryPoint(): RacingBoundaryPoint = RacingBoundaryPoint(lat, lon)

private fun RacingWaypoint.toBoundaryPoint(): RacingBoundaryPoint = RacingBoundaryPoint(lat, lon)

private fun RacingNavigationFix.toBoundaryPoint(): RacingBoundaryPoint = RacingBoundaryPoint(lat, lon)
