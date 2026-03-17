package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.example.xcpro.tasks.domain.logic.TaskValidator
import com.example.xcpro.testing.MainDispatcherRule
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class TaskSheetViewModelViewportEffectTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onAddWaypoint_emitsFitCurrentTask() {
        val fixture = mockCoordinator()
        val viewModel = createViewModel(fixture.coordinator)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val collected = collectEffects(viewModel)

        viewModel.onAddWaypoint(
            SearchWaypoint(
                id = "tp1",
                title = "TP1",
                subtitle = "",
                lat = -34.95,
                lon = 138.7
            )
        )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(listOf(TaskSheetViewportEffect.RequestFitCurrentTask), collected.effects)
        collected.job.cancel()
    }

    @Test
    fun tryImportPersistedTask_emitsSingleFitCurrentTask() {
        val fixture = mockCoordinator()
        val viewModel = createViewModel(fixture.coordinator)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val collected = collectEffects(viewModel)
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

        val imported = viewModel.tryImportPersistedTask(Gson().toJson(persisted))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertTrue(imported)
        assertEquals(listOf(TaskSheetViewportEffect.RequestFitCurrentTask), collected.effects)
        collected.job.cancel()
    }

    @Test
    fun onSetActiveLeg_doesNotEmitViewportEffect() {
        val fixture = mockCoordinator(
            task = Task(
                id = "snapshot-task",
                waypoints = listOf(
                    sampleWaypoint(id = "start", role = WaypointRole.START),
                    sampleWaypoint(id = "finish", role = WaypointRole.FINISH)
                )
            )
        )
        val viewModel = createViewModel(fixture.coordinator)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val collected = collectEffects(viewModel)

        viewModel.onSetActiveLeg(1)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertTrue(collected.effects.isEmpty())
        collected.job.cancel()
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

    private fun mockCoordinator(
        taskType: TaskType = TaskType.RACING,
        task: Task = Task(id = "snapshot-task")
    ): CoordinatorFixture {
        val coordinator = Mockito.mock(TaskSheetCoordinatorUseCase::class.java)
        val snapshots = MutableStateFlow(
            TaskCoordinatorSnapshot(
                task = task,
                taskType = taskType,
                activeLeg = 0
            )
        )
        Mockito.`when`(coordinator.snapshotFlow).thenReturn(snapshots)
        return CoordinatorFixture(coordinator, snapshots)
    }

    private fun sampleWaypoint(id: String, role: WaypointRole): TaskWaypoint {
        return TaskWaypoint(
            id = id,
            title = id,
            subtitle = "",
            lat = -34.9,
            lon = 138.6,
            role = role
        )
    }

    private fun collectEffects(viewModel: TaskSheetViewModel): CollectedEffects {
        val effects = mutableListOf<TaskSheetViewportEffect>()
        val job = CoroutineScope(mainDispatcherRule.dispatcher).launch {
            viewModel.viewportEffects.collect { effects += it }
        }
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        return CollectedEffects(effects = effects, job = job)
    }

    private data class CoordinatorFixture(
        val coordinator: TaskSheetCoordinatorUseCase,
        val snapshots: MutableStateFlow<TaskCoordinatorSnapshot>
    )

    private data class CollectedEffects(
        val effects: MutableList<TaskSheetViewportEffect>,
        val job: Job
    )
}
