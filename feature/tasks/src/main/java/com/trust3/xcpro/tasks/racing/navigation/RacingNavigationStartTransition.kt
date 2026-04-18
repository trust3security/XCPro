package com.trust3.xcpro.tasks.racing.navigation

import com.trust3.xcpro.tasks.core.RacingStartCustomParams
import com.trust3.xcpro.tasks.racing.RacingGeometryUtils
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryCrossing
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryCrossingMath
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryGeometry
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlanner
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryTransition
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole
import kotlin.math.cos
import kotlin.math.sin

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
    startArmed: Boolean,
    zoneDetector: RacingZoneDetector,
    crossingPlanner: RacingBoundaryCrossingPlanner,
    epsilonPolicy: RacingBoundaryEpsilonPolicy,
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
    val stateWithApproachEvidence = stateWithAltitude.copy(
        hasObservedRequiredApproachSideForActiveLeg =
            stateWithAltitude.hasObservedRequiredApproachSideForActiveLeg ||
            hasObservedRequiredStartApproachSide(
                waypoint = activeWaypoint,
                nextWaypoint = nextWaypoint,
                fix = fix,
                directionOverrideDegrees = startRules.directionOverrideDegrees,
                epsilonPolicy = epsilonPolicy
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
        if (!stateWithApproachEvidence.hasObservedRequiredApproachSideForActiveLeg) {
            return RacingNavigationDecision(state = stateWithApproachEvidence, event = null)
        }
        if (!startArmed) {
            return RacingNavigationDecision(state = stateWithApproachEvidence, event = null)
        }
        val strictCandidate = startEvaluator.evaluateStrictCandidate(
            state = stateWithApproachEvidence,
            fix = fix,
            timestampMillis = strictCrossingMillis,
            startType = activeWaypoint.startPointType,
            rules = startRules
        ).copy(
            crossingEvidence = strictCrossing?.toEventEvidence()
        )
        val stateWithCandidate = startEvaluator.appendCandidate(stateWithApproachEvidence, strictCandidate)
        return applyStartCandidate(taskWaypoints, stateWithCandidate, activeWaypoint, strictCandidate)
    }

    if (wrongDirection) {
        if (!startArmed) {
            return RacingNavigationDecision(state = stateWithApproachEvidence, event = null)
        }
        val rejected = startEvaluator.rejectedDirectionCandidate(fix.timestampMillis)
        val stateWithCandidate = startEvaluator.appendCandidate(stateWithApproachEvidence, rejected)
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

    if (!toleranceTriggered || !stateWithApproachEvidence.hasObservedRequiredApproachSideForActiveLeg) {
        return RacingNavigationDecision(state = stateWithApproachEvidence, event = null)
    }
    if (!startArmed) {
        return RacingNavigationDecision(state = stateWithApproachEvidence, event = null)
    }

    val toleranceCandidate = startEvaluator.evaluateToleranceCandidate(
        state = stateWithApproachEvidence,
        fix = fix,
        timestampMillis = fix.timestampMillis,
        startType = activeWaypoint.startPointType,
        rules = startRules
    )
    val stateWithCandidate = startEvaluator.appendCandidate(stateWithApproachEvidence, toleranceCandidate)
    return if (toleranceCandidate.isValid) {
        RacingNavigationDecision(state = stateWithCandidate, event = null)
    } else {
        rejectedStartDecision(stateWithCandidate, activeWaypoint, toleranceCandidate)
    }
}

private fun hasObservedRequiredStartApproachSide(
    waypoint: RacingWaypoint,
    nextWaypoint: RacingWaypoint?,
    fix: RacingNavigationFix,
    directionOverrideDegrees: Double?,
    epsilonPolicy: RacingBoundaryEpsilonPolicy
): Boolean {
    val epsilonMeters = epsilonPolicy.epsilonMeters(fix)
    val center = RacingBoundaryPoint(waypoint.lat, waypoint.lon)

    return when (waypoint.startPointType) {
        RacingStartPointType.START_LINE -> {
            val sectorBearingDegrees = startLineSectorBearingDegrees(
                waypoint = waypoint,
                nextWaypoint = nextWaypoint,
                directionOverrideDegrees = directionOverrideDegrees
            ) ?: return false
            val radiusMeters = waypoint.gateWidthMeters / 2.0
            if (radiusMeters <= 0.0) {
                false
            } else {
                RacingBoundaryCrossingMath.isInsideLineSector(
                    center = center,
                    sectorBearingDegrees = sectorBearingDegrees,
                    radiusMeters = radiusMeters,
                    epsilonMeters = epsilonMeters,
                    fix = fix
                ) && lineSectorProjectionMeters(
                    center = center,
                    sectorBearingDegrees = sectorBearingDegrees,
                    fix = fix
                ) > epsilonMeters
            }
        }

        RacingStartPointType.START_CYLINDER -> {
            val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
                waypoint.lat,
                waypoint.lon,
                fix.lat,
                fix.lon
            )
            distanceMeters < (waypoint.gateWidthMeters - epsilonMeters)
        }

        RacingStartPointType.FAI_START_SECTOR -> {
            val sectorBearingDegrees = directionOverrideDegrees
                ?: nextWaypoint?.let {
                    RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, it.lat, it.lon)
                }
                ?: return false
            RacingBoundaryCrossingMath.isInsideSector(
                center = center,
                sectorBearingDegrees = sectorBearingDegrees,
                halfAngleDegrees = 45.0,
                radiusMeters = waypoint.gateWidthMeters,
                epsilonMeters = epsilonMeters,
                fix = fix
            )
        }
    }
}

