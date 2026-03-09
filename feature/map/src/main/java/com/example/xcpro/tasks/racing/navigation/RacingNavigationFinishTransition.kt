package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossing
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlanner
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryTransition
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint

internal fun evaluatePostFinishState(
    state: RacingNavigationState,
    fix: RacingNavigationFix,
    finishRules: RacingFinishCustomParams
): RacingNavigationState {
    if (state.finishOutcome != RacingFinishOutcome.LANDING_PENDING ||
        !finishRules.requireLandWithoutDelay
    ) {
        return state
    }
    val landingDeadlineMillis = state.finishLandingDeadlineMillis ?: return state
    val stopStartMillis = updateStopStartTimeMillis(
        previousStopStartMillis = state.finishLandingStopStartTimeMillis,
        fix = fix,
        speedThresholdMs = finishRules.landingSpeedThresholdMs
    )

    val landed = stopStartMillis != null &&
        (fix.timestampMillis - stopStartMillis) >= finishRules.landingHoldSeconds * 1000L
    if (landed) {
        val landedWithinDelay = fix.timestampMillis <= landingDeadlineMillis
        return state.copy(
            finishOutcome = if (landedWithinDelay) {
                RacingFinishOutcome.LANDED_WITHOUT_DELAY
            } else {
                RacingFinishOutcome.LANDING_DELAY_VIOLATION
            },
            finishLandingStopStartTimeMillis = stopStartMillis
        )
    }

    if (fix.timestampMillis > landingDeadlineMillis) {
        return state.copy(
            finishOutcome = RacingFinishOutcome.LANDING_DELAY_VIOLATION,
            finishLandingStopStartTimeMillis = stopStartMillis
        )
    }

    return state.copy(finishLandingStopStartTimeMillis = stopStartMillis)
}

