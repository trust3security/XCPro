package com.example.xcpro.tasks.aat.areas

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AssignedArea
import com.example.xcpro.tasks.aat.models.AreaGeometry
import kotlin.math.*

/**
 * Calculator for circular assigned areas in AAT tasks.
 * This class is completely autonomous and handles all circular area operations
 * without dependencies on other task modules.
 */
class CircleAreaCalculator {
    
    /**
     * Check if a point is inside a circular area
     *
     * @param point The point to test
     * @param center Center of the circular area
     * @param radius Radius in meters
     * @return true if point is inside or on the boundary
     */
    fun isInsideArea(point: AATLatLng, center: AATLatLng, radius: Double): Boolean {
        // ✅ CRASH PREVENTION: Validate input parameters
        if (radius <= 0) {
            println("❌ AAT ERROR: Invalid radius $radius for circle area check")
            return false
        }

        try {
            val distance = AATMathUtils.calculateDistance(point, center)
            return distance <= radius
        } catch (e: Exception) {
            println("❌ AAT ERROR: Exception in circle area check: ${e.message}")
            return false
        }
    }
    
    /**
     * Check if a point is inside a circular assigned area
     */
    fun isInsideArea(point: AATLatLng, area: AssignedArea): Boolean {
        return when (val geometry = area.geometry) {
            is AreaGeometry.Circle -> {
                isInsideArea(point, area.centerPoint, geometry.radius)
            }
            else -> false // Not a circular area
        }
    }
    
    /**
     * Find the nearest point on the boundary of a circular area from a given point.
     * If the point is inside the circle, returns the point on the boundary along
     * the line from center to the point.
     *
     * @param from The reference point
     * @param center Center of the circular area
     * @param radius Radius in meters
     * @return Nearest point on the circle boundary
     */
    fun nearestPointOnBoundary(from: AATLatLng, center: AATLatLng, radius: Double): AATLatLng {
        // ✅ CRASH PREVENTION: Validate radius
        if (radius <= 0) {
            println("❌ AAT ERROR: Invalid radius $radius for boundary calculation")
            return center // Return center as fallback
        }

        try {
            val distance = AATMathUtils.calculateDistance(from, center)

            if (distance < 0.001) {
                // Point is at the center, return arbitrary point on boundary (north)
                return AATMathUtils.calculatePointAtBearing(center, 0.0, radius)
            }

            val bearing = AATMathUtils.calculateBearing(center, from)
            return AATMathUtils.calculatePointAtBearing(center, bearing, radius)
        } catch (e: Exception) {
            println("❌ AAT ERROR: Exception in boundary calculation: ${e.message}")
            return center // Return center as safe fallback
        }
    }
    
    /**
     * Find the farthest point on the boundary of a circular area from a given point.
     * This is always the point on the opposite side of the circle from the reference point.
     * 
     * @param from The reference point
     * @param center Center of the circular area
     * @param radius Radius in meters
     * @return Farthest point on the circle boundary
     */
    fun farthestPointOnBoundary(from: AATLatLng, center: AATLatLng, radius: Double): AATLatLng {
        val distance = AATMathUtils.calculateDistance(from, center)
        
        if (distance < 0.001) {
            // Point is at the center, return arbitrary point on boundary (south)
            return AATMathUtils.calculatePointAtBearing(center, 180.0, radius)
        }
        
        val bearing = AATMathUtils.calculateBearing(center, from)
        val oppositeBearing = (bearing + 180.0) % 360.0
        return AATMathUtils.calculatePointAtBearing(center, oppositeBearing, radius)
    }
    
    /**
     * Calculate the credited fix for a flight track through a circular area.
     * This finds the point in the track that provides the best achievement of the area.
     * 
     * @param track List of track points (chronologically ordered)
     * @param area The assigned area to analyze
     * @return The credited fix point, or null if area not achieved
     */
    fun calculateCreditedFix(track: List<AATLatLng>, area: AssignedArea): AATLatLng? {
        val geometry = area.geometry
        if (geometry !is AreaGeometry.Circle) {
            return null // Not a circular area
        }
        
        if (track.isEmpty()) return null
        
        // Find all points inside the area
        val pointsInArea = track.filter { point ->
            isInsideArea(point, area.centerPoint, geometry.radius)
        }
        
        if (pointsInArea.isEmpty()) return null
        
        // For AAT, we want the point that is deepest into the area
        // This is the point closest to the center
        return pointsInArea.minByOrNull { point ->
            AATMathUtils.calculateDistance(point, area.centerPoint)
        }
    }
    
