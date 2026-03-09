package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossing
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlanner
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryTransition
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole

internal fun evaluateStartTransition(
    taskWaypoints: List<RacingWaypoint>,
    state: RacingNavigationState,
    fix: RacingNavigationFix,
    previousFix: RacingNavigationFix?,
    previousNavPoint: NavPoint?,
    activeWaypoint: RacingWaypoint,
    nextWaypoint: RacingWaypoint?,
    insidePrevious: Boolean,
    insideNow: Boolean,
    startRules: RacingStartCustomParams,
    zoneDetector: RacingZoneDetector,
    crossingPlanner: RacingBoundaryCrossingPlanner,
    startEvaluator: RacingStartEvaluator
): RacingNavigationDecision {
    if (activeWaypoint.role != RacingWaypointRole.START) {
        return RacingNavigationDecision(state = state, event = null)
    }

    val stateWithAltitude = state.copy(
        preStartAltitudeSatisfied = startEvaluator.updatePreStartAltitudeSatisfied(
            state = state,
            fix = fix,
            rules = startRules
        )
    )

    val currentNavPoint = NavPoint(fix.lat, fix.lon)
    val lineTransitionAllowed = previousNavPoint?.let { navPoint ->
        zoneDetector.isLineTransitionAllowed(navPoint, currentNavPoint, activeWaypoint)
    } ?: false

    var strictCrossing: RacingBoundaryCrossing? = null
    var strictCrossingMillis: Long? = null
    var wrongDirection = false

    when (activeWaypoint.startPointType) {
        RacingStartPointType.START_LINE -> {
            strictCrossing = detectStartLineCrossing(
                crossingPlanner = crossingPlanner,
                waypoint = activeWaypoint,
                nextWaypoint = nextWaypoint,
                previousFix = previousFix,
                currentFix = fix,
                directionOverrideDegrees = startRules.directionOverrideDegrees
            )
            strictCrossingMillis = strictCrossing?.crossingTimeMillis
            val wrongDirectionCrossing = detectStartLineWrongDirectionCrossing(
                crossingPlanner = crossingPlanner,
                waypoint = activeWaypoint,
                nextWaypoint = nextWaypoint,
                previousFix = previousFix,
                currentFix = fix,
                directionOverrideDegrees = startRules.directionOverrideDegrees
            )
            wrongDirection = strictCrossingMillis == null &&
                wrongDirectionCrossing != null &&
                lineTransitionAllowed
        }
        RacingStartPointType.START_CYLINDER -> {
            strictCrossing = detectCylinderCrossing(
                crossingPlanner = crossingPlanner,
                waypoint = activeWaypoint,
                previousFix = previousFix,
                currentFix = fix,
                transition = RacingBoundaryTransition.EXIT
            )
            strictCrossingMillis = strictCrossing?.crossingTimeMillis
            wrongDirection = strictCrossingMillis == null && !insidePrevious && insideNow
        }
        RacingStartPointType.FAI_START_SECTOR -> {
            strictCrossing = detectStartSectorCrossing(
                crossingPlanner = crossingPlanner,
                waypoint = activeWaypoint,
                nextWaypoint = nextWaypoint,
                previousFix = previousFix,
                currentFix = fix,
                directionOverrideDegrees = startRules.directionOverrideDegrees
            )
            strictCrossingMillis = strictCrossing?.crossingTimeMillis
            wrongDirection = strictCrossingMillis == null && !insidePrevious && insideNow
        }
    }

    if (strictCrossingMillis != null) {
        val strictCandidate = startEvaluator.evaluateStrictCandidate(
            state = stateWithAltitude,
            fix = fix,
            timestampMillis = strictCrossingMillis,
            startType = activeWaypoint.startPointType,
            rules = startRules
        )
        val stateWithCandidate = startEvaluator.appendCandidate(stateWithAltitude, strictCandidate)
        return applyStartCandidate(
            taskWaypoints = taskWaypoints,
            state = stateWithCandidate,
            activeWaypoint = activeWaypoint,
            candidate = strictCandidate,
            crossingEvidence = strictCrossing?.let { crossing ->
                RacingBoundaryCrossingEvidence(
                    crossingPoint = crossing.crossingPoint,
                    insideAnchor = crossing.insideAnchor,
                    outsideAnchor = crossing.outsideAnchor,
                    evidenceSource = crossing.evidenceSource
                )
            }
        )
    }

    if (wrongDirection) {
        val rejected = startEvaluator.rejectedDirectionCandidate(fix.timestampMillis)
        val stateWithCandidate = startEvaluator.appendCandidate(stateWithAltitude, rejected)
        return rejectedStartDecision(stateWithCandidate, activeWaypoint, rejected)
    }

    val toleranceDistanceMeters = distanceToStartBoundaryMeters(
        waypoint = activeWaypoint,
        nextWaypoint = nextWaypoint,
        fix = fix,
        directionOverrideDegrees = startRules.directionOverrideDegrees
    )
    val toleranceTriggered = previousFix != null &&
        toleranceDistanceMeters != null &&
        toleranceDistanceMeters <= startRules.toleranceMeters

    if (!toleranceTriggered) {
        return RacingNavigationDecision(state = stateWithAltitude, event = null)
    }

    val toleranceCandidate = startEvaluator.evaluateToleranceCandidate(
        state = stateWithAltitude,
        fix = fix,
        timestampMillis = fix.timestampMillis,
        startType = activeWaypoint.startPointType,
        rules = startRules
    )
    val stateWithCandidate = startEvaluator.appendCandidate(stateWithAltitude, toleranceCandidate)
    return applyStartCandidate(taskWaypoints, stateWithCandidate, activeWaypoint, toleranceCandidate)
}

