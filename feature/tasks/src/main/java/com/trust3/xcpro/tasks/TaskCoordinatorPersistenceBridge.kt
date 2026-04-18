package com.trust3.xcpro.tasks

import com.trust3.xcpro.tasks.aat.AATTaskManager
import com.trust3.xcpro.tasks.core.AATTaskTimeCustomParams
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.domain.engine.AATTaskEngine
import com.trust3.xcpro.tasks.domain.engine.RacingTaskEngine
import com.trust3.xcpro.tasks.domain.persistence.TaskEnginePersistenceService
import com.trust3.xcpro.tasks.racing.RacingTaskManager
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class TaskCoordinatorPersistenceBridge(
    private val taskTypeState: MutableStateFlow<TaskType>,
    private val taskEnginePersistenceService: TaskEnginePersistenceService?,
    private val racingTaskEngine: RacingTaskEngine?,
    private val aatTaskEngine: AATTaskEngine?,
    private val racingTaskManager: RacingTaskManager,
    private val aatTaskManager: AATTaskManager,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit
) {
    fun persistTaskType(taskType: TaskType) {
        val service = taskEnginePersistenceService ?: return
        scope.launch {
            service.saveTaskType(taskType)
        }
    }

    fun syncAndAutosave(taskType: TaskType) {
        val service = taskEnginePersistenceService ?: return
        syncEngineFromManager(taskType)
        scope.launch {
            service.autosaveEngines()
        }
    }

    fun syncAllAndAutosave() {
        val service = taskEnginePersistenceService ?: return
        syncEnginesFromManagers()
        scope.launch {
            service.autosaveEngines()
        }
    }

    suspend fun loadSavedTasks() {
        val service = taskEnginePersistenceService
        if (service != null) {
            val restoredType = service.restore(taskTypeState.value)
            taskTypeState.value = restoredType
            applyEngineTaskToManager(TaskType.RACING)
            applyEngineTaskToManager(TaskType.AAT)
            log("Finished loading saved tasks from service (type=${restoredType.name})")
            return
        }
        syncEnginesFromManagers()
        log("Finished loading task state without persistence service (type=${taskTypeState.value.name})")
    }

    suspend fun getSavedTasks(): List<String> {
        val service = taskEnginePersistenceService ?: return emptyList()
        return service.listTaskNames(taskTypeState.value)
    }

    suspend fun saveTask(taskName: String): Boolean {
        val service = taskEnginePersistenceService ?: return false
        val currentType = taskTypeState.value
        syncEngineFromManager(currentType)
        return service.saveNamedTask(currentType, taskName)
    }

    suspend fun loadTask(taskName: String): Boolean {
        val service = taskEnginePersistenceService ?: return false
        val currentType = taskTypeState.value
        val loaded = service.loadNamedTask(currentType, taskName)
        if (loaded) {
            applyEngineTaskToManager(currentType)
        }
        return loaded
    }

    suspend fun deleteTask(taskName: String): Boolean {
        val service = taskEnginePersistenceService ?: return false
        return service.deleteNamedTask(taskTypeState.value, taskName)
    }

    fun syncEngineFromManager(taskType: TaskType) {
        when (taskType) {
            TaskType.RACING -> racingTaskEngine?.setTask(racingTaskManager.getCoreTask())
            TaskType.AAT -> {
                val minimum = aatTaskManager.currentAATTask.minimumTime
                val maximum = aatTaskManager.currentAATTask.maximumTime
                val taskWithTimes = withAatTimes(
                    task = aatTaskManager.getCoreTask(),
                    minimum = minimum,
                    maximum = maximum
                )
                aatTaskEngine?.setTask(taskWithTimes)
            }
        }
    }

    private fun syncEnginesFromManagers() {
        syncEngineFromManager(TaskType.RACING)
        syncEngineFromManager(TaskType.AAT)
    }

    private fun applyEngineTaskToManager(taskType: TaskType) {
        when (taskType) {
            TaskType.RACING -> {
                val state = racingTaskEngine?.state?.value ?: return
                racingTaskManager.initializeFromCoreTask(
                    task = state.base.task,
                    activeLegIndex = state.base.activeLegIndex
                )
            }

            TaskType.AAT -> {
                val state = aatTaskEngine?.state?.value ?: return
                aatTaskManager.initializeFromCoreTask(
                    task = state.base.task,
                    activeLegIndex = state.base.activeLegIndex
                )
            }
        }
    }

    private fun withAatTimes(
        task: Task,
        minimum: Duration,
        maximum: Duration?
    ): Task {
        if (task.waypoints.isEmpty()) return task
        val typedTimes = AATTaskTimeCustomParams(
            minimumTimeSeconds = minimum.seconds.toDouble(),
            maximumTimeSeconds = maximum?.seconds?.toDouble()
        )
        return task.copy(
            waypoints = task.waypoints.map { waypoint ->
                val params = waypoint.customParameters.toMutableMap()
                typedTimes.applyTo(params)
                waypoint.copy(customParameters = params)
            }
        )
    }
}
