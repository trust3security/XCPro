package com.example.xcpro.tasks

import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.domain.engine.AATTaskEngine
import com.example.xcpro.tasks.domain.engine.RacingTaskEngine
import com.example.xcpro.tasks.domain.persistence.TaskEnginePersistenceService
import com.example.xcpro.tasks.racing.RacingTaskManager
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
                racingTaskManager.initializeFromGenericWaypoints(state.base.task.waypoints)
                racingTaskManager.setRacingLeg(state.base.activeLegIndex)
            }

            TaskType.AAT -> {
                val state = aatTaskEngine?.state?.value ?: return
                aatTaskManager.initializeFromGenericWaypoints(state.base.task.waypoints)
                aatTaskManager.updateAATTimes(
                    minTime = state.minimumTime,
                    maxTime = state.maximumTime.takeIf { !it.isNegative && !it.isZero }
                )
                aatTaskManager.setAATLeg(state.base.activeLegIndex)
            }
        }
    }

    private fun withAatTimes(
        task: Task,
        minimum: Duration,
        maximum: Duration?
    ): Task {
        if (task.waypoints.isEmpty()) return task
        val minSeconds = minimum.seconds.toDouble()
        val maxSeconds = maximum?.seconds?.toDouble()
        return task.copy(
            waypoints = task.waypoints.map { waypoint ->
                val params = waypoint.customParameters.toMutableMap()
                params[KEY_AAT_MIN_TIME_SECONDS] = minSeconds
                if (maxSeconds != null) {
                    params[KEY_AAT_MAX_TIME_SECONDS] = maxSeconds
                } else {
                    params.remove(KEY_AAT_MAX_TIME_SECONDS)
                }
                waypoint.copy(customParameters = params)
            }
        )
    }

    private companion object {
        private const val KEY_AAT_MIN_TIME_SECONDS = "aatMinimumTimeSeconds"
        private const val KEY_AAT_MAX_TIME_SECONDS = "aatMaximumTimeSeconds"
    }
}
