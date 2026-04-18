package com.trust3.xcpro.tasks

import com.trust3.xcpro.common.waypoint.SearchWaypoint
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.trust3.xcpro.tasks.domain.logic.TaskValidator
import com.trust3.xcpro.tasks.racing.RacingTaskStructureRules
import com.trust3.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.trust3.xcpro.testing.MainDispatcherRule
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        val fixture = mockTaskManager()
        val viewModel = createViewModel(fixture.taskManager)
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
        val fixture = mockTaskManager()
        val viewModel = createViewModel(fixture.taskManager)
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
    fun loadTask_success_emitsFitCurrentTask() = runBlocking {
        val fixture = mockTaskManager()
        Mockito.`when`(fixture.taskManager.loadTask("demo-task")).thenReturn(true)
        val viewModel = createViewModel(fixture.taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val collected = collectEffects(viewModel)

        viewModel.loadTask("demo-task")
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(listOf(TaskSheetViewportEffect.RequestFitCurrentTask), collected.effects)
        collected.job.cancel()
    }

    @Test
    fun loadTask_failure_doesNotEmitViewportEffect() = runBlocking {
        val fixture = mockTaskManager()
        Mockito.`when`(fixture.taskManager.loadTask("missing-task")).thenReturn(false)
        val viewModel = createViewModel(fixture.taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val collected = collectEffects(viewModel)

        viewModel.loadTask("missing-task")
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertTrue(collected.effects.isEmpty())
        collected.job.cancel()
    }

    @Test
    fun onSetActiveLeg_doesNotEmitViewportEffect() {
        val fixture = mockTaskManager(
            task = Task(
                id = "snapshot-task",
                waypoints = listOf(
                    sampleWaypoint(id = "start", role = WaypointRole.START),
                    sampleWaypoint(id = "finish", role = WaypointRole.FINISH)
                )
            )
        )
        val viewModel = createViewModel(fixture.taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val collected = collectEffects(viewModel)

        viewModel.onSetActiveLeg(1)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertTrue(collected.effects.isEmpty())
        collected.job.cancel()
    }

    private fun createViewModel(taskManager: TaskManagerCoordinator): TaskSheetViewModel {
        val useCase = TaskSheetUseCase(
            taskManager = taskManager,
            repository = TaskRepository(validator = TaskValidator()),
            proximityEvaluator = TaskProximityEvaluator(),
            persistedTaskImporter = TaskSheetPersistedTaskImporter()
        )
        return TaskSheetViewModel(useCase = useCase)
    }

    private fun mockTaskManager(
        taskType: TaskType = TaskType.RACING,
        task: Task = Task(id = "snapshot-task")
    ): TaskManagerFixture {
        val taskManager = Mockito.mock(TaskManagerCoordinator::class.java)
        val snapshots = MutableStateFlow(
            TaskRuntimeSnapshot(
                taskType = taskType,
                task = task,
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

    private data class TaskManagerFixture(
        val taskManager: TaskManagerCoordinator,
        val snapshots: MutableStateFlow<TaskRuntimeSnapshot>
    )

    private data class CollectedEffects(
        val effects: MutableList<TaskSheetViewportEffect>,
        val job: Job
    )
}
