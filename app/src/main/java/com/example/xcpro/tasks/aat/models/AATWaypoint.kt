package com.example.xcpro.tasks.aat.models

/**
 * AAT-specific waypoint model - COMPLETELY INDEPENDENT from shared models
 * ✅ SSOT FIX: Removed kotlin.math.* import (no longer needed after removing duplicate haversine)
 * Part of the task separation architecture to prevent cross-contamination
 *
 * This model is specific to AAT tasks and should NEVER be used by Racing or DHT modules
 */
data class AATWaypoint(
    val id: String,
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val role: AATWaypointRole,
    val assignedArea: AATAssignedArea,

    // NEW: Point type properties (matching Racing structure)
    val startPointType: AATStartPointType = AATStartPointType.AAT_START_LINE,
    val finishPointType: AATFinishPointType = AATFinishPointType.AAT_FINISH_CYLINDER,
    val turnPointType: AATTurnPointType = AATTurnPointType.AAT_CYLINDER,

    // NEW: Movable target point within assigned area
    val targetPoint: AATLatLng = AATLatLng(lat, lon), // Default to area center
    val isTargetPointCustomized: Boolean = false

    // ✅ SSOT FIX: Removed duplicate radius properties (gateWidth, keyholeInnerRadius, sectorOuterRadius)
    // All radius values now read from assignedArea via AATRadiusAuthority.getRadiusForWaypoint()
) {
    /**
     * Get the center point of this waypoint
     */
    val centerPoint: AATLatLng get() = AATLatLng(lat, lon)

    /**
     * Check if this is a start or finish point
     */
    val isStartOrFinish: Boolean get() = role == AATWaypointRole.START || role == AATWaypointRole.FINISH

    /**
     * Get current point type display name based on role
     */
    val currentPointType: String get() = when (role) {
        AATWaypointRole.START -> startPointType.displayName
        AATWaypointRole.FINISH -> finishPointType.displayName
        AATWaypointRole.TURNPOINT -> turnPointType.displayName
    }

    /**
     * Get distance from area center to target point
     * ✅ SSOT FIX: Use centralized AATMathUtils instead of duplicate haversine
     */
    val targetPointOffset: Double get() =
        com.example.xcpro.tasks.aat.calculations.AATMathUtils.calculateDistanceKm(
            lat, lon, targetPoint.latitude, targetPoint.longitude
        )

    /**
     * Check if target point is within assigned area bounds
     */
    fun isTargetPointValid(): Boolean {
        val distance = targetPointOffset
        return distance <= (assignedArea.radiusMeters / 1000.0) // Convert to km
    }

    /**
     * Get effective area radius in meters
     */
    val effectiveRadiusMeters: Double get() = when (assignedArea.shape) {
        AATAreaShape.CIRCLE -> assignedArea.radiusMeters
        AATAreaShape.SECTOR -> assignedArea.outerRadiusMeters
        AATAreaShape.LINE -> assignedArea.lineWidthMeters / 2.0
    }

    // ✅ SSOT FIX: Removed duplicate haversineDistance function
    // All distance calculations now use AATMathUtils.calculateDistanceKm()
}

/**
 * AAT-specific waypoint roles
 */
enum class AATWaypointRole {
    START,
    TURNPOINT,
    FINISH
}

/**
 * AAT-specific assigned area definition
 */
data class AATAssignedArea(
    val shape: AATAreaShape,
    val radiusMeters: Double = getStandardizedAATDefault(), // Standardized defaults
    val innerRadiusMeters: Double = 0.0, // For sectors (annulus)
    val outerRadiusMeters: Double = getStandardizedAATDefault(), // For sectors
    val startAngleDegrees: Double = 0.0, // For sectors
    val endAngleDegrees: Double = 90.0, // For sectors
    val lineWidthMeters: Double = 3000.0 // 3km standardized for start/finish lines
) {
    companion object {
        /**
         * Get standardized AAT defaults per TASK_TYPE_RULES.md:
         * - START: 10km line length
         * - FINISH: 3km cylinder radius
         * - TURNPOINT: 10km area radius
         */
        private fun getStandardizedAATDefault(): Double = 10000.0 // 10km default

        /**
         * Create AAT area with standardized defaults based on waypoint role
         */
        fun createWithStandardizedDefaults(
            shape: AATAreaShape,
            role: AATWaypointRole
        ): AATAssignedArea {
            return when (role) {
                AATWaypointRole.START -> AATAssignedArea(
                    shape = AATAreaShape.LINE,
                    radiusMeters = 10000.0, // 10km start line
                    lineWidthMeters = 10000.0 // 10km line width
                )
                AATWaypointRole.FINISH -> AATAssignedArea(
                    shape = AATAreaShape.CIRCLE,
                    radiusMeters = 3000.0, // 3km finish cylinder
                    outerRadiusMeters = 3000.0
                )
                AATWaypointRole.TURNPOINT -> AATAssignedArea(
                    shape = shape,
                    radiusMeters = 10000.0, // 10km AAT area
                    outerRadiusMeters = 10000.0
                )
            }
        }
    }
}

/**
 * AAT area shapes
 */
enum class AATAreaShape {
    CIRCLE,
    SECTOR,
    LINE
}

/**
 * AAT-specific coordinate representation
 */
data class AATLatLng(
    val latitude: Double,
    val longitude: Double
) {
    /**
     * Convert to radians for calculations
     */
    fun toRadians(): AATLatLng = AATLatLng(
        Math.toRadians(latitude),
        Math.toRadians(longitude)
    )
}
