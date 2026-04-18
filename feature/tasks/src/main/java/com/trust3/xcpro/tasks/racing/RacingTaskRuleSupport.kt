package com.trust3.xcpro.tasks.racing

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskWaypointParamKeys
import com.trust3.xcpro.tasks.core.WaypointRole

internal val RACING_START_RULE_KEYS = setOf(
    TaskWaypointParamKeys.START_GATE_OPEN_TIME_MILLIS,
    TaskWaypointParamKeys.START_GATE_CLOSE_TIME_MILLIS,
    TaskWaypointParamKeys.START_TOLERANCE_METERS,
    TaskWaypointParamKeys.PRE_START_ALTITUDE_METERS,
    TaskWaypointParamKeys.START_ALTITUDE_REFERENCE,
    TaskWaypointParamKeys.START_DIRECTION_OVERRIDE_DEGREES,
    TaskWaypointParamKeys.MAX_START_ALTITUDE_METERS,
    TaskWaypointParamKeys.MAX_START_GROUNDSPEED_MS,
    TaskWaypointParamKeys.PEV_ENABLED,
    TaskWaypointParamKeys.PEV_WAIT_TIME_MINUTES,
    TaskWaypointParamKeys.PEV_START_WINDOW_MINUTES,
    TaskWaypointParamKeys.PEV_MAX_PRESSES,
    TaskWaypointParamKeys.PEV_DEDUPE_SECONDS,
    TaskWaypointParamKeys.PEV_MIN_INTERVAL_MINUTES,
    TaskWaypointParamKeys.PEV_PRESS_TIMESTAMPS_MILLIS,
    TaskWaypointParamKeys.RACING_VALIDATION_PROFILE
)

internal val RACING_FINISH_RULE_KEYS = setOf(
    TaskWaypointParamKeys.FINISH_CLOSE_TIME_MILLIS,
    TaskWaypointParamKeys.FINISH_MIN_ALTITUDE_METERS,
    TaskWaypointParamKeys.FINISH_ALTITUDE_REFERENCE,
    TaskWaypointParamKeys.FINISH_DIRECTION_OVERRIDE_DEGREES,
    TaskWaypointParamKeys.FINISH_ALLOW_STRAIGHT_IN_BELOW_MIN_ALTITUDE,
    TaskWaypointParamKeys.FINISH_REQUIRE_LAND_WITHOUT_DELAY,
    TaskWaypointParamKeys.FINISH_LAND_WITHOUT_DELAY_WINDOW_SECONDS,
    TaskWaypointParamKeys.FINISH_LANDING_SPEED_THRESHOLD_MS,
    TaskWaypointParamKeys.FINISH_LANDING_HOLD_SECONDS,
    TaskWaypointParamKeys.FINISH_CONTEST_BOUNDARY_RADIUS_METERS,
    TaskWaypointParamKeys.FINISH_STOP_PLUS_FIVE_ENABLED,
    TaskWaypointParamKeys.FINISH_STOP_PLUS_FIVE_MINUTES
)

internal fun resolveRacingValidationProfile(task: Task): RacingTaskStructureRules.Profile {
    val raw = task.waypoints
        .firstOrNull { it.role == WaypointRole.START }
        ?.customParameters
        ?.get(TaskWaypointParamKeys.RACING_VALIDATION_PROFILE) as? String
    return raw?.let { name ->
        RacingTaskStructureRules.Profile.entries.firstOrNull { profile ->
            profile.name.equals(name, ignoreCase = true)
        }
    } ?: RacingTaskStructureRules.Profile.FAI_STRICT
}

internal fun Task.extractRoleRules(role: WaypointRole, keys: Set<String>): Map<String, Any> {
    val source = waypoints.firstOrNull { it.role == role }?.customParameters ?: return emptyMap()
    return source.filterKeys { key -> key in keys }
}

internal fun Task.applyRoleRules(startRules: Map<String, Any>, finishRules: Map<String, Any>): Task {
    if (waypoints.isEmpty()) return this
    val updatedWaypoints = waypoints.map { waypoint ->
        val updatedParameters = waypoint.customParameters.toMutableMap()
        RACING_START_RULE_KEYS.forEach { key -> updatedParameters.remove(key) }
        RACING_FINISH_RULE_KEYS.forEach { key -> updatedParameters.remove(key) }
        when (waypoint.role) {
            WaypointRole.START -> updatedParameters.putAll(startRules)
            WaypointRole.FINISH -> updatedParameters.putAll(finishRules)
            else -> Unit
        }
        waypoint.copy(customParameters = updatedParameters)
    }
    return copy(waypoints = updatedWaypoints)
}

internal fun Task.withValidationProfile(profile: RacingTaskStructureRules.Profile): Task {
    val startIndex = waypoints.indexOfFirst { it.role == WaypointRole.START }
    if (startIndex < 0) return this
    val updatedWaypoints = waypoints.toMutableList()
    val startWaypoint = updatedWaypoints[startIndex]
    val updatedParameters = startWaypoint.customParameters.toMutableMap().apply {
        this[TaskWaypointParamKeys.RACING_VALIDATION_PROFILE] = profile.name
    }
    updatedWaypoints[startIndex] = startWaypoint.copy(customParameters = updatedParameters)
    return copy(waypoints = updatedWaypoints)
}
