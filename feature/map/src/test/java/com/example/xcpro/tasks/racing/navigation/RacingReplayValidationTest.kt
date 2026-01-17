package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.replay.IgcParser
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingReplayValidationTest {

    private val engine = RacingNavigationEngine()

    @Test
    fun replayPathTriggersStartTurnFinishInOrder() {
        val task = buildSimpleTask()
        val resource = javaClass.classLoader?.getResourceAsStream(REPLAY_RESOURCE)
            ?: error("Missing replay fixture: $REPLAY_RESOURCE")
        val log = IgcParser.parse(resource)
        assertTrue("Expected IGC points", log.points.isNotEmpty())

        val events = mutableListOf<RacingNavigationEvent>()
        var state = RacingNavigationState()
        log.points.forEach { point ->
            val fix = RacingNavigationFix(
                lat = point.latitude,
                lon = point.longitude,
                timestampMillis = point.timestampMillis
            )
            val decision = engine.step(task, state, fix)
            state = decision.state
            decision.event?.let(events::add)
        }

        val startMillis = log.points.first().timestampMillis
        assertEquals(3, events.size)
        assertEquals(RacingNavigationEventType.START, events[0].type)
        assertEquals(RacingNavigationEventType.TURNPOINT, events[1].type)
        assertEquals(RacingNavigationEventType.FINISH, events[2].type)
        assertTrue(events[0].timestampMillis >= startMillis)
        assertTrue(events[1].timestampMillis >= events[0].timestampMillis)
        assertTrue(events[2].timestampMillis >= events[1].timestampMillis)
    }

    private fun buildSimpleTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start",
            title = "Start",
            subtitle = "",
            lat = 37.0,
            lon = -122.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_LINE
        )
        val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp1",
            title = "TP1",
            subtitle = "",
            lat = 37.0,
            lon = -121.9,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish",
            title = "Finish",
            subtitle = "",
            lat = 37.0,
            lon = -121.8,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task",
            waypoints = listOf(start, turnpoint, finish)
        )
    }

    companion object {
        private const val REPLAY_RESOURCE = "replay/racing-task-basic.igc"
    }
}
