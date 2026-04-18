package com.trust3.xcpro.tasks.aat.waypoints

import com.trust3.xcpro.common.waypoint.SearchWaypoint
import com.trust3.xcpro.tasks.aat.SimpleAATTask
import com.trust3.xcpro.tasks.aat.models.AATAreaShape
import com.trust3.xcpro.tasks.aat.models.AATAssignedArea
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATRadiusAuthority
import com.trust3.xcpro.tasks.aat.models.AATWaypoint
import com.trust3.xcpro.tasks.aat.models.AATWaypointRole
import com.trust3.xcpro.tasks.core.AATTaskTimeCustomParams
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.TargetStateCustomParams
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.core.AATWaypointCustomParams
import java.time.Duration
import java.util.Locale
import java.util.UUID

internal object AATWaypointInitializationSupport {

    fun initializeTask(waypoints: List<SearchWaypoint>): SimpleAATTask {
        val aatWaypoints = waypoints.mapIndexed { index, searchWaypoint ->
            val role = when {
                index == 0 -> AATWaypointRole.START
                index == waypoints.lastIndex -> AATWaypointRole.FINISH
                else -> AATWaypointRole.TURNPOINT
            }

            AATWaypoint(
                id = searchWaypoint.id,
                title = searchWaypoint.title,
                subtitle = searchWaypoint.subtitle,
                lat = searchWaypoint.lat,
                lon = searchWaypoint.lon,
                role = role,
                assignedArea = AATAssignedArea.createWithStandardizedDefaults(
                    shape = AATAreaShape.CIRCLE,
                    role = role
                )
            )
        }

        return SimpleAATTask(
            id = UUID.randomUUID().toString(),
            waypoints = aatWaypoints,
            minimumTime = Duration.ofHours(3),
            maximumTime = Duration.ofHours(6)
        )
    }

    fun initializeFromGenericWaypoints(genericWaypoints: List<TaskWaypoint>): SimpleAATTask {
        val aatWaypoints = genericWaypoints.map { genericWaypoint ->
            val aatRole = when (genericWaypoint.role) {
                WaypointRole.START -> AATWaypointRole.START
                WaypointRole.TURNPOINT -> AATWaypointRole.TURNPOINT
                WaypointRole.OPTIONAL -> AATWaypointRole.TURNPOINT
                WaypointRole.FINISH -> AATWaypointRole.FINISH
            }

            val radiusMeters = AATRadiusAuthority.getRadiusMetersForRole(aatRole)

            val aatShape = when (genericWaypoint.customPointType) {
                "CIRCLE" -> AATAreaShape.CIRCLE
                "SECTOR" -> AATAreaShape.SECTOR
                "LINE" -> AATAreaShape.LINE
                else -> AATAreaShape.CIRCLE
            }

            val typedParams = AATWaypointCustomParams.from(
                source = genericWaypoint.customParameters,
                fallbackLat = genericWaypoint.lat,
                fallbackLon = genericWaypoint.lon,
                fallbackRadiusMeters = radiusMeters
            )
            val targetState = TargetStateCustomParams.from(genericWaypoint.customParameters)

            AATWaypoint(
                id = genericWaypoint.id,
                title = genericWaypoint.title,
                subtitle = genericWaypoint.subtitle,
                lat = genericWaypoint.lat,
                lon = genericWaypoint.lon,
                role = aatRole,
                assignedArea = AATAssignedArea(
                    shape = aatShape,
                    radiusMeters = typedParams.radiusMeters,
                    innerRadiusMeters = typedParams.innerRadiusMeters,
                    outerRadiusMeters = typedParams.outerRadiusMeters,
                    startAngleDegrees = typedParams.startAngleDegrees,
                    endAngleDegrees = typedParams.endAngleDegrees,
                    lineWidthMeters = typedParams.lineWidthMeters
                ),
                targetPoint = AATLatLng(typedParams.targetLat, typedParams.targetLon),
                isTargetPointCustomized = typedParams.isTargetPointCustomized,
                targetParam = targetState.targetParam,
                targetLocked = targetState.targetLocked
            )
        }

        return SimpleAATTask(
            id = UUID.randomUUID().toString(),
            waypoints = aatWaypoints,
            minimumTime = Duration.ofHours(3),
            maximumTime = Duration.ofHours(6)
        )
    }

    fun initializeFromCoreTask(task: Task): SimpleAATTask {
        val hydrated = initializeFromGenericWaypoints(task.waypoints)
        val timeParams = AATTaskTimeCustomParams.from(
            source = task.waypoints.firstOrNull()?.customParameters.orEmpty(),
            fallbackMinimumTimeSeconds = Duration.ofHours(3).seconds.toDouble(),
            fallbackMaximumTimeSeconds = Duration.ofHours(6).seconds.toDouble()
        )
        return hydrated.copy(
            id = task.id.ifBlank { deterministicTaskIdFromTaskWaypoints(task.waypoints) },
            minimumTime = Duration.ofSeconds(timeParams.minimumTimeSeconds.toLong()),
            maximumTime = timeParams.maximumTimeSeconds?.toLong()?.let(Duration::ofSeconds)
        )
    }

    private fun deterministicTaskIdFromTaskWaypoints(waypoints: List<TaskWaypoint>): String {
        val fingerprint = buildString {
            waypoints.forEachIndexed { index, waypoint ->
                append(index)
                append('|')
                append(waypoint.id.trim())
                append('|')
                append(waypoint.title.trim())
                append('|')
                append(String.format(Locale.US, "%.6f", waypoint.lat))
                append('|')
                append(String.format(Locale.US, "%.6f", waypoint.lon))
                append('|')
                append(waypoint.role.name)
                append(';')
            }
        }
        val suffix = fingerprint.hashCode().toUInt().toString(16).padStart(8, '0')
        return "aat_$suffix"
    }
}
