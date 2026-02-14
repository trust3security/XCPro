package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.replay.IgcParser
import com.example.xcpro.replay.IgcPoint
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
        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(resource)
        assertTrue("Expected IGC points", log.points.isNotEmpty())

        val firstRunEvents = replayEvents(task, log.points)
        val secondRunEvents = replayEvents(task, log.points)
        assertEquals(firstRunEvents, secondRunEvents)

        val startMillis = log.points.first().timestampMillis
        assertEquals(3, firstRunEvents.size)
        assertEquals(RacingNavigationEventType.START, firstRunEvents[0].type)
        assertEquals(RacingNavigationEventType.TURNPOINT, firstRunEvents[1].type)
        assertEquals(RacingNavigationEventType.FINISH, firstRunEvents[2].type)
        assertTrue(firstRunEvents[0].timestampMillis >= startMillis)
        assertTrue(firstRunEvents[1].timestampMillis >= firstRunEvents[0].timestampMillis)
        assertTrue(firstRunEvents[2].timestampMillis >= firstRunEvents[1].timestampMillis)
    }

    private fun replayEvents(
        task: SimpleRacingTask,
        points: List<IgcPoint>
    ): List<RacingNavigationEvent> {
        val events = mutableListOf<RacingNavigationEvent>()
        var state = RacingNavigationState()
        points.forEach { point ->
            val fix = RacingNavigationFix(
                lat = point.latitude,
                lon = point.longitude,
                timestampMillis = point.timestampMillis
            )
            val decision = engine.step(task, state, fix)
            state = decision.state
            decision.event?.let(events::add)
        }
        return events
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
