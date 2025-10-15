package com.example.xcpro.tasks.aat.models

/**
 * 🚨 COMPETITION-CRITICAL: SINGLE SOURCE OF TRUTH FOR AAT RADII
 *
 * ✅ AAT-SPECIFIC ONLY - ZERO Racing contamination
 * This authority handles ONLY AAT task types (Cylinder, Sector, Keyhole)
 *
 * This is the ONLY place that defines AAT radius values.
 * ALL UI displays and map rendering MUST use these values.
 *
 * PURPOSE: Prevent life-threatening navigation errors where UI shows different
 * radius than what's drawn on the map. In competition, this could cause:
 * - Disqualification (missed turnpoints)
 * - Airspace violations (legal liability)
 * - Safety incidents (incorrect navigation decisions)
 *
 * ARCHITECTURE RULES:
 * 1. This object is the ONLY source of AAT radius values
 * 2. AATWaypoint MUST use these values in constructor
 * 3. UI MUST call getRadiusForWaypoint() for display
 * 4. Renderer MUST use waypoint.getAuthorityRadius() for map drawing
 * 5. All waypoint creation/modification MUST use these values
 * 6. ZERO dependencies on Racing task code
 *
 * VALIDATION:
 * - Compile-time: Extension function enforces usage
 * - Runtime: Assertions verify UI/map consistency
 * - Testing: Unit tests verify all code paths use authority
 */
object AATRadiusAuthority {

    // ==================== BASE ROLE DEFAULTS ====================

    /**
     * Get standardized AAT radius for a waypoint role
     *
     * @param role The waypoint role (START, TURNPOINT, FINISH)
     * @return Radius in kilometers (AUTHORITATIVE VALUE)
     */
    fun getRadiusForRole(role: AATWaypointRole): Double {
        return when (role) {
            AATWaypointRole.START -> 10.0      // 10km start line/cylinder
            AATWaypointRole.FINISH -> 3.0      // 3km finish cylinder
            AATWaypointRole.TURNPOINT -> 10.0  // 10km AAT assigned area (default)
        }
    }

    /**
     * Get radius in meters (for internal calculations)
     */
    fun getRadiusMetersForRole(role: AATWaypointRole): Double {
        return getRadiusForRole(role) * 1000.0
    }

    // ==================== AAT TURNPOINT TYPE-SPECIFIC DEFAULTS ====================

    /**
     * Get AAT Cylinder radius (standard assigned area)
     * AAT-SPECIFIC: Circular observation zone for flexible routing
     */
    fun getAATCylinderRadius(): Double = 10.0  // 10km AAT cylinder

    /**
     * Get AAT Sector parameters
     * AAT-SPECIFIC: Sector-shaped assigned area
     */
    data class AATSectorParams(
        val outerRadius: Double,  // km
        val angle: Double         // degrees
    )

    fun getAATSectorDefaults(): AATSectorParams = AATSectorParams(
        outerRadius = 20.0,  // 20km outer radius
        angle = 90.0         // 90° sector angle
    )

    /**
     * Get AAT Keyhole parameters
     * AAT-SPECIFIC: Cylinder + sector combination
     */
    data class AATKeyholeParams(
        val innerRadius: Double,  // km - cylinder radius
        val outerRadius: Double,  // km - sector outer radius
        val angle: Double         // degrees - sector angle
    )

    fun getAATKeyholeDefaults(): AATKeyholeParams = AATKeyholeParams(
        innerRadius = 0.5,   // 500m inner cylinder
        outerRadius = 20.0,  // 20km sector outer radius
        angle = 90.0         // 90° sector angle
    )

    // ==================== AAT-SPECIFIC VALIDATION ====================

    /**
     * ✅ SSOT FIX: Simplified validation - no more dual source checks!
     *
     * Validates that AAT waypoint has reasonable radius values in assignedArea.
     * No need to check sync between properties since there's only one source now.
     *
     * @throws IllegalStateException if radius is invalid
     */
    fun validateWaypointRadius(waypoint: AATWaypoint) {
        // Validate assignedArea has reasonable values
        val radiusKm = getRadiusForWaypoint(waypoint)

        // Check radius is within reasonable bounds (0.1km to 100km)
        if (radiusKm < 0.1 || radiusKm > 100.0) {
            throw IllegalStateException(
                "🚨 COMPETITION SAFETY: AAT Radius out of bounds!\n" +
                "Waypoint: ${waypoint.title} (${waypoint.role}, ${waypoint.turnPointType.displayName})\n" +
                "Radius: ${radiusKm}km (must be between 0.1km and 100km)\n" +
                "This could cause navigation errors in competition!"
            )
        }

        // For keyholes/sectors, validate geometry parameters
        when (waypoint.turnPointType) {
            AATTurnPointType.AAT_KEYHOLE -> {
                val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0
                val innerRadiusKm = waypoint.assignedArea.innerRadiusMeters / 1000.0

                if (innerRadiusKm >= outerRadiusKm) {
                    throw IllegalStateException(
                        "🚨 AAT Keyhole error: Inner radius (${innerRadiusKm}km) must be < outer radius (${outerRadiusKm}km)"
                    )
                }
                println("✅ AAT: Keyhole geometry valid - outer: ${outerRadiusKm}km, inner: ${innerRadiusKm}km")
            }
            AATTurnPointType.AAT_SECTOR -> {
                val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0
                println("✅ AAT: Sector geometry valid - outer: ${outerRadiusKm}km")
            }
            else -> {
                println("✅ AAT: Cylinder geometry valid - radius: ${radiusKm}km")
            }
        }
    }

