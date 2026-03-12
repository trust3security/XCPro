package com.example.xcpro.map

import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEventType

internal fun currentRacingTaskOrNull(taskManager: TaskManagerCoordinator): Task? {
    if (taskManager.taskType != TaskType.RACING) {
        return null
    }
    val task = taskManager.currentTask
    if (!RacingTaskStructureRules.validate(task).isValid) {
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
        RacingNavigationEventType.START_REJECTED ->
            if (waypointName != null) "Start rejected: $waypointName" else "Start rejected"
        RacingNavigationEventType.TURNPOINT ->
            if (waypointName != null) "Turnpoint reached: $waypointName" else "Turnpoint reached"
        RacingNavigationEventType.TURNPOINT_NEAR_MISS -> {
            val suffix = event.turnpointNearMissDistanceMeters
                ?.let { distance -> " (${distance.toInt()}m)" }
                .orEmpty()
            if (waypointName != null) {
                "Turnpoint near miss: $waypointName$suffix"
            } else {
                "Turnpoint near miss$suffix"
            }
        }
        RacingNavigationEventType.FINISH -> {
            val finishOutcome = event.finishOutcome
            val outcomeSuffix = when (finishOutcome) {
                null -> ""
                else -> " (${finishOutcome.name})"
            }
            if (waypointName != null) "Finish reached: $waypointName$outcomeSuffix"
            else "Finish reached$outcomeSuffix"
        }
    }
}