internal fun evaluateFinishProgressTransition(
    state: RacingNavigationState,
    fix: RacingNavigationFix,
    previousFix: RacingNavigationFix?,
    activeWaypoint: RacingWaypoint,
    previousWaypoint: RacingWaypoint?,
    insidePrevious: Boolean,
    insideNow: Boolean,
    crossingPlanner: RacingBoundaryCrossingPlanner,
    finishRules: RacingFinishCustomParams
): RacingNavigationDecision {
    val stateWithCloseTracking = rememberLastFixBeforeClose(state, fix, finishRules)

    val finishCloseTimeMillis = finishRules.closeTimeMillis

    var crossing: RacingBoundaryCrossing? = null
    val finishTriggered = when (activeWaypoint.finishPointType) {
        RacingFinishPointType.FINISH_LINE -> {
            crossing = detectFinishLineCrossing(
                crossingPlanner = crossingPlanner,
                waypoint = activeWaypoint,
                previousWaypoint = previousWaypoint,
                previousFix = previousFix,
                currentFix = fix,
                directionOverrideDegrees = finishRules.directionOverrideDegrees
            )
            crossing != null
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
        if (finishCloseTimeMillis != null && fix.timestampMillis > finishCloseTimeMillis) {
            val closeDecision = finishOutlandedAtCloseDecision(
                state = stateWithCloseTracking,
                activeWaypoint = activeWaypoint,
                fallbackFix = previousFix
            )
            if (closeDecision != null) {
                return closeDecision
            }
        }
        val stopPlusFiveDecision = finishContestBoundaryStopPlusFiveDecision(
            state = stateWithCloseTracking,
            fix = fix,
            waypoint = activeWaypoint,
            finishRules = finishRules
        )
        if (stopPlusFiveDecision != null) {
            return stopPlusFiveDecision
        }
        return RacingNavigationDecision(state = stateWithCloseTracking, event = null)
    }

    val transitionTime = crossing?.crossingTimeMillis ?: fix.timestampMillis
    if (finishCloseTimeMillis != null && transitionTime > finishCloseTimeMillis) {
        val closeDecision = finishOutlandedAtCloseDecision(
            state = stateWithCloseTracking,
            activeWaypoint = activeWaypoint,
            fallbackFix = previousFix
        )
        if (closeDecision != null) {
            return closeDecision
        }
        return RacingNavigationDecision(state = stateWithCloseTracking, event = null)
    }

    val minAltitudeMeters = finishRules.minAltitudeMeters
    val altitudeMeters = resolveNavigationAltitudeMeters(fix, finishRules.altitudeReference)
    val missingAltitudeEvidence = minAltitudeMeters != null && altitudeMeters == null
    if (missingAltitudeEvidence) {
        return RacingNavigationDecision(state = stateWithCloseTracking, event = null)
    }
    val belowMinAltitude = minAltitudeMeters != null &&
        altitudeMeters != null &&
        altitudeMeters < minAltitudeMeters
    val straightInException = belowMinAltitude && finishRules.allowStraightInBelowMinAltitude
    if (belowMinAltitude && !straightInException) {
        return RacingNavigationDecision(state = stateWithCloseTracking, event = null)
    }

    val landingPending = finishRules.requireLandWithoutDelay
    val finishOutcome = if (landingPending) {
        RacingFinishOutcome.LANDING_PENDING
    } else {
        RacingFinishOutcome.VALID
    }
    val landingDeadlineMillis = if (landingPending) {
        transitionTime + finishRules.landWithoutDelayWindowSeconds * 1000L
    } else {
        null
    }

    val nextState = stateWithCloseTracking.copy(
        status = RacingNavigationStatus.FINISHED,
        lastTransitionTimeMillis = transitionTime,
        finishOutcome = finishOutcome,
        finishUsedStraightInException = straightInException,
        finishCrossingTimeMillis = transitionTime,
        finishLandingDeadlineMillis = landingDeadlineMillis,
        finishLandingStopStartTimeMillis = null,
        finishBoundaryStopStartTimeMillis = null
    )
    val event = RacingNavigationEvent(
        type = RacingNavigationEventType.FINISH,
        fromLegIndex = state.currentLegIndex,
        toLegIndex = state.currentLegIndex,
        waypointRole = activeWaypoint.role,
        timestampMillis = transitionTime,
        finishOutcome = finishOutcome,
        finishUsedStraightInException = straightInException,
        crossingEvidence = crossing?.let { boundaryCrossing ->
            RacingBoundaryCrossingEvidence(
                crossingPoint = boundaryCrossing.crossingPoint,
                insideAnchor = boundaryCrossing.insideAnchor,
                outsideAnchor = boundaryCrossing.outsideAnchor,
                evidenceSource = boundaryCrossing.evidenceSource
            )
        }
    )
    return RacingNavigationDecision(state = nextState, event = event)
}

private fun rememberLastFixBeforeClose(
    state: RacingNavigationState,
    fix: RacingNavigationFix,
    finishRules: RacingFinishCustomParams
): RacingNavigationState {
    val closeTimeMillis = finishRules.closeTimeMillis ?: return state
    if (fix.timestampMillis > closeTimeMillis) {
        return state
    }
    val existing = state.lastFixBeforeFinishClose
    val shouldReplace = existing == null || fix.timestampMillis >= existing.timestampMillis
    return if (shouldReplace) {
        state.copy(lastFixBeforeFinishClose = fix)
    } else {
        state
    }
}

private fun finishOutlandedAtCloseDecision(
    state: RacingNavigationState,
    activeWaypoint: RacingWaypoint,
    fallbackFix: RacingNavigationFix?
): RacingNavigationDecision? {
    val outlandingFix = state.lastFixBeforeFinishClose
        ?: fallbackFix
        ?: return null
    val transitionTime = outlandingFix.timestampMillis
    val nextState = state.copy(
        status = RacingNavigationStatus.FINISHED,
        lastTransitionTimeMillis = transitionTime,
        finishOutcome = RacingFinishOutcome.OUTLANDED_AT_CLOSE,
        finishUsedStraightInException = false,
        finishCrossingTimeMillis = transitionTime,
        finishLandingDeadlineMillis = null,
        finishLandingStopStartTimeMillis = null,
        finishBoundaryStopStartTimeMillis = null
    )
    val event = RacingNavigationEvent(
        type = RacingNavigationEventType.FINISH,
        fromLegIndex = state.currentLegIndex,
        toLegIndex = state.currentLegIndex,
        waypointRole = activeWaypoint.role,
        timestampMillis = transitionTime,
        finishOutcome = RacingFinishOutcome.OUTLANDED_AT_CLOSE
    )
    return RacingNavigationDecision(state = nextState, event = event)
}

private fun finishContestBoundaryStopPlusFiveDecision(
    state: RacingNavigationState,
    fix: RacingNavigationFix,
    waypoint: RacingWaypoint,
    finishRules: RacingFinishCustomParams
): RacingNavigationDecision? {
    if (!finishRules.stopPlusFiveEnabled || waypoint.finishPointType != RacingFinishPointType.FINISH_LINE) {
        return null
    }
    val boundaryRadiusMeters = finishRules.contestBoundaryRadiusMeters ?: return null
    val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
        waypoint.lat,
        waypoint.lon,
        fix.lat,
        fix.lon
    )
    val insideBoundary = distanceMeters <= boundaryRadiusMeters
    val stopStartMillis = if (insideBoundary) {
        updateStopStartTimeMillis(
            previousStopStartMillis = state.finishBoundaryStopStartTimeMillis,
            fix = fix,
            speedThresholdMs = finishRules.landingSpeedThresholdMs
        )
    } else {
        null
    }
    val stateWithStop = state.copy(finishBoundaryStopStartTimeMillis = stopStartMillis)
    val stableStopMillis = stopStartMillis ?: return RacingNavigationDecision(state = stateWithStop, event = null)
    val finishTimeMillis = stableStopMillis + finishRules.stopPlusFiveMinutes * 60_000L
    if (fix.timestampMillis < finishTimeMillis) {
        return RacingNavigationDecision(state = stateWithStop, event = null)
    }

    val transitionTime = nearestSecondTimestamp(finishTimeMillis)
    val nextState = stateWithStop.copy(
        status = RacingNavigationStatus.FINISHED,
        lastTransitionTimeMillis = transitionTime,
        finishOutcome = RacingFinishOutcome.CONTEST_BOUNDARY_STOP_PLUS_FIVE,
        finishUsedStraightInException = false,
        finishCrossingTimeMillis = transitionTime,
        finishLandingDeadlineMillis = null,
        finishLandingStopStartTimeMillis = null,
        finishBoundaryStopStartTimeMillis = null
    )
    val event = RacingNavigationEvent(
        type = RacingNavigationEventType.FINISH,
        fromLegIndex = state.currentLegIndex,
        toLegIndex = state.currentLegIndex,
        waypointRole = waypoint.role,
        timestampMillis = transitionTime,
        finishOutcome = RacingFinishOutcome.CONTEST_BOUNDARY_STOP_PLUS_FIVE
    )
    return RacingNavigationDecision(state = nextState, event = event)
}

private fun updateStopStartTimeMillis(
    previousStopStartMillis: Long?,
    fix: RacingNavigationFix,
    speedThresholdMs: Double
): Long? {
    val speedMs = fix.groundSpeedMs ?: return null
    if (speedMs <= speedThresholdMs) {
        return previousStopStartMillis ?: fix.timestampMillis
    }
    return null
}

private fun nearestSecondTimestamp(timestampMillis: Long): Long {
    return ((timestampMillis + 500L) / 1000L) * 1000L
}
