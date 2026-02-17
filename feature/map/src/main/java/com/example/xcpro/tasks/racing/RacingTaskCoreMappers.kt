package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.RacingWaypointCustomParams
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole

internal fun Task.toSimpleRacingTask(): SimpleRacingTask {
    val racingWaypoints = waypoints.mapIndexed { index, waypoint ->
        val role = when {
            waypoints.size == 1 -> RacingWaypointRole.START
            index == 0 -> RacingWaypointRole.START
            index == waypoints.lastIndex -> RacingWaypointRole.FINISH
            else -> RacingWaypointRole.TURNPOINT
        }
        val startType = waypoint.customPointType
            ?.let { runCatching { RacingStartPointType.valueOf(it) }.getOrNull() }
            ?: RacingStartPointType.START_LINE
        val finishType = waypoint.customPointType
            ?.let { runCatching { RacingFinishPointType.valueOf(it) }.getOrNull() }
            ?: RacingFinishPointType.FINISH_CYLINDER
        val turnType = waypoint.customPointType
            ?.let { runCatching { RacingTurnPointType.valueOf(it) }.getOrNull() }
            ?: RacingTurnPointType.TURN_POINT_CYLINDER
        val racingParams = RacingWaypointCustomParams.from(waypoint.customParameters)

        RacingWaypoint.createWithStandardizedDefaults(
            id = waypoint.id,
            title = waypoint.title,
            subtitle = waypoint.subtitle,
            lat = waypoint.lat,
            lon = waypoint.lon,
            role = role,
            startPointType = startType,
            finishPointType = finishType,
            turnPointType = turnType,
            customGateWidth = waypoint.customRadius,
            keyholeInnerRadius = racingParams.keyholeInnerRadius,
            keyholeAngle = racingParams.keyholeAngle,
            faiQuadrantOuterRadius = racingParams.faiQuadrantOuterRadius
        )
    }

    return SimpleRacingTask(
        id = id.ifBlank { "racing-task" },
        waypoints = racingWaypoints
    )
}
