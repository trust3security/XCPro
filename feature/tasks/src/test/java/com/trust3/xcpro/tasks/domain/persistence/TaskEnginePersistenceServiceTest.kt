package com.trust3.xcpro.tasks.domain.persistence

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.data.persistence.AUTOSAVE_SLOT
import com.trust3.xcpro.tasks.domain.engine.AATTaskEngine
import com.trust3.xcpro.tasks.domain.engine.AATTaskEngineState
import com.trust3.xcpro.tasks.domain.engine.RacingTaskEngine
import com.trust3.xcpro.tasks.domain.engine.RacingTaskEngineState
import com.trust3.xcpro.tasks.domain.engine.TaskEngineState
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskEnginePersistenceServiceTest {

    @Test
    fun `restore loads autosaved tasks into both engines and returns task type`() = runTest {
        val taskTypeRepo = FakeTaskTypeSettingsRepository(TaskType.AAT)
        val racingRepo = FakeRacingTaskPersistence().apply {
            autosavedTask = Task(id = "racing-auto", waypoints = listOf(waypoint("r1")))
        }
        val aatRepo = FakeAATTaskPersistence().apply {
            autosavedTask = Task(id = "aat-auto", waypoints = listOf(waypoint("a1")))
        }
        val racingEngine = FakeRacingTaskEngine()
        val aatEngine = FakeAATTaskEngine()
        val service = TaskEnginePersistenceService(taskTypeRepo, racingRepo, aatRepo, racingEngine, aatEngine)

        val restored = service.restore(defaultType = TaskType.RACING)

        assertEquals(TaskType.AAT, restored)
        assertEquals("racing-auto", racingEngine.state.value.base.task.id)
        assertEquals("aat-auto", aatEngine.state.value.base.task.id)
    }

    @Test
    fun `autosave writes current engine tasks to autosave slot`() = runTest {
        val taskTypeRepo = FakeTaskTypeSettingsRepository(TaskType.RACING)
        val racingRepo = FakeRacingTaskPersistence()
        val aatRepo = FakeAATTaskPersistence()
        val racingEngine = FakeRacingTaskEngine()
        val aatEngine = FakeAATTaskEngine()
        racingEngine.setTask(Task(id = "racing-live", waypoints = listOf(waypoint("r1"))))
        aatEngine.setTask(Task(id = "aat-live", waypoints = listOf(waypoint("a1"))))
        val service = TaskEnginePersistenceService(taskTypeRepo, racingRepo, aatRepo, racingEngine, aatEngine)

        service.autosaveEngines()

        assertEquals(AUTOSAVE_SLOT, racingRepo.lastSavedName)
        assertEquals("racing-live", racingRepo.lastSavedTask?.id)
        assertEquals(AUTOSAVE_SLOT, aatRepo.lastSavedName)
        assertEquals("aat-live", aatRepo.lastSavedTask?.id)
    }

    @Test
    fun `named save and load route by selected task type`() = runTest {
        val taskTypeRepo = FakeTaskTypeSettingsRepository(TaskType.RACING)
        val racingRepo = FakeRacingTaskPersistence()
        val aatRepo = FakeAATTaskPersistence()
        val racingEngine = FakeRacingTaskEngine().apply {
            setTask(Task(id = "racing-current", waypoints = listOf(waypoint("r1"))))
        }
        val aatEngine = FakeAATTaskEngine().apply {
            setTask(Task(id = "aat-current", waypoints = listOf(waypoint("a1"))))
        }
        racingRepo.namedTasks["race-final"] = Task(id = "race-file", waypoints = listOf(waypoint("rf")))
        aatRepo.namedTasks["aat-final"] = Task(id = "aat-file", waypoints = listOf(waypoint("af")))
        val service = TaskEnginePersistenceService(taskTypeRepo, racingRepo, aatRepo, racingEngine, aatEngine)

        val racingSaved = service.saveNamedTask(TaskType.RACING, "race-final")
        val aatSaved = service.saveNamedTask(TaskType.AAT, "aat-final")
        val racingLoaded = service.loadNamedTask(TaskType.RACING, "race-final")
        val aatLoaded = service.loadNamedTask(TaskType.AAT, "aat-final")

        assertTrue(racingSaved)
        assertTrue(aatSaved)
        assertTrue(racingLoaded)
        assertTrue(aatLoaded)
        assertEquals("racing-current", racingEngine.state.value.base.task.id)
        assertEquals("aat-current", aatEngine.state.value.base.task.id)
    }

    private fun waypoint(id: String): TaskWaypoint = TaskWaypoint(
        id = id,
        title = id,
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = WaypointRole.TURNPOINT
    )
}

