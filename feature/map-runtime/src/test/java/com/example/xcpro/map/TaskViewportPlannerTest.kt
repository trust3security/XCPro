package com.example.xcpro.map

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskViewportPlannerTest {

    @Test
    fun plan_returnsNull_forEmptyTask() {
        val plan = TaskViewportPlanner.plan(
            task = Task(id = "empty"),
            viewport = MapCameraViewportMetrics(widthPx = 1080, heightPx = 1920, pixelRatio = 1f)
        )

        assertNull(plan)
    }

    @Test
    fun plan_returnsWaypointCenter_andDefaultZoom_forSingleWaypoint() {
        val plan = TaskViewportPlanner.plan(
            task = Task(
                id = "single",
                waypoints = listOf(sampleWaypoint("solo", -34.95, 138.7))
            ),
            viewport = MapCameraViewportMetrics(widthPx = 1080, heightPx = 1920, pixelRatio = 1f)
        )

        assertNotNull(plan)
        assertEquals(-34.95, plan?.target?.latitude ?: Double.NaN, 1e-6)
        assertEquals(138.7, plan?.target?.longitude ?: Double.NaN, 1e-6)
        assertEquals(12.0, plan?.zoom ?: Double.NaN, 1e-6)
    }

    @Test
    fun plan_zoomsOut_moreForWiderTasks() {
        val viewport = MapCameraViewportMetrics(widthPx = 1080, heightPx = 1920, pixelRatio = 1f)
        val compact = TaskViewportPlanner.plan(
            task = Task(
                id = "compact",
                waypoints = listOf(
                    sampleWaypoint("a", -34.90, 138.60),
                    sampleWaypoint("b", -34.91, 138.62)
                )
            ),
            viewport = viewport
        )
        val wide = TaskViewportPlanner.plan(
            task = Task(
                id = "wide",
                waypoints = listOf(
                    sampleWaypoint("a", -34.90, 138.60),
                    sampleWaypoint("b", -35.20, 139.20)
                )
            ),
            viewport = viewport
        )

        assertNotNull(compact)
        assertNotNull(wide)
        assertTrue((compact?.zoom ?: 0.0) > (wide?.zoom ?: Double.MAX_VALUE))
        assertEquals((-34.90 + -35.20) / 2.0, wide?.target?.latitude ?: Double.NaN, 1e-6)
        assertEquals((138.60 + 139.20) / 2.0, wide?.target?.longitude ?: Double.NaN, 1e-6)
    }

    private fun sampleWaypoint(id: String, lat: Double, lon: Double): TaskWaypoint {
        return TaskWaypoint(
            id = id,
            title = id,
            subtitle = "",
            lat = lat,
            lon = lon,
            role = WaypointRole.TURNPOINT
        )
    }
}
