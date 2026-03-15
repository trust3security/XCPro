package com.example.xcpro.gestures

import com.example.xcpro.tasks.aat.gestures.AatGestureHandler
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskGestureHandlerFactoryTest {

    @Test
    fun create_returnsAatHandlerForAatTasks() {
        val handler = TaskGestureHandlerFactory.create(
            taskType = TaskType.AAT,
            waypointsProvider = { listOf(sampleWaypoint()) },
            callbacks = TaskGestureCallbacks()
        )

        assertTrue(handler is AatGestureHandler)
    }

    @Test
    fun create_returnsNoOpHandlerForRacingTasks() {
        val handler = TaskGestureHandlerFactory.create(
            taskType = TaskType.RACING,
            waypointsProvider = { listOf(sampleWaypoint()) },
            callbacks = TaskGestureCallbacks()
        )

        assertSame(NoOpTaskGestureHandler, handler)
    }

    private fun sampleWaypoint(): TaskWaypoint = TaskWaypoint(
        id = "wp-1",
        title = "Waypoint",
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = WaypointRole.TURNPOINT
    )
}
