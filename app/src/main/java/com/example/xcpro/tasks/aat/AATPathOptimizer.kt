package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.calculations.AATSpeedCalculator
import com.example.xcpro.tasks.aat.models.AATTask
import java.time.Duration
import kotlin.math.*

/**
 * Path optimizer for AAT tasks.
 * Calculates optimal paths to maximize distance while finishing just after minimum time.
 * This class is completely autonomous for the AAT module.
 */
class AATPathOptimizer {
    
    private val areaBoundaryCalculator = AreaBoundaryCalculator()
    private val speedCalculator = AATSpeedCalculator()
    
    /**
     * Calculate the optimal path through all assigned areas to finish just after minimum time.
     * 
     * @param task The AAT task
     * @param currentPosition Current aircraft position
     * @param elapsedTime Time already spent on task
     * @param expectedSpeed Expected average speed for remaining flight (km/h)
     * @return List of optimal points to fly through each remaining area
     */
    fun calculateOptimalPath(
        task: AATTask,
        currentPosition: AATLatLng,
        elapsedTime: Duration,
        expectedSpeed: Double
    ): List<AATLatLng> {
        val remainingTime = task.minimumTaskTime - elapsedTime
        if (remainingTime <= Duration.ZERO) {
            // Past minimum time, fly shortest path
            return calculateShortestRemainingPath(task, currentPosition)
        }
        
        val remainingTimeHours = remainingTime.toMinutes() / 60.0
        val targetRemainingDistance = expectedSpeed * remainingTimeHours * 1000.0 // meters
        
        return calculatePathForTargetDistance(task, currentPosition, targetRemainingDistance)
    }
    
    /**
     * Calculate optimal path for a specific target distance.
     * 
     * @param task The AAT task
     * @param currentPosition Current position
     * @param targetDistance Target distance to fly in meters
     * @return Optimal path waypoints
     */
    fun calculatePathForTargetDistance(
        task: AATTask,
        currentPosition: AATLatLng,
        targetDistance: Double
    ): List<AATLatLng> {
        if (task.assignedAreas.isEmpty()) {
            return listOf(task.finish.position)
        }
        
        // Find which areas haven't been completed yet
        // For this implementation, assume we need to visit all areas
        val remainingAreas = task.assignedAreas
        
        // Calculate path that achieves target distance
        return optimizePathForDistance(
            currentPosition,
            task.finish.position,
            remainingAreas,
            targetDistance
        )
    }
    
    /**
     * Calculate the shortest path from current position through remaining areas.
     */
    fun calculateShortestRemainingPath(
        task: AATTask,
        currentPosition: AATLatLng
    ): List<AATLatLng> {
        val waypoints = mutableListOf<AATLatLng>()
        var currentPoint = currentPosition
        
        // Visit each area via nearest boundary point
        for (area in task.assignedAreas) {
            val nearestPoint = areaBoundaryCalculator.nearestPointOnBoundary(currentPoint, area)
            waypoints.add(nearestPoint)
            currentPoint = nearestPoint
        }
        
        waypoints.add(task.finish.position)
        return waypoints
    }
    
    /**
     * Calculate the longest possible path from current position.
     */
    fun calculateLongestRemainingPath(
        task: AATTask,
        currentPosition: AATLatLng
    ): List<AATLatLng> {
        val waypoints = mutableListOf<AATLatLng>()
        var currentPoint = currentPosition
        
        // Visit each area via farthest boundary point
        for (area in task.assignedAreas) {
            val farthestPoint = areaBoundaryCalculator.farthestPointOnBoundary(currentPoint, area)
            waypoints.add(farthestPoint)
            currentPoint = farthestPoint
        }
        
        waypoints.add(task.finish.position)
        return waypoints
    }
    
