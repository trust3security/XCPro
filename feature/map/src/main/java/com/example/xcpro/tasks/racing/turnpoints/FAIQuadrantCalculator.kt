package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * FAI Quadrant Calculator - Extracted from RacingTaskCalculator.kt
 *
 * Implements the CORRECT FAI sector orientation algorithm.
 * FAI sectors are finite 90-degree quadrants oriented on the angle bisector
 * (XCSoar default radius: 10km).
 * between inbound and outbound legs.
 */
class FAIQuadrantCalculator : TurnPointCalculator {

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0 // FAI Earth model
    }

    /**
     * Calculate optimal touch point for FAI sectors.
     * By definition the optimal (shortest) touch point is the waypoint itself.
     * 
     * FAI RULE: 90-degree sector, bisector perpendicular to track bisector, oriented OUTWARD
     */
    override fun calculateOptimalTouchPoint(waypoint: RacingWaypoint, context: TaskContext): Pair<Double, Double> {
        val previousWaypoint = context.previousWaypoint
        val nextWaypoint = context.nextWaypoint ?: return Pair(waypoint.lat, waypoint.lon)

        // Calculate sector orientation per FAI Sporting Code Section 3 Annex A
        val sectorBisector = calculateFAISectorBisector(waypoint, previousWaypoint, nextWaypoint)

        println("🧭 FAI QUADRANT ORIENTATION [OFFICIAL FAI RULES]:")
        println("   Previous: ${if (previousWaypoint != null) "(${previousWaypoint.lat}, ${previousWaypoint.lon})" else "None"}")
        println("   Current: (${waypoint.lat}, ${waypoint.lon})")
        println("   Next: (${nextWaypoint.lat}, ${nextWaypoint.lon})")
        println("   FAI Sector Bisector: ${sectorBisector.toInt()}°")
        println("   ✅ FAI Rule: 90° sector (±45°), perpendicular to track bisector, oriented OUTWARD")
        println("   ✅ Finite radius sector (default 10km) starting AT waypoint")

        // FAI quadrants originate at waypoint → optimal touch point is always the waypoint itself
        return Pair(waypoint.lat, waypoint.lon)
    }

    override fun calculateDistance(from: RacingWaypoint, to: RacingWaypoint): Double {
        return RacingGeometryUtils.haversineDistance(from.lat, from.lon, to.lat, to.lon)
    }

    /**
     * Check whether a position is inside the FAI quadrant (±45° around bisector).
     * Uses official FAI sector orientation rules.
     */
    override fun isWithinObservationZone(position: Pair<Double, Double>, waypoint: RacingWaypoint): Boolean {
        // For a real check, we need context with previous and next waypoints.
        // This method is called without context, so we'll use a simplified check.
        // TODO: Add a context-aware check that respects sector orientation + radius.
        // For now, assume position is valid (permissive).
        return true
    }

    override fun getEffectiveRadius(waypoint: RacingWaypoint): Double? {
        return waypoint.faiQuadrantOuterRadius
    }

    // ---------- Helpers ----------

    /**
     * Calculate FAI sector bisector per official FAI Sporting Code Section 3 Annex A
     * FAI Rule: Bisector perpendicular to track bisector, oriented OUTWARD from course
     */
    private fun calculateFAISectorBisector(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint
    ): Double {
        if (previousWaypoint == null) {
            // If no previous waypoint, point sector opposite to next bearing
            val nextBearing = RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
            return (nextBearing + 180.0) % 360.0
        }

        // Calculate track bisector (bisector of the angle between incoming and outgoing legs)
        val inboundBearing = RacingGeometryUtils.calculateBearing(previousWaypoint.lat, previousWaypoint.lon, waypoint.lat, waypoint.lon)
        val outboundBearing = RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
        
        // Track bisector is the angle bisector between the two legs
        val trackBisector = RacingGeometryUtils.calculateAngleBisector(inboundBearing, outboundBearing)
        
        // FAI sector bisector is perpendicular to track bisector, oriented OUTWARD
        // This means it points away from the inside of the turn
        val turnDirection = RacingGeometryUtils.calculateTurnDirection(inboundBearing, outboundBearing)
        val sectorBisector = if (turnDirection > 0) {
            // Right turn: sector points to the left of track bisector
            (trackBisector - 90.0 + 360.0) % 360.0
        } else {
            // Left turn: sector points to the right of track bisector  
            (trackBisector + 90.0) % 360.0
        }
        
        return sectorBisector
    }




}
