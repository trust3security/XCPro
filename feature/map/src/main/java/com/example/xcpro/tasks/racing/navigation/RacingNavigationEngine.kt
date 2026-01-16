package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import kotlin.math.abs

internal class RacingNavigationEngine(
    private val zoneDetector: RacingZoneDetector = RacingZoneDetector()
) {

    companion object {
        private const val TRANSITION_COOLDOWN_MILLIS = 1500L
        private const val MIN_MOVEMENT_METERS = 1.0
    }

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
        if (lastFix != null && distanceMeters(lastFix, fix) < MIN_MOVEMENT_METERS) {
            return RacingNavigationDecision(
                state = state.copy(lastFix = fix),
                event = null
            )
        }

        val cooldownActive = fix.timestampMillis - state.lastTransitionTimeMillis < TRANSITION_COOLDOWN_MILLIS
        if (cooldownActive) {
            return RacingNavigationDecision(
                state = state.copy(lastFix = fix),
                event = null
            )
        }

        val activeIndex = state.currentLegIndex.coerceIn(0, task.waypoints.lastIndex)
        val activeWaypoint = task.waypoints[activeIndex]
        val previousWaypoint = task.waypoints.getOrNull(activeIndex - 1)
        val nextWaypoint = task.waypoints.getOrNull(activeIndex + 1)

        val currentNavPoint = NavPoint(fix.lat, fix.lon)
        val previousNavPoint = lastFix?.let { NavPoint(it.lat, it.lon) }
        val previousFixTimestampMillis = lastFix?.timestampMillis

        val insideNow = isInside(activeWaypoint, currentNavPoint, previousWaypoint, nextWaypoint)
        val insidePrevious = previousNavPoint?.let {
            isInside(activeWaypoint, it, previousWaypoint, nextWaypoint)
        } ?: false

        val status = if (state.status == RacingNavigationStatus.STARTED) {
            RacingNavigationStatus.IN_PROGRESS
        } else {
            state.status
        }

        val decision = when (status) {
            RacingNavigationStatus.PENDING_START -> handleStartTransition(
                task = task,
                state = state.copy(status = status),
                fix = fix,
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
                previousNavPoint = previousNavPoint,
                activeWaypoint = activeWaypoint,
                previousWaypoint = previousWaypoint,
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

        val startTimestampMillis = previousFixTimestampMillis ?: return RacingNavigationDecision(state = state, event = null)

        val startTriggered = when (activeWaypoint.startPointType) {
            RacingStartPointType.START_LINE -> {
                val previous = previousNavPoint ?: return RacingNavigationDecision(state, null)
                val transitionAllowed = zoneDetector.isLineTransitionAllowed(previous, NavPoint(fix.lat, fix.lon), activeWaypoint)
                insidePrevious && !insideNow && transitionAllowed
            }
            RacingStartPointType.START_CYLINDER,
            RacingStartPointType.FAI_START_SECTOR -> insidePrevious && !insideNow
        }

        if (!startTriggered) {
            return RacingNavigationDecision(state = state, event = null)
        }

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
        previousNavPoint: NavPoint?,
        activeWaypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        insidePrevious: Boolean,
        insideNow: Boolean
    ): RacingNavigationDecision {
        return when (activeWaypoint.role) {
            RacingWaypointRole.TURNPOINT -> {
                if (!insidePrevious && insideNow) {
                    val nextIndex = minOf(state.currentLegIndex + 1, task.waypoints.lastIndex)
                    val nextState = state.copy(
                        status = RacingNavigationStatus.IN_PROGRESS,
                        currentLegIndex = nextIndex,
                        lastTransitionTimeMillis = fix.timestampMillis
                    )
                    val event = RacingNavigationEvent(
                        type = RacingNavigationEventType.TURNPOINT,
                        fromLegIndex = state.currentLegIndex,
                        toLegIndex = nextIndex,
                        waypointRole = activeWaypoint.role,
                        timestampMillis = fix.timestampMillis
                    )
                    RacingNavigationDecision(state = nextState, event = event)
                } else {
                    RacingNavigationDecision(state = state, event = null)
                }
            }
            RacingWaypointRole.FINISH -> {
                val finishTriggered = when (activeWaypoint.finishPointType) {
                    RacingFinishPointType.FINISH_LINE -> {
                        val previous = previousNavPoint ?: return RacingNavigationDecision(state, null)
                        val transitionAllowed = zoneDetector.isLineTransitionAllowed(previous, NavPoint(fix.lat, fix.lon), activeWaypoint)
                        !insidePrevious && insideNow && transitionAllowed
                    }
                    RacingFinishPointType.FINISH_CYLINDER -> !insidePrevious && insideNow
                }

                if (!finishTriggered) {
                    return RacingNavigationDecision(state = state, event = null)
                }

                val nextState = state.copy(
                    status = RacingNavigationStatus.FINISHED,
                    lastTransitionTimeMillis = fix.timestampMillis
                )
                val event = RacingNavigationEvent(
                    type = RacingNavigationEventType.FINISH,
                    fromLegIndex = state.currentLegIndex,
                    toLegIndex = state.currentLegIndex,
                    waypointRole = activeWaypoint.role,
                    timestampMillis = fix.timestampMillis
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
            }
        }
    }

    private fun distanceMeters(a: RacingNavigationFix, b: RacingNavigationFix): Double {
        val km = RacingGeometryUtils.haversineDistance(a.lat, a.lon, b.lat, b.lon)
        return abs(km * 1000.0)
    }
}