private class FakeTaskTypeSettingsRepository(
    private var storedType: TaskType
) : TaskTypeSettingsRepository {
    private val _flow = MutableStateFlow(storedType)
    override val taskTypeFlow: StateFlow<TaskType> = _flow.asStateFlow()

    override suspend fun loadTaskType(defaultType: TaskType): TaskType {
        return storedType
    }

    override suspend fun saveTaskType(taskType: TaskType) {
        storedType = taskType
        _flow.value = taskType
    }
}

private class FakeRacingTaskPersistence : RacingTaskPersistence {
    var autosavedTask: Task? = null
    val namedTasks = mutableMapOf<String, Task>()
    var lastSavedName: String? = null
    var lastSavedTask: Task? = null

    override suspend fun listTaskNames(): List<String> = namedTasks.keys.toList()
    override suspend fun loadAutosavedTask(): Task? = autosavedTask
    override suspend fun loadTask(taskName: String): Task? = namedTasks[taskName]
    override suspend fun saveTask(taskName: String, task: Task): Boolean {
        lastSavedName = taskName
        lastSavedTask = task
        if (taskName == AUTOSAVE_SLOT) {
            autosavedTask = task
        } else {
            namedTasks[taskName] = task
        }
        return true
    }

    override suspend fun deleteTask(taskName: String): Boolean = namedTasks.remove(taskName) != null
}

private class FakeAATTaskPersistence : AATTaskPersistence {
    var autosavedTask: Task? = null
    val namedTasks = mutableMapOf<String, Task>()
    var lastSavedName: String? = null
    var lastSavedTask: Task? = null

    override suspend fun listTaskNames(): List<String> = namedTasks.keys.toList()
    override suspend fun loadAutosavedTask(): Task? = autosavedTask
    override suspend fun loadTask(taskName: String): Task? = namedTasks[taskName]
    override suspend fun saveTask(taskName: String, task: Task): Boolean {
        lastSavedName = taskName
        lastSavedTask = task
        if (taskName == AUTOSAVE_SLOT) {
            autosavedTask = task
        } else {
            namedTasks[taskName] = task
        }
        return true
    }

    override suspend fun deleteTask(taskName: String): Boolean = namedTasks.remove(taskName) != null
}

private class FakeRacingTaskEngine : RacingTaskEngine {
    private val _state = MutableStateFlow(
        RacingTaskEngineState(base = TaskEngineState(taskType = TaskType.RACING))
    )
    override val state: StateFlow<RacingTaskEngineState> = _state.asStateFlow()

    override fun setTask(task: Task) {
        _state.value = _state.value.copy(base = _state.value.base.copy(task = task))
    }

    override fun addWaypoint(waypoint: TaskWaypoint) = Unit
    override fun removeWaypoint(index: Int) = Unit
    override fun reorderWaypoints(fromIndex: Int, toIndex: Int) = Unit
    override fun setActiveLeg(index: Int) = Unit
    override fun clearTask() = Unit
    override fun calculateTaskDistanceMeters(task: Task): Double = 0.0
    override fun calculateSegmentDistanceMeters(from: TaskWaypoint, to: TaskWaypoint): Double = 0.0
}

private class FakeAATTaskEngine : AATTaskEngine {
    private val _state = MutableStateFlow(
        AATTaskEngineState(
            base = TaskEngineState(taskType = TaskType.AAT),
            minimumTime = Duration.ofHours(3),
            maximumTime = Duration.ofHours(6)
        )
    )
    override val state: StateFlow<AATTaskEngineState> = _state.asStateFlow()

    override fun setTask(task: Task) {
        _state.value = _state.value.copy(base = _state.value.base.copy(task = task))
    }

    override fun addWaypoint(waypoint: TaskWaypoint) = Unit
    override fun removeWaypoint(index: Int) = Unit
    override fun reorderWaypoints(fromIndex: Int, toIndex: Int) = Unit
    override fun setActiveLeg(index: Int) = Unit
    override fun clearTask() = Unit
    override fun updateTargetPoint(index: Int, lat: Double, lon: Double) = Unit
    override fun updateParameters(minimumTime: Duration, maximumTime: Duration) = Unit
    override fun updateAreaRadiusMeters(index: Int, radiusMeters: Double) = Unit
}
