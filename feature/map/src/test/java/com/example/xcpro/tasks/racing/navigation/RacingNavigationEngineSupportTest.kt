package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEpsilonPolicy
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingNavigationEngineSupportTest {

    private val epsilonPolicy = RacingBoundaryEpsilonPolicy()

    @Test
    fun buildTaskSignature_changesWhenWaypointParametersChange() {
        val base = buildTask(
            turnpointGateWidthKm = 0.5
        )
        val modified = buildTask(
            turnpointGateWidthKm = 1.2
        )

        val baseSignature = buildTaskSignature(base)
        val modifiedSignature = buildTaskSignature(modified)

        assertNotEquals(baseSignature, modifiedSignature)
    }

    @Test
    fun normalizeNavigationState_resetsWhenSignatureChanges() {
        val task = buildTask()
        val newSignature = buildTaskSignature(task)
        val staleState = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 2,
            taskSignature = "stale-signature"
        )

        val normalized = normalizeNavigationState(staleState, task, newSignature)

        assertEquals(newSignature, normalized.taskSignature)
        assertEquals(0, normalized.currentLegIndex)
        assertEquals(RacingNavigationStatus.PENDING_START, normalized.status)
    }

    @Test
    fun shouldEvaluateTransitions_falseWhenCurrentAndPreviousFixesAreFarOutsideRadius() {
        val task = buildTask(turnpointGateWidthKm = 0.001)
        val waypoint = task.waypoints[1]
        val previousFix = RacingNavigationFix(
            lat = waypoint.lat,
            lon = waypoint.lon + 0.1,
            timestampMillis = 1_000L
        )
        val currentFix = RacingNavigationFix(
            lat = waypoint.lat,
            lon = waypoint.lon + 0.2,
            timestampMillis = 2_000L
        )

        val shouldEvaluate = shouldEvaluateTransitions(
            activeWaypoint = waypoint,
            previousWaypoint = task.waypoints[0],
            nextWaypoint = task.waypoints[2],
            fix = currentFix,
            lastFix = previousFix,
            epsilonPolicy = epsilonPolicy
        )

        assertFalse(shouldEvaluate)
    }

    @Test
    fun shouldEvaluateTransitions_trueWhenPreviousFixWasInsideRadiusWindow() {
        val task = buildTask(turnpointGateWidthKm = 0.2)
        val waypoint = task.waypoints[1]
        val previousFix = RacingNavigationFix(
            lat = waypoint.lat,
            lon = waypoint.lon + 0.001,
            timestampMillis = 1_000L
        )
        val currentFix = RacingNavigationFix(
            lat = waypoint.lat,
            lon = waypoint.lon + 0.2,
            timestampMillis = 2_000L
        )

        val shouldEvaluate = shouldEvaluateTransitions(
            activeWaypoint = waypoint,
            previousWaypoint = task.waypoints[0],
            nextWaypoint = task.waypoints[2],
            fix = currentFix,
            lastFix = previousFix,
            epsilonPolicy = epsilonPolicy
        )

        assertTrue(shouldEvaluate)
    }

    private fun buildTask(turnpointGateWidthKm: Double = 0.5): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start",
            title = "Start",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_LINE
        )
        val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp1",
            title = "TP1",
            subtitle = "",
            lat = 0.0,
            lon = 0.1,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER,
            customGateWidth = turnpointGateWidthKm
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish",
            title = "Finish",
            subtitle = "",
            lat = 0.0,
            lon = 0.2,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task",
            waypoints = listOf(start, turnpoint, finish)
        )
    }
}
