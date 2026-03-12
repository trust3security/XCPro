package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.core.AATTaskTimeCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskManagerCanonicalHydrateTest {

    @Test
    fun racingManager_initializeFromCoreTask_preservesTaskIdAndLeg() {
        val manager = RacingTaskManager(null)
        val task = Task(
            id = "canonical-racing-id",
            waypoints = listOf(
                waypoint(id = "start", lat = 0.0, lon = 0.0, role = WaypointRole.START),
                waypoint(id = "finish", lat = 0.0, lon = 0.1, role = WaypointRole.FINISH)
            )
        )

        manager.initializeFromCoreTask(task, activeLegIndex = 1)

        assertEquals("canonical-racing-id", manager.currentRacingTask.id)
        assertEquals("canonical-racing-id", manager.currentTask.id)
        assertEquals(1, manager.currentLeg)
    }

    @Test
    fun racingManager_mutationsKeepCanonicalTaskIdAsAuthority() {
        val manager = RacingTaskManager(null)
        val task = Task(
            id = "canonical-racing-id",
            waypoints = listOf(
                waypoint(id = "start", lat = 0.0, lon = 0.0, role = WaypointRole.START),
                waypoint(id = "finish", lat = 0.0, lon = 0.1, role = WaypointRole.FINISH)
            )
        )
        manager.initializeFromCoreTask(task, activeLegIndex = 0)

        manager.addRacingWaypoint(
            SearchWaypoint(
                id = "extra-tp",
                title = "extra-tp",
                subtitle = "",
                lat = 0.0,
                lon = 0.05
            )
        )

        assertEquals("canonical-racing-id", manager.currentTask.id)
        assertEquals("canonical-racing-id", manager.getCoreTask().id)

        manager.clearRacingTask()
        assertEquals("", manager.currentTask.id)
        assertTrue(manager.currentTask.waypoints.isEmpty())
    }

    @Test
    fun racingManager_defaultProfileIsStrict_andExtendedCanBeOptedIn() {
        val manager = RacingTaskManager(null)
        val shortTask = Task(
            id = "short-racing",
            waypoints = listOf(
                waypoint(id = "start", lat = 0.0, lon = 0.0, role = WaypointRole.START),
                waypoint(id = "finish", lat = 0.0, lon = 0.1, role = WaypointRole.FINISH)
            )
        )

        manager.initializeFromCoreTask(shortTask)
        assertFalse(manager.isRacingTaskValid())

        manager.setRacingValidationProfile(RacingTaskStructureRules.Profile.XC_PRO_EXTENDED)
        assertTrue(manager.isRacingTaskValid())
    }

    @Test
    fun aatManager_initializeFromCoreTask_preservesTaskIdTimesAndLeg() {
        val manager = AATTaskManager(null)
        val params = mutableMapOf<String, Any>()
        AATTaskTimeCustomParams(
            minimumTimeSeconds = Duration.ofHours(2).seconds.toDouble(),
            maximumTimeSeconds = Duration.ofHours(3).seconds.toDouble()
        ).applyTo(params)
        val task = Task(
            id = "canonical-aat-id",
            waypoints = listOf(
                waypoint(id = "start", lat = 0.0, lon = 0.0, role = WaypointRole.START, customParameters = params),
                waypoint(id = "finish", lat = 0.0, lon = 0.1, role = WaypointRole.FINISH, customParameters = params)
            )
        )

        manager.initializeFromCoreTask(task, activeLegIndex = 1)

        assertEquals("canonical-aat-id", manager.currentAATTask.id)
        assertEquals(Duration.ofHours(2), manager.currentAATTask.minimumTime)
        assertEquals(Duration.ofHours(3), manager.currentAATTask.maximumTime)
        assertEquals(1, manager.currentLeg)
    }

    private fun waypoint(
        id: String,
        lat: Double,
        lon: Double,
        role: WaypointRole,
        customParameters: Map<String, Any> = emptyMap()
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = id,
        subtitle = "",
        lat = lat,
        lon = lon,
        role = role,
        customParameters = customParameters
    )
}
