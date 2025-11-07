package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * Configurable Keyhole Calculator - Enhanced flexibility for racing tasks
 *
 * Configurable Keyhole specification:
 * - Outer radius: user sets via gateWidth (sector outer radius)
 * - Inner radius: user sets via keyholeInnerRadius (cylinder radius)
 * - Angle: user sets via keyholeAngle (sector angle, default 90°)
 * - For racing optimization, evaluate both parts to find optimal touch point
 * - Much more flexible than fixed FAI implementation
 */
class KeyholeCalculator : TurnPointCalculator {
    
    companion object {
        private const val EARTH_RADIUS_KM = 6371.0 // FAI Earth model
    }
    
    override fun calculateOptimalTouchPoint(waypoint: RacingWaypoint, context: TaskContext): Pair<Double, Double> {
        val previousWaypoint = context.previousWaypoint
        val nextWaypoint = context.nextWaypoint ?: return Pair(waypoint.lat, waypoint.lon)
        
        // ✅ CONFIGURABLE: Use new flexible keyhole parameters
        val cylinderRadiusKm = waypoint.keyholeInnerRadius // Inner cylinder radius
        val sectorRadiusKm = waypoint.gateWidth // Outer sector radius
        val sectorAngleDegrees = waypoint.keyholeAngle // Configurable angle

        println("🔑 CONFIGURABLE KEYHOLE:")
        println("   Inner radius: ${cylinderRadiusKm}km (cylinder)")
        println("   Outer radius: ${sectorRadiusKm}km (sector)")
        println("   Angle: ${sectorAngleDegrees}° (was fixed 90°)")
        
        // Calculate optimal touch point for cylinder part
        val optimalCylinder = calculateOptimalCylinderTouchPoint(waypoint, previousWaypoint, nextWaypoint, cylinderRadiusKm)
        
        // Calculate optimal touch point for sector part (if sector provides shorter path)
        val optimalSector = calculateOptimalSectorTouchPoint(waypoint, previousWaypoint, nextWaypoint, sectorRadiusKm, sectorAngleDegrees)
        
        // Compare total distances and choose the shorter path
        val cylinderDistance = calculateTotalDistance(optimalCylinder, previousWaypoint, nextWaypoint)
        val sectorDistance = calculateTotalDistance(optimalSector, previousWaypoint, nextWaypoint)
        
        return if (cylinderDistance <= sectorDistance) {
            println("🔑 KEYHOLE: Using cylinder touch point (${String.format("%.2f", cylinderDistance)}km)")
            optimalCylinder
        } else {
            println("🔑 KEYHOLE: Using sector touch point (${String.format("%.2f", sectorDistance)}km)")  
            optimalSector
        }
    }
    
    override fun calculateDistance(from: RacingWaypoint, to: RacingWaypoint): Double {
        return RacingGeometryUtils.haversineDistance(from.lat, from.lon, to.lat, to.lon)
    }
    
    override fun isWithinObservationZone(position: Pair<Double, Double>, waypoint: RacingWaypoint): Boolean {
        // Check if within configurable cylinder part
        val distanceToCenter = RacingGeometryUtils.haversineDistance(position.first, position.second, waypoint.lat, waypoint.lon)
        val cylinderRadiusKm = waypoint.keyholeInnerRadius // Inner cylinder radius

        if (distanceToCenter <= cylinderRadiusKm) {
            return true
        }

        // Check if within configurable sector part
        // NOTE: This method lacks TaskContext needed for proper sector calculation
        // For now, assume positions beyond cylinder are valid if within outer radius
        // In practice, a context-aware version should be used
        val sectorRadiusKm = waypoint.gateWidth // Outer sector radius

        if (distanceToCenter <= sectorRadiusKm) {
            // TODO: Add proper sector angle validation when context is available
            return true
        }

        return false
    }
    
    override fun getEffectiveRadius(waypoint: RacingWaypoint): Double {
        // Return outer radius for map bounds calculation
        return waypoint.gateWidth // Outer sector radius
    }
    
