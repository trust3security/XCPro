package com.example.xcpro.tasks

import android.content.SharedPreferences
import com.example.xcpro.tasks.core.TaskType

private const val KEY_CURRENT_TASK_TYPE = "current_task_type"

/**
 * Handles persistence and bulk loading concerns for [TaskManagerCoordinator].
 */
internal class TaskCoordinatorPersistence(
    private val prefs: SharedPreferences?,
    private val loadRacingTask: () -> Boolean,
    private val loadAATTask: () -> Boolean,
    private val log: (String) -> Unit
) {

    fun saveTaskType(taskType: TaskType) {
        prefs?.edit()?.putString(KEY_CURRENT_TASK_TYPE, taskType.name)?.apply()
        log("Saved task type: ${taskType.name}")
    }

    fun loadTaskType(defaultType: TaskType): TaskType {
        val stored = prefs?.getString(KEY_CURRENT_TASK_TYPE, defaultType.name)
        val resolved = runCatching { TaskType.valueOf(stored ?: defaultType.name) }.getOrDefault(defaultType)
        log("Loaded task type: ${resolved.name}")
        return resolved
    }

    fun loadSavedTasks(): TaskLoadResult {
        log("Loading saved tasks from persistence store...")
        val racingLoaded = loadRacingTask()
        val aatLoaded = loadAATTask()
        log("Finished loading saved tasks (racingLoaded=$racingLoaded, aatLoaded=$aatLoaded)")
        return TaskLoadResult(racingLoaded, aatLoaded)
    }
}

internal data class TaskLoadResult(
    val racingLoaded: Boolean,
    val aatLoaded: Boolean
)

