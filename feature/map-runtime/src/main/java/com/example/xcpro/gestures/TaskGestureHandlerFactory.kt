package com.example.xcpro.gestures

import com.example.xcpro.tasks.aat.gestures.AatGestureHandler
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint

object TaskGestureHandlerFactory {

    fun create(
        taskType: TaskType,
        waypointsProvider: () -> List<TaskWaypoint>,
        callbacks: TaskGestureCallbacks
    ): TaskGestureHandler {
        return if (taskType == TaskType.AAT) {
            AatGestureHandler(
                waypointsProvider = waypointsProvider,
                callbacks = callbacks
            )
        } else {
            NoOpTaskGestureHandler
        }
    }
}
