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
 * - Angle: user sets via keyholeAngle (sector angle, default 90)
 * - For racing optimization, evaluate both parts to find optimal touch point
 * - Much more flexible than fixed FAI implementation
 */
class KeyholeCalculator : TurnPointCalculator {

    override fun calculateOptimalTouchPoint(waypoint: RacingWaypoint, context: TaskContext): Pair<Double, Double> {
        val previousWaypoint = context.previousWaypoint
        val nextWaypoint = context.nextWaypoint ?: return Pair(waypoint.lat, waypoint.lon)

        //  CONFIGURABLE: Use new flexible keyhole parameters
        val cylinderRadiusMeters = waypoint.keyholeInnerRadiusMeters
        val sectorRadiusMeters = waypoint.gateWidthMeters
        // Clamp tiny floating error (89.9999...) to a clean degree value
        val sectorAngleDegrees = waypoint.keyholeAngle.let { angle ->
            if (abs(angle - 90.0) < 1e-3) 90.0 else angle
        }

        
        // Calculate optimal touch point for cylinder part
        val optimalCylinder = calculateOptimalCylinderTouchPoint(waypoint, previousWaypoint, nextWaypoint, cylinderRadiusMeters)
        
        // Calculate optimal touch point for sector part (if sector provides shorter path)
        val optimalSector = calculateOptimalSectorTouchPoint(waypoint, previousWaypoint, nextWaypoint, sectorRadiusMeters, sectorAngleDegrees)
        
        // Compare total distances and choose the shorter path
        val cylinderDistance = calculateTotalDistance(optimalCylinder, previousWaypoint, nextWaypoint)
        val sectorDistance = calculateTotalDistance(optimalSector, previousWaypoint, nextWaypoint)
        
        return if (cylinderDistance <= sectorDistance) {
            optimalCylinder
        } else {
            optimalSector
        }
    }
    
    override fun calculateDistanceMeters(from: RacingWaypoint, to: RacingWaypoint): Double {
        return RacingGeometryUtils.haversineDistanceMeters(from.lat, from.lon, to.lat, to.lon)
    }
    
    override fun isWithinObservationZone(position: Pair<Double, Double>, waypoint: RacingWaypoint): Boolean {
        // Check if within configurable cylinder part
        val distanceToCenterMeters = RacingGeometryUtils.haversineDistanceMeters(position.first, position.second, waypoint.lat, waypoint.lon)
        val cylinderRadiusMeters = waypoint.keyholeInnerRadiusMeters

        if (distanceToCenterMeters <= cylinderRadiusMeters) {
            return true
        }

        // Check if within configurable sector part
        // NOTE: This method lacks TaskContext needed for proper sector calculation
        // For now, assume positions beyond cylinder are valid if within outer radius
        // In practice, a context-aware version should be used
        val sectorRadiusMeters = waypoint.gateWidthMeters

        if (distanceToCenterMeters <= sectorRadiusMeters) {
            // Context-free fallback: validate against outer radius only.
            // Angle validation requires previous/next leg context.
            return true
        }

        return false
    }
    
    override fun getEffectiveRadiusMeters(waypoint: RacingWaypoint): Double {
        // Return outer radius for map bounds calculation
        return waypoint.gateWidthMeters
    }
    
    /**
     * Calculate optimal cylinder touch point for keyhole
     * Uses the same logic as cylinder turnpoints for the cylinder portion
     */
    private fun calculateOptimalCylinderTouchPoint(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint,
        radiusMeters: Double
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
                radiusMeters
            )

            val distFromPrev = RacingGeometryUtils.haversineDistanceMeters(fromPoint.first, fromPoint.second, testPoint.first, testPoint.second)
            val distToNext = RacingGeometryUtils.haversineDistanceMeters(testPoint.first, testPoint.second, nextWaypoint.lat, nextWaypoint.lon)
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
        sectorRadiusMeters: Double,
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
        val startAngle = (sectorBisector - sectorSpan + 360.0) % 360.0
        val endAngle = (sectorBisector + sectorSpan) % 360.0
        
        // Debug logging for configurable sector

        // Test 21 points along sector edge (handle wrap-around correctly)
        val steps = 20
        val angleRange = if (endAngle >= startAngle) {
            endAngle - startAngle
        } else {
            360.0 - startAngle + endAngle
        }

        for (i in 0..steps) {
            val fraction = i.toDouble() / steps
            val testAngle = (startAngle + angleRange * fraction) % 360.0
            val testPoint = RacingGeometryUtils.calculateDestinationPoint(
                waypoint.lat, waypoint.lon,
                testAngle, // Already in degrees
                sectorRadiusMeters
            )

            val distFromPrev = RacingGeometryUtils.haversineDistanceMeters(previousWaypoint.lat, previousWaypoint.lon, testPoint.first, testPoint.second)
            val distToNext = RacingGeometryUtils.haversineDistanceMeters(testPoint.first, testPoint.second, nextWaypoint.lat, nextWaypoint.lon)
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
            RacingGeometryUtils.haversineDistanceMeters(previousWaypoint.lat, previousWaypoint.lon, touchPoint.first, touchPoint.second)
        } else 0.0

        val distToNext = RacingGeometryUtils.haversineDistanceMeters(touchPoint.first, touchPoint.second, nextWaypoint.lat, nextWaypoint.lon)
        
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
