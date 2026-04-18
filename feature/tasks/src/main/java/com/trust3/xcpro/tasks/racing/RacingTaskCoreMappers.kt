package com.trust3.xcpro.tasks.racing

import com.trust3.xcpro.tasks.core.RacingWaypointCustomParams
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.racing.models.RacingFinishPointType
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import com.trust3.xcpro.tasks.racing.models.RacingTurnPointType
import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole

internal fun Task.toSimpleRacingTask(): SimpleRacingTask {
    return SimpleRacingTask(
        id = id,
        waypoints = toRacingWaypoints()
    )
}

fun SimpleRacingTask.toCoreTask(
    existingCustomParametersById: Map<String, Map<String, Any>> = emptyMap()
): Task {
    return Task(
        id = id,
        waypoints = waypoints.map { waypoint ->
            val customParameters = existingCustomParametersById[waypoint.id]
                ?.toMutableMap()
                ?: mutableMapOf()
            RacingWaypointCustomParams(
                keyholeInnerRadiusMeters = waypoint.keyholeInnerRadiusMeters,
                keyholeAngle = waypoint.keyholeAngle,
                faiQuadrantOuterRadiusMeters = waypoint.faiQuadrantOuterRadiusMeters
            ).applyTo(customParameters)
            TaskWaypoint(
                id = waypoint.id,
                title = waypoint.title,
                subtitle = waypoint.subtitle,
                lat = waypoint.lat,
                lon = waypoint.lon,
                role = when (waypoint.role) {
                    RacingWaypointRole.START -> WaypointRole.START
                    RacingWaypointRole.TURNPOINT -> WaypointRole.TURNPOINT
                    RacingWaypointRole.FINISH -> WaypointRole.FINISH
                },
                customRadius = null,
                customRadiusMeters = waypoint.gateWidthMeters,
                customPointType = when (waypoint.role) {
                    RacingWaypointRole.START -> waypoint.startPointType.name
                    RacingWaypointRole.TURNPOINT -> waypoint.turnPointType.name
                    RacingWaypointRole.FINISH -> waypoint.finishPointType.name
                },
                customParameters = customParameters
            )
        }
    )
}

fun Task.toRacingWaypoints(): List<RacingWaypoint> {
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
            customGateWidthMeters = waypoint.resolvedCustomRadiusMeters(),
            keyholeInnerRadiusMeters = racingParams.keyholeInnerRadiusMeters,
            keyholeAngle = racingParams.keyholeAngle,
            faiQuadrantOuterRadiusMeters = racingParams.faiQuadrantOuterRadiusMeters
        )
    }

    return racingWaypoints
}
