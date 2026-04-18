package com.trust3.xcpro.tasks.domain.persistence

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.data.persistence.AUTOSAVE_SLOT
import com.trust3.xcpro.tasks.domain.engine.AATTaskEngine
import com.trust3.xcpro.tasks.domain.engine.RacingTaskEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine-facing persistence orchestration for task state and task type settings.
 */
@Singleton
class TaskEnginePersistenceService @Inject constructor(
    private val taskTypeSettingsRepository: TaskTypeSettingsRepository,
    private val racingTaskPersistence: RacingTaskPersistence,
    private val aatTaskPersistence: AATTaskPersistence,
    private val racingTaskEngine: RacingTaskEngine,
    private val aatTaskEngine: AATTaskEngine
) {

    suspend fun restore(defaultType: TaskType = TaskType.RACING): TaskType {
        val restoredType = taskTypeSettingsRepository.loadTaskType(defaultType)
        racingTaskPersistence.loadAutosavedTask()?.let { racingTaskEngine.setTask(it) }
        aatTaskPersistence.loadAutosavedTask()?.let { aatTaskEngine.setTask(it) }
        return restoredType
    }

    suspend fun saveTaskType(taskType: TaskType) {
        taskTypeSettingsRepository.saveTaskType(taskType)
    }

    suspend fun autosaveEngines() {
        racingTaskPersistence.saveTask(
            taskName = AUTOSAVE_SLOT,
            task = racingTaskEngine.state.value.base.task
        )
        aatTaskPersistence.saveTask(
            taskName = AUTOSAVE_SLOT,
            task = aatTaskEngine.state.value.base.task
        )
    }

    suspend fun listTaskNames(taskType: TaskType): List<String> {
        return persistenceFor(taskType).listTaskNames()
    }

    suspend fun saveNamedTask(taskType: TaskType, taskName: String): Boolean {
        return persistenceFor(taskType).saveTask(taskName, taskFor(taskType))
    }

    suspend fun loadNamedTask(taskType: TaskType, taskName: String): Boolean {
        val loaded = persistenceFor(taskType).loadTask(taskName) ?: return false
        engineFor(taskType).setTask(loaded)
        return true
    }

    suspend fun deleteNamedTask(taskType: TaskType, taskName: String): Boolean {
        return persistenceFor(taskType).deleteTask(taskName)
    }

    private fun persistenceFor(taskType: TaskType): TaskPersistenceFacade {
        return when (taskType) {
            TaskType.RACING -> RacingFacade(racingTaskPersistence)
            TaskType.AAT -> AatFacade(aatTaskPersistence)
        }
    }

    private fun engineFor(taskType: TaskType): TaskEngineFacade {
        return when (taskType) {
            TaskType.RACING -> RacingEngineFacade(racingTaskEngine)
            TaskType.AAT -> AatEngineFacade(aatTaskEngine)
        }
    }

    private fun taskFor(taskType: TaskType): Task {
        return when (taskType) {
            TaskType.RACING -> racingTaskEngine.state.value.base.task
            TaskType.AAT -> aatTaskEngine.state.value.base.task
        }
    }
}

private interface TaskPersistenceFacade {
    suspend fun listTaskNames(): List<String>
    suspend fun loadTask(taskName: String): Task?
    suspend fun saveTask(taskName: String, task: Task): Boolean
    suspend fun deleteTask(taskName: String): Boolean
}

private class RacingFacade(
    private val persistence: RacingTaskPersistence
) : TaskPersistenceFacade {
    override suspend fun listTaskNames(): List<String> = persistence.listTaskNames()
    override suspend fun loadTask(taskName: String): Task? = persistence.loadTask(taskName)
    override suspend fun saveTask(taskName: String, task: Task): Boolean = persistence.saveTask(taskName, task)
    override suspend fun deleteTask(taskName: String): Boolean = persistence.deleteTask(taskName)
}

private class AatFacade(
    private val persistence: AATTaskPersistence
) : TaskPersistenceFacade {
    override suspend fun listTaskNames(): List<String> = persistence.listTaskNames()
    override suspend fun loadTask(taskName: String): Task? = persistence.loadTask(taskName)
    override suspend fun saveTask(taskName: String, task: Task): Boolean = persistence.saveTask(taskName, task)
    override suspend fun deleteTask(taskName: String): Boolean = persistence.deleteTask(taskName)
}

private interface TaskEngineFacade {
    fun setTask(task: Task)
}

private class RacingEngineFacade(
    private val engine: RacingTaskEngine
) : TaskEngineFacade {
    override fun setTask(task: Task) = engine.setTask(task)
}

private class AatEngineFacade(
    private val engine: AATTaskEngine
) : TaskEngineFacade {
    override fun setTask(task: Task) = engine.setTask(task)
}
