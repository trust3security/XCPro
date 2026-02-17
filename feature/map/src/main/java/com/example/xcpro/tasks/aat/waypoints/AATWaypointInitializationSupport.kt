package com.example.xcpro.tasks.aat.waypoints

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATRadiusAuthority
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.core.AATWaypointCustomParams
import java.time.Duration
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

            val radiusKm = AATRadiusAuthority.getRadiusForRole(aatRole)
            val radiusMeters = radiusKm * 1000.0

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
                isTargetPointCustomized = typedParams.isTargetPointCustomized
            )
        }

        return SimpleAATTask(
            id = UUID.randomUUID().toString(),
            waypoints = aatWaypoints,
            minimumTime = Duration.ofHours(3),
            maximumTime = Duration.ofHours(6)
        )
    }
}
