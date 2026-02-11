package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATFinishPointType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATRadiusAuthority
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.WaypointRole
import java.time.Duration

private const val KEY_MIN_TIME_SECONDS = "aatMinimumTimeSeconds"
private const val KEY_MAX_TIME_SECONDS = "aatMaximumTimeSeconds"

internal fun Task.toSimpleAATTask(): SimpleAATTask {
    val minimumTime = extractDurationFromTask(KEY_MIN_TIME_SECONDS, Duration.ofHours(3))
    val maximumTime = extractDurationFromTask(KEY_MAX_TIME_SECONDS, Duration.ofHours(6))

    val aatWaypoints = waypoints.map { waypoint ->
        val role = when (waypoint.role) {
            WaypointRole.START -> AATWaypointRole.START
            WaypointRole.FINISH -> AATWaypointRole.FINISH
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> AATWaypointRole.TURNPOINT
        }

        val startType = waypoint.customPointType
            ?.let { runCatching { AATStartPointType.valueOf(it) }.getOrNull() }
            ?: AATStartPointType.AAT_START_LINE
        val finishType = waypoint.customPointType
            ?.let { runCatching { AATFinishPointType.valueOf(it) }.getOrNull() }
            ?: AATFinishPointType.AAT_FINISH_CYLINDER
        val turnType = waypoint.customPointType
            ?.let { runCatching { AATTurnPointType.valueOf(it) }.getOrNull() }
            ?: AATTurnPointType.AAT_CYLINDER

        val radiusMeters = (waypoint.customParameters["radiusMeters"] as? Number)?.toDouble()
            ?: ((waypoint.customRadius ?: AATRadiusAuthority.getRadiusForRole(role)) * 1000.0)
        val outerRadiusMeters = (waypoint.customParameters["outerRadiusMeters"] as? Number)?.toDouble()
            ?: radiusMeters
        val innerRadiusMeters = (waypoint.customParameters["innerRadiusMeters"] as? Number)?.toDouble() ?: 0.0
        val startAngleDegrees = (waypoint.customParameters["startAngleDegrees"] as? Number)?.toDouble() ?: 0.0
        val endAngleDegrees = (waypoint.customParameters["endAngleDegrees"] as? Number)?.toDouble() ?: 90.0
        val lineWidthMeters = (waypoint.customParameters["lineWidthMeters"] as? Number)?.toDouble() ?: radiusMeters
        val targetLat = (waypoint.customParameters["targetLat"] as? Number)?.toDouble() ?: waypoint.lat
        val targetLon = (waypoint.customParameters["targetLon"] as? Number)?.toDouble() ?: waypoint.lon
        val isTargetPointCustomized = (waypoint.customParameters["isTargetPointCustomized"] as? Boolean)
            ?: (targetLat != waypoint.lat || targetLon != waypoint.lon)

        val areaShape = when (role) {
            AATWaypointRole.START -> if (startType == AATStartPointType.AAT_START_LINE) AATAreaShape.LINE else AATAreaShape.CIRCLE
            AATWaypointRole.FINISH -> if (finishType == AATFinishPointType.AAT_FINISH_LINE) AATAreaShape.LINE else AATAreaShape.CIRCLE
            AATWaypointRole.TURNPOINT -> if (turnType == AATTurnPointType.AAT_CYLINDER) AATAreaShape.CIRCLE else AATAreaShape.SECTOR
        }

        AATWaypoint(
            id = waypoint.id,
            title = waypoint.title,
            subtitle = waypoint.subtitle,
            lat = waypoint.lat,
            lon = waypoint.lon,
            role = role,
            assignedArea = AATAssignedArea(
                shape = areaShape,
                radiusMeters = radiusMeters,
                innerRadiusMeters = innerRadiusMeters,
                outerRadiusMeters = outerRadiusMeters,
                startAngleDegrees = startAngleDegrees,
                endAngleDegrees = endAngleDegrees,
                lineWidthMeters = lineWidthMeters
            ),
            startPointType = startType,
            finishPointType = finishType,
            turnPointType = turnType,
            targetPoint = AATLatLng(targetLat, targetLon),
            isTargetPointCustomized = isTargetPointCustomized
        )
    }

    return SimpleAATTask(
        id = id.ifBlank { "aat-task" },
        waypoints = aatWaypoints,
        minimumTime = minimumTime,
        maximumTime = maximumTime
    )
}

private fun Task.extractDurationFromTask(key: String, fallback: Duration): Duration {
    val first = waypoints.firstOrNull() ?: return fallback
    val seconds = (first.customParameters[key] as? Number)?.toLong() ?: return fallback
    if (seconds <= 0) return fallback
    return Duration.ofSeconds(seconds)
}
