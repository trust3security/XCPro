package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.argumentCaptor

class TaskSheetPersistedTaskImporterTest {

    @Test
    fun import_restoresPersistedTaskThroughCoordinatorSeam() {
        val taskManager = Mockito.mock(TaskManagerCoordinator::class.java)
        val importer = TaskSheetPersistedTaskImporter()
        val persisted = TaskPersistSerializer.PersistedTask(
            taskType = TaskType.AAT,
            waypoints = listOf(
                TaskPersistSerializer.PersistedWaypoint(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = -34.9000,
                    lon = 138.6000,
                    role = WaypointRole.START,
                    ozType = "LINE",
                    ozParams = emptyMap()
                ),
                TaskPersistSerializer.PersistedWaypoint(
                    id = "tp1",
                    title = "Turnpoint 1",
                    subtitle = "",
                    lat = -34.9500,
                    lon = 138.7000,
                    role = WaypointRole.TURNPOINT,
                    ozType = "SEGMENT",
                    ozParams = mapOf(
                        "outerRadiusMeters" to 5500.0,
                        "innerRadiusMeters" to 1200.0,
                        "angleDeg" to 70.0
                    ),
                    targetParam = 0.62,
                    targetLocked = true,
                    targetLat = -34.9510,
                    targetLon = 138.7010
                )
            )
        )

        importer.import(Gson().toJson(persisted), taskManager)

        Mockito.verify(taskManager).setTaskType(TaskType.AAT)
        Mockito.verify(taskManager).clearTask()
        Mockito.verify(taskManager).applyAATTargetState(1, 0.62, true, -34.9510, 138.7010)
        Mockito.verify(taskManager).updateAATArea(1, 5500.0)
        val updateCaptor = argumentCaptor<AATWaypointTypeUpdate>()
        Mockito.verify(taskManager, Mockito.times(2)).updateAATWaypointPointType(updateCaptor.capture())
        Mockito.verify(taskManager).setActiveLeg(0)
    }

    @Test
    fun import_invalidJsonFailsWithoutMutatingCoordinator() {
        val taskManager = Mockito.mock(TaskManagerCoordinator::class.java)
        val importer = TaskSheetPersistedTaskImporter()

        val result = runCatching { importer.import("not-json", taskManager) }

        assertTrue(result.isFailure)
        Mockito.verifyNoInteractions(taskManager)
    }
}
