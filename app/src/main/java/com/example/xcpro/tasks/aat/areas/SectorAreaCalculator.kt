package com.example.xcpro.tasks.aat.areas

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AssignedArea
import com.example.xcpro.tasks.aat.models.AreaGeometry
import kotlin.math.*

/**
 * Calculator for sector assigned areas in AAT tasks.
 * This class is completely autonomous and handles all sector area operations
 * without dependencies on other task modules.
 * 
 * A sector is defined by:
 * - Center point
 * - Inner radius (optional, null means sector starts from center)
 * - Outer radius 
 * - Start bearing (degrees from north)
 * - End bearing (degrees from north)
 */
class SectorAreaCalculator {
    
    /**
     * Check if a point is inside a sector area
     * 
     * @param point The point to test
     * @param center Center of the sector
     * @param innerRadius Inner radius in meters (null for full sector from center)
     * @param outerRadius Outer radius in meters
     * @param startBearing Start bearing in degrees (0-360, 0 = north)
     * @param endBearing End bearing in degrees (0-360, 0 = north)
     * @return true if point is inside the sector
     */
    fun isInsideArea(
        point: AATLatLng, 
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): Boolean {
        val distance = AATMathUtils.calculateDistance(point, center)
        
        // Check radial distance constraints
        if (distance > outerRadius) return false
        if (innerRadius != null && distance < innerRadius) return false
        
        // Check angular constraints
        val bearingToPoint = AATMathUtils.calculateBearing(center, point)
        return AATMathUtils.isAngleBetween(bearingToPoint, startBearing, endBearing)
    }
    
    /**
     * Check if a point is inside a sector assigned area
     */
    fun isInsideArea(point: AATLatLng, area: AssignedArea): Boolean {
        return when (val geometry = area.geometry) {
            is AreaGeometry.Sector -> {
                isInsideArea(
                    point, area.centerPoint, geometry.innerRadius, geometry.outerRadius,
                    geometry.startBearing, geometry.endBearing
                )
            }
            else -> false // Not a sector area
        }
    }
    
    /**
     * Find the nearest point on the boundary of a sector area from a given point.
     * The boundary consists of:
     * - Inner arc (if innerRadius is not null)
     * - Outer arc
     * - Two radial lines connecting inner and outer arcs
     * 
     * @param from The reference point
     * @param center Center of the sector
     * @param innerRadius Inner radius in meters (null for full sector)
     * @param outerRadius Outer radius in meters
     * @param startBearing Start bearing in degrees
     * @param endBearing End bearing in degrees
     * @return Nearest point on the sector boundary
     */
    fun nearestPointOnBoundary(
        from: AATLatLng,
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): AATLatLng {
        val distanceToCenter = AATMathUtils.calculateDistance(from, center)
        val bearingToFrom = AATMathUtils.calculateBearing(center, from)
        
        // Check if the point's bearing is within the sector's angular range
        val isInSectorAngle = AATMathUtils.isAngleBetween(bearingToFrom, startBearing, endBearing)
        
        if (isInSectorAngle) {
            // Point is within the angular range of the sector
            when {
                distanceToCenter <= (innerRadius ?: 0.0) -> {
                    // Point is inside inner radius (or at center if no inner radius)
                    return if (innerRadius != null) {
                        AATMathUtils.calculatePointAtBearing(center, bearingToFrom, innerRadius)
                    } else {
                        // No inner radius, nearest point is on outer boundary
                        AATMathUtils.calculatePointAtBearing(center, bearingToFrom, outerRadius)
                    }
                }
                distanceToCenter >= outerRadius -> {
                    // Point is outside outer radius
                    return AATMathUtils.calculatePointAtBearing(center, bearingToFrom, outerRadius)
                }
                else -> {
                    // Point is within the radial range, find nearest boundary
                    val distanceToInner = innerRadius?.let { distanceToCenter - it } ?: Double.MAX_VALUE
                    val distanceToOuter = outerRadius - distanceToCenter
                    
                    return if (innerRadius != null && distanceToInner < distanceToOuter) {
                        AATMathUtils.calculatePointAtBearing(center, bearingToFrom, innerRadius)
                    } else {
                        AATMathUtils.calculatePointAtBearing(center, bearingToFrom, outerRadius)
                    }
                }
            }
        } else {
            // Point is outside the angular range, find nearest radial line
            val distanceToStart = abs(AATMathUtils.angleDifference(bearingToFrom, startBearing))
            val distanceToEnd = abs(AATMathUtils.angleDifference(bearingToFrom, endBearing))
            
            val nearestBearing = if (distanceToStart < distanceToEnd) startBearing else endBearing
            
            // Project onto the nearest radial line
            val clampedRadius = when {
                distanceToCenter < (innerRadius ?: 0.0) -> innerRadius ?: outerRadius
                distanceToCenter > outerRadius -> outerRadius
                else -> distanceToCenter
            }
            
            return AATMathUtils.calculatePointAtBearing(center, nearestBearing, clampedRadius)
        }
    }
    
