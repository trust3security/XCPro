package com.example.xcpro.map

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RacingReplayTaskHelpersTest {

    @Test
    fun currentRacingTaskOrNull_returnsCanonicalTaskForValidRacingTask() {
        val coordinator = createCoordinator()
        coordinator.setTaskTypeForTesting(TaskType.RACING)
        coordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))
        coordinator.addWaypoint(searchWaypoint("tp1", 0.0, 0.03))
        coordinator.addWaypoint(searchWaypoint("tp2", 0.0, 0.06))
        coordinator.addWaypoint(searchWaypoint("finish", 0.0, 0.1))

        val replayTask = currentRacingTaskOrNull(coordinator)

        assertNotNull(replayTask)
        assertEquals(coordinator.currentTask.id, replayTask?.id)
        assertEquals(coordinator.currentTask.waypoints, replayTask?.waypoints)
    }

    @Test
    fun currentRacingTaskOrNull_returnsNullWhenRacingTaskIsInvalid() {
        val coordinator = createCoordinator()
        coordinator.setTaskTypeForTesting(TaskType.RACING)
        coordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))

        val replayTask = currentRacingTaskOrNull(coordinator)

        assertNull(replayTask)
    }

    @Test
    fun currentRacingTaskOrNull_returnsNullWhenTaskTypeIsNotRacing() {
        val coordinator = createCoordinator()
        coordinator.setTaskTypeForTesting(TaskType.AAT)
        coordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))
        coordinator.addWaypoint(searchWaypoint("finish", 0.0, 0.1))

        val replayTask = currentRacingTaskOrNull(coordinator)

        assertNull(replayTask)
    }

    @Test
    fun currentRacingTaskOrNull_returnsNullForStrictProfileCylinderStartTask() {
        val coordinator = createCoordinator()
        coordinator.setTaskTypeForTesting(TaskType.RACING)
        coordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))
        coordinator.addWaypoint(searchWaypoint("tp1", 0.0, 0.03))
        coordinator.addWaypoint(searchWaypoint("tp2", 0.0, 0.06))
        coordinator.addWaypoint(searchWaypoint("finish", 0.0, 0.1))
        coordinator.updateWaypointPointType(
            index = 0,
            startType = RacingStartPointType.START_CYLINDER,
            finishType = null,
            turnType = null,
            gateWidthMeters = null,
            keyholeInnerRadiusMeters = null,
            keyholeAngle = null,
            faiQuadrantOuterRadiusMeters = null
        )

        val replayTask = currentRacingTaskOrNull(coordinator)

        assertNull(replayTask)
    }

    private fun createCoordinator(): TaskManagerCoordinator =
        TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager()
        )

    private fun searchWaypoint(id: String, lat: Double, lon: Double): SearchWaypoint =
        SearchWaypoint(
            id = id,
            title = id,
            subtitle = "",
            lat = lat,
            lon = lon
        )
}
