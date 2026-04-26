package com.trust3.xcpro.map

import com.trust3.xcpro.tasks.TaskRuntimeSnapshot
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEventType

internal fun buildRacingEventMessage(
    taskSnapshot: TaskRuntimeSnapshot,
    event: RacingNavigationEvent
): String {
    val waypointName = taskSnapshot.task.waypoints
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