    /**
     * Find the farthest point on the boundary of a sector area from a given point.
     * 
     * @param from The reference point
     * @param center Center of the sector
     * @param innerRadius Inner radius in meters (null for full sector)
     * @param outerRadius Outer radius in meters
     * @param startBearing Start bearing in degrees
     * @param endBearing End bearing in degrees
     * @return Farthest point on the sector boundary
     */
    fun farthestPointOnBoundary(
        from: AATLatLng,
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): AATLatLng {
        // Generate candidate points on the boundary and find the farthest
        val candidates = mutableListOf<AATLatLng>()
        
        // Add points on outer arc
        val numOuterPoints = 20
        val sectorSpan = if (endBearing >= startBearing) {
            endBearing - startBearing
        } else {
            360 - startBearing + endBearing
        }
        
        for (i in 0..numOuterPoints) {
            val fraction = i.toDouble() / numOuterPoints
            val bearing = if (endBearing >= startBearing) {
                startBearing + fraction * (endBearing - startBearing)
            } else {
                AATMathUtils.normalizeAngle(startBearing + fraction * sectorSpan)
            }
            candidates.add(AATMathUtils.calculatePointAtBearing(center, bearing, outerRadius))
        }
        
        // Add points on inner arc (if exists)
        if (innerRadius != null) {
            for (i in 0..numOuterPoints) {
                val fraction = i.toDouble() / numOuterPoints
                val bearing = if (endBearing >= startBearing) {
                    startBearing + fraction * (endBearing - startBearing)
                } else {
                    AATMathUtils.normalizeAngle(startBearing + fraction * sectorSpan)
                }
                candidates.add(AATMathUtils.calculatePointAtBearing(center, bearing, innerRadius))
            }
        }
        
        // Add radial line endpoints
        val startRadiusOuter = AATMathUtils.calculatePointAtBearing(center, startBearing, outerRadius)
        val endRadiusOuter = AATMathUtils.calculatePointAtBearing(center, endBearing, outerRadius)
        candidates.add(startRadiusOuter)
        candidates.add(endRadiusOuter)
        
        if (innerRadius != null) {
            val startRadiusInner = AATMathUtils.calculatePointAtBearing(center, startBearing, innerRadius)
            val endRadiusInner = AATMathUtils.calculatePointAtBearing(center, endBearing, innerRadius)
            candidates.add(startRadiusInner)
            candidates.add(endRadiusInner)
        }
        
        // Find the candidate with maximum distance
        return candidates.maxByOrNull { candidate ->
            AATMathUtils.calculateDistance(from, candidate)
        } ?: candidates.first()
    }
    
    /**
     * Calculate the credited fix for a flight track through a sector area.
     * This finds the point in the track that provides the best achievement of the area.
     * 
     * @param track List of track points (chronologically ordered)
     * @param area The assigned area to analyze
     * @return The credited fix point, or null if area not achieved
     */
    fun calculateCreditedFix(track: List<AATLatLng>, area: AssignedArea): AATLatLng? {
        val geometry = area.geometry
        if (geometry !is AreaGeometry.Sector) {
            return null // Not a sector area
        }
        
        if (track.isEmpty()) return null
        
        // Find all points inside the area
        val pointsInArea = track.filter { point ->
            isInsideArea(point, area.centerPoint, geometry.innerRadius, geometry.outerRadius,
                        geometry.startBearing, geometry.endBearing)
        }
        
        if (pointsInArea.isEmpty()) return null
        
        // For AAT sectors, we want the point that maximizes the distance flown
        // This is typically the point farthest from the center (but still in area)
        return pointsInArea.maxByOrNull { point ->
            AATMathUtils.calculateDistance(point, area.centerPoint)
        }
    }
    
    /**
     * Calculate the optimal entry point for a sector area given approach and exit directions.
     * 
     * @param center Center of the sector
     * @param innerRadius Inner radius in meters (null for full sector)
     * @param outerRadius Outer radius in meters
     * @param startBearing Start bearing in degrees
     * @param endBearing End bearing in degrees
     * @param approachFrom Point flying from
     * @param exitTo Point flying to
     * @return Optimal touch point on the sector boundary
     */
    fun calculateOptimalTouchPoint(
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double,
        approachFrom: AATLatLng,
        exitTo: AATLatLng
    ): AATLatLng {
        // Calculate the bearing from approach to center and center to exit
        val approachBearing = AATMathUtils.calculateBearing(approachFrom, center)
        val exitBearing = AATMathUtils.calculateBearing(center, exitTo)
        
        // Find the optimal bearing that minimizes total path length
        val optimalBearing = calculateOptimalBearing(
            approachBearing, exitBearing, startBearing, endBearing
        )
        
        // Use outer radius for maximum distance credit in AAT
        return AATMathUtils.calculatePointAtBearing(center, optimalBearing, outerRadius)
    }
    
