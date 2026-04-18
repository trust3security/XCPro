package com.trust3.xcpro.gestures

import com.trust3.xcpro.tasks.aat.gestures.AatGestureHandler
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint

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
