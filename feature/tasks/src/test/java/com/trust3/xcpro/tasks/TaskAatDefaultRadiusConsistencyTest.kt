package com.trust3.xcpro.tasks

import com.trust3.xcpro.tasks.aat.models.AATRadiusAuthority
import com.trust3.xcpro.tasks.aat.models.AATWaypointRole
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.domain.model.CylinderOZ
import com.trust3.xcpro.tasks.domain.model.SegmentOZ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAatDefaultRadiusConsistencyTest {

    @Test
    fun `resolver uses authoritative AAT turnpoint default radius`() {
        val zone = TaskObservationZoneResolver.resolve(
            taskType = TaskType.AAT,
            waypoint = TaskWaypoint(
                id = "tp1",
                title = "TP1",
                subtitle = "",
                lat = 45.0,
                lon = 7.0,
                role = WaypointRole.TURNPOINT
            ),
            role = WaypointRole.TURNPOINT
        )

        assertTrue(zone is SegmentOZ)
        val defaultRadiusMeters = AATRadiusAuthority.getRadiusMetersForRole(AATWaypointRole.TURNPOINT)
        assertEquals(defaultRadiusMeters, (zone as SegmentOZ).radiusMeters, 1e-9)
    }

    @Test
    fun `serializer default AAT turnpoint oz params use authoritative radius`() {
        val serialized = TaskPersistSerializer.serialize(
            task = Task(
                id = "aat-default-radius",
                waypoints = listOf(
                    TaskWaypoint(
                        id = "tp1",
                        title = "TP1",
                        subtitle = "",
                        lat = 45.0,
                        lon = 7.0,
                        role = WaypointRole.TURNPOINT
                    )
                )
            ),
            taskType = TaskType.AAT,
            targets = emptyList()
        )

        val persisted = TaskPersistSerializer.deserialize(serialized)
        val waypoint = persisted.waypoints.single()
        val defaultRadiusMeters = AATRadiusAuthority.getRadiusMetersForRole(AATWaypointRole.TURNPOINT)
        assertEquals(defaultRadiusMeters, waypoint.ozParams["radiusMeters"] ?: Double.NaN, 1e-9)
        assertEquals(defaultRadiusMeters, waypoint.ozParams["outerRadiusMeters"] ?: Double.NaN, 1e-9)
    }

    @Test
    fun `resolver uses authoritative AAT finish default radius`() {
        val zone = TaskObservationZoneResolver.resolve(
            taskType = TaskType.AAT,
            waypoint = TaskWaypoint(
                id = "finish",
                title = "Finish",
                subtitle = "",
                lat = 45.1,
                lon = 7.1,
                role = WaypointRole.FINISH
            ),
            role = WaypointRole.FINISH
        )

        assertTrue(zone is CylinderOZ)
        val defaultRadiusMeters = AATRadiusAuthority.getRadiusMetersForRole(AATWaypointRole.FINISH)
        assertEquals(defaultRadiusMeters, (zone as CylinderOZ).radiusMeters, 1e-9)
    }
}