    /**
     * Calculate the optimal entry point for a circular area given approach and exit directions.
     * This finds the point on the boundary that minimizes the total path distance.
     * 
     * @param center Center of the circular area
     * @param radius Radius in meters
     * @param approachFrom Point flying from
     * @param exitTo Point flying to
     * @return Optimal entry/exit point on the boundary
     */
    fun calculateOptimalTouchPoint(
        center: AATLatLng, 
        radius: Double,
        approachFrom: AATLatLng, 
        exitTo: AATLatLng
    ): AATLatLng {
        // Calculate the bearing from approach to exit
        val approachBearing = AATMathUtils.calculateBearing(approachFrom, center)
        val exitBearing = AATMathUtils.calculateBearing(center, exitTo)
        
        // The optimal point is generally where the line from approach to exit
        // intersects the circle, but we need to handle cases where this line
        // doesn't intersect or passes through the center
        
        // For simplicity, we'll use the point that bisects the approach and exit bearings
        val averageBearing = calculateAverageBearing(approachBearing, exitBearing)
        return AATMathUtils.calculatePointAtBearing(center, averageBearing, radius)
    }
    
    /**
     * Calculate the two possible intersection points of a line with a circle.
     * 
     * @param lineStart Start point of the line
     * @param lineEnd End point of the line
     * @param center Center of the circle
     * @param radius Radius in meters
     * @return List of intersection points (0, 1, or 2 points)
     */
    fun calculateLineCircleIntersections(
        lineStart: AATLatLng,
        lineEnd: AATLatLng,
        center: AATLatLng,
        radius: Double
    ): List<AATLatLng> {
        // This is a complex calculation involving spherical geometry
        // For AAT purposes, we'll use a simplified approach
        
        val bearing = AATMathUtils.calculateBearing(lineStart, lineEnd)
        val distanceToCenter = AATMathUtils.calculateDistance(lineStart, center)
        val bearingToCenter = AATMathUtils.calculateBearing(lineStart, center)
        
        val crossTrackDistance = abs(
            AATMathUtils.calculateCrossTrackDistance(center, lineStart, lineEnd)
        )
        
        if (crossTrackDistance > radius) {
            // Line doesn't intersect circle
            return emptyList()
        }
        
        // Calculate along-track distance to intersection points
        val alongTrackToCenter = AATMathUtils.calculateAlongTrackDistance(center, lineStart, lineEnd)
        val halfChordLength = sqrt(radius * radius - crossTrackDistance * crossTrackDistance)
        
        val intersection1Distance = alongTrackToCenter - halfChordLength
        val intersection2Distance = alongTrackToCenter + halfChordLength
        
        val intersections = mutableListOf<AATLatLng>()
        
        if (intersection1Distance >= 0) {
            intersections.add(
                AATMathUtils.calculatePointAtBearing(lineStart, bearing, intersection1Distance)
            )
        }
        
        if (intersection2Distance >= 0 && intersection2Distance != intersection1Distance) {
            intersections.add(
                AATMathUtils.calculatePointAtBearing(lineStart, bearing, intersection2Distance)
            )
        }
        
        return intersections
    }
    
    /**
     * Calculate points on the circle boundary at regular intervals.
     * Useful for generating display polygons.
     * 
     * @param center Center of the circle
     * @param radius Radius in meters
     * @param numPoints Number of points to generate
     * @return List of points on the circle boundary
     */
    fun generateBoundaryPoints(
        center: AATLatLng, 
        radius: Double, 
        numPoints: Int = 36
    ): List<AATLatLng> {
        val points = mutableListOf<AATLatLng>()
        val angleStep = 360.0 / numPoints
        
        for (i in 0 until numPoints) {
            val bearing = i * angleStep
            val point = AATMathUtils.calculatePointAtBearing(center, bearing, radius)
            points.add(point)
        }
        
        return points
    }
    
    /**
     * Calculate the area of the circle in square kilometers
     */
    fun calculateAreaSizeKm2(radius: Double): Double {
        // ✅ CRASH PREVENTION: Validate radius
        if (radius <= 0) {
            println("❌ AAT ERROR: Invalid radius $radius for area calculation")
            return 0.0
        }

        try {
            val radiusKm = radius / 1000.0
            return PI * radiusKm * radiusKm
        } catch (e: Exception) {
            println("❌ AAT ERROR: Exception in area calculation: ${e.message}")
            return 0.0
        }
    }
    
    /**
     * Helper function to calculate the average of two bearings, handling wrap-around
     */
    private fun calculateAverageBearing(bearing1: Double, bearing2: Double): Double {
        val diff = AATMathUtils.angleDifference(bearing1, bearing2)
        return AATMathUtils.normalizeAngle(bearing1 + diff / 2.0)
    }
    
    /**
     * Calculate the minimum distance from a point to the circular area boundary
     * Returns negative if inside the area
     */
    fun distanceToBoundary(point: AATLatLng, center: AATLatLng, radius: Double): Double {
        val distanceToCenter = AATMathUtils.calculateDistance(point, center)
        return distanceToCenter - radius
    }
    
    /**
     * Check if a track segment passes through a circular area
     */
    fun doesTrackIntersectArea(
        trackStart: AATLatLng,
        trackEnd: AATLatLng,
        center: AATLatLng,
        radius: Double
    ): Boolean {
        // Check if either endpoint is inside
        if (isInsideArea(trackStart, center, radius) || 
            isInsideArea(trackEnd, center, radius)) {
            return true
        }
        
        // Check if track intersects the circle
        val intersections = calculateLineCircleIntersections(trackStart, trackEnd, center, radius)
        return intersections.isNotEmpty()
    }
}