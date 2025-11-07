package com.example.xcpro.tasks.aat.areas

import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AssignedArea
import com.example.xcpro.tasks.aat.models.AreaGeometry

/**
 * Unified calculator for area boundary operations.
 * This class delegates to the appropriate specific calculator based on area type.
 * Completely autonomous for the AAT module.
 */
class AreaBoundaryCalculator {
    
    private val circleCalculator = CircleAreaCalculator()
    private val sectorCalculator = SectorAreaCalculator()
    
    /**
     * Check if a point is inside any type of assigned area
     */
    fun isInsideArea(point: AATLatLng, area: AssignedArea): Boolean {
        try {
            return when (area.geometry) {
                is AreaGeometry.Circle -> {
                    // ✅ CRASH PREVENTION: Validate circle geometry
                    if (area.geometry.radius <= 0) {
                        println("❌ AAT ERROR: Invalid circle radius ${area.geometry.radius} in area '${area.name}'")
                        return false
                    }
                    circleCalculator.isInsideArea(point, area.centerPoint, area.geometry.radius)
                }
                is AreaGeometry.Sector -> {
                    // ✅ CRASH PREVENTION: Validate sector geometry
                    val innerRadius = area.geometry.innerRadius ?: 0.0
                    if (area.geometry.outerRadius <= 0 || innerRadius < 0 ||
                        innerRadius >= area.geometry.outerRadius) {
                        println("❌ AAT ERROR: Invalid sector radii (inner=$innerRadius, outer=${area.geometry.outerRadius}) in area '${area.name}'")
                        return false
                    }
                    sectorCalculator.isInsideArea(
                        point, area.centerPoint,
                        innerRadius, area.geometry.outerRadius,
                        area.geometry.startBearing, area.geometry.endBearing
                    )
                }
            }
        } catch (e: Exception) {
            println("❌ AAT ERROR: Exception checking if point is inside area '${area.name}': ${e.message}")
            return false
        }
    }
    
    /**
     * Find the nearest point on the boundary of any type of assigned area
     */
    fun nearestPointOnBoundary(from: AATLatLng, area: AssignedArea): AATLatLng {
        return when (area.geometry) {
            is AreaGeometry.Circle -> {
                circleCalculator.nearestPointOnBoundary(
                    from, area.centerPoint, area.geometry.radius
                )
            }
            is AreaGeometry.Sector -> {
                sectorCalculator.nearestPointOnBoundary(
                    from, area.centerPoint,
                    area.geometry.innerRadius, area.geometry.outerRadius,
                    area.geometry.startBearing, area.geometry.endBearing
                )
            }
        }
    }
    
    /**
     * Find the farthest point on the boundary of any type of assigned area
     */
    fun farthestPointOnBoundary(from: AATLatLng, area: AssignedArea): AATLatLng {
        return when (area.geometry) {
            is AreaGeometry.Circle -> {
                circleCalculator.farthestPointOnBoundary(
                    from, area.centerPoint, area.geometry.radius
                )
            }
            is AreaGeometry.Sector -> {
                sectorCalculator.farthestPointOnBoundary(
                    from, area.centerPoint,
                    area.geometry.innerRadius, area.geometry.outerRadius,
                    area.geometry.startBearing, area.geometry.endBearing
                )
            }
        }
    }
    
    /**
     * Calculate the credited fix for a flight track through any type of assigned area
     */
    fun calculateCreditedFix(track: List<AATLatLng>, area: AssignedArea): AATLatLng? {
        return when (area.geometry) {
            is AreaGeometry.Circle -> {
                circleCalculator.calculateCreditedFix(track, area)
            }
            is AreaGeometry.Sector -> {
                sectorCalculator.calculateCreditedFix(track, area)
            }
        }
    }
    
    /**
     * Calculate the optimal touch point for any type of assigned area
     */
    fun calculateOptimalTouchPoint(
        area: AssignedArea,
        approachFrom: AATLatLng,
        exitTo: AATLatLng
    ): AATLatLng {
        return when (area.geometry) {
            is AreaGeometry.Circle -> {
                circleCalculator.calculateOptimalTouchPoint(
                    area.centerPoint, area.geometry.radius, approachFrom, exitTo
                )
            }
            is AreaGeometry.Sector -> {
                sectorCalculator.calculateOptimalTouchPoint(
                    area.centerPoint, area.geometry.innerRadius, area.geometry.outerRadius,
                    area.geometry.startBearing, area.geometry.endBearing,
                    approachFrom, exitTo
                )
            }
        }
    }
    
    /**
     * Generate boundary points for display of any type of assigned area
     */
    fun generateBoundaryPoints(area: AssignedArea, numPoints: Int = 36): List<AATLatLng> {
        return when (area.geometry) {
            is AreaGeometry.Circle -> {
                circleCalculator.generateBoundaryPoints(
                    area.centerPoint, area.geometry.radius, numPoints
                )
            }
            is AreaGeometry.Sector -> {
                sectorCalculator.generateBoundaryPoints(
                    area.centerPoint, area.geometry.innerRadius, area.geometry.outerRadius,
                    area.geometry.startBearing, area.geometry.endBearing, numPoints
                )
            }
        }
    }
    
    /**
     * Calculate the area size in square kilometers for any type of assigned area
     */
    fun calculateAreaSizeKm2(area: AssignedArea): Double {
        return when (area.geometry) {
            is AreaGeometry.Circle -> {
                circleCalculator.calculateAreaSizeKm2(area.geometry.radius)
            }
            is AreaGeometry.Sector -> {
                sectorCalculator.calculateAreaSizeKm2(
                    area.geometry.innerRadius, area.geometry.outerRadius,
                    area.geometry.startBearing, area.geometry.endBearing
                )
            }
        }
    }
    
    /**
     * Check if a track segment passes through any type of assigned area
     */
    fun doesTrackIntersectArea(
        trackStart: AATLatLng,
        trackEnd: AATLatLng,
        area: AssignedArea
    ): Boolean {
        return when (area.geometry) {
            is AreaGeometry.Circle -> {
                circleCalculator.doesTrackIntersectArea(
                    trackStart, trackEnd, area.centerPoint, area.geometry.radius
                )
            }
            is AreaGeometry.Sector -> {
                sectorCalculator.doesTrackIntersectArea(
                    trackStart, trackEnd, area.centerPoint,
                    area.geometry.innerRadius, area.geometry.outerRadius,
                    area.geometry.startBearing, area.geometry.endBearing
                )
            }
        }
    }
    
    /**
     * Get a list of all points where a track enters or exits an area
     */
    fun findAreaTransitionPoints(track: List<AATLatLng>, area: AssignedArea): List<AreaTransition> {
        if (track.size < 2) return emptyList()
        
        val transitions = mutableListOf<AreaTransition>()
        var wasInside = isInsideArea(track.first(), area)
        
        for (i in 1 until track.size) {
            val isInside = isInsideArea(track[i], area)
            
            if (wasInside != isInside) {
                val transitionType = if (isInside) {
                    AreaTransitionType.ENTRY
                } else {
                    AreaTransitionType.EXIT
                }
                
                transitions.add(
                    AreaTransition(
                        type = transitionType,
                        position = track[i],
                        trackIndex = i,
                        areaName = area.name
                    )
                )
            }
            
            wasInside = isInside
        }
        
        return transitions
    }
    
    /**
     * Calculate the total distance flown within an area
     */
    fun calculateDistanceInArea(track: List<AATLatLng>, area: AssignedArea): Double {
        if (track.size < 2) return 0.0
        
        var totalDistance = 0.0
        
        for (i in 1 until track.size) {
            val start = track[i - 1]
            val end = track[i]
            
            val startInside = isInsideArea(start, area)
            val endInside = isInsideArea(end, area)
            
            if (startInside && endInside) {
                // Both points inside - add full segment distance
                totalDistance += com.example.xcpro.tasks.aat.calculations.AATMathUtils.calculateDistance(start, end)
            } else if (startInside || endInside) {
                // One point inside - add partial distance (approximation)
                totalDistance += com.example.xcpro.tasks.aat.calculations.AATMathUtils.calculateDistance(start, end) * 0.5
            }
        }
        
        return totalDistance
    }
}

/**
 * Represents a transition point where a track enters or exits an area
 */
data class AreaTransition(
    val type: AreaTransitionType,
    val position: AATLatLng,
    val trackIndex: Int,
    val areaName: String
)

/**
 * Types of area transitions
 */
enum class AreaTransitionType {
    ENTRY,  // Track enters the area
    EXIT    // Track exits the area
}