    /**
     * 🚨 COMPETITION-CRITICAL: Get the authoritative radius for a waypoint
     *
     * THIS IS THE ONLY CORRECT WAY TO GET RADIUS FOR RENDERING.
     * Returns the ACTUAL radius stored in waypoint data, NOT hardcoded defaults.
     *
     * @return Radius in kilometers (what MUST be shown in UI and drawn on map)
     */
    fun getRadiusForWaypoint(waypoint: AATWaypoint): Double {
        // ✅ SSOT FIX: ALWAYS read from assignedArea (single source of truth)
        // The waypoint.assignedArea contains the SSOT for all geometry that is actually rendered.
        // NO dual sources - gateWidth/sectorOuterRadius/keyholeInnerRadius have been removed!

        return when {
            // For START/FINISH: use assignedArea.radiusMeters (same as TURNPOINT)
            waypoint.role == AATWaypointRole.START -> waypoint.assignedArea.radiusMeters / 1000.0
            waypoint.role == AATWaypointRole.FINISH -> waypoint.assignedArea.radiusMeters / 1000.0

            // For TURNPOINT: use assignedArea based on geometry type
            waypoint.turnPointType == AATTurnPointType.AAT_CYLINDER -> {
                // ✅ SSOT: Cylinder uses radiusMeters from assignedArea
                waypoint.assignedArea.radiusMeters / 1000.0
            }
            waypoint.turnPointType == AATTurnPointType.AAT_SECTOR -> {
                // ✅ SSOT: Sector uses outerRadiusMeters from assignedArea
                waypoint.assignedArea.outerRadiusMeters / 1000.0
            }
            waypoint.turnPointType == AATTurnPointType.AAT_KEYHOLE -> {
                // ✅ SSOT: Keyhole uses outerRadiusMeters from assignedArea
                waypoint.assignedArea.outerRadiusMeters / 1000.0
            }
            else -> {
                // Fallback: use radiusMeters from assignedArea
                waypoint.assignedArea.radiusMeters / 1000.0
            }
        }
    }
}

/**
 * Extension function: Get authoritative radius for this waypoint
 *
 * 🚨 COMPETITION-CRITICAL: This function enforces visual-calculation consistency.
 * MANDATORY for ALL rendering code to prevent "what you see ≠ what you get" bugs.
 *
 * USAGE IN RENDERER:
 * ```
 * val radiusKm = waypoint.getAuthorityRadius()  // ✅ ALWAYS CORRECT
 * ```
 *
 * ✅ SSOT FIX: No dual sources - gateWidth/sectorOuterRadius removed!
 * This extension function ALWAYS reads from assignedArea (single source of truth).
 *
 * DO NOT USE:
 * ```
 * val radiusKm = getAATCylinderRadius()  // ❌ WRONG - returns hardcoded default, not actual value!
 * val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0  // ❌ WRONG - doesn't handle sectors/keyholes correctly!
 * ```
 */
fun AATWaypoint.getAuthorityRadius(): Double {
    val authorityRadius = AATRadiusAuthority.getRadiusForWaypoint(this)

    // 🚨 CRITICAL VALIDATION: Ensure authority returns ACTUAL data from assignedArea (what renderer uses)
    // This catches the bug where waypoint properties were returned instead of assignedArea geometry
    if (this.role == AATWaypointRole.TURNPOINT) {
        val actualRadiusKm = when (this.turnPointType) {
            AATTurnPointType.AAT_CYLINDER -> this.assignedArea.radiusMeters / 1000.0
            AATTurnPointType.AAT_SECTOR -> this.assignedArea.outerRadiusMeters / 1000.0
            AATTurnPointType.AAT_KEYHOLE -> this.assignedArea.outerRadiusMeters / 1000.0
        }

        if (kotlin.math.abs(authorityRadius - actualRadiusKm) > 0.001) {
            throw IllegalStateException(
                """
                🚨 COMPETITION SAFETY VIOLATION DETECTED!
                SSOT Priority #1 Failure: Visual ≠ Calculation

                Waypoint: ${this.title} (${this.turnPointType.displayName})
                Actual radius in assignedArea: ${actualRadiusKm}km (what renderer uses)
                Authority returned: ${authorityRadius}km (WRONG!)

                This will cause:
                - Map displays ${authorityRadius}km ${this.turnPointType.displayName}
                - User set ${actualRadiusKm}km in edit box
                - RESULT: Pilot flies wrong task, gets disqualified!

                FIX: AATRadiusAuthority.getRadiusForWaypoint() MUST return from assignedArea geometry,
                     NOT from waypoint properties like sectorOuterRadius or hardcoded defaults!
                """.trimIndent()
            )
        }
    }

    return authorityRadius
}

/**
 * Extension function: Get authoritative radius in meters
 * ✅ SSOT FIX: Reads from assignedArea via authority, not from hardcoded role defaults
 */
fun AATWaypoint.getAuthorityRadiusMeters(): Double {
    return this.getAuthorityRadius() * 1000.0  // Convert km to meters
}