private fun startLineSectorBearingDegrees(
    waypoint: RacingWaypoint,
    nextWaypoint: RacingWaypoint?,
    directionOverrideDegrees: Double?
): Double? {
    val bearingToNext = directionOverrideDegrees
        ?: nextWaypoint?.let {
            RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, it.lat, it.lon)
        }
        ?: return null
    return (bearingToNext + 180.0) % 360.0
}

private fun lineSectorProjectionMeters(
    center: RacingBoundaryPoint,
    sectorBearingDegrees: Double,
    fix: RacingNavigationFix
): Double {
    val point = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(fix.lat, fix.lon))
    val sectorRadians = Math.toRadians(sectorBearingDegrees)
    val axisX = sin(sectorRadians)
    val axisY = cos(sectorRadians)
    return point.first * axisX + point.second * axisY
}

private fun applyStartCandidate(
    taskWaypoints: List<RacingWaypoint>,
    state: RacingNavigationState,
    activeWaypoint: RacingWaypoint,
    candidate: RacingStartCandidate
): RacingNavigationDecision {
    if (!candidate.isValid) {
        return rejectedStartDecision(state, activeWaypoint, candidate)
    }

    val selectedIndex = RacingStartCandidateSelector.selectBestIndex(state.startCandidates)
    val selectedCandidate = selectedIndex
        ?.let { index -> state.startCandidates.getOrNull(index) }
        ?: candidate
    val creditedCandidate = selectedCandidate.takeIf { it.crossingEvidence != null } ?: candidate
    val creditedStart = buildCreditedStartHit(
        legIndex = state.currentLegIndex,
        waypointRole = activeWaypoint.role,
        candidate = creditedCandidate
    ) ?: return RacingNavigationDecision(state = state, event = null)
    val nextIndex = minOf(state.currentLegIndex + 1, taskWaypoints.lastIndex)
    val nextState = state.copy(
        status = RacingNavigationStatus.STARTED,
        currentLegIndex = nextIndex,
        lastTransitionTimeMillis = creditedCandidate.timestampMillis,
        selectedStartCandidateIndex = selectedIndex,
        creditedStart = creditedStart,
        hasObservedRequiredApproachSideForActiveLeg = false
    )
    val event = RacingNavigationEvent(
        type = RacingNavigationEventType.START,
        fromLegIndex = state.currentLegIndex,
        toLegIndex = nextIndex,
        waypointRole = activeWaypoint.role,
        timestampMillis = creditedCandidate.timestampMillis,
        startCandidate = creditedCandidate,
        crossingEvidence = creditedCandidate.crossingEvidence
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

private fun buildCreditedStartHit(
    legIndex: Int,
    waypointRole: RacingWaypointRole,
    candidate: RacingStartCandidate
): RacingCreditedBoundaryHit? {
    val crossingEvidence = candidate.crossingEvidence ?: return null
    return RacingCreditedBoundaryHit(
        legIndex = legIndex,
        waypointRole = waypointRole,
        timestampMillis = candidate.timestampMillis,
        crossingEvidence = crossingEvidence,
        altitudeSourceFix = candidate.sampleFix,
        altitudeReference = candidate.altitudeReference
    )
}
