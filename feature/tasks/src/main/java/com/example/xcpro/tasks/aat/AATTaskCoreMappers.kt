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
import com.example.xcpro.tasks.aat.models.getAuthorityRadiusMeters
import com.example.xcpro.tasks.core.AATTaskTimeCustomParams
import com.example.xcpro.tasks.core.AATWaypointCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TargetStateCustomParams
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import java.time.Duration

fun Task.toSimpleAATTask(): SimpleAATTask {
    val firstParams = waypoints.firstOrNull()?.customParameters ?: emptyMap()
    val timeParams = AATTaskTimeCustomParams.from(
        source = firstParams,
        fallbackMinimumTimeSeconds = Duration.ofHours(3).seconds.toDouble(),
        fallbackMaximumTimeSeconds = Duration.ofHours(6).seconds.toDouble()
    )
    val minimumTime = Duration.ofSeconds(timeParams.minimumTimeSeconds.toLong())
    val maximumTime = timeParams.maximumTimeSeconds?.toLong()?.let(Duration::ofSeconds)

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

        val fallbackRadiusMeters =
            waypoint.resolvedCustomRadiusMeters() ?: AATRadiusAuthority.getRadiusMetersForRole(role)
        val typedParams = AATWaypointCustomParams.from(
            source = waypoint.customParameters,
            fallbackLat = waypoint.lat,
            fallbackLon = waypoint.lon,
            fallbackRadiusMeters = fallbackRadiusMeters
        )
        val targetState = TargetStateCustomParams.from(waypoint.customParameters)

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
                radiusMeters = typedParams.radiusMeters,
                innerRadiusMeters = typedParams.innerRadiusMeters,
                outerRadiusMeters = typedParams.outerRadiusMeters,
                startAngleDegrees = typedParams.startAngleDegrees,
                endAngleDegrees = typedParams.endAngleDegrees,
                lineWidthMeters = typedParams.lineWidthMeters
            ),
            startPointType = startType,
            finishPointType = finishType,
            turnPointType = turnType,
            targetPoint = AATLatLng(typedParams.targetLat, typedParams.targetLon),
            isTargetPointCustomized = typedParams.isTargetPointCustomized,
            targetParam = targetState.targetParam,
            targetLocked = targetState.targetLocked
        )
    }

    return SimpleAATTask(
        id = id.ifBlank { "aat-task" },
        waypoints = aatWaypoints,
        minimumTime = minimumTime,
        maximumTime = maximumTime
    )
}

fun SimpleAATTask.toCoreTask(
    waypoints: List<AATWaypoint> = this.waypoints
): Task {
    val minTimeSeconds = minimumTime.seconds.toDouble()
    val maxTimeSeconds = maximumTime?.seconds?.toDouble()
    return Task(
        id = id,
        waypoints = waypoints.map { waypoint ->
            val customParameters = mutableMapOf<String, Any>()
            AATWaypointCustomParams(
                radiusMeters = waypoint.assignedArea.radiusMeters,
                innerRadiusMeters = waypoint.assignedArea.innerRadiusMeters,
                outerRadiusMeters = waypoint.assignedArea.outerRadiusMeters,
                startAngleDegrees = waypoint.assignedArea.startAngleDegrees,
                endAngleDegrees = waypoint.assignedArea.endAngleDegrees,
                lineWidthMeters = waypoint.assignedArea.lineWidthMeters,
                targetLat = waypoint.targetPoint.latitude,
                targetLon = waypoint.targetPoint.longitude,
                isTargetPointCustomized = waypoint.isTargetPointCustomized
            ).applyTo(customParameters)
            TargetStateCustomParams(
                targetParam = waypoint.targetParam,
                targetLocked = waypoint.targetLocked,
                targetLat = waypoint.targetPoint.latitude,
                targetLon = waypoint.targetPoint.longitude
            ).applyTo(customParameters)
            AATTaskTimeCustomParams(
                minimumTimeSeconds = minTimeSeconds,
                maximumTimeSeconds = maxTimeSeconds
            ).applyTo(customParameters)
            TaskWaypoint(
                id = waypoint.id,
                title = waypoint.title,
                subtitle = waypoint.subtitle,
                lat = waypoint.lat,
                lon = waypoint.lon,
                role = when (waypoint.role) {
                    AATWaypointRole.START -> WaypointRole.START
                    AATWaypointRole.TURNPOINT -> WaypointRole.TURNPOINT
                    AATWaypointRole.FINISH -> WaypointRole.FINISH
                },
                customRadius = null,
                customRadiusMeters = waypoint.getAuthorityRadiusMeters(),
                customPointType = when (waypoint.role) {
                    AATWaypointRole.START -> waypoint.startPointType.name
                    AATWaypointRole.TURNPOINT -> waypoint.turnPointType.name
                    AATWaypointRole.FINISH -> waypoint.finishPointType.name
                },
                customParameters = customParameters
            )
        }
    )
}
