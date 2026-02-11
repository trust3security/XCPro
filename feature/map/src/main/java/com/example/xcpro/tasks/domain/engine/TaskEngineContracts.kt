package com.example.xcpro.tasks.domain.engine

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import java.time.Duration
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared pure task-engine state for both task types.
 */
data class TaskEngineState(
    val taskType: TaskType,
    val task: Task = Task(id = "new-task"),
    val activeLegIndex: Int = 0,
    val isTaskValid: Boolean = false
)

/**
 * Racing-only state exposed by the future pure Racing engine.
 */
data class RacingTaskEngineState(
    val base: TaskEngineState = TaskEngineState(taskType = TaskType.RACING),
    val taskDistanceMeters: Double = 0.0
)

/**
 * AAT-only state exposed by the future pure AAT engine.
 */
data class AATTaskEngineState(
    val base: TaskEngineState = TaskEngineState(taskType = TaskType.AAT),
    val minimumTime: Duration = Duration.ZERO,
    val maximumTime: Duration = Duration.ZERO,
    val editWaypointIndex: Int? = null
)

/**
 * Minimal mutation/query surface shared by pure task engines.
 */
interface TaskEngine<S> {
    val state: StateFlow<S>

    fun setTask(task: Task)
    fun addWaypoint(waypoint: TaskWaypoint)
    fun removeWaypoint(index: Int)
    fun reorderWaypoints(fromIndex: Int, toIndex: Int)
    fun setActiveLeg(index: Int)
    fun clearTask()
}

/**
 * Racing domain engine contract.
 */
interface RacingTaskEngine : TaskEngine<RacingTaskEngineState> {
    fun calculateTaskDistanceMeters(task: Task = state.value.base.task): Double
    fun calculateSegmentDistanceMeters(from: TaskWaypoint, to: TaskWaypoint): Double
}

/**
 * AAT domain engine contract.
 */
interface AATTaskEngine : TaskEngine<AATTaskEngineState> {
    fun updateTargetPoint(index: Int, lat: Double, lon: Double)
    fun updateParameters(minimumTime: Duration, maximumTime: Duration)
    fun updateAreaRadiusMeters(index: Int, radiusMeters: Double)
}