    /**
     * Calculate optimal strategy (0.0 = shortest, 1.0 = longest) for given conditions.
     * 
     * @param task The AAT task
     * @param currentPosition Current position
     * @param elapsedTime Time already spent
     * @param expectedSpeed Expected speed (km/h)
     * @return Strategy value between 0.0 and 1.0
     */
    fun calculateOptimalStrategy(
        task: AATTask,
        currentPosition: AATLatLng,
        elapsedTime: Duration,
        expectedSpeed: Double
    ): Double {
        val remainingTime = task.minimumTaskTime - elapsedTime
        if (remainingTime <= Duration.ZERO) {
            return 0.0 // Use shortest path if past minimum time
        }
        
        val remainingTimeHours = remainingTime.toMinutes() / 60.0
        val targetDistance = expectedSpeed * remainingTimeHours * 1000.0
        
        val shortestPath = calculateShortestRemainingPath(task, currentPosition)
        val longestPath = calculateLongestRemainingPath(task, currentPosition)
        
        val shortestDistance = calculatePathDistance(shortestPath)
        val longestDistance = calculatePathDistance(longestPath)
        
        if (longestDistance <= shortestDistance) return 0.0
        
        // Calculate strategy to achieve target distance
        val strategy = (targetDistance - shortestDistance) / (longestDistance - shortestDistance)
        return strategy.coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculate recommended points in each area for a given strategy.
     * 
     * @param task The AAT task
     * @param currentPosition Current position
     * @param strategy Strategy (0.0 = nearest points, 1.0 = farthest points)
     * @return Optimal points for each area
     */
    fun calculateRecommendedPoints(
        task: AATTask,
        currentPosition: AATLatLng,
        strategy: Double
    ): List<AATLatLng> {
        val clampedStrategy = strategy.coerceIn(0.0, 1.0)
        val waypoints = mutableListOf<AATLatLng>()
        var currentPoint = currentPosition
        
        for (i in task.assignedAreas.indices) {
            val area = task.assignedAreas[i]
            val nextPoint = if (i < task.assignedAreas.size - 1) {
                task.assignedAreas[i + 1].centerPoint
            } else {
                task.finish.position
            }
            
            val nearestPoint = areaBoundaryCalculator.nearestPointOnBoundary(currentPoint, area)
            val farthestPoint = areaBoundaryCalculator.farthestPointOnBoundary(currentPoint, area)
            
            // Interpolate between nearest and farthest based on strategy
            val recommendedPoint = interpolatePoints(nearestPoint, farthestPoint, clampedStrategy)
            waypoints.add(recommendedPoint)
            currentPoint = recommendedPoint
        }
        
        return waypoints
    }
    
    /**
     * Calculate real-time path recommendation based on current flight conditions.
     * 
     * @param task The AAT task
     * @param currentPosition Current position
     * @param elapsedTime Time elapsed so far
     * @param groundSpeed Current ground speed (km/h)
     * @param areasCompleted Number of areas already completed
     * @return Real-time path recommendation
     */
    fun calculateRealTimeRecommendation(
        task: AATTask,
        currentPosition: AATLatLng,
        elapsedTime: Duration,
        groundSpeed: Double,
        areasCompleted: Int = 0
    ): PathRecommendation {
        val remainingAreas = task.assignedAreas.drop(areasCompleted)
        val remainingTime = task.minimumTaskTime - elapsedTime
        
        if (remainingAreas.isEmpty()) {
            // All areas completed, head to finish
            return PathRecommendation(
                nextWaypoint = task.finish.position,
                recommendedStrategy = 0.0,
                timeToMinimum = remainingTime,
                distanceRemaining = AATMathUtils.calculateDistance(currentPosition, task.finish.position)
            )
        }
        
        val shortestRemaining = calculateShortestPathDistance(currentPosition, remainingAreas, task.finish.position)
        val longestRemaining = calculateLongestPathDistance(currentPosition, remainingAreas, task.finish.position)
        
        val remainingTimeHours = maxOf(remainingTime.toMinutes() / 60.0, 0.0)
        val targetDistance = if (remainingTimeHours > 0 && groundSpeed > 0) {
            groundSpeed * remainingTimeHours * 1000.0
        } else {
            shortestRemaining
        }
        
        val strategy = if (longestRemaining > shortestRemaining) {
            (targetDistance - shortestRemaining) / (longestRemaining - shortestRemaining)
        } else {
            0.0
        }.coerceIn(0.0, 1.0)
        
        val nextArea = remainingAreas.first()
        val nearestPoint = areaBoundaryCalculator.nearestPointOnBoundary(currentPosition, nextArea)
        val farthestPoint = areaBoundaryCalculator.farthestPointOnBoundary(currentPosition, nextArea)
        val recommendedPoint = interpolatePoints(nearestPoint, farthestPoint, strategy)
        
        return PathRecommendation(
            nextWaypoint = recommendedPoint,
            recommendedStrategy = strategy,
            timeToMinimum = remainingTime,
            distanceRemaining = calculatePathDistance(
                listOf(currentPosition) + calculateRecommendedPoints(
                    task.copy(assignedAreas = remainingAreas), 
                    currentPosition, 
                    strategy
                ) + task.finish.position
            )
        )
    }
    
    /**
     * Optimize path for a specific distance target using iterative approach.
     */
    private fun optimizePathForDistance(
        start: AATLatLng,
        finish: AATLatLng,
        areas: List<com.example.xcpro.tasks.aat.models.AssignedArea>,
        targetDistance: Double
    ): List<AATLatLng> {
        // Use binary search to find optimal strategy
        var minStrategy = 0.0
        var maxStrategy = 1.0
        var bestStrategy = 0.5
        val tolerance = 100.0 // meters
        
        repeat(20) { // Max iterations
            val testPoints = calculatePointsForStrategy(start, areas, bestStrategy)
            val testDistance = calculatePathDistance(listOf(start) + testPoints + finish)
            
            when {
                abs(testDistance - targetDistance) <= tolerance -> {
                    // Close enough
                    return testPoints
                }
                testDistance < targetDistance -> {
                    minStrategy = bestStrategy
                }
                else -> {
                    maxStrategy = bestStrategy
                }
            }
            
            bestStrategy = (minStrategy + maxStrategy) / 2.0
        }
        
        return calculatePointsForStrategy(start, areas, bestStrategy)
    }
    
    /**
     * Calculate points for a given strategy value.
     */
    private fun calculatePointsForStrategy(
        start: AATLatLng,
        areas: List<com.example.xcpro.tasks.aat.models.AssignedArea>,
        strategy: Double
    ): List<AATLatLng> {
        val points = mutableListOf<AATLatLng>()
        var currentPoint = start
        
        for (area in areas) {
            val nearest = areaBoundaryCalculator.nearestPointOnBoundary(currentPoint, area)
            val farthest = areaBoundaryCalculator.farthestPointOnBoundary(currentPoint, area)
            val point = interpolatePoints(nearest, farthest, strategy)
            points.add(point)
            currentPoint = point
        }
        
        return points
    }
    
    /**
     * Calculate shortest path distance through remaining areas.
     */
    private fun calculateShortestPathDistance(
        start: AATLatLng,
        areas: List<com.example.xcpro.tasks.aat.models.AssignedArea>,
        finish: AATLatLng
    ): Double {
        val shortestPoints = calculatePointsForStrategy(start, areas, 0.0)
        return calculatePathDistance(listOf(start) + shortestPoints + finish)
    }
    
    /**
     * Calculate longest path distance through remaining areas.
     */
    private fun calculateLongestPathDistance(
        start: AATLatLng,
        areas: List<com.example.xcpro.tasks.aat.models.AssignedArea>,
        finish: AATLatLng
    ): Double {
        val longestPoints = calculatePointsForStrategy(start, areas, 1.0)
        return calculatePathDistance(listOf(start) + longestPoints + finish)
    }
    
    /**
     * Calculate total distance of a path.
     */
    private fun calculatePathDistance(waypoints: List<AATLatLng>): Double {
        if (waypoints.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until waypoints.size) {
            totalDistance += AATMathUtils.calculateDistance(waypoints[i - 1], waypoints[i])
        }
        return totalDistance
    }
    
    /**
     * Interpolate between two points based on a fraction.
     */
    private fun interpolatePoints(point1: AATLatLng, point2: AATLatLng, fraction: Double): AATLatLng {
        return AATMathUtils.interpolatePosition(point1, point2, fraction)
    }
}

/**
 * Real-time path recommendation result
 */
data class PathRecommendation(
    val nextWaypoint: AATLatLng,
    val recommendedStrategy: Double,    // 0.0 to 1.0
    val timeToMinimum: Duration,
    val distanceRemaining: Double       // meters
)