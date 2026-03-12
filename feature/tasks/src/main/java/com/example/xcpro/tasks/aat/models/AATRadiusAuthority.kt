package com.example.xcpro.tasks.aat.models

/**
 * Single source of truth for AAT radius values.
 * Internal contracts are meters.
 */
object AATRadiusAuthority {
    private const val METERS_PER_KILOMETER = 1000.0

    data class AATSectorDefaults(
        val outerRadiusMeters: Double,
        val angleDegrees: Double
    )

    data class AATKeyholeDefaults(
        val innerRadiusMeters: Double,
        val outerRadiusMeters: Double,
        val angleDegrees: Double
    )

    fun getRadiusMetersForRole(role: AATWaypointRole): Double = when (role) {
        AATWaypointRole.START -> 10_000.0
        AATWaypointRole.FINISH -> 3_000.0
        AATWaypointRole.TURNPOINT -> 10_000.0
    }

    fun getAATCylinderRadiusMeters(): Double = 10_000.0

    fun getAATSectorDefaults(): AATSectorDefaults = AATSectorDefaults(
        outerRadiusMeters = 20_000.0,
        angleDegrees = 90.0
    )

    fun getAATKeyholeDefaults(): AATKeyholeDefaults = AATKeyholeDefaults(
        innerRadiusMeters = 500.0,
        outerRadiusMeters = 20_000.0,
        angleDegrees = 90.0
    )

    fun validateWaypointRadius(waypoint: AATWaypoint) {
        val radiusMeters = getRadiusMetersForWaypoint(waypoint)
        if (radiusMeters < 100.0 || radiusMeters > 100_000.0) {
            throw IllegalStateException(
                "AAT radius out of bounds for ${waypoint.title}: " +
                    "${radiusMeters / METERS_PER_KILOMETER}km " +
                    "(must be between 0.1km and 100km)"
            )
        }

        when (waypoint.turnPointType) {
            AATTurnPointType.AAT_KEYHOLE -> {
                val outerRadiusMeters = waypoint.assignedArea.outerRadiusMeters
                val innerRadiusMeters = waypoint.assignedArea.innerRadiusMeters
                if (innerRadiusMeters >= outerRadiusMeters) {
                    throw IllegalStateException(
                        "AAT keyhole invalid: inner radius ${innerRadiusMeters / METERS_PER_KILOMETER}km " +
                            "must be smaller than outer radius ${outerRadiusMeters / METERS_PER_KILOMETER}km"
                    )
                }
            }

            AATTurnPointType.AAT_CYLINDER,
            AATTurnPointType.AAT_SECTOR -> Unit
        }
    }

    fun getRadiusMetersForWaypoint(waypoint: AATWaypoint): Double = when {
        waypoint.role == AATWaypointRole.START -> waypoint.assignedArea.radiusMeters
        waypoint.role == AATWaypointRole.FINISH -> waypoint.assignedArea.radiusMeters
        waypoint.turnPointType == AATTurnPointType.AAT_CYLINDER -> waypoint.assignedArea.radiusMeters
        waypoint.turnPointType == AATTurnPointType.AAT_SECTOR -> waypoint.assignedArea.outerRadiusMeters
        waypoint.turnPointType == AATTurnPointType.AAT_KEYHOLE -> waypoint.assignedArea.outerRadiusMeters
        else -> waypoint.assignedArea.radiusMeters
    }
}

fun AATWaypoint.getAuthorityRadiusMeters(): Double {
    return AATRadiusAuthority.getRadiusMetersForWaypoint(this)
}
