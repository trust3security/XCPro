package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * Cylinder Calculator - Extracted from RacingTaskCalculator.kt
 * 
 * Implements cylinder turnpoint calculations with configurable radius.
 * Uses geometric optimization to find the optimal touch point on cylinder edge.
 */
class CylinderCalculator : TurnPointCalculator {

    override fun calculateOptimalTouchPoint(waypoint: RacingWaypoint, context: TaskContext): Pair<Double, Double> {
        val previousWaypoint = context.previousWaypoint
        val nextWaypoint = context.nextWaypoint ?: return Pair(waypoint.lat, waypoint.lon)

        val radiusMeters = waypoint.gateWidthMeters
        
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
    
    override fun calculateDistanceMeters(from: RacingWaypoint, to: RacingWaypoint): Double {
        return RacingGeometryUtils.haversineDistanceMeters(from.lat, from.lon, to.lat, to.lon)
    }
    
    override fun isWithinObservationZone(position: Pair<Double, Double>, waypoint: RacingWaypoint): Boolean {
        val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(position.first, position.second, waypoint.lat, waypoint.lon)
        return distanceMeters <= waypoint.gateWidthMeters
    }
    
    override fun getEffectiveRadiusMeters(waypoint: RacingWaypoint): Double {
        return waypoint.gateWidthMeters
    }
    
    /**
     * Calculate optimal cylinder entry point for finish
     */
    fun calculateOptimalEntryPoint(cylinder: RacingWaypoint, fromWaypoint: RacingWaypoint): Pair<Double, Double> {
        val radiusMeters = cylinder.gateWidthMeters

        // Calculate bearing from previous waypoint to cylinder center
        val bearing = RacingGeometryUtils.calculateBearing(fromWaypoint.lat, fromWaypoint.lon, cylinder.lat, cylinder.lon)
        
        // Entry point is on the near edge of the cylinder
        return RacingGeometryUtils.calculateDestinationPoint(
            cylinder.lat, cylinder.lon,
            bearing + 180.0, // Already in degrees
            radiusMeters
        )
    }
    
}
