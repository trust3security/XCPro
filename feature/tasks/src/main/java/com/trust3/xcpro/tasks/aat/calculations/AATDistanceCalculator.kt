package com.trust3.xcpro.tasks.aat.calculations

import com.trust3.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.trust3.xcpro.tasks.aat.models.*

/**
 * Calculator for various distance measurements in AAT tasks.
 * This class is completely autonomous and handles all distance calculations
 * without dependencies on other task modules.
 * 
 * AAT distance calculations include:
 * - Minimum distance: Shortest path through nearest area boundaries
 * - Maximum distance: Longest path through farthest area boundaries  
 * - Nominal distance: Path through area centers
 * - Actual distance: Distance through pilot's chosen credited fixes
 */
class AATDistanceCalculator {
    private val areaBoundaryCalculator = AreaBoundaryCalculator()
    private val interactiveDistanceCalculator = AATInteractiveDistanceCalculator()
    
    /**
     * Calculate the minimum possible task distance.
     * This is the shortest path through the nearest edges of all assigned areas.
     * 
     * @param task The AAT task
     * @return Minimum distance in meters
     */
    fun calculateMinimumDistance(task: AATTask): Double {
        if (task.assignedAreas.isEmpty()) return 0.0
        
        val waypoints = mutableListOf<AATLatLng>()
        waypoints.add(task.start.position)
        
        // For minimum distance, find the nearest boundary point of each area
        // considering the optimal path from previous point to next point
        for (i in task.assignedAreas.indices) {
            val area = task.assignedAreas[i]
            val previousPoint = waypoints.last()
            
            val nextPoint = if (i < task.assignedAreas.size - 1) {
                task.assignedAreas[i + 1].centerPoint
            } else {
                task.finish.position
            }
            
            // Find the point on this area's boundary that minimizes total path distance
            val optimalPoint = findMinimumDistancePoint(area, previousPoint, nextPoint)
            waypoints.add(optimalPoint)
        }
        
        waypoints.add(task.finish.position)
        
        return calculateTotalPathDistance(waypoints)
    }
    
    /**
     * Calculate the maximum possible task distance.
     * This is the longest path through the farthest edges of all assigned areas.
     * 
     * @param task The AAT task
     * @return Maximum distance in meters
     */
    fun calculateMaximumDistance(task: AATTask): Double {
        if (task.assignedAreas.isEmpty()) return 0.0
        
        val waypoints = mutableListOf<AATLatLng>()
        waypoints.add(task.start.position)
        
        // For maximum distance, find the farthest boundary point of each area
        for (i in task.assignedAreas.indices) {
            val area = task.assignedAreas[i]
            val previousPoint = waypoints.last()
            
            val nextPoint = if (i < task.assignedAreas.size - 1) {
                task.assignedAreas[i + 1].centerPoint
            } else {
                task.finish.position
            }
            
            // Find the point on this area's boundary that maximizes total path distance
            val optimalPoint = findMaximumDistancePoint(area, previousPoint, nextPoint)
            waypoints.add(optimalPoint)
        }
        
        waypoints.add(task.finish.position)
        
        return calculateTotalPathDistance(waypoints)
    }
    
    /**
     * Calculate the nominal task distance.
     * This is the path through the center of each assigned area.
     * 
     * @param task The AAT task
     * @return Nominal distance in meters
     */
    fun calculateNominalDistance(task: AATTask): Double {
        if (task.assignedAreas.isEmpty()) return 0.0
        
        val waypoints = mutableListOf<AATLatLng>()
        waypoints.add(task.start.position)
        
        // Add center point of each area
        task.assignedAreas.forEach { area ->
            waypoints.add(area.centerPoint)
        }
        
        waypoints.add(task.finish.position)
        
        return calculateTotalPathDistance(waypoints)
    }
    
    /**
     * Calculate the actual distance flown through credited fixes.
     * 
     * @param task The AAT task
     * @param creditedFixes List of credited fix points for each area
     * @return Actual distance in meters
     */
    fun calculateActualDistance(
        task: AATTask,
        creditedFixes: List<AATLatLng>
    ): Double {
        if (task.assignedAreas.isEmpty() || creditedFixes.isEmpty()) return 0.0
        
        val waypoints = mutableListOf<AATLatLng>()
        waypoints.add(task.start.position)
        
        // Add credited fixes (should match number of assigned areas)
        val fixesToUse = creditedFixes.take(task.assignedAreas.size)
        waypoints.addAll(fixesToUse)
        
        // If we don't have enough credited fixes, use area centers for missing ones
        if (fixesToUse.size < task.assignedAreas.size) {
            for (i in fixesToUse.size until task.assignedAreas.size) {
                waypoints.add(task.assignedAreas[i].centerPoint)
            }
        }
        
        waypoints.add(task.finish.position)
        
        return calculateTotalPathDistance(waypoints)
    }
    
    /**
     * Calculate all distance measurements for a task and return as a structured result
     */
    fun calculateTaskDistances(task: AATTask): AATTaskDistance {
        return AATTaskDistance(
            minimumDistance = calculateMinimumDistance(task),
            maximumDistance = calculateMaximumDistance(task),
            nominalDistance = calculateNominalDistance(task)
        )
    }
    
    /**
     * Calculate the distance saved/gained by choosing specific points in areas
     * compared to nominal (center) points.
     * 
     * @param task The AAT task
     * @param creditedFixes List of credited fix points
     * @return Distance difference in meters (positive = longer than nominal)
     */
    fun calculateDistanceDifferenceFromNominal(
        task: AATTask,
        creditedFixes: List<AATLatLng>
    ): Double {
        val actualDistance = calculateActualDistance(task, creditedFixes)
        val nominalDistance = calculateNominalDistance(task)
        return actualDistance - nominalDistance
    }
    
