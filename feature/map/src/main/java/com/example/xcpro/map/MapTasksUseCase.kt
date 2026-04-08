package com.example.xcpro.map

import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.TaskType
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Map-shell adapter over the task coordinator.
 * Keeps map route/task shell orchestration out of feature:map-runtime while
 * reusing the runtime-owned TaskRenderSnapshot model.
 */
class MapTasksUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator
) {
    val taskTypeFlow: StateFlow<TaskType> = taskManager.taskTypeFlow
    val aatEditWaypointIndexFlow: StateFlow<Int?> = taskManager.aatEditWaypointIndexFlow

    /**
     * Map-shell task reads stay on the coordinator snapshot seam.
     */
    fun currentRuntimeSnapshot(): TaskRuntimeSnapshot = taskManager.currentSnapshot()

    suspend fun loadSavedTasks() {
        taskManager.loadSavedTasks()
    }

    fun enterAATEditMode(waypointIndex: Int) {
        taskManager.enterAATEditMode(waypointIndex)
    }

    fun exitAATEditMode() {
        taskManager.exitAATEditMode()
    }

    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        taskManager.updateAATTargetPoint(index, lat, lon)
    }

    fun clearTask() {
        taskManager.clearTask()
    }

    suspend fun saveTask(taskName: String): Boolean = taskManager.saveTask(taskName)

    fun taskRenderSnapshot(): TaskRenderSnapshot {
        val snapshot = currentRuntimeSnapshot()
        return TaskRenderSnapshot(
            task = snapshot.task,
            taskType = snapshot.taskType,
            aatEditWaypointIndex = aatEditWaypointIndexFlow.value
        )
    }
}
