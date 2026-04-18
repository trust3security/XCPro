package com.trust3.xcpro.tasks.data.persistence

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPersistenceAdaptersDeterministicIdTest {
    @Test
    fun `deterministic fallback id is stable for same task content`() {
        val task = baseTask(id = "", latShift = 0.0)

        val first = task.deterministicFallbackId(prefix = "racing")
        val second = task.deterministicFallbackId(prefix = "racing")

        assertTrue(first.startsWith("racing_"))
        assertEquals(first, second)
    }

    @Test
    fun `deterministic fallback id changes when task content changes`() {
        val base = baseTask(id = "", latShift = 0.0)
        val changed = baseTask(id = "", latShift = 0.03)

        val baseId = base.deterministicFallbackId(prefix = "aat")
        val changedId = changed.deterministicFallbackId(prefix = "aat")

        assertTrue(baseId.startsWith("aat_"))
        assertTrue(changedId.startsWith("aat_"))
        assertNotEquals(baseId, changedId)
    }

    @Test
    fun `non blank task id is preserved`() {
        val task = baseTask(id = "explicit_id", latShift = 0.0)
        assertEquals("explicit_id", task.deterministicFallbackId(prefix = "racing"))
    }

    private fun baseTask(id: String, latShift: Double): Task {
        return Task(
            id = id,
            waypoints = listOf(
                TaskWaypoint(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = 45.0 + latShift,
                    lon = 7.0,
                    role = WaypointRole.START
                ),
                TaskWaypoint(
                    id = "tp1",
                    title = "Turnpoint",
                    subtitle = "",
                    lat = 45.1 + latShift,
                    lon = 7.1,
                    role = WaypointRole.TURNPOINT
                ),
                TaskWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = 45.2 + latShift,
                    lon = 7.2,
                    role = WaypointRole.FINISH
                )
            )
        )
    }
}
