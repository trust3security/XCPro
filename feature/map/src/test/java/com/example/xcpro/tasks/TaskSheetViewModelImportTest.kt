package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.example.xcpro.tasks.domain.logic.TaskValidator
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class TaskSheetViewModelImportTest {

    @Test
    fun importPersistedTask_aat_appliesTargetsAndObservationZones() {
        val coordinator = mockCoordinator(taskType = TaskType.AAT)
        val viewModel = createViewModel(coordinator)
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
                ),
                TaskPersistSerializer.PersistedWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = -35.0000,
                    lon = 138.8000,
                    role = WaypointRole.FINISH,
                    ozType = "CYLINDER",
                    ozParams = emptyMap()
                )
            )
        )

        viewModel.importPersistedTask(Gson().toJson(persisted))

        Mockito.verify(coordinator).setTaskType(TaskType.AAT)
        Mockito.verify(coordinator).clearTask()
        Mockito.verify(coordinator).setActiveLeg(0)
        Mockito.verify(coordinator).updateAATTargetPoint(1, -34.9510, 138.7010)
        Mockito.verify(coordinator).updateAATArea(1, 5500.0)

        val indexCaptor = ArgumentCaptor.forClass(Int::class.javaObjectType)
        Mockito.verify(coordinator, Mockito.times(3)).updateAATWaypointPointType(
            indexCaptor.capture(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.nullable(Double::class.java),
            Mockito.nullable(Double::class.java),
            Mockito.nullable(Double::class.java),
            Mockito.nullable(Double::class.java)
        )
        assertEquals(listOf(0, 1, 2), indexCaptor.allValues)
    }

    @Test
    fun importPersistedTask_racing_appliesGateWidthOnlyForTurnpoints() {
        val coordinator = mockCoordinator(taskType = TaskType.RACING)
        val viewModel = createViewModel(coordinator)
        val persisted = TaskPersistSerializer.PersistedTask(
            taskType = TaskType.RACING,
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
                    ozType = "CYLINDER",
                    ozParams = mapOf("radiusMeters" to 800.0)
                ),
                TaskPersistSerializer.PersistedWaypoint(
                    id = "tp2",
                    title = "Turnpoint 2",
                    subtitle = "",
                    lat = -34.9700,
                    lon = 138.7500,
                    role = WaypointRole.TURNPOINT,
                    ozType = "CYLINDER",
                    ozParams = mapOf("radiusMeters" to 1200.0)
                ),
                TaskPersistSerializer.PersistedWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = -35.0000,
                    lon = 138.8000,
                    role = WaypointRole.FINISH,
                    ozType = "CYLINDER",
                    ozParams = emptyMap()
                )
            )
        )

        viewModel.importPersistedTask(Gson().toJson(persisted))

        Mockito.verify(coordinator).setTaskType(TaskType.RACING)
        Mockito.verify(coordinator).clearTask()
        Mockito.verify(coordinator).setActiveLeg(0)

        val indexCaptor = ArgumentCaptor.forClass(Int::class.javaObjectType)
        val gateWidthCaptor = ArgumentCaptor.forClass(Double::class.java)
        Mockito.verify(coordinator, Mockito.times(2)).updateWaypointPointType(
            indexCaptor.capture(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            gateWidthCaptor.capture(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull()
        )
        assertEquals(listOf(1, 2), indexCaptor.allValues)
        assertEquals(listOf(0.8, 1.2), gateWidthCaptor.allValues)

        Mockito.verify(coordinator, Mockito.times(0)).updateAATTargetPoint(
            Mockito.anyInt(),
            Mockito.anyDouble(),
            Mockito.anyDouble()
        )
        Mockito.verify(coordinator, Mockito.times(0)).updateAATArea(Mockito.anyInt(), Mockito.anyDouble())
        Mockito.verify(coordinator, Mockito.times(0)).updateAATWaypointPointType(
            Mockito.anyInt(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.nullable(Double::class.java),
            Mockito.nullable(Double::class.java),
            Mockito.nullable(Double::class.java),
            Mockito.nullable(Double::class.java)
        )
    }

    @Test
    fun tryImportPersistedTask_batchesCoordinatorSyncToSingleSnapshotRefresh() {
        val coordinator = mockCoordinator(taskType = TaskType.AAT)
        val viewModel = createViewModel(coordinator)
        Mockito.clearInvocations(coordinator)
        Mockito.`when`(coordinator.snapshot()).thenReturn(
            TaskCoordinatorSnapshot(
                task = Task(id = "snapshot-task"),
                taskType = TaskType.AAT,
                activeLeg = 0
            )
        )
        val persisted = TaskPersistSerializer.PersistedTask(
            taskType = TaskType.AAT,
            waypoints = listOf(
                TaskPersistSerializer.PersistedWaypoint(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = -34.90,
                    lon = 138.60,
                    role = WaypointRole.START,
                    ozType = "LINE",
                    ozParams = emptyMap()
                ),
                TaskPersistSerializer.PersistedWaypoint(
                    id = "tp1",
                    title = "TP1",
                    subtitle = "",
                    lat = -34.95,
                    lon = 138.70,
                    role = WaypointRole.TURNPOINT,
                    ozType = "SEGMENT",
                    ozParams = mapOf(
                        "outerRadiusMeters" to 5000.0,
                        "innerRadiusMeters" to 0.0,
                        "angleDeg" to 90.0
                    )
                ),
                TaskPersistSerializer.PersistedWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = -35.0,
                    lon = 138.80,
                    role = WaypointRole.FINISH,
                    ozType = "CYLINDER",
                    ozParams = mapOf("radiusMeters" to 3000.0)
                )
            )
        )

        val imported = viewModel.tryImportPersistedTask(Gson().toJson(persisted))

        assertTrue(imported)
        Mockito.verify(coordinator, Mockito.times(1)).snapshot()
    }

    private fun createViewModel(coordinator: TaskSheetCoordinatorUseCase): TaskSheetViewModel {
        val repository = TaskRepository(
            validator = TaskValidator(),
            proximityEvaluator = TaskProximityEvaluator()
        )
        val useCase = TaskSheetUseCase(repository)
        return TaskSheetViewModel(
            taskCoordinator = coordinator,
            useCase = useCase
        )
    }

    private fun mockCoordinator(taskType: TaskType): TaskSheetCoordinatorUseCase {
        val coordinator = Mockito.mock(TaskSheetCoordinatorUseCase::class.java)
        Mockito.`when`(coordinator.snapshot()).thenReturn(
            TaskCoordinatorSnapshot(
                task = Task(id = "snapshot-task"),
                taskType = taskType,
                activeLeg = 0
            )
        )
        return coordinator
    }
}
