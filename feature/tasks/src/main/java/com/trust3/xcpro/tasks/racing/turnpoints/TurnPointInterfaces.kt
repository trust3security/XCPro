package com.trust3.xcpro.tasks.racing.turnpoints

import com.trust3.xcpro.tasks.racing.models.RacingWaypoint

/**
 * Task context information needed for turnpoint calculations
 */
data class TaskContext(
    val waypointIndex: Int,
    val allWaypoints: List<RacingWaypoint>,
    val previousWaypoint: RacingWaypoint?,
    val nextWaypoint: RacingWaypoint?
)

/**
 * Interface for turnpoint mathematical calculations
 * Each turnpoint type implements its own geometry and optimal touch point logic
 */
interface TurnPointCalculator {
    /**
     * Calculate the optimal touch point for racing tasks
     * @param waypoint The turnpoint waypoint
     * @param context Task context with neighboring waypoints
     * @return Optimal touch point as (latitude, longitude)
     */
    fun calculateOptimalTouchPoint(waypoint: RacingWaypoint, context: TaskContext): Pair<Double, Double>

    /**
     * Calculate distance between two waypoints using this turnpoint's geometry
     * @param from Source waypoint
     * @param to Destination waypoint
     * @return Distance in meters
     */
    fun calculateDistanceMeters(from: RacingWaypoint, to: RacingWaypoint): Double
    
    /**
     * Check if a position is within this turnpoint's observation zone
     * @param position Position to check as (latitude, longitude)
     * @param waypoint The turnpoint waypoint
     * @return True if position is within observation zone
     */
    fun isWithinObservationZone(position: Pair<Double, Double>, waypoint: RacingWaypoint): Boolean
    
    /**
     * Get the effective radius for this turnpoint (null for unbounded sectors, if any)
     * @param waypoint The turnpoint waypoint
     * @return Radius in meters, or null when unbounded
     */
    fun getEffectiveRadiusMeters(waypoint: RacingWaypoint): Double?
}

/**
 * Interface for turnpoint visual representation
 * Each turnpoint type generates its own GeoJSON geometry
 */
interface TurnPointDisplay {
    /**
     * Generate GeoJSON geometry for map display
     * @param waypoint The turnpoint waypoint
     * @param context Task context with neighboring waypoints
     * @return GeoJSON Feature string for map rendering
     */
    fun generateVisualGeometry(waypoint: RacingWaypoint, context: TaskContext): String
    
    /**
     * Get the display radius for visualization
     * @param waypoint The turnpoint waypoint
     * @return Display radius in meters
     */
    fun getDisplayRadius(waypoint: RacingWaypoint): Double
    
    /**
     * Get the observation zone type for this turnpoint
     * @return Type identifier for properties
     */
    fun getObservationZoneType(): String
}
