package com.example.xcpro.map
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

data class TaskRenderSnapshot(
    val task: Task,
    val taskType: TaskType,
    val aatEditWaypointIndex: Int?
)

class MapTasksUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator
) {
    val taskTypeFlow: StateFlow<TaskType> = taskManager.taskTypeFlow
    val aatEditWaypointIndexFlow: StateFlow<Int?> = taskManager.aatEditWaypointIndexFlow

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

    fun currentTaskSnapshot(): Task = taskManager.currentSnapshot().task

    fun currentWaypointCount(): Int = taskManager.currentSnapshot().task.waypoints.size

    fun taskRenderSnapshot(): TaskRenderSnapshot {
        val snapshot = taskManager.currentSnapshot()
        return TaskRenderSnapshot(
            task = snapshot.task,
            taskType = snapshot.taskType,
            aatEditWaypointIndex = aatEditWaypointIndexFlow.value
        )
    }
}
