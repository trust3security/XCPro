package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingNavigationEngineTest {

    private val engine = RacingNavigationEngine()

    @Test
    fun startLineExitStartsTaskWithinFixWindow() {
        val task = buildLineStartTask()
        val firstFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix)
        assertNull(firstDecision.event)

        val secondFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L)
        val secondDecision = engine.step(task, firstDecision.state, secondFix)
        val event = secondDecision.event
        assertNotNull(event)
        assertEquals(RacingNavigationEventType.START, event?.type)
        assertEquals(1, secondDecision.state.currentLegIndex)
        assertTrue(
            "Start time should be within fix window",
            event!!.timestampMillis in firstFix.timestampMillis..secondFix.timestampMillis
        )
    }

    @Test
    fun startLineExitOutsideCircleDoesNotStart() {
        val task = buildLineStartTask()
        val insideFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), insideFix)

        val farOutsideFix = RacingNavigationFix(lat = 0.0, lon = 0.2, timestampMillis = 2_000L)
        val secondDecision = engine.step(task, firstDecision.state, farOutsideFix)

        assertNull(secondDecision.event)
        assertEquals(0, secondDecision.state.currentLegIndex)
    }

    @Test
    fun turnpointCylinderEntryAdvancesLeg() {
        val task = buildLineStartTask()
        val turnpoint = task.waypoints[1]
        val outsideFix = RacingNavigationFix(
            lat = turnpoint.lat,
            lon = turnpoint.lon + 0.007,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = turnpoint.lat,
            lon = turnpoint.lon + 0.002,
            timestampMillis = 2_000L
        )
        val decision = engine.step(task, state, insideFix)
        assertEquals(RacingNavigationEventType.TURNPOINT, decision.event?.type)
        assertTrue(
            "Transition time should be within fix window",
            decision.event!!.timestampMillis in outsideFix.timestampMillis..insideFix.timestampMillis
        )
        assertEquals(2, decision.state.currentLegIndex)
    }

    @Test
    fun finishCylinderEntryCompletesTask() {
        val task = buildLineStartTask()
        val finish = task.waypoints.last()
        val outsideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.05,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.005,
            timestampMillis = 2_000L
        )
        val decision = engine.step(task, state, insideFix)
        assertEquals(RacingNavigationEventType.FINISH, decision.event?.type)
        assertTrue(
            "Finish time should be within fix window",
            decision.event!!.timestampMillis in outsideFix.timestampMillis..insideFix.timestampMillis
        )
        assertEquals(RacingNavigationStatus.FINISHED, decision.state.status)
    }

    @Test
    fun keyholeSectorEntryAdvancesLeg() {
        val task = buildKeyholeTask()
        val keyhole = task.waypoints[1]
        val outsideFix = RacingNavigationFix(
            lat = keyhole.lat,
            lon = keyhole.lon - 0.02,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = keyhole.lat,
            lon = keyhole.lon + 0.02,
            timestampMillis = 2_000L
        )
        val decision = engine.step(task, state, insideFix)
        assertEquals(RacingNavigationEventType.TURNPOINT, decision.event?.type)
        assertEquals(2, decision.state.currentLegIndex)
    }

    private fun buildLineStartTask(): SimpleRacingTask {
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
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
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

    private fun buildKeyholeTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start",
            title = "Start",
            subtitle = "",
            lat = -0.1,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_CYLINDER
        )
        val keyhole = RacingWaypoint.createWithStandardizedDefaults(
            id = "keyhole",
            title = "Keyhole",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.KEYHOLE,
            customGateWidthMeters = 10_000.0,
            keyholeInnerRadiusMeters = 500.0,
            keyholeAngle = 90.0
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish",
            title = "Finish",
            subtitle = "",
            lat = 0.1,
            lon = 0.0,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task-keyhole",
            waypoints = listOf(start, keyhole, finish)
        )
    }
}