    /**
     * Calculate optimal cylinder touch point for keyhole
     * Uses the same logic as cylinder turnpoints for the cylinder portion
     */
    private fun calculateOptimalCylinderTouchPoint(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint,
        radiusKm: Double
    ): Pair<Double, Double> {
        // Calculate optimal touch point using geometric approximation
        val fromPoint = if (previousWaypoint != null) {
            Pair(previousWaypoint.lat, previousWaypoint.lon)
        } else {
            Pair(waypoint.lat, waypoint.lon)
        }
        
        // Test points around cylinder to find minimum total distance
        var minTotalDistance = Double.MAX_VALUE
        var optimalPoint = Pair(waypoint.lat, waypoint.lon)
        
        for (angle in 0 until 360 step 10) {
            val testPoint = RacingGeometryUtils.calculateDestinationPoint(
                waypoint.lat, waypoint.lon,
                angle.toDouble(), // Already in degrees
                radiusKm * 1000.0 // Convert km to meters
            )
            
            val distFromPrev = RacingGeometryUtils.haversineDistance(fromPoint.first, fromPoint.second, testPoint.first, testPoint.second)
            val distToNext = RacingGeometryUtils.haversineDistance(testPoint.first, testPoint.second, nextWaypoint.lat, nextWaypoint.lon)
            val totalDistance = distFromPrev + distToNext
            
            if (totalDistance < minTotalDistance) {
                minTotalDistance = totalDistance
                optimalPoint = testPoint
            }
        }
        
        return optimalPoint
    }
    
    /**
     * Calculate optimal sector touch point for keyhole (configurable angle sector part)
     */
    private fun calculateOptimalSectorTouchPoint(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint,
        sectorRadiusKm: Double,
        sectorAngleDegrees: Double
    ): Pair<Double, Double> {
        if (previousWaypoint == null) {
            // No previous waypoint, use cylinder center
            return Pair(waypoint.lat, waypoint.lon)
        }
        
        // Calculate FAI sector orientation (same as FAI quadrants)
        val sectorBisector = calculateFAISectorBisector(waypoint, previousWaypoint, nextWaypoint)
        
        // Test points along configurable sector edge to find optimal entry
        var minTotalDistance = Double.MAX_VALUE
        var optimalPoint = Pair(waypoint.lat, waypoint.lon)

        val sectorSpan = sectorAngleDegrees / 2.0 // degrees each side of bisector
        val startAngle = (sectorBisector - sectorSpan) % 360.0
        val endAngle = (sectorBisector + sectorSpan) % 360.0
        
        // Debug logging for configurable sector
        println("🔑 CONFIGURABLE SECTOR:")
        println("   Angle: ${sectorAngleDegrees}° (±${sectorSpan}° from bisector)")
        println("   Start: ${startAngle.toInt()}°, End: ${endAngle.toInt()}°")
        println("   Bisector: ${sectorBisector.toInt()}°")

        // Test 20 points along sector edge
        for (i in 0..20) {
            val testAngle = startAngle + (endAngle - startAngle) * i / 20.0
            val testPoint = RacingGeometryUtils.calculateDestinationPoint(
                waypoint.lat, waypoint.lon,
                testAngle, // Already in degrees
                sectorRadiusKm * 1000.0 // Convert km to meters
            )

            val distFromPrev = RacingGeometryUtils.haversineDistance(previousWaypoint.lat, previousWaypoint.lon, testPoint.first, testPoint.second)
            val distToNext = RacingGeometryUtils.haversineDistance(testPoint.first, testPoint.second, nextWaypoint.lat, nextWaypoint.lon)
            val totalDistance = distFromPrev + distToNext

            if (totalDistance < minTotalDistance) {
                minTotalDistance = totalDistance
                optimalPoint = testPoint
            }
        }
        
        return optimalPoint
    }
    
    /**
     * Calculate total distance for a given touch point
     */
    private fun calculateTotalDistance(
        touchPoint: Pair<Double, Double>,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint
    ): Double {
        val distFromPrev = if (previousWaypoint != null) {
            RacingGeometryUtils.haversineDistance(previousWaypoint.lat, previousWaypoint.lon, touchPoint.first, touchPoint.second)
        } else 0.0
        
        val distToNext = RacingGeometryUtils.haversineDistance(touchPoint.first, touchPoint.second, nextWaypoint.lat, nextWaypoint.lon)
        
        return distFromPrev + distToNext
    }
    
    /**
     * Calculate FAI sector bisector (same algorithm as FAI quadrants)
     */
    private fun calculateFAISectorBisector(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint,
        nextWaypoint: RacingWaypoint
    ): Double {
        // Calculate track bisector
        val inboundBearing = RacingGeometryUtils.calculateBearing(previousWaypoint.lat, previousWaypoint.lon, waypoint.lat, waypoint.lon)
        val outboundBearing = RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
        
        val trackBisector = RacingGeometryUtils.calculateAngleBisector(inboundBearing, outboundBearing)
        
        // FAI sector bisector is perpendicular to track bisector, oriented OUTWARD
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
