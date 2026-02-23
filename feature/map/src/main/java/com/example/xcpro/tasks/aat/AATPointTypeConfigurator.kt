package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.calculations.AATMathUtils

/**
 * AAT Point Type Configurator - Handles turnpoint type updates and geometry configuration
 *
 * Extracted from AATTaskManager.kt for file size compliance.
 * Contains complex geometry calculation logic for cylinders, sectors, and keyholes.
 *
 * STATELESS HELPER: All methods are pure functions (no internal state)
 * SSOT COMPLIANT: Uses AATMathUtils for all calculations
 */
internal class AATPointTypeConfigurator {

    /**
     * Update AAT waypoint point type and geometry
     *
     * Handles conversion between cylinder, sector, and keyhole geometries with proper
     * FAI-compliant orientation calculations.
     *
     * @param waypoint Current waypoint to update
     * @param allWaypoints All waypoints in task (for sector orientation calculation)
     * @param waypointIndex Index of waypoint in task
     * @param startType New start point type (if waypoint is START)
     * @param finishType New finish point type (if waypoint is FINISH)
     * @param turnType New turnpoint type (if waypoint is TURNPOINT)
     * @param gateWidthMeters Gate width in meters (for cylinders)
     * @param keyholeInnerRadiusMeters Inner radius in meters (for keyholes)
     * @param keyholeAngle Sector angle in degrees (for sectors/keyholes)
     * @param sectorOuterRadiusMeters Outer radius in meters (for sectors/keyholes)
     * @return Updated waypoint with new geometry, or null if index invalid
     */
    fun updateWaypointPointType(
        waypoint: AATWaypoint,
        allWaypoints: List<AATWaypoint>,
        waypointIndex: Int,
        startType: com.example.xcpro.tasks.aat.models.AATStartPointType?,
        finishType: com.example.xcpro.tasks.aat.models.AATFinishPointType?,
        turnType: com.example.xcpro.tasks.aat.models.AATTurnPointType?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        sectorOuterRadiusMeters: Double?
    ): AATWaypoint {

        //  SSOT FIX: Read current values from assignedArea (single source of truth)
        val currentGateWidthMeters = when (waypoint.assignedArea.shape) {
            AATAreaShape.CIRCLE -> waypoint.assignedArea.radiusMeters
            AATAreaShape.SECTOR -> waypoint.assignedArea.outerRadiusMeters
            else -> waypoint.assignedArea.radiusMeters
        }
        val currentKeyholeInnerRadiusMeters = waypoint.assignedArea.innerRadiusMeters
        val currentSectorOuterRadiusMeters = waypoint.assignedArea.outerRadiusMeters

        // Use UI-provided values or fall back to current assignedArea values
        val newKeyholeInnerRadiusMeters =
            (keyholeInnerRadiusMeters ?: currentKeyholeInnerRadiusMeters).let { inner ->
                if (inner > 0) inner else 500.0
        }
        val newKeyholeAngle = (keyholeAngle ?: 90.0).let { angle ->
            if (kotlin.math.abs(angle - 90.0) < 1e-2) 90.0 else angle
        }  // Default and clamp to a clean 90 when within tolerance
        val newSectorOuterRadiusMeters = sectorOuterRadiusMeters ?: currentSectorOuterRadiusMeters
        val newGateWidthMeters = gateWidthMeters ?: currentGateWidthMeters
        val newTurnType = turnType ?: waypoint.turnPointType

        //  BUG FIX: Convert TurnPointType to AssignedArea geometry
        val newAssignedArea = if (waypoint.role == AATWaypointRole.TURNPOINT && turnType != null) {
            when (turnType) {
                com.example.xcpro.tasks.aat.models.AATTurnPointType.AAT_CYLINDER -> {
                    // Cylinder: Use gateWidth as radius
                    AATAssignedArea(
                        shape = AATAreaShape.CIRCLE,
                        radiusMeters = newGateWidthMeters
                    )
                }
                com.example.xcpro.tasks.aat.models.AATTurnPointType.AAT_SECTOR -> {
                    //  FIXED: Proper FAI sector orientation - perpendicular to track bisector, outward
                    val sectorBearing = calculateSectorBearing(
                        waypoint, allWaypoints, waypointIndex
                    )
                    val halfAngle = newKeyholeAngle / 2.0
                    val startBearing = (sectorBearing - halfAngle + 360.0) % 360.0
                    val endBearing = (sectorBearing + halfAngle) % 360.0


                    AATAssignedArea(
                        shape = AATAreaShape.SECTOR,
                        radiusMeters = newSectorOuterRadiusMeters,
                        innerRadiusMeters = 0.0, // Full sector from center
                        outerRadiusMeters = newSectorOuterRadiusMeters,
                        startAngleDegrees = startBearing,
                        endAngleDegrees = endBearing
                    )
                }
                com.example.xcpro.tasks.aat.models.AATTurnPointType.AAT_KEYHOLE -> {
                    //  FIXED: Proper FAI keyhole orientation - perpendicular to track bisector, outward
                    val sectorBearing = calculateSectorBearing(
                        waypoint, allWaypoints, waypointIndex
                    )
                    val halfAngle = newKeyholeAngle / 2.0
                    val startBearing = (sectorBearing - halfAngle + 360.0) % 360.0
                    val endBearing = (sectorBearing + halfAngle) % 360.0


                    AATAssignedArea(
                        shape = AATAreaShape.SECTOR,
                        radiusMeters = newSectorOuterRadiusMeters,
                        innerRadiusMeters = newKeyholeInnerRadiusMeters, //  Inner cylinder
                        outerRadiusMeters = newSectorOuterRadiusMeters,
                        startAngleDegrees = startBearing,
                        endAngleDegrees = endBearing
                    )
                }
            }
        } else if (waypoint.role == AATWaypointRole.TURNPOINT) {
            //  BUG FIX: Update geometry parameters based on current shape type
            // When user changes radius/angle without changing turn type, must update correct fields
            when (waypoint.assignedArea.shape) {
                AATAreaShape.CIRCLE -> {
                    // Cylinder: Update radiusMeters only
                    waypoint.assignedArea.copy(
                        radiusMeters = newGateWidthMeters
                    )
                }
                AATAreaShape.SECTOR -> {
                    // Sector/Keyhole: Update outerRadiusMeters, innerRadiusMeters

                    waypoint.assignedArea.copy(
                        radiusMeters = newSectorOuterRadiusMeters,  // Update primary radius
                        outerRadiusMeters = newSectorOuterRadiusMeters,  //  FIX: Update outer radius
                        innerRadiusMeters = newKeyholeInnerRadiusMeters,  //  FIX: Update inner radius
                        // Keep existing angles (would need recalculation for orientation changes)
                        startAngleDegrees = waypoint.assignedArea.startAngleDegrees,
                        endAngleDegrees = waypoint.assignedArea.endAngleDegrees
                    )
                }
                else -> waypoint.assignedArea  // LINE/other shapes unchanged
            }
        } else {
            waypoint.assignedArea
        }

        //  RESET target point to center when turnpoint type changes (like creating new turnpoint)
        val resetTargetPoint = if (waypoint.role == AATWaypointRole.TURNPOINT && turnType != null && turnType != waypoint.turnPointType) {
            // Type changed - reset to center like a new turnpoint
            AATLatLng(waypoint.lat, waypoint.lon)
        } else {
            // Type didn't change - keep existing target point
            waypoint.targetPoint
        }

        //  SSOT FIX: Only update assignedArea (removed gateWidth, keyholeInnerRadius, etc.)
        val updatedWaypoint = waypoint.copy(
            startPointType = startType ?: waypoint.startPointType,
            finishPointType = finishType ?: waypoint.finishPointType,
            turnPointType = newTurnType,
            //  SSOT: assignedArea is the ONLY source of truth for geometry (no property sync)
            assignedArea = newAssignedArea,
            targetPoint = resetTargetPoint
        )


        return updatedWaypoint
    }

    /**
     * Calculate FAI-compliant sector bearing for a waypoint
     *
     * Sector bisector is PERPENDICULAR to track bisector, oriented OUTWARD from turn
     */
    private fun calculateSectorBearing(
        waypoint: AATWaypoint,
        allWaypoints: List<AATWaypoint>,
        waypointIndex: Int
    ): Double {
        val prevWaypoint = if (waypointIndex > 0) allWaypoints[waypointIndex - 1] else null
        val nextWaypoint = if (waypointIndex < allWaypoints.lastIndex) allWaypoints[waypointIndex + 1] else null

        return if (prevWaypoint != null && nextWaypoint != null) {
            val inboundBearing = AATMathUtils.calculateBearing(
                AATLatLng(prevWaypoint.lat, prevWaypoint.lon),
                AATLatLng(waypoint.lat, waypoint.lon)
            )
            val outboundBearing = AATMathUtils.calculateBearing(
                AATLatLng(waypoint.lat, waypoint.lon),
                AATLatLng(nextWaypoint.lat, nextWaypoint.lon)
            )

            // Calculate track bisector (angle bisector between legs)
            val trackBisector = calculateAngleBisector(inboundBearing, outboundBearing)

            //  FAI RULE: Sector bisector is PERPENDICULAR to track bisector, oriented OUTWARD
            val turnDirection = calculateTurnDirection(inboundBearing, outboundBearing)
            if (turnDirection > 0) {
                // Right turn: sector points left of track bisector
                (trackBisector - 90.0 + 360.0) % 360.0
            } else {
                // Left turn: sector points right of track bisector
                (trackBisector + 90.0) % 360.0
            }
        } else if (nextWaypoint != null) {
            // No previous waypoint: point sector opposite to next bearing
            val nextBearing = AATMathUtils.calculateBearing(
                AATLatLng(waypoint.lat, waypoint.lon),
                AATLatLng(nextWaypoint.lat, nextWaypoint.lon)
            )
            (nextBearing + 180.0) % 360.0
        } else {
            0.0 // Default to north
        }
    }

    /**
     * Calculate angle bisector between two bearings (FAI compliant)
     * Handles wrap-around at 360 degrees correctly
     */
    private fun calculateAngleBisector(bearing1: Double, bearing2: Double): Double {
        // Normalize bearings to 0-360 range
        val b1 = (bearing1 + 360.0) % 360.0
        val b2 = (bearing2 + 360.0) % 360.0

        // Calculate the difference between bearings
        val diff = (b2 - b1 + 360.0) % 360.0

        // The bisector is halfway between the two bearings
        // Handle the case where we need to go the "short way" around the circle
        val bisector = if (diff <= 180.0) {
            // Short way: add half the difference
            (b1 + diff / 2.0) % 360.0
        } else {
            // Long way: go the other direction
            (b1 - (360.0 - diff) / 2.0 + 360.0) % 360.0
        }

        return bisector
    }

    /**
     * Calculate turn direction for proper sector orientation
     * Returns positive for right turn, negative for left turn
     */
    private fun calculateTurnDirection(incomingBearing: Double, outgoingBearing: Double): Double {
        val angleDifference = (outgoingBearing - incomingBearing + 360.0) % 360.0
        return if (angleDifference <= 180.0) angleDifference else angleDifference - 360.0
    }
}
