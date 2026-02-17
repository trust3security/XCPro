package com.example.xcpro.tasks.domain.engine

import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATFinishPointType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATRadiusAuthority
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.core.AATWaypointCustomParams
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole

internal object AATTaskWaypointCodec {

    fun normalizeWaypointsForAAT(waypoints: List<TaskWaypoint>): List<TaskWaypoint> {
        if (waypoints.isEmpty()) return emptyList()
        return waypoints.mapIndexed { index, waypoint ->
            val normalizedRole = when {
                waypoints.size == 1 -> WaypointRole.START
                index == 0 -> WaypointRole.START
                index == waypoints.lastIndex -> WaypointRole.FINISH
                else -> WaypointRole.TURNPOINT
            }
            val normalizedType = normalizePointType(waypoint.customPointType, normalizedRole)
            val defaultRadius = defaultRadiusKm(normalizedRole)
            val normalizedRadius = if (waypoint.role != normalizedRole) {
                defaultRadius
            } else {
                waypoint.customRadius?.takeIf { it > 0.0 } ?: defaultRadius
            }
            val typedParams = AATWaypointCustomParams.from(
                source = waypoint.customParameters,
                fallbackLat = waypoint.lat,
                fallbackLon = waypoint.lon,
                fallbackRadiusMeters = normalizedRadius * 1000.0
            )
            val normalizedParams = waypoint.customParameters.toMutableMap().apply {
                typedParams.applyTo(this)
            }
            waypoint.copy(
                role = normalizedRole,
                customPointType = normalizedType,
                customRadius = normalizedRadius,
                customParameters = normalizedParams
            )
        }
    }

    fun toAATWaypoint(waypoint: TaskWaypoint): AATWaypoint {
        val normalizedRole = when (waypoint.role) {
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
            (waypoint.customRadius?.takeIf { it > 0.0 } ?: AATRadiusAuthority.getRadiusForRole(normalizedRole)) * 1000.0
        val typedParams = AATWaypointCustomParams.from(
            source = waypoint.customParameters,
            fallbackLat = waypoint.lat,
            fallbackLon = waypoint.lon,
            fallbackRadiusMeters = fallbackRadiusMeters
        )

        val areaShape = when (normalizedRole) {
            AATWaypointRole.START -> {
                if (startType == AATStartPointType.AAT_START_LINE) AATAreaShape.LINE else AATAreaShape.CIRCLE
            }

            AATWaypointRole.FINISH -> {
                if (finishType == AATFinishPointType.AAT_FINISH_LINE) AATAreaShape.LINE else AATAreaShape.CIRCLE
            }

            AATWaypointRole.TURNPOINT -> {
                if (turnType == AATTurnPointType.AAT_SECTOR || turnType == AATTurnPointType.AAT_KEYHOLE) {
                    AATAreaShape.SECTOR
                } else {
                    AATAreaShape.CIRCLE
                }
            }
        }

        return AATWaypoint(
            id = waypoint.id,
            title = waypoint.title,
            subtitle = waypoint.subtitle,
            lat = waypoint.lat,
            lon = waypoint.lon,
            role = normalizedRole,
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
            isTargetPointCustomized = typedParams.isTargetPointCustomized
        )
    }

    private fun normalizePointType(customPointType: String?, role: WaypointRole): String {
        return when (role) {
            WaypointRole.START -> {
                val valid = setOf(
                    AATStartPointType.AAT_START_LINE.name,
                    AATStartPointType.AAT_START_CYLINDER.name,
                    AATStartPointType.AAT_START_SECTOR.name
                )
                if (customPointType in valid) customPointType!! else AATStartPointType.AAT_START_LINE.name
            }

            WaypointRole.FINISH -> {
                val valid = setOf(
                    AATFinishPointType.AAT_FINISH_CYLINDER.name,
                    AATFinishPointType.AAT_FINISH_LINE.name
                )
                if (customPointType in valid) customPointType!! else AATFinishPointType.AAT_FINISH_CYLINDER.name
            }

            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> {
                val valid = setOf(
                    AATTurnPointType.AAT_CYLINDER.name,
                    AATTurnPointType.AAT_SECTOR.name,
                    AATTurnPointType.AAT_KEYHOLE.name
                )
                if (customPointType in valid) customPointType!! else AATTurnPointType.AAT_CYLINDER.name
            }
        }
    }

    private fun defaultRadiusKm(role: WaypointRole): Double {
        val aatRole = when (role) {
            WaypointRole.START -> AATWaypointRole.START
            WaypointRole.FINISH -> AATWaypointRole.FINISH
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> AATWaypointRole.TURNPOINT
        }
        return AATRadiusAuthority.getRadiusForRole(aatRole)
    }
}