    /**
     * Calculate the theoretical optimal distance for finishing exactly at minimum time.
     * This finds the path that maximizes distance while allowing the pilot to finish
     * just as the minimum task time is reached.
     * 
     * @param task The AAT task
     * @param expectedAverageSpeedMs Expected average speed in m/s
     * @return Optimal distance in meters
     */
    fun calculateOptimalDistanceForMinimumTime(
        task: AATTask,
        expectedAverageSpeedMs: Double
    ): Double {
        val minimumTimeSeconds = task.minimumTaskTime.toMillis() / 1000.0
        val targetDistanceMeters = expectedAverageSpeedMs * minimumTimeSeconds

        val minimumDistance = calculateMinimumDistance(task)
        val maximumDistance = calculateMaximumDistance(task)
        
        // Clamp target distance to achievable range
        return targetDistanceMeters.coerceIn(minimumDistance, maximumDistance)
    }
    
    /**
     * Find the point in an area that minimizes the total path distance
     */
    private fun findMinimumDistancePoint(
        area: com.trust3.xcpro.tasks.aat.models.AssignedArea,
        previousPoint: AATLatLng,
        nextPoint: AATLatLng
    ): AATLatLng {
        // For minimum distance, we want the nearest point on the area boundary
        // that lies on or near the direct path from previous to next point
        
        // Calculate the midpoint of the direct path as a reference
        val midpoint = AATMathUtils.interpolatePosition(previousPoint, nextPoint, 0.5)
        
        return areaBoundaryCalculator.nearestPointOnBoundary(midpoint, area)
    }
    
    /**
     * Find the point in an area that maximizes the total path distance
     */
    private fun findMaximumDistancePoint(
        area: com.trust3.xcpro.tasks.aat.models.AssignedArea,
        previousPoint: AATLatLng,
        nextPoint: AATLatLng
    ): AATLatLng {
        // For maximum distance, we want the farthest point from the direct path
        
        // Calculate several candidate points and choose the one that maximizes distance
        val candidates = mutableListOf<AATLatLng>()
        
        // Add points farthest from previous and next points
        candidates.add(areaBoundaryCalculator.farthestPointOnBoundary(previousPoint, area))
        candidates.add(areaBoundaryCalculator.farthestPointOnBoundary(nextPoint, area))
        
        // Add some additional boundary points for better coverage
        val boundaryPoints = areaBoundaryCalculator.generateBoundaryPoints(area, 12)
        candidates.addAll(boundaryPoints)
        
        // Find the candidate that results in the longest total path
        return candidates.maxByOrNull { candidate ->
            AATMathUtils.calculateDistanceMeters(previousPoint, candidate) +
            AATMathUtils.calculateDistanceMeters(candidate, nextPoint)
        } ?: area.centerPoint
    }
    
    /**
     * Calculate the total distance of a path through waypoints
     */
    private fun calculateTotalPathDistance(waypoints: List<AATLatLng>): Double {
        if (waypoints.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until waypoints.size) {
            totalDistance += AATMathUtils.calculateDistanceMeters(
                waypoints[i - 1],
                waypoints[i]
            )
        }

        return totalDistance
    }
    
    /**
     * Calculate intermediate distances for each leg of the task
     */
    fun calculateLegDistances(task: AATTask, waypoints: List<AATLatLng>): List<Double> {
        if (waypoints.size < 2) return emptyList()
        
        val legDistances = mutableListOf<Double>()
        for (i in 1 until waypoints.size) {
            val distance = AATMathUtils.calculateDistanceMeters(
                waypoints[i - 1],
                waypoints[i]
            )
            legDistances.add(distance)
        }
        
        return legDistances
    }
    
    /**
     * Calculate the distance range (min to max) for the task
     */
    fun calculateTaskDistanceRange(task: AATTask): Pair<Double, Double> {
        val minDistance = calculateMinimumDistance(task)
        val maxDistance = calculateMaximumDistance(task)
        return Pair(minDistance, maxDistance)
    }
    
    /**
     * Estimate the distance achievable with a given path strategy
     *
     * @param task The AAT task
     * @param strategy Path strategy (0.0 = minimum distance, 1.0 = maximum distance)
     * @return Estimated distance in meters
     */
    fun estimateDistanceForStrategy(task: AATTask, strategy: Double): Double {
        val clampedStrategy = strategy.coerceIn(0.0, 1.0)
        val minDistance = calculateMinimumDistance(task)
        val maxDistance = calculateMaximumDistance(task)

        return minDistance + (maxDistance - minDistance) * clampedStrategy
    }

    fun calculateInteractiveTaskDistance(waypoints: List<AATWaypoint>): AATInteractiveTaskDistance {
        return interactiveDistanceCalculator.calculateInteractiveTaskDistance(waypoints)
    }

    fun calculateDistanceUpdate(
        waypoints: List<AATWaypoint>,
        changedWaypointIndex: Int,
        newTargetPoint: AATLatLng
    ): AATInteractiveTaskDistance {
        return interactiveDistanceCalculator.calculateDistanceUpdate(
            waypoints = waypoints,
            changedWaypointIndex = changedWaypointIndex,
            newTargetPoint = newTargetPoint
        )
    }

    fun optimizeTargetPointsForMaxDistance(waypoints: List<AATWaypoint>): List<AATWaypoint> {
        return interactiveDistanceCalculator.optimizeTargetPointsForMaxDistance(waypoints)
    }
}
