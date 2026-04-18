package com.trust3.xcpro.tasks

import com.trust3.xcpro.tasks.aat.models.AATRadiusAuthority
import com.trust3.xcpro.tasks.aat.models.AATWaypointRole
import com.trust3.xcpro.tasks.core.AATWaypointCustomParams
import com.trust3.xcpro.tasks.core.PersistedOzParams
import com.trust3.xcpro.tasks.core.RacingWaypointCustomParams
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.TaskWaypointParamKeys
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.domain.model.AnnularSectorOZ
import com.trust3.xcpro.tasks.domain.model.CylinderOZ
import com.trust3.xcpro.tasks.domain.model.KeyholeOZ
import com.trust3.xcpro.tasks.domain.model.LineOZ
import com.trust3.xcpro.tasks.domain.model.ObservationZone
import com.trust3.xcpro.tasks.domain.model.SectorOZ
import com.trust3.xcpro.tasks.domain.model.SegmentOZ
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal object TaskObservationZoneResolver {
    fun resolve(
        taskType: TaskType,
        waypoint: TaskWaypoint,
        role: WaypointRole
    ): ObservationZone {
        val explicitType = (waypoint.customParameters[TaskWaypointParamKeys.OZ_TYPE] as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.US)
        val explicitParams = parsePersistedOzParams(waypoint.customParameters[TaskWaypointParamKeys.OZ_PARAMS])
        val inferredType = explicitType ?: inferOzType(taskType, role, waypoint.customPointType)
        val fallback = buildOzFallback(taskType, role, waypoint)
        return zoneFromType(inferredType, explicitParams, fallback)
    }

    private data class OzFallback(
        val radiusMeters: Double,
        val outerRadiusMeters: Double,
        val innerRadiusMeters: Double,
        val angleDeg: Double,
        val lengthMeters: Double,
        val widthMeters: Double
    )

    private fun inferOzType(
        taskType: TaskType,
        role: WaypointRole,
        customPointType: String?
    ): String {
        val normalized = customPointType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.US)
            ?: return defaultOzType(taskType, role)

        return when {
            normalized.contains("KEYHOLE") -> "KEYHOLE"
            normalized.contains("ANNULAR") -> "ANNULAR_SECTOR"
            normalized.contains("LINE") -> "LINE"
            normalized.contains("SECTOR") -> {
                if (taskType == TaskType.AAT && (role == WaypointRole.TURNPOINT || role == WaypointRole.OPTIONAL)) {
                    "SEGMENT"
                } else {
                    "SECTOR"
                }
            }
            normalized.contains("QUADRANT") -> "SECTOR"
            normalized.contains("CYLINDER") -> "CYLINDER"
            else -> defaultOzType(taskType, role)
        }
    }

    private fun defaultOzType(taskType: TaskType, role: WaypointRole): String = when (role) {
        WaypointRole.START -> "LINE"
        WaypointRole.FINISH -> "CYLINDER"
        WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> {
            if (taskType == TaskType.AAT) "SEGMENT" else "CYLINDER"
        }
    }

    private fun buildOzFallback(
        taskType: TaskType,
        role: WaypointRole,
        waypoint: TaskWaypoint
    ): OzFallback {
        val fallbackRadiusMeters = waypoint.resolvedCustomRadiusMeters()?.takeIf { it > 0.0 }
            ?: defaultRadiusMeters(taskType, role)

        val roleDefaults = when (role) {
            WaypointRole.START -> OzFallback(
                radiusMeters = fallbackRadiusMeters,
                outerRadiusMeters = fallbackRadiusMeters,
                innerRadiusMeters = max(1.0, fallbackRadiusMeters / 2.0),
                angleDeg = 90.0,
                lengthMeters = 1000.0,
                widthMeters = 200.0
            )
            WaypointRole.FINISH -> OzFallback(
                radiusMeters = fallbackRadiusMeters,
                outerRadiusMeters = fallbackRadiusMeters,
                innerRadiusMeters = max(1.0, fallbackRadiusMeters / 2.0),
                angleDeg = 90.0,
                lengthMeters = 1000.0,
                widthMeters = 200.0
            )
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> {
                if (taskType == TaskType.AAT) {
                    val aatParams = AATWaypointCustomParams.from(
                        source = waypoint.customParameters,
                        fallbackLat = waypoint.lat,
                        fallbackLon = waypoint.lon,
                        fallbackRadiusMeters = fallbackRadiusMeters
                    )
                    val outer = aatParams.outerRadiusMeters.takeIf { it > 0.0 } ?: fallbackRadiusMeters
                    val inner = aatParams.innerRadiusMeters.takeIf { it > 0.0 } ?: max(1.0, outer / 2.0)
                    val computedAngle = abs(aatParams.endAngleDegrees - aatParams.startAngleDegrees)
                    OzFallback(
                        radiusMeters = fallbackRadiusMeters,
                        outerRadiusMeters = outer,
                        innerRadiusMeters = if (inner < outer) inner else max(1.0, outer / 2.0),
                        angleDeg = if (computedAngle > 0.0) computedAngle else 90.0,
                        lengthMeters = 1000.0,
                        widthMeters = 200.0
                    )
                } else {
                    val racingParams = RacingWaypointCustomParams.from(waypoint.customParameters)
                    val outerRadiusMeters = waypoint.resolvedCustomRadiusMeters()?.takeIf { it > 0.0 }
                        ?: racingParams.faiQuadrantOuterRadiusMeters.coerceAtLeast(1.0)
                    OzFallback(
                        radiusMeters = fallbackRadiusMeters,
                        outerRadiusMeters = outerRadiusMeters,
                        innerRadiusMeters = racingParams.keyholeInnerRadiusMeters.coerceAtLeast(1.0),
                        angleDeg = racingParams.keyholeAngle.coerceAtLeast(1.0),
                        lengthMeters = 1000.0,
                        widthMeters = 200.0
                    )
                }
            }
        }

        val safeInner = if (roleDefaults.innerRadiusMeters < roleDefaults.outerRadiusMeters) {
            roleDefaults.innerRadiusMeters
        } else {
            max(1.0, roleDefaults.outerRadiusMeters / 2.0)
        }

        return roleDefaults.copy(innerRadiusMeters = safeInner)
    }

    private fun defaultRadiusMeters(taskType: TaskType, role: WaypointRole): Double {
        if (taskType == TaskType.AAT) {
            val aatRole = when (role) {
                WaypointRole.START -> AATWaypointRole.START
                WaypointRole.FINISH -> AATWaypointRole.FINISH
                WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> AATWaypointRole.TURNPOINT
            }
            return AATRadiusAuthority.getRadiusMetersForRole(aatRole)
        }

        return when (role) {
            WaypointRole.START -> 10_000.0
            WaypointRole.FINISH -> 3_000.0
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> 500.0
        }
    }

    private fun parsePersistedOzParams(raw: Any?): PersistedOzParams? {
        val map = raw as? Map<*, *> ?: return null
        val params = mapOf(
            TaskWaypointParamKeys.RADIUS_METERS to mapNumber(map, TaskWaypointParamKeys.RADIUS_METERS),
            TaskWaypointParamKeys.OUTER_RADIUS_METERS to mapNumber(map, TaskWaypointParamKeys.OUTER_RADIUS_METERS),
            TaskWaypointParamKeys.INNER_RADIUS_METERS to mapNumber(map, TaskWaypointParamKeys.INNER_RADIUS_METERS),
            TaskWaypointParamKeys.OZ_ANGLE_DEG to mapNumber(map, TaskWaypointParamKeys.OZ_ANGLE_DEG),
            TaskWaypointParamKeys.OZ_LENGTH_METERS to mapNumber(map, TaskWaypointParamKeys.OZ_LENGTH_METERS),
            TaskWaypointParamKeys.OZ_WIDTH_METERS to mapNumber(map, TaskWaypointParamKeys.OZ_WIDTH_METERS)
        )
        if (params.values.none { it != null }) return null
        return PersistedOzParams.from(params)
    }

    private fun mapNumber(map: Map<*, *>, key: String): Double? {
        val value = map[key]
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun zoneFromType(
        ozType: String,
        explicitParams: PersistedOzParams?,
        fallback: OzFallback
    ): ObservationZone {
        val fallbackOuter = fallback.outerRadiusMeters.coerceAtLeast(1.0)
        val fallbackInner = fallback.innerRadiusMeters
            .coerceAtLeast(1.0)
            .coerceAtMost(max(1.0, fallbackOuter - 1.0))
        val radiusMeters = (
            explicitParams?.radiusMeters
                ?: explicitParams?.outerRadiusMeters
                ?: fallback.radiusMeters
            ).coerceAtLeast(1.0)
        val outerRadiusMeters = (
            explicitParams?.outerRadiusMeters
                ?: explicitParams?.radiusMeters
                ?: fallbackOuter
            ).coerceAtLeast(1.0)
        val innerRadiusCandidate = (
            explicitParams?.innerRadiusMeters
                ?: fallbackInner
            ).coerceAtLeast(1.0)
        val innerRadiusMeters = innerRadiusCandidate.coerceAtMost(max(1.0, outerRadiusMeters - 1.0))
        val angleDeg = (
            explicitParams?.angleDeg
                ?: fallback.angleDeg
            ).coerceAtLeast(1.0)
        val lengthMeters = (
            explicitParams?.lengthMeters
                ?: fallback.lengthMeters
            ).coerceAtLeast(1.0)
        val widthMeters = (
            explicitParams?.widthMeters
                ?: fallback.widthMeters
            ).coerceAtLeast(1.0)

        return when (ozType.uppercase(Locale.US)) {
            "LINE" -> LineOZ(lengthMeters = lengthMeters, widthMeters = widthMeters)
            "CYLINDER" -> CylinderOZ(radiusMeters = radiusMeters)
            "SECTOR" -> SectorOZ(radiusMeters = outerRadiusMeters, angleDeg = angleDeg)
            "SEGMENT" -> SegmentOZ(radiusMeters = outerRadiusMeters, angleDeg = angleDeg)
            "ANNULAR_SECTOR" -> AnnularSectorOZ(
                innerRadiusMeters = innerRadiusMeters,
                outerRadiusMeters = outerRadiusMeters,
                angleDeg = angleDeg
            )
            "KEYHOLE" -> KeyholeOZ(
                innerRadiusMeters = innerRadiusMeters,
                outerRadiusMeters = outerRadiusMeters,
                angleDeg = angleDeg
            )
            else -> CylinderOZ(radiusMeters = radiusMeters)
        }
    }
}
