package com.trust3.xcpro.tasks.domain.persistence

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists the selected task type independently from task content.
 */
interface TaskTypeSettingsRepository {
    val taskTypeFlow: StateFlow<TaskType>

    suspend fun loadTaskType(defaultType: TaskType = TaskType.RACING): TaskType
    suspend fun saveTaskType(taskType: TaskType)
}

/**
 * Racing-task persistence contract for domain wiring.
 */
interface RacingTaskPersistence {
    suspend fun listTaskNames(): List<String>
    suspend fun loadAutosavedTask(): Task?
    suspend fun loadTask(taskName: String): Task?
    suspend fun saveTask(taskName: String, task: Task): Boolean
    suspend fun deleteTask(taskName: String): Boolean
}

/**
 * AAT-task persistence contract for domain wiring.
 */
interface AATTaskPersistence {
    suspend fun listTaskNames(): List<String>
    suspend fun loadAutosavedTask(): Task?
    suspend fun loadTask(taskName: String): Task?
    suspend fun saveTask(taskName: String, task: Task): Boolean
    suspend fun deleteTask(taskName: String): Boolean
}

typealias RacingTaskPersistencePort = RacingTaskPersistence
typealias AATTaskPersistencePort = AATTaskPersistence
