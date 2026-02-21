package com.example.xcpro.tasks.racing.navigation

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
        val signature = buildTaskSignature(task)
        val state = normalizeNavigationState(previousState, task, signature)

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

        if (!shouldEvaluateTransitions(
                activeWaypoint = activeWaypoint,
                previousWaypoint = previousWaypoint,
                nextWaypoint = nextWaypoint,
                fix = fix,
                lastFix = lastFix,
                epsilonPolicy = epsilonPolicy
            )
        ) {
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
                    crossing = detectStartLineCrossing(
                        crossingPlanner = crossingPlanner,
                        waypoint = activeWaypoint,
                        nextWaypoint = nextWaypoint,
                        previousFix = previousFix,
                        currentFix = fix
                    )
                    crossing != null || (lineTransitionAllowed && insidePrevious && !insideNow)
                }
                RacingStartPointType.START_CYLINDER -> {
                    crossing = detectCylinderCrossing(
                        crossingPlanner = crossingPlanner,
                        waypoint = activeWaypoint,
                        previousFix = previousFix,
                        currentFix = fix,
                    transition = RacingBoundaryTransition.EXIT
                )
                crossing != null || (insidePrevious && !insideNow)
                }
                RacingStartPointType.FAI_START_SECTOR -> {
                    crossing = detectStartSectorCrossing(
                        crossingPlanner = crossingPlanner,
                        waypoint = activeWaypoint,
                        nextWaypoint = nextWaypoint,
                        previousFix = previousFix,
                        currentFix = fix
                    )
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
                        crossingPlanner = crossingPlanner,
                        waypoint = activeWaypoint,
                        previousFix = previousFix,
                        currentFix = fix,
                        transition = RacingBoundaryTransition.ENTER
                    )
                    RacingTurnPointType.KEYHOLE -> detectKeyholeCrossing(
                        crossingPlanner = crossingPlanner,
                        waypoint = activeWaypoint,
                        previousWaypoint = previousWaypoint,
                        nextWaypoint = nextWaypoint,
                        previousFix = previousFix,
                        currentFix = fix
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
                        crossing = detectFinishLineCrossing(
                            crossingPlanner = crossingPlanner,
                            waypoint = activeWaypoint,
                            previousWaypoint = previousWaypoint,
                            previousFix = previousFix,
                            currentFix = fix
                        )
                        crossing != null || (lineTransitionAllowed && !insidePrevious && insideNow)
                    }
                    RacingFinishPointType.FINISH_CYLINDER -> {
                        crossing = detectCylinderCrossing(
                            crossingPlanner = crossingPlanner,
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
}
