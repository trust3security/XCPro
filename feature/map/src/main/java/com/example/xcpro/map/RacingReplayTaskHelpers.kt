package com.example.xcpro.map

import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEventType
import com.example.xcpro.tasks.racing.toSimpleRacingTask

internal fun currentRacingTaskOrNull(taskManager: TaskManagerCoordinator): SimpleRacingTask? {
    if (taskManager.taskType != TaskType.RACING) {
        return null
    }
    val task = taskManager.currentTask.toSimpleRacingTask()
    if (task.waypoints.size < 2) {
        return null
    }
    return task
}

internal fun buildRacingEventMessage(
    taskManager: TaskManagerCoordinator,
    event: RacingNavigationEvent
): String {
    val waypointName = taskManager.currentTask.waypoints
        .getOrNull(event.fromLegIndex)
        ?.title
    return when (event.type) {
        RacingNavigationEventType.START ->
            if (waypointName != null) "Start crossed: $waypointName" else "Start crossed"
        RacingNavigationEventType.TURNPOINT ->
            if (waypointName != null) "Turnpoint reached: $waypointName" else "Turnpoint reached"
        RacingNavigationEventType.FINISH ->
            if (waypointName != null) "Finish reached: $waypointName" else "Finish reached"
    }
}