private fun applyStartCandidate(
    taskWaypoints: List<RacingWaypoint>,
    state: RacingNavigationState,
    activeWaypoint: RacingWaypoint,
    candidate: RacingStartCandidate,
    crossingEvidence: RacingBoundaryCrossingEvidence? = null
): RacingNavigationDecision {
    if (!candidate.isValid) {
        return rejectedStartDecision(state, activeWaypoint, candidate)
    }

    val selectedIndex = RacingStartCandidateSelector.selectBestIndex(state.startCandidates)
    val selectedCandidate = selectedIndex
        ?.let { index -> state.startCandidates.getOrNull(index) }
        ?: candidate
    val nextIndex = minOf(state.currentLegIndex + 1, taskWaypoints.lastIndex)
    val nextState = state.copy(
        status = RacingNavigationStatus.STARTED,
        currentLegIndex = nextIndex,
        lastTransitionTimeMillis = selectedCandidate.timestampMillis,
        selectedStartCandidateIndex = selectedIndex
    )
    val event = RacingNavigationEvent(
        type = RacingNavigationEventType.START,
        fromLegIndex = state.currentLegIndex,
        toLegIndex = nextIndex,
        waypointRole = activeWaypoint.role,
        timestampMillis = selectedCandidate.timestampMillis,
        startCandidate = selectedCandidate,
        crossingEvidence = crossingEvidence
    )
    return RacingNavigationDecision(state = nextState, event = event)
}

private fun rejectedStartDecision(
    state: RacingNavigationState,
    activeWaypoint: RacingWaypoint,
    candidate: RacingStartCandidate
): RacingNavigationDecision {
    val event = RacingNavigationEvent(
        type = RacingNavigationEventType.START_REJECTED,
        fromLegIndex = state.currentLegIndex,
        toLegIndex = state.currentLegIndex,
        waypointRole = activeWaypoint.role,
        timestampMillis = candidate.timestampMillis,
        startCandidate = candidate
    )
    return RacingNavigationDecision(state = state, event = event)
}
