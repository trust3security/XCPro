package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.example.xcpro.tasks.domain.logic.TaskValidator
import com.example.xcpro.testing.MainDispatcherRule
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class TaskSheetViewModelImportTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun importPersistedTask_aat_appliesTargetsAndObservationZones() {
        val fixture = mockCoordinator(taskType = TaskType.AAT)
        val viewModel = createViewModel(fixture.coordinator)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
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

        Mockito.verify(fixture.coordinator).setTaskType(TaskType.AAT)
        Mockito.verify(fixture.coordinator).clearTask()
        Mockito.verify(fixture.coordinator).setActiveLeg(0)
        Mockito.verify(fixture.coordinator).applyAATTargetState(1, 0.62, true, -34.9510, 138.7010)
        Mockito.verify(fixture.coordinator).updateAATArea(1, 5500.0)

        val indexCaptor = ArgumentCaptor.forClass(Int::class.javaObjectType)
        Mockito.verify(fixture.coordinator, Mockito.times(3)).updateAATWaypointPointTypeMeters(
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
        val fixture = mockCoordinator(taskType = TaskType.RACING)
        val viewModel = createViewModel(fixture.coordinator)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
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

        Mockito.verify(fixture.coordinator).setTaskType(TaskType.RACING)
        Mockito.verify(fixture.coordinator).clearTask()
        Mockito.verify(fixture.coordinator).setActiveLeg(0)

        val indexCaptor = ArgumentCaptor.forClass(Int::class.javaObjectType)
        val gateWidthCaptor = ArgumentCaptor.forClass(Double::class.java)
        Mockito.verify(fixture.coordinator, Mockito.times(2)).updateWaypointPointType(
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
        assertEquals(listOf(800.0, 1200.0), gateWidthCaptor.allValues)

        Mockito.verify(fixture.coordinator, Mockito.times(0)).applyAATTargetState(
            Mockito.anyInt(),
            Mockito.anyDouble(),
            Mockito.anyBoolean(),
            Mockito.nullable(Double::class.java),
            Mockito.nullable(Double::class.java)
        )
    }

    @Test
    fun uiState_tracks_external_coordinator_snapshot_updates() {
        val fixture = mockCoordinator(taskType = TaskType.AAT)
        val viewModel = createViewModel(fixture.coordinator)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        fixture.snapshots.value = TaskCoordinatorSnapshot(
            task = Task(id = "updated-task"),
            taskType = TaskType.AAT,
            activeLeg = 2
        )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals("updated-task", viewModel.uiState.value.task.id)
        assertEquals(2, viewModel.uiState.value.stats.activeIndex)
    }

    @Test
    fun onCleared_clearsCoordinatorProximityHandler() {
        val fixture = mockCoordinator(taskType = TaskType.AAT)
        val viewModel = createViewModel(fixture.coordinator)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        TaskSheetViewModel::class.java.getDeclaredMethod("onCleared").apply {
            isAccessible = true
            invoke(viewModel)
        }

        Mockito.verify(fixture.coordinator).clearProximityHandler()
    }

    private fun createViewModel(coordinator: TaskSheetCoordinatorUseCase): TaskSheetViewModel {
        val repository = TaskRepository(
            validator = TaskValidator()
        )
        val useCase = TaskSheetUseCase(
            repository = repository,
            proximityEvaluator = TaskProximityEvaluator()
        )
        return TaskSheetViewModel(
            taskCoordinator = coordinator,
            useCase = useCase
        )
    }

    private fun mockCoordinator(taskType: TaskType): CoordinatorFixture {
        val coordinator = Mockito.mock(TaskSheetCoordinatorUseCase::class.java)
        val snapshots = MutableStateFlow(
            TaskCoordinatorSnapshot(
                task = Task(id = "snapshot-task"),
                taskType = taskType,
                activeLeg = 0
            )
        )
        Mockito.`when`(coordinator.snapshotFlow).thenReturn(snapshots)
        return CoordinatorFixture(coordinator, snapshots)
    }

    private data class CoordinatorFixture(
        val coordinator: TaskSheetCoordinatorUseCase,
        val snapshots: MutableStateFlow<TaskCoordinatorSnapshot>
    )
}
