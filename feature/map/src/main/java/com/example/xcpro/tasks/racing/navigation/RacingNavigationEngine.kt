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

internal class RacingNavigationEngine(
    private val zoneDetector: RacingZoneDetector = RacingZoneDetector(),
    private val crossingPlanner: RacingBoundaryCrossingPlanner = RacingBoundaryCrossingPlanner(),
    private val epsilonPolicy: RacingBoundaryEpsilonPolicy = RacingBoundaryEpsilonPolicy()
) {

    fun step(
        task: SimpleRacingTask,
        previousState: RacingNavigationState,
        fix: RacingNavigationFix
    ): RacingNavigationDecision {
        val signature = signature(task)
        val state = normalizeState(previousState, task, signature)

        if (state.status == RacingNavigationStatus.FINISHED ||
            state.status == RacingNavigationStatus.INVALIDATED
        ) {
            return RacingNavigationDecision(
                state = state.copy(lastFix = fix),
                event = null
            )
        }

        if (task.waypoints.isEmpty()) {
            return RacingNavigationDecision(
                state = state.copy(lastFix = fix, taskSignature = signature, currentLegIndex = 0),
                event = null
            )
        }

        val lastFix = state.lastFix
        val activeIndex = state.currentLegIndex.coerceIn(0, task.waypoints.lastIndex)
        val activeWaypoint = task.waypoints[activeIndex]
        val previousWaypoint = task.waypoints.getOrNull(activeIndex - 1)
        val nextWaypoint = task.waypoints.getOrNull(activeIndex + 1)

        val status = if (state.status == RacingNavigationStatus.STARTED) {
            RacingNavigationStatus.IN_PROGRESS
        } else {
            state.status
        }

        if (!shouldEvaluateTransitions(activeWaypoint, previousWaypoint, nextWaypoint, fix, lastFix)) {
            val updatedState = state.copy(
                status = status,
                lastFix = fix,
                taskSignature = signature
            )
            return RacingNavigationDecision(state = updatedState, event = null)
        }

        val currentNavPoint = NavPoint(fix.lat, fix.lon)
        val previousNavPoint = lastFix?.let { NavPoint(it.lat, it.lon) }
        val previousFixTimestampMillis = lastFix?.timestampMillis

        val insideNow = isInside(activeWaypoint, currentNavPoint, previousWaypoint, nextWaypoint)
        val insidePrevious = previousNavPoint?.let {
            isInside(activeWaypoint, it, previousWaypoint, nextWaypoint)
        } ?: false

        val decision = when (status) {
            RacingNavigationStatus.PENDING_START -> handleStartTransition(
                task = task,
                state = state.copy(status = status),
                fix = fix,
                previousFix = lastFix,
                previousNavPoint = previousNavPoint,
                previousFixTimestampMillis = previousFixTimestampMillis,
                activeWaypoint = activeWaypoint,
                nextWaypoint = nextWaypoint,
                insidePrevious = insidePrevious,
                insideNow = insideNow
            )
            RacingNavigationStatus.IN_PROGRESS -> handleProgressTransition(
                task = task,
                state = state.copy(status = status),
                fix = fix,
                previousFix = lastFix,
                previousNavPoint = previousNavPoint,
                activeWaypoint = activeWaypoint,
                previousWaypoint = previousWaypoint,
                nextWaypoint = nextWaypoint,
                insidePrevious = insidePrevious,
                insideNow = insideNow
            )
            RacingNavigationStatus.STARTED -> RacingNavigationDecision(state = state, event = null)
            RacingNavigationStatus.FINISHED,
            RacingNavigationStatus.INVALIDATED -> RacingNavigationDecision(state = state, event = null)
        }

        val updatedState = decision.state.copy(lastFix = fix, taskSignature = signature)
        return RacingNavigationDecision(state = updatedState, event = decision.event)
    }

    private fun handleStartTransition(
        task: SimpleRacingTask,
        state: RacingNavigationState,
        fix: RacingNavigationFix,
        previousFix: RacingNavigationFix?,
        previousNavPoint: NavPoint?,
        previousFixTimestampMillis: Long?,
        activeWaypoint: RacingWaypoint,
        nextWaypoint: RacingWaypoint?,
        insidePrevious: Boolean,
        insideNow: Boolean
    ): RacingNavigationDecision {
        if (activeWaypoint.role != RacingWaypointRole.START) {
            return RacingNavigationDecision(state = state, event = null)
        }

        val currentNavPoint = NavPoint(fix.lat, fix.lon)
        val lineTransitionAllowed = previousNavPoint?.let { navPoint ->
            zoneDetector.isLineTransitionAllowed(navPoint, currentNavPoint, activeWaypoint)
        } ?: false

        var crossing: RacingBoundaryCrossing? = null
        val startTriggered = when (activeWaypoint.startPointType) {
            RacingStartPointType.START_LINE -> {
                crossing = detectStartLineCrossing(activeWaypoint, nextWaypoint, previousFix, fix)
                crossing != null || (lineTransitionAllowed && insidePrevious && !insideNow)
            }
            RacingStartPointType.START_CYLINDER -> {
                crossing = detectCylinderCrossing(
                    waypoint = activeWaypoint,
                    previousFix = previousFix,
                    currentFix = fix,
                    transition = RacingBoundaryTransition.EXIT
                )
                crossing != null || (insidePrevious && !insideNow)
            }
            RacingStartPointType.FAI_START_SECTOR -> {
                crossing = detectStartSectorCrossing(activeWaypoint, nextWaypoint, previousFix, fix)
                crossing != null || (insidePrevious && !insideNow)
            }
        }

        if (!startTriggered) {
            return RacingNavigationDecision(state = state, event = null)
        }

        val startTimestampMillis = crossing?.crossingTimeMillis
            ?: previousFixTimestampMillis
            ?: return RacingNavigationDecision(state = state, event = null)

        val nextIndex = minOf(state.currentLegIndex + 1, task.waypoints.lastIndex)
        val nextState = state.copy(
            status = RacingNavigationStatus.STARTED,
            currentLegIndex = nextIndex,
            lastTransitionTimeMillis = startTimestampMillis
        )
        val event = RacingNavigationEvent(
            type = RacingNavigationEventType.START,
            fromLegIndex = state.currentLegIndex,
            toLegIndex = nextIndex,
            waypointRole = activeWaypoint.role,
            timestampMillis = startTimestampMillis
        )
        return RacingNavigationDecision(state = nextState, event = event)
    }

    private fun handleProgressTransition(
        task: SimpleRacingTask,
        state: RacingNavigationState,
        fix: RacingNavigationFix,
        previousFix: RacingNavigationFix?,
        previousNavPoint: NavPoint?,
        activeWaypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint?,
        insidePrevious: Boolean,
        insideNow: Boolean
    ): RacingNavigationDecision {
        val currentNavPoint = NavPoint(fix.lat, fix.lon)
        return when (activeWaypoint.role) {
            RacingWaypointRole.TURNPOINT -> {
                val crossing = when (activeWaypoint.turnPointType) {
                    RacingTurnPointType.TURN_POINT_CYLINDER -> detectCylinderCrossing(
                        waypoint = activeWaypoint,
                        previousFix = previousFix,
                        currentFix = fix,
                        transition = RacingBoundaryTransition.ENTER
                    )
                    RacingTurnPointType.KEYHOLE -> detectKeyholeCrossing(
                        activeWaypoint,
                        previousWaypoint,
                        nextWaypoint,
                        previousFix,
                        fix
                    )
                    RacingTurnPointType.FAI_QUADRANT -> null
                }

                val shouldTrigger = crossing != null || (!insidePrevious && insideNow)

                if (shouldTrigger) {
                    val transitionTime = crossing?.crossingTimeMillis ?: fix.timestampMillis
                    val nextIndex = minOf(state.currentLegIndex + 1, task.waypoints.lastIndex)
                    val nextState = state.copy(
                        status = RacingNavigationStatus.IN_PROGRESS,
                        currentLegIndex = nextIndex,
                        lastTransitionTimeMillis = transitionTime
                    )
                    val event = RacingNavigationEvent(
                        type = RacingNavigationEventType.TURNPOINT,
                        fromLegIndex = state.currentLegIndex,
                        toLegIndex = nextIndex,
                        waypointRole = activeWaypoint.role,
                        timestampMillis = transitionTime
                    )
                    RacingNavigationDecision(state = nextState, event = event)
                } else {
                    RacingNavigationDecision(state = state, event = null)
                }
            }
            RacingWaypointRole.FINISH -> {
                var crossing: RacingBoundaryCrossing? = null
                val lineTransitionAllowed = previousNavPoint?.let { navPoint ->
                    zoneDetector.isLineTransitionAllowed(navPoint, currentNavPoint, activeWaypoint)
                } ?: false
                val finishTriggered = when (activeWaypoint.finishPointType) {
                    RacingFinishPointType.FINISH_LINE -> {
                        crossing = detectFinishLineCrossing(activeWaypoint, previousWaypoint, previousFix, fix)
                        crossing != null || (lineTransitionAllowed && !insidePrevious && insideNow)
                    }
                    RacingFinishPointType.FINISH_CYLINDER -> {
                        crossing = detectCylinderCrossing(
                            waypoint = activeWaypoint,
                            previousFix = previousFix,
                            currentFix = fix,
                            transition = RacingBoundaryTransition.ENTER
                        )
                        crossing != null || (!insidePrevious && insideNow)
                    }
                }

                if (!finishTriggered) {
                    return RacingNavigationDecision(state = state, event = null)
                }

                val transitionTime = crossing?.crossingTimeMillis ?: fix.timestampMillis
                val nextState = state.copy(
                    status = RacingNavigationStatus.FINISHED,
                    lastTransitionTimeMillis = transitionTime
                )
                val event = RacingNavigationEvent(
                    type = RacingNavigationEventType.FINISH,
                    fromLegIndex = state.currentLegIndex,
                    toLegIndex = state.currentLegIndex,
                    waypointRole = activeWaypoint.role,
                    timestampMillis = transitionTime
                )
                RacingNavigationDecision(state = nextState, event = event)
            }
            RacingWaypointRole.START -> RacingNavigationDecision(state = state, event = null)
        }
    }

    private fun isInside(
        waypoint: RacingWaypoint,
        position: NavPoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint?
    ): Boolean {
        return when (waypoint.role) {
            RacingWaypointRole.START -> zoneDetector.isInsideStartZone(position, waypoint, nextWaypoint)
            RacingWaypointRole.TURNPOINT -> zoneDetector.isInsideTurnZone(position, waypoint, previousWaypoint, nextWaypoint)
            RacingWaypointRole.FINISH -> zoneDetector.isInsideFinishZone(position, waypoint, previousWaypoint)
        }
    }

    private fun normalizeState(
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

    private fun signature(task: SimpleRacingTask): String {
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
                append(waypoint.gateWidth)
                append(":")
                append(waypoint.keyholeInnerRadius)
                append(":")
                append(waypoint.keyholeAngle)
                append(":")
                append(waypoint.faiQuadrantOuterRadius)
                append(":")
                append(waypoint.lat)
                append(":")
                append(waypoint.lon)
            }
        }
    }

    private fun distanceMeters(a: RacingNavigationFix, b: RacingNavigationFix): Double {
        val km = RacingGeometryUtils.haversineDistance(a.lat, a.lon, b.lat, b.lon)
        return abs(km * 1000.0)
    }

    private fun shouldEvaluateTransitions(
        activeWaypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint?,
        fix: RacingNavigationFix,
        lastFix: RacingNavigationFix?
    ): Boolean {
        val radiusMeters = proximityRadiusMeters(activeWaypoint, previousWaypoint, nextWaypoint) ?: return true
        val margin = epsilonPolicy.epsilonMeters(fix)
        val limit = radiusMeters + margin
        val currentDistance = distanceMeters(activeWaypoint, fix)
        if (currentDistance <= limit) return true
        val lastDistance = lastFix?.let { distanceMeters(activeWaypoint, it) } ?: currentDistance
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
        val lengthMeters = waypoint.gateWidth * 1000.0
        return lengthMeters / 2.0
    }

    private fun cylinderRadiusMeters(waypoint: RacingWaypoint): Double {
        return waypoint.gateWidth * 1000.0
    }

    private fun sectorRadiusMeters(
        waypoint: RacingWaypoint,
        nextWaypoint: RacingWaypoint?
    ): Double {
        if (nextWaypoint == null) return 0.0
        return waypoint.gateWidth * 1000.0
    }

    private fun keyholeRadiusMeters(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint?
    ): Double {
        if (previousWaypoint == null || nextWaypoint == null) return 0.0
        val outer = waypoint.gateWidth * 1000.0
        val inner = waypoint.keyholeInnerRadius * 1000.0
        return max(outer, inner)
    }

    private fun faiQuadrantRadiusMeters(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint?
    ): Double {
        if (previousWaypoint == null || nextWaypoint == null) return 0.0
        return waypoint.faiQuadrantOuterRadius * 1000.0
    }

    private fun distanceMeters(waypoint: RacingWaypoint, fix: RacingNavigationFix): Double {
        val km = RacingGeometryUtils.haversineDistance(waypoint.lat, waypoint.lon, fix.lat, fix.lon)
        return abs(km * 1000.0)
    }

    private fun detectCylinderCrossing(
        waypoint: RacingWaypoint,
        previousFix: RacingNavigationFix?,
        currentFix: RacingNavigationFix,
        transition: RacingBoundaryTransition
    ): RacingBoundaryCrossing? {
        if (previousFix == null) return null
        val radiusMeters = waypoint.gateWidth * 1000.0
        if (radiusMeters <= 0.0) return null
        return crossingPlanner.detectCylinderCrossing(
            center = RacingBoundaryPoint(waypoint.lat, waypoint.lon),
            radiusMeters = radiusMeters,
            previousFix = previousFix,
            currentFix = currentFix,
            transition = transition
        )
    }

    private fun detectStartLineCrossing(
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
            lineLengthMeters = waypoint.gateWidth * 1000.0,
            lineBearingDegrees = lineBearing,
            sectorBearingDegrees = sectorBearing,
            previousFix = previousFix,
            currentFix = currentFix,
            transition = RacingBoundaryTransition.EXIT
        )
    }

    private fun detectFinishLineCrossing(
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
            lineLengthMeters = waypoint.gateWidth * 1000.0,
            lineBearingDegrees = lineBearing,
            sectorBearingDegrees = sectorBearing,
            previousFix = previousFix,
            currentFix = currentFix,
            transition = RacingBoundaryTransition.ENTER
        )
    }

    private fun detectStartSectorCrossing(
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
            radiusMeters = waypoint.gateWidth * 1000.0,
            sectorBearingDegrees = bearingToNext,
            halfAngleDegrees = halfAngle,
            previousFix = previousFix,
            currentFix = currentFix,
            transition = RacingBoundaryTransition.EXIT
        )
    }

    private fun detectKeyholeCrossing(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint?,
        previousFix: RacingNavigationFix?,
        currentFix: RacingNavigationFix
    ): RacingBoundaryCrossing? {
        if (previousFix == null || previousWaypoint == null || nextWaypoint == null) return null
        val innerRadiusMeters = waypoint.keyholeInnerRadius * 1000.0
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
            radiusMeters = waypoint.gateWidth * 1000.0,
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
}
