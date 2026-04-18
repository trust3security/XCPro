package com.trust3.xcpro.map

import com.trust3.xcpro.gestures.TaskGestureCallbacks
import com.trust3.xcpro.gestures.TaskGestureHandler
import com.trust3.xcpro.gestures.TaskGestureHandlerFactory
import com.trust3.xcpro.tasks.core.TaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

internal class MapScreenTaskShellCoordinator(
    scope: CoroutineScope,
    private val mapTasksUseCase: MapTasksUseCase
) {
    val taskType: StateFlow<TaskType> = mapTasksUseCase.taskTypeFlow
    val isAATEditMode: StateFlow<Boolean> = mapTasksUseCase.aatEditWaypointIndexFlow
        .map { editWaypointIndex -> editWaypointIndex != null }
        .eagerState(scope = scope, initial = mapTasksUseCase.aatEditWaypointIndexFlow.value != null)

    fun createTaskGestureHandler(callbacks: TaskGestureCallbacks): TaskGestureHandler =
        TaskGestureHandlerFactory.create(
            taskType = mapTasksUseCase.taskTypeFlow.value,
            waypointsProvider = { mapTasksUseCase.currentRuntimeSnapshot().task.waypoints },
            callbacks = callbacks
        )

    fun enterAATEditMode(waypointIndex: Int) {
        mapTasksUseCase.enterAATEditMode(waypointIndex)
    }

    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        mapTasksUseCase.updateAATTargetPoint(index, lat, lon)
    }

    fun exitAATEditMode() {
        mapTasksUseCase.exitAATEditMode()
    }
}
