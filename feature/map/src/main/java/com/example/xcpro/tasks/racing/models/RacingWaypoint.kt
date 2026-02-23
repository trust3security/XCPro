package com.example.xcpro.tasks.racing.models

/**
 * Racing-specific waypoint model - COMPLETELY INDEPENDENT from shared models
 * Part of the task separation architecture to prevent cross-contamination
 *
 * This model is specific to Racing tasks and should NEVER be used by AAT or DHT modules
 */
data class RacingWaypoint(
    val id: String,
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val role: RacingWaypointRole,
    val startPointType: RacingStartPointType = RacingStartPointType.START_LINE,
    val finishPointType: RacingFinishPointType = RacingFinishPointType.FINISH_CYLINDER,
    val turnPointType: RacingTurnPointType = RacingTurnPointType.TURN_POINT_CYLINDER,
    val gateWidthMeters: Double, // canonical storage in meters
    // Keyhole-specific parameters
    val keyholeInnerRadiusMeters: Double = 500.0, // canonical storage in meters
    val keyholeAngle: Double = 90.0, // degrees, sector angle for keyhole (default 90 deg)
    // FAI Quadrant-specific parameters
    val faiQuadrantOuterRadiusMeters: Double = 10_000.0 // canonical storage in meters
) {
    companion object {
        private const val METERS_PER_KILOMETER = 1000.0
        private const val DEFAULT_KEYHOLE_INNER_RADIUS_METERS = 500.0
        private const val DEFAULT_FAI_QUADRANT_OUTER_RADIUS_METERS = 10_000.0

        /**
         * Create a Racing waypoint with standardized defaults
         * - Start waypoints: 10km default (matches PRD FAI standard)
         * - Finish waypoints: 3km default
         * - Turnpoints: 0.5km default for cylinders, 10km FAI quadrant radius, 10km for keyholes
         */
        fun createWithStandardizedDefaults(
            id: String,
            title: String,
            subtitle: String,
            lat: Double,
            lon: Double,
            role: RacingWaypointRole,
            startPointType: RacingStartPointType = RacingStartPointType.START_LINE,
            finishPointType: RacingFinishPointType = RacingFinishPointType.FINISH_CYLINDER,
            turnPointType: RacingTurnPointType = RacingTurnPointType.TURN_POINT_CYLINDER,
            customGateWidthMeters: Double? = null,
            keyholeInnerRadiusMeters: Double? = null,
            keyholeAngle: Double = 90.0,
            faiQuadrantOuterRadiusMeters: Double? = null,
            customGateWidth: Double? = null, // Allow override for user customizations
            keyholeInnerRadius: Double = DEFAULT_KEYHOLE_INNER_RADIUS_METERS / METERS_PER_KILOMETER,
            faiQuadrantOuterRadius: Double = DEFAULT_FAI_QUADRANT_OUTER_RADIUS_METERS / METERS_PER_KILOMETER
        ): RacingWaypoint {
            val standardizedGateWidthMeters = customGateWidthMeters
                ?: customGateWidth?.takeIf { it > 0.0 }?.times(METERS_PER_KILOMETER)
                ?: defaultGateWidthMeters(role = role, turnPointType = turnPointType)
            val resolvedKeyholeInnerRadiusMeters = keyholeInnerRadiusMeters
                ?: keyholeInnerRadius * METERS_PER_KILOMETER
            val resolvedFaiQuadrantOuterRadiusMeters = faiQuadrantOuterRadiusMeters
                ?: faiQuadrantOuterRadius * METERS_PER_KILOMETER

            return RacingWaypoint(
                id = id,
                title = title,
                subtitle = subtitle,
                lat = lat,
                lon = lon,
                role = role,
                startPointType = startPointType,
                finishPointType = finishPointType,
                turnPointType = turnPointType,
                gateWidthMeters = standardizedGateWidthMeters,
                keyholeInnerRadiusMeters = resolvedKeyholeInnerRadiusMeters,
                keyholeAngle = keyholeAngle,
                faiQuadrantOuterRadiusMeters = resolvedFaiQuadrantOuterRadiusMeters
            )
        }

        private fun defaultGateWidthMeters(
            role: RacingWaypointRole,
            turnPointType: RacingTurnPointType
        ): Double = when (role) {
            RacingWaypointRole.START -> 10_000.0
            RacingWaypointRole.FINISH -> 3_000.0
            RacingWaypointRole.TURNPOINT -> when (turnPointType) {
                RacingTurnPointType.KEYHOLE -> 10_000.0
                RacingTurnPointType.TURN_POINT_CYLINDER,
                RacingTurnPointType.FAI_QUADRANT -> 500.0
            }
        }
    }

    /**
     * Normalized sector angle: clamp floating noise (e.g., 89.999999) to a clean 90.0 when close.
     */
    val normalizedKeyholeAngle: Double
        get() = when {
            keyholeAngle.isNaN() -> 90.0
            kotlin.math.abs(keyholeAngle - 90.0) < 1e-2 -> 90.0
            else -> keyholeAngle
        }

    /**
     * Get the current point type display name based on role
     */
    val currentPointType: String get() = when (role) {
        RacingWaypointRole.START -> startPointType.displayName
        RacingWaypointRole.FINISH -> finishPointType.displayName
        RacingWaypointRole.TURNPOINT -> turnPointType.displayName
    }

}

/**
 * Racing-specific waypoint roles
 */
enum class RacingWaypointRole {
    START,
    TURNPOINT,
    FINISH
}

/**
 * Racing-specific start point types
 */
enum class RacingStartPointType(
    val displayName: String,
    val description: String
) {
    START_LINE("Start Line", "Perpendicular line to the first leg"),
    START_CYLINDER("Start Cylinder", "Cylinder around start waypoint"),
    FAI_START_SECTOR("FAI Start Sector", "90 D-shaped sector facing away from first leg")
}

/**
 * Racing-specific finish point types
 */
enum class RacingFinishPointType(
    val displayName: String,
    val description: String
) {
    FINISH_LINE("Finish Line", "Perpendicular line to the last leg"),
    FINISH_CYLINDER("Finish Cylinder", "Cylinder around finish waypoint")
}

/**
 * Racing-specific turn point types
 */
enum class RacingTurnPointType(
    val displayName: String,
    val description: String
) {
    TURN_POINT_CYLINDER("Cylinder", "Simple cylinder observation zone"),
    FAI_QUADRANT("FAI Quadrant", "90 sector with finite radius (default 10km)"),
    KEYHOLE("Keyhole", "0.5km cylinder + 10km sector combination")
}
