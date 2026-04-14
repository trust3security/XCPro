package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.example.xcpro.tasks.domain.logic.TaskValidator
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.testing.MainDispatcherRule
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class TaskSheetViewModelImportTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun importPersistedTask_aat_appliesTargetsAndObservationZones() {
        val fixture = mockTaskManager(taskType = TaskType.AAT)
        val viewModel = createViewModel(fixture.taskManager)
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

        Mockito.verify(fixture.taskManager).setTaskType(TaskType.AAT)
        Mockito.verify(fixture.taskManager).clearTask()
        Mockito.verify(fixture.taskManager).setActiveLeg(0)
        Mockito.verify(fixture.taskManager).applyAATTargetState(1, 0.62, true, -34.9510, 138.7010)
        Mockito.verify(fixture.taskManager).updateAATArea(1, 5500.0)

        val updateCaptor = argumentCaptor<AATWaypointTypeUpdate>()
        Mockito.verify(fixture.taskManager, Mockito.times(3)).updateAATWaypointPointType(updateCaptor.capture())
        assertEquals(listOf(0, 1, 2), updateCaptor.allValues.map { it.index })
    }

    @Test
    fun importPersistedTask_racing_appliesGateWidthOnlyForTurnpoints() {
        val fixture = mockTaskManager(taskType = TaskType.RACING)
        val viewModel = createViewModel(fixture.taskManager)
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

        Mockito.verify(fixture.taskManager).setTaskType(TaskType.RACING)
        Mockito.verify(fixture.taskManager).clearTask()
        Mockito.verify(fixture.taskManager).setActiveLeg(0)

        val updateCaptor = argumentCaptor<RacingWaypointTypeUpdate>()
        Mockito.verify(fixture.taskManager, Mockito.times(2)).updateWaypointPointType(updateCaptor.capture())
        assertEquals(listOf(1, 2), updateCaptor.allValues.map { it.index })
        assertEquals(listOf(800.0, 1200.0), updateCaptor.allValues.map { it.gateWidthMeters })

        verify(fixture.taskManager, never()).applyAATTargetState(
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun uiState_tracks_external_coordinator_snapshot_updates() {
        val fixture = mockTaskManager(taskType = TaskType.AAT)
        val viewModel = createViewModel(fixture.taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        fixture.snapshots.value = TaskRuntimeSnapshot(
            taskType = TaskType.AAT,
            task = Task(id = "updated-task"),
            activeLeg = 2
        )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals("updated-task", viewModel.uiState.value.task.id)
        assertEquals(2, viewModel.uiState.value.stats.activeIndex)
    }

    @Test
    fun onCleared_clearsCoordinatorProximityHandler() {
        val fixture = mockTaskManager(taskType = TaskType.AAT)
        val viewModel = createViewModel(fixture.taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        TaskSheetViewModel::class.java.getDeclaredMethod("onCleared").apply {
            isAccessible = true
            invoke(viewModel)
        }

        Mockito.verify(fixture.taskManager).clearProximityHandler()
    }

    private fun createViewModel(taskManager: TaskManagerCoordinator): TaskSheetViewModel {
        return TaskSheetViewModel(useCase = createUseCase(taskManager))
    }

    private fun createUseCase(taskManager: TaskManagerCoordinator): TaskSheetUseCase {
        val repository = TaskRepository(
            validator = TaskValidator()
        )
        return TaskSheetUseCase(
            taskManager = taskManager,
            repository = repository,
            proximityEvaluator = TaskProximityEvaluator(),
            persistedTaskImporter = TaskSheetPersistedTaskImporter()
        )
    }

    private fun mockTaskManager(taskType: TaskType): TaskManagerFixture {
        val taskManager = Mockito.mock(TaskManagerCoordinator::class.java)
        val snapshots = MutableStateFlow(
            TaskRuntimeSnapshot(
                taskType = taskType,
                task = Task(id = "snapshot-task"),
                activeLeg = 0
            )
        )
        val racingAdvanceSnapshots = MutableStateFlow(
            RacingAdvanceState().snapshot()
        )
        Mockito.`when`(taskManager.taskSnapshotFlow).thenReturn(snapshots)
        Mockito.`when`(taskManager.racingAdvanceSnapshotFlow).thenReturn(racingAdvanceSnapshots)
        Mockito.`when`(taskManager.getRacingValidationProfile()).thenReturn(RacingTaskStructureRules.Profile.FAI_STRICT)
        return TaskManagerFixture(taskManager, snapshots)
    }

    private data class TaskManagerFixture(
        val taskManager: TaskManagerCoordinator,
        val snapshots: MutableStateFlow<TaskRuntimeSnapshot>
    )
}
