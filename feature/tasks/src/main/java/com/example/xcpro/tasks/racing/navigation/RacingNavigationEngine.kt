package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlanner
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryTransition
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole

class RacingNavigationEngine internal constructor(
    private val zoneDetector: RacingZoneDetector,
    private val crossingPlanner: RacingBoundaryCrossingPlanner,
    private val epsilonPolicy: RacingBoundaryEpsilonPolicy,
    private val startEvaluator: RacingStartEvaluator
) {

    constructor() : this(
        zoneDetector = RacingZoneDetector(),
        crossingPlanner = RacingBoundaryCrossingPlanner(),
        epsilonPolicy = RacingBoundaryEpsilonPolicy(),
        startEvaluator = RacingStartEvaluator()
    )

    constructor(
        crossingPlanner: RacingBoundaryCrossingPlanner,
        epsilonPolicy: RacingBoundaryEpsilonPolicy
    ) : this(
        zoneDetector = RacingZoneDetector(),
        crossingPlanner = crossingPlanner,
        epsilonPolicy = epsilonPolicy,
        startEvaluator = RacingStartEvaluator()
    )

    fun step(
        taskWaypoints: List<RacingWaypoint>,
        previousState: RacingNavigationState,
        fix: RacingNavigationFix,
        startRules: RacingStartCustomParams = RacingStartCustomParams(),
        finishRules: RacingFinishCustomParams = RacingFinishCustomParams()
    ): RacingNavigationDecision {
        val signature = buildTaskSignature(taskWaypoints)
        val state = normalizeNavigationState(previousState, taskWaypoints, signature)

        if (state.status == RacingNavigationStatus.INVALIDATED) {
            return RacingNavigationDecision(
                state = state.copy(lastFix = fix),
                event = null
            )
        }
        if (state.status == RacingNavigationStatus.FINISHED) {
            val postFinishState = evaluatePostFinishState(state, fix, finishRules)
            return RacingNavigationDecision(
                state = postFinishState.copy(lastFix = fix),
                event = null
            )
        }

        if (taskWaypoints.isEmpty()) {
            return RacingNavigationDecision(
                state = state.copy(lastFix = fix, taskSignature = signature, currentLegIndex = 0),
                event = null
            )
        }

        val lastFix = state.lastFix
        val activeIndex = state.currentLegIndex.coerceIn(0, taskWaypoints.lastIndex)
        val activeWaypoint = taskWaypoints[activeIndex]
        val previousWaypoint = taskWaypoints.getOrNull(activeIndex - 1)
        val nextWaypoint = taskWaypoints.getOrNull(activeIndex + 1)

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

        val insideNow = isInside(activeWaypoint, currentNavPoint, previousWaypoint, nextWaypoint)
        val insidePrevious = previousNavPoint?.let {
            isInside(activeWaypoint, it, previousWaypoint, nextWaypoint)
        } ?: false

        val decision = when (status) {
            RacingNavigationStatus.PENDING_START -> evaluateStartTransition(
                taskWaypoints = taskWaypoints,
                state = state.copy(status = status),
                fix = fix,
                previousFix = lastFix,
                previousNavPoint = previousNavPoint,
                activeWaypoint = activeWaypoint,
                nextWaypoint = nextWaypoint,
                insidePrevious = insidePrevious,
                insideNow = insideNow,
                startRules = startRules,
                zoneDetector = zoneDetector,
                crossingPlanner = crossingPlanner,
                startEvaluator = startEvaluator
            )
            RacingNavigationStatus.IN_PROGRESS -> handleProgressTransition(
                taskWaypoints = taskWaypoints,
                state = state.copy(status = status),
                fix = fix,
                previousFix = lastFix,
                activeWaypoint = activeWaypoint,
                previousWaypoint = previousWaypoint,
                nextWaypoint = nextWaypoint,
                insidePrevious = insidePrevious,
                insideNow = insideNow,
                finishRules = finishRules
            )
            RacingNavigationStatus.STARTED -> RacingNavigationDecision(state = state, event = null)
            RacingNavigationStatus.FINISHED,
            RacingNavigationStatus.INVALIDATED -> RacingNavigationDecision(state = state, event = null)
        }

        val updatedState = decision.state.copy(lastFix = fix, taskSignature = signature)
        return RacingNavigationDecision(state = updatedState, event = decision.event)
    }

    fun step(
        task: SimpleRacingTask,
        previousState: RacingNavigationState,
        fix: RacingNavigationFix,
        startRules: RacingStartCustomParams = RacingStartCustomParams(),
        finishRules: RacingFinishCustomParams = RacingFinishCustomParams()
    ): RacingNavigationDecision = step(task.waypoints, previousState, fix, startRules, finishRules)

    private fun handleProgressTransition(
        taskWaypoints: List<RacingWaypoint>,
        state: RacingNavigationState,
        fix: RacingNavigationFix,
        previousFix: RacingNavigationFix?,
        activeWaypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint?,
        insidePrevious: Boolean,
        insideNow: Boolean,
        finishRules: RacingFinishCustomParams
    ): RacingNavigationDecision {
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
                    RacingTurnPointType.FAI_QUADRANT -> detectFaiQuadrantCrossing(
                        crossingPlanner = crossingPlanner,
                        waypoint = activeWaypoint,
                        previousWaypoint = previousWaypoint,
                        nextWaypoint = nextWaypoint,
                        previousFix = previousFix,
                        currentFix = fix
                    )
                }

                val achievedByInsideFix = !insidePrevious && insideNow
                val shouldTrigger = crossing != null || achievedByInsideFix

                if (shouldTrigger) {
                    val transitionTime = crossing?.crossingTimeMillis ?: fix.timestampMillis
                    val nextIndex = minOf(state.currentLegIndex + 1, taskWaypoints.lastIndex)
                    val nextState = state.copy(
                        status = RacingNavigationStatus.IN_PROGRESS,
                        currentLegIndex = nextIndex,
                        lastTransitionTimeMillis = transitionTime,
                        reportedNearMissTurnpointLegIndices = state.reportedNearMissTurnpointLegIndices -
                            state.currentLegIndex
                    )
                    val event = RacingNavigationEvent(
                        type = RacingNavigationEventType.TURNPOINT,
                        fromLegIndex = state.currentLegIndex,
                        toLegIndex = nextIndex,
                        waypointRole = activeWaypoint.role,
                        timestampMillis = transitionTime,
                        crossingEvidence = crossing?.let { boundaryCrossing ->
                            RacingBoundaryCrossingEvidence(
                                crossingPoint = boundaryCrossing.crossingPoint,
                                insideAnchor = boundaryCrossing.insideAnchor,
                                outsideAnchor = boundaryCrossing.outsideAnchor,
                                evidenceSource = boundaryCrossing.evidenceSource
                            )
                        }
                    )
                    RacingNavigationDecision(state = nextState, event = event)
                } else {
                    val nearMissDistance = turnpointNearMissDistanceMeters(
                        waypoint = activeWaypoint,
                        previousFix = previousFix,
                        currentFix = fix
                    )
                    val hasReportedNearMiss = state.reportedNearMissTurnpointLegIndices.contains(state.currentLegIndex)
                    if (nearMissDistance != null && !hasReportedNearMiss) {
                        val nextState = state.copy(
                            reportedNearMissTurnpointLegIndices = state.reportedNearMissTurnpointLegIndices +
                                state.currentLegIndex
                        )
                        val event = RacingNavigationEvent(
                            type = RacingNavigationEventType.TURNPOINT_NEAR_MISS,
                            fromLegIndex = state.currentLegIndex,
                            toLegIndex = state.currentLegIndex,
                            waypointRole = activeWaypoint.role,
                            timestampMillis = fix.timestampMillis,
                            turnpointNearMissDistanceMeters = nearMissDistance
                        )
                        RacingNavigationDecision(state = nextState, event = event)
                    } else {
                        RacingNavigationDecision(state = state, event = null)
                    }
                }
            }
            RacingWaypointRole.FINISH -> {
                evaluateFinishProgressTransition(
                    state = state,
                    fix = fix,
                    previousFix = previousFix,
                    activeWaypoint = activeWaypoint,
                    previousWaypoint = previousWaypoint,
                    insidePrevious = insidePrevious,
                    insideNow = insideNow,
                    crossingPlanner = crossingPlanner,
                    finishRules = finishRules
                )
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