    /**
     * Generate boundary points for display purposes
     * 
     * @param center Center of the sector
     * @param innerRadius Inner radius in meters (null for full sector)
     * @param outerRadius Outer radius in meters
     * @param startBearing Start bearing in degrees
     * @param endBearing End bearing in degrees
     * @param numArcPoints Number of points per arc
     * @return List of points defining the sector boundary
     */
    fun generateBoundaryPoints(
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double,
        numArcPoints: Int = 36
    ): List<AATLatLng> {
        val points = mutableListOf<AATLatLng>()
        
        val sectorSpan = if (endBearing >= startBearing) {
            endBearing - startBearing
        } else {
            360 - startBearing + endBearing
        }
        
        val actualPoints = ((sectorSpan / 360.0) * numArcPoints).toInt().coerceAtLeast(3)
        
        // Generate outer arc points
        for (i in 0..actualPoints) {
            val fraction = i.toDouble() / actualPoints
            val bearing = if (endBearing >= startBearing) {
                startBearing + fraction * (endBearing - startBearing)
            } else {
                AATMathUtils.normalizeAngle(startBearing + fraction * sectorSpan)
            }
            points.add(AATMathUtils.calculatePointAtBearing(center, bearing, outerRadius))
        }
        
        // If there's an inner radius, add inner arc points (in reverse order)
        if (innerRadius != null) {
            for (i in actualPoints downTo 0) {
                val fraction = i.toDouble() / actualPoints
                val bearing = if (endBearing >= startBearing) {
                    startBearing + fraction * (endBearing - startBearing)
                } else {
                    AATMathUtils.normalizeAngle(startBearing + fraction * sectorSpan)
                }
                points.add(AATMathUtils.calculatePointAtBearing(center, bearing, innerRadius))
            }
        } else {
            // No inner radius, connect to center
            points.add(center)
        }
        
        return points
    }
    
    /**
     * Calculate the area of the sector in square kilometers
     */
    fun calculateAreaSizeKm2(
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): Double {
        val outerRadiusKm = outerRadius / 1000.0
        val innerRadiusKm = (innerRadius ?: 0.0) / 1000.0
        
        val sectorAngle = if (endBearing >= startBearing) {
            endBearing - startBearing
        } else {
            360 - startBearing + endBearing
        }
        
        val sectorFraction = sectorAngle / 360.0
        
        return sectorFraction * PI * (outerRadiusKm * outerRadiusKm - innerRadiusKm * innerRadiusKm)
    }
    
    /**
     * Helper function to calculate optimal bearing within sector constraints
     */
    private fun calculateOptimalBearing(
        approachBearing: Double,
        exitBearing: Double,
        startBearing: Double,
        endBearing: Double
    ): Double {
        // Calculate the ideal bearing (bisector of approach and exit)
        val bearingDiff = AATMathUtils.angleDifference(approachBearing, exitBearing)
        val idealBearing = AATMathUtils.normalizeAngle(approachBearing + bearingDiff / 2.0)
        
        // Check if ideal bearing is within sector
        if (AATMathUtils.isAngleBetween(idealBearing, startBearing, endBearing)) {
            return idealBearing
        }
        
        // If not, choose the sector boundary closest to the ideal bearing
        val distanceToStart = abs(AATMathUtils.angleDifference(idealBearing, startBearing))
        val distanceToEnd = abs(AATMathUtils.angleDifference(idealBearing, endBearing))
        
        return if (distanceToStart < distanceToEnd) startBearing else endBearing
    }
    
    /**
     * Check if a track segment passes through a sector area
     */
    fun doesTrackIntersectArea(
        trackStart: AATLatLng,
        trackEnd: AATLatLng,
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): Boolean {
        // Check if either endpoint is inside the sector
        if (isInsideArea(trackStart, center, innerRadius, outerRadius, startBearing, endBearing) ||
            isInsideArea(trackEnd, center, innerRadius, outerRadius, startBearing, endBearing)) {
            return true
        }
        
        // For a more comprehensive check, we would need to test intersection
        // with all sector boundaries (arcs and radial lines)
        // For now, we'll use a simplified approach
        return false
    }
}