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
    val gateWidth: Double, // km, for start line/cylinder and finish dimensions (also outer radius for keyhole) - MUST be provided with standardized defaults
    // Keyhole-specific parameters
    val keyholeInnerRadius: Double = 0.5, // km, inner cylinder radius for keyhole (default 0.5km)
    val keyholeAngle: Double = 90.0, // degrees, sector angle for keyhole (default 90°)
    // FAI Quadrant-specific parameters
    val faiQuadrantOuterRadius: Double = 20.0 // km, visual display radius for FAI quadrant (default 20km, math remains infinite)
) {
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

    /**
     * Get the effective radius/width for this waypoint based on its role and type
     */
    val effectiveRadius: Double get() = when (role) {
        RacingWaypointRole.START -> when (startPointType) {
            RacingStartPointType.START_LINE -> gateWidth // Line half-width
            RacingStartPointType.START_CYLINDER -> gateWidth // Cylinder radius
            RacingStartPointType.FAI_START_SECTOR -> gateWidth // Sector radius
        }
        RacingWaypointRole.FINISH -> when (finishPointType) {
            RacingFinishPointType.FINISH_LINE -> gateWidth // Line half-width
            RacingFinishPointType.FINISH_CYLINDER -> gateWidth // Cylinder radius
        }
        RacingWaypointRole.TURNPOINT -> when (turnPointType) {
            RacingTurnPointType.TURN_POINT_CYLINDER -> gateWidth
            RacingTurnPointType.FAI_QUADRANT -> faiQuadrantOuterRadius // Display radius (math calculations remain infinite)
            RacingTurnPointType.KEYHOLE -> gateWidth // Outer radius (sector part)
        }
    }

    companion object {
        /**
         * Create a Racing waypoint with standardized defaults
         * - Start waypoints: 1km default (matches PRD FAI standard)
         * - Finish waypoints: 3km default
         * - Turnpoints: 0.5km default for cylinders/FAI quadrants, 10km for keyholes
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
            customGateWidth: Double? = null, // Allow override for user customizations
            keyholeInnerRadius: Double = 0.5,
            keyholeAngle: Double = 90.0,
            faiQuadrantOuterRadius: Double = 20.0
        ): RacingWaypoint {
            val standardizedGateWidth = customGateWidth ?: when (role) {
                RacingWaypointRole.START -> 10.0   // 10km start lines/sectors/cylinders (FAI standard per PRD)
                RacingWaypointRole.FINISH -> 3.0   // 3km finish cylinders (standardized)
                RacingWaypointRole.TURNPOINT -> when (turnPointType) {
                    RacingTurnPointType.KEYHOLE -> 10.0  // 10km keyhole outer radius default
                    else -> 0.5 // 0.5km Racing turnpoint default for cylinder and FAI quadrant
                }
            }

            // DEBUG: Log the default value calculation to prove fix is working
            println("🏁 WAYPOINT CREATION DEBUG: role=$role, turnPointType=$turnPointType, customGateWidth=$customGateWidth, standardizedGateWidth=$standardizedGateWidth")

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
                gateWidth = standardizedGateWidth,
                keyholeInnerRadius = keyholeInnerRadius,
                keyholeAngle = keyholeAngle,
                faiQuadrantOuterRadius = faiQuadrantOuterRadius
            )
        }
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
    FAI_START_SECTOR("FAI Start Sector", "90° D-shaped sector facing away from first leg")
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
    FAI_QUADRANT("FAI Quadrant", "90° sector with infinite radius"),
    KEYHOLE("Keyhole", "0.5km cylinder + 10km sector combination")
}
