package com.trust3.xcpro.map

import com.trust3.xcpro.tasks.TaskRuntimeSnapshot
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole
import com.trust3.xcpro.tasks.racing.navigation.RacingFinishOutcome
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class RacingNavigationEventMessagesTest {

    @Test
    fun startEvent_includesWaypointName() {
        val message = buildRacingEventMessage(
            taskSnapshot = taskSnapshot(),
            event = event(type = RacingNavigationEventType.START, fromLegIndex = 0)
        )

        assertEquals("Start crossed: Start", message)
    }

    @Test
    fun nearMissEvent_includesWaypointNameAndDistance() {
        val message = buildRacingEventMessage(
            taskSnapshot = taskSnapshot(),
            event = event(
                type = RacingNavigationEventType.TURNPOINT_NEAR_MISS,
                fromLegIndex = 1,
                turnpointNearMissDistanceMeters = 42.9
            )
        )

        assertEquals("Turnpoint near miss: TP1 (42m)", message)
    }

    @Test
    fun finishEvent_includesOutcome() {
        val message = buildRacingEventMessage(
            taskSnapshot = taskSnapshot(),
            event = event(
                type = RacingNavigationEventType.FINISH,
                fromLegIndex = 2,
                finishOutcome = RacingFinishOutcome.VALID
            )
        )

        assertEquals("Finish reached: Finish (VALID)", message)
    }

    private fun event(
        type: RacingNavigationEventType,
        fromLegIndex: Int,
        turnpointNearMissDistanceMeters: Double? = null,
        finishOutcome: RacingFinishOutcome? = null
    ): RacingNavigationEvent = RacingNavigationEvent(
        type = type,
        fromLegIndex = fromLegIndex,
        toLegIndex = fromLegIndex + 1,
        waypointRole = RacingWaypointRole.TURNPOINT,
        timestampMillis = 1_000L,
        turnpointNearMissDistanceMeters = turnpointNearMissDistanceMeters,
        finishOutcome = finishOutcome
    )

    private fun taskSnapshot(): TaskRuntimeSnapshot = TaskRuntimeSnapshot(
        taskType = TaskType.RACING,
        task = Task(
            id = "task",
            waypoints = listOf(
                waypoint("start", "Start", WaypointRole.START),
                waypoint("tp1", "TP1", WaypointRole.TURNPOINT),
                waypoint("finish", "Finish", WaypointRole.FINISH)
            )
        ),
        activeLeg = 0
    )

    private fun waypoint(
        id: String,
        title: String,
        role: WaypointRole
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = title,
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = role
    )
}
