package com.example.xcpro

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.xcpro.tasks.TaskPersistSerializer
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskPersistSerializerInstrumentedTest {
    private companion object {
        const val KEY_TARGET_PARAM = "targetParam"
        const val KEY_TARGET_LOCKED = "targetLocked"
        const val KEY_TARGET_LAT = "targetLat"
        const val KEY_TARGET_LON = "targetLon"
        const val KEY_OZ_TYPE = "ozType"
        const val KEY_OZ_PARAMS = "ozParams"
    }

    @Test
    fun serializeAatTurnpointUsesSegmentDefaults() {
        val serialized = TaskPersistSerializer.serialize(
            task = sampleTask(),
            taskType = TaskType.AAT,
            targets = emptyList()
        )
        val persisted = TaskPersistSerializer.deserialize(serialized)
        val turnpoint = persisted.waypoints.first { it.role == WaypointRole.TURNPOINT }

        assertEquals("SEGMENT", turnpoint.ozType)
        assertEquals(5000.0, turnpoint.ozParams["radiusMeters"] ?: Double.NaN, 0.0)
        assertEquals(5000.0, turnpoint.ozParams["outerRadiusMeters"] ?: Double.NaN, 0.0)
        assertEquals(0.0, turnpoint.ozParams["innerRadiusMeters"] ?: Double.NaN, 0.0)
        assertEquals(90.0, turnpoint.ozParams["angleDeg"] ?: Double.NaN, 0.0)
    }

    @Test
    fun serializeRacingTurnpointUsesCylinderDefaultRadius() {
        val serialized = TaskPersistSerializer.serialize(
            task = sampleTask(),
            taskType = TaskType.RACING,
            targets = emptyList()
        )
        val persisted = TaskPersistSerializer.deserialize(serialized)
        val turnpoint = persisted.waypoints.first { it.role == WaypointRole.TURNPOINT }

        assertEquals("CYLINDER", turnpoint.ozType)
        assertEquals(500.0, turnpoint.ozParams["radiusMeters"] ?: Double.NaN, 0.0)
    }

    @Test
    fun toTaskAddsTargetFallbacksWhenMissing() {
        val persisted = TaskPersistSerializer.PersistedTask(
            taskType = TaskType.AAT,
            waypoints = listOf(
                TaskPersistSerializer.PersistedWaypoint(
                    id = "wp-1",
                    title = "TP1",
                    subtitle = "",
                    lat = 45.2,
                    lon = 7.1,
                    role = WaypointRole.TURNPOINT,
                    ozType = "SEGMENT",
                    ozParams = mapOf("radiusMeters" to 5000.0),
                    targetParam = null,
                    targetLocked = null,
                    targetLat = null,
                    targetLon = null
                )
            )
        )

        val (task, targets) = TaskPersistSerializer.toTask(persisted)
        val waypoint = task.waypoints.single()
        val targetSnapshot = targets.single()

        assertEquals(0.5, waypoint.customParameters[KEY_TARGET_PARAM] as Double, 0.0)
        assertEquals(false, waypoint.customParameters[KEY_TARGET_LOCKED] as Boolean)
        assertEquals(45.2, waypoint.customParameters[KEY_TARGET_LAT] as Double, 0.0)
        assertEquals(7.1, waypoint.customParameters[KEY_TARGET_LON] as Double, 0.0)
        assertEquals(0.5, targetSnapshot.targetParam, 0.0)
        assertEquals(false, targetSnapshot.isLocked)
    }

    @Test
    fun toTaskPreservesOzTypeAndOzParamsPayload() {
        val persisted = TaskPersistSerializer.PersistedTask(
            taskType = TaskType.AAT,
            waypoints = listOf(
                TaskPersistSerializer.PersistedWaypoint(
                    id = "wp-2",
                    title = "TP2",
                    subtitle = "",
                    lat = 44.0,
                    lon = 8.0,
                    role = WaypointRole.TURNPOINT,
                    ozType = "SEGMENT",
                    ozParams = mapOf(
                        "radiusMeters" to 5500.0,
                        "outerRadiusMeters" to 6500.0,
                        "innerRadiusMeters" to 500.0,
                        "angleDeg" to 70.0
                    ),
                    targetParam = 0.7,
                    targetLocked = true,
                    targetLat = 44.1,
                    targetLon = 8.1
                )
            )
        )

        val (task, _) = TaskPersistSerializer.toTask(persisted)
        val waypoint = task.waypoints.single()
        val ozType = waypoint.customParameters[KEY_OZ_TYPE] as String
        val ozParams = waypoint.customParameters[KEY_OZ_PARAMS] as Map<*, *>

        assertEquals("SEGMENT", ozType)
        assertEquals(5500.0, ozParams["radiusMeters"] as Double, 0.0)
        assertEquals(6500.0, ozParams["outerRadiusMeters"] as Double, 0.0)
        assertEquals(500.0, ozParams["innerRadiusMeters"] as Double, 0.0)
        assertEquals(70.0, ozParams["angleDeg"] as Double, 0.0)
    }

    @Test
    fun serializeThenToTaskRetainsWaypointOrderAndRoles() {
        val serialized = TaskPersistSerializer.serialize(
            task = sampleTask(),
            taskType = TaskType.AAT,
            targets = emptyList()
        )
        val persisted = TaskPersistSerializer.deserialize(serialized)
        val (task, targets) = TaskPersistSerializer.toTask(persisted)

        assertEquals(listOf("s", "t", "f"), task.waypoints.map { it.id })
        assertEquals(
            listOf(WaypointRole.START, WaypointRole.TURNPOINT, WaypointRole.FINISH),
            task.waypoints.map { it.role }
        )
        assertEquals(3, targets.size)
    }

    @Test
    fun deserializeOutputCanBeReencoded() {
        val first = TaskPersistSerializer.serialize(
            task = sampleTask(),
            taskType = TaskType.RACING,
            targets = emptyList()
        )
        val parsed = TaskPersistSerializer.deserialize(first)
        val second = TaskPersistSerializer.serialize(
            task = Task(
                id = "reencoded",
                waypoints = parsed.waypoints.map {
                    TaskWaypoint(
                        id = it.id,
                        title = it.title,
                        subtitle = it.subtitle,
                        lat = it.lat,
                        lon = it.lon,
                        role = it.role
                    )
                }
            ),
            taskType = parsed.taskType,
            targets = emptyList()
        )
        val parsedAgain = TaskPersistSerializer.deserialize(second)

        assertNotNull(parsedAgain)
        assertTrue(parsedAgain.waypoints.isNotEmpty())
        assertEquals(parsed.taskType, parsedAgain.taskType)
    }

    private fun sampleTask(): Task = Task(
        id = "sample",
        waypoints = listOf(
            TaskWaypoint(
                id = "s",
                title = "Start",
                subtitle = "",
                lat = 45.0,
                lon = 7.0,
                role = WaypointRole.START
            ),
            TaskWaypoint(
                id = "t",
                title = "TP",
                subtitle = "",
                lat = 45.1,
                lon = 7.1,
                role = WaypointRole.TURNPOINT
            ),
            TaskWaypoint(
                id = "f",
                title = "Finish",
                subtitle = "",
                lat = 45.2,
                lon = 7.2,
                role = WaypointRole.FINISH
            )
        )
    )
}
