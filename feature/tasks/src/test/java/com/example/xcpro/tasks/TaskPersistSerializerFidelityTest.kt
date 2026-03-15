package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TaskWaypointParamKeys
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.model.GeoPoint
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPersistSerializerFidelityTest {

    @Test
    fun `serialize preserves custom waypoint fields and custom oz payload`() {
        val waypoint = TaskWaypoint(
            id = "tp1",
            title = "TP1",
            subtitle = "desc",
            lat = 45.0,
            lon = 7.0,
            role = WaypointRole.TURNPOINT,
            customRadiusMeters = 2200.0,
            customPointType = "KEYHOLE",
            customParameters = mapOf(
                TaskWaypointParamKeys.OZ_TYPE to "SEGMENT",
                TaskWaypointParamKeys.OZ_PARAMS to mapOf(
                    "radiusMeters" to 6200.0,
                    "outerRadiusMeters" to 7000.0,
                    "innerRadiusMeters" to 800.0,
                    "angleDeg" to 65.0
                ),
                "extraFlag" to true
            )
        )
        val serialized = TaskPersistSerializer.serialize(
            task = Task(id = "fidelity-task", waypoints = listOf(waypoint)),
            taskType = TaskType.AAT,
            targets = emptyList()
        )
        val persisted = TaskPersistSerializer.deserialize(serialized)
        val persistedWaypoint = persisted.waypoints.single()

        assertEquals("fidelity-task", persisted.taskId)
        assertEquals(2.2, persistedWaypoint.customRadius ?: Double.NaN, 1e-9)
        assertEquals(2200.0, persistedWaypoint.customRadiusMeters ?: Double.NaN, 1e-9)
        assertEquals("KEYHOLE", persistedWaypoint.customPointType)
        assertEquals("SEGMENT", persistedWaypoint.ozType)
        assertEquals(6200.0, persistedWaypoint.ozParams["radiusMeters"] ?: Double.NaN, 1e-9)
        assertEquals(7000.0, persistedWaypoint.ozParams["outerRadiusMeters"] ?: Double.NaN, 1e-9)
        assertEquals(800.0, persistedWaypoint.ozParams["innerRadiusMeters"] ?: Double.NaN, 1e-9)
        assertEquals(65.0, persistedWaypoint.ozParams["angleDeg"] ?: Double.NaN, 1e-9)
        assertEquals(true, persistedWaypoint.customParameters["extraFlag"])
    }

    @Test
    fun `toTask preserves custom fields and allowsTarget semantics by task type`() {
        val persisted = TaskPersistSerializer.PersistedTask(
            taskId = "reconstructed",
            taskType = TaskType.RACING,
            waypoints = listOf(
                TaskPersistSerializer.PersistedWaypoint(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = 45.0,
                    lon = 7.0,
                    role = WaypointRole.START,
                    ozType = "LINE",
                    ozParams = mapOf("lengthMeters" to 1100.0),
                    customRadius = 1.1,
                    customPointType = "START_LINE",
                    customParameters = mapOf("note" to "keep")
                ),
                TaskPersistSerializer.PersistedWaypoint(
                    id = "tp1",
                    title = "TP1",
                    subtitle = "",
                    lat = 45.1,
                    lon = 7.1,
                    role = WaypointRole.TURNPOINT,
                    ozType = "CYLINDER",
                    ozParams = mapOf("radiusMeters" to 900.0),
                    customRadius = 0.9,
                    customPointType = "CYLINDER",
                    customParameters = mapOf("foo" to 123.0),
                    targetParam = 0.67,
                    targetLocked = true,
                    targetLat = 45.11,
                    targetLon = 7.11
                )
            )
        )

        val (task, targets) = TaskPersistSerializer.toTask(persisted)
        val turnpoint = task.waypoints[1]

        assertEquals("reconstructed", task.id)
        assertNull(turnpoint.customRadius)
        assertEquals(900.0, turnpoint.customRadiusMeters ?: Double.NaN, 1e-9)
        assertEquals("CYLINDER", turnpoint.customPointType)
        assertEquals(123.0, turnpoint.customParameters["foo"] as Double, 1e-9)

        assertEquals(2, targets.size)
        assertFalse(targets[0].allowsTarget)
        assertFalse(targets[1].allowsTarget)
        assertEquals(0.67, targets[1].targetParam, 1e-9)
        assertTrue(targets[1].isLocked)
        assertEquals(45.11, targets[1].target?.lat ?: Double.NaN, 1e-9)
        assertEquals(7.11, targets[1].target?.lon ?: Double.NaN, 1e-9)
    }

    @Test
    fun `serialize uses provided targets over waypoint fallback target params`() {
        val waypoint = TaskWaypoint(
            id = "tp1",
            title = "TP1",
            subtitle = "",
            lat = 45.0,
            lon = 7.0,
            role = WaypointRole.TURNPOINT,
            customParameters = mapOf(
                TaskWaypointParamKeys.TARGET_PARAM to 0.1,
                TaskWaypointParamKeys.TARGET_LOCKED to false,
                TaskWaypointParamKeys.TARGET_LAT to 45.01,
                TaskWaypointParamKeys.TARGET_LON to 7.01
            )
        )
        val targets = listOf(
            TaskTargetSnapshot(
                index = 0,
                id = "tp1",
                name = "TP1",
                allowsTarget = true,
                targetParam = 0.9,
                isLocked = true,
                target = GeoPoint(lat = 45.09, lon = 7.09)
            )
        )

        val serialized = TaskPersistSerializer.serialize(
            task = Task(id = "target-priority", waypoints = listOf(waypoint)),
            taskType = TaskType.AAT,
            targets = targets
        )
        val persistedWaypoint = TaskPersistSerializer.deserialize(serialized).waypoints.single()

        assertEquals(0.9, persistedWaypoint.targetParam ?: Double.NaN, 1e-9)
        assertEquals(true, persistedWaypoint.targetLocked)
        assertEquals(45.09, persistedWaypoint.targetLat ?: Double.NaN, 1e-9)
        assertEquals(7.09, persistedWaypoint.targetLon ?: Double.NaN, 1e-9)
    }

    @Test
    fun `serialize falls back to waypoint canonical target state when overlays are absent`() {
        val waypoint = TaskWaypoint(
            id = "tp1",
            title = "TP1",
            subtitle = "",
            lat = 45.0,
            lon = 7.0,
            role = WaypointRole.TURNPOINT,
            customParameters = mapOf(
                TaskWaypointParamKeys.TARGET_PARAM to 0.42,
                TaskWaypointParamKeys.TARGET_LOCKED to true,
                TaskWaypointParamKeys.TARGET_LAT to 45.12,
                TaskWaypointParamKeys.TARGET_LON to 7.12
            )
        )

        val serialized = TaskPersistSerializer.serialize(
            task = Task(id = "canonical-target-fallback", waypoints = listOf(waypoint)),
            taskType = TaskType.AAT,
            targets = emptyList()
        )
        val persistedWaypoint = TaskPersistSerializer.deserialize(serialized).waypoints.single()

        assertEquals(0.42, persistedWaypoint.targetParam ?: Double.NaN, 1e-9)
        assertEquals(true, persistedWaypoint.targetLocked)
        assertEquals(45.12, persistedWaypoint.targetLat ?: Double.NaN, 1e-9)
        assertEquals(7.12, persistedWaypoint.targetLon ?: Double.NaN, 1e-9)
    }

    @Test
    fun `toTask converts legacy km radius into canonical meters and clears legacy field`() {
        val persisted = TaskPersistSerializer.PersistedTask(
            taskId = "legacy-km",
            taskType = TaskType.AAT,
            waypoints = listOf(
                TaskPersistSerializer.PersistedWaypoint(
                    id = "tp1",
                    title = "TP1",
                    subtitle = "",
                    lat = 45.0,
                    lon = 7.0,
                    role = WaypointRole.TURNPOINT,
                    ozType = "SEGMENT",
                    ozParams = mapOf("radiusMeters" to 2500.0),
                    customRadius = 2.5,
                    customRadiusMeters = null
                )
            )
        )

        val (task, _) = TaskPersistSerializer.toTask(persisted)
        val waypoint = task.waypoints.single()

        assertNull(waypoint.customRadius)
        assertEquals(2500.0, waypoint.customRadiusMeters ?: Double.NaN, 1e-9)
    }
}
