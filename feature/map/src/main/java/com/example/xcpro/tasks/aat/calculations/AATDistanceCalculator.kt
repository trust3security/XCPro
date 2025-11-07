package com.example.xcpro.tasks.aat.calculations

import com.example.xcpro.tasks.aat.models.*
import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import kotlin.math.*

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
     * @param expectedAverageSpeed Expected average speed in km/h
     * @return Optimal distance in meters
     */
    fun calculateOptimalDistanceForMinimumTime(
        task: AATTask,
        expectedAverageSpeed: Double
    ): Double {
        val minimumTimeHours = task.minimumTaskTime.toMinutes() / 60.0
        val targetDistance = expectedAverageSpeed * minimumTimeHours * 1000.0 // Convert to meters
        
        val minimumDistance = calculateMinimumDistance(task)
        val maximumDistance = calculateMaximumDistance(task)
        
        // Clamp target distance to achievable range
        return targetDistance.coerceIn(minimumDistance, maximumDistance)
    }
    
    /**
     * Find the point in an area that minimizes the total path distance
     */
    private fun findMinimumDistancePoint(
        area: com.example.xcpro.tasks.aat.models.AssignedArea,
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
        area: com.example.xcpro.tasks.aat.models.AssignedArea,
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
            AATMathUtils.calculateDistance(previousPoint, candidate) +
            AATMathUtils.calculateDistance(candidate, nextPoint)
        } ?: area.centerPoint
    }
    
    /**
     * Calculate the total distance of a path through waypoints
     */
    private fun calculateTotalPathDistance(waypoints: List<AATLatLng>): Double {
        if (waypoints.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until waypoints.size) {
            totalDistance += AATMathUtils.calculateDistance(waypoints[i - 1], waypoints[i])
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
            val distance = AATMathUtils.calculateDistance(waypoints[i - 1], waypoints[i])
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

    // ========== INTERACTIVE TURNPOINT SUPPORT (Phase 3) ==========

    /**
     * Calculate real-time task distance through target points for interactive AAT system
     * This method supports movable target points within AAT areas.
     */
    fun calculateInteractiveTaskDistance(waypoints: List<AATWaypoint>): AATInteractiveTaskDistance {
        val startTime = System.currentTimeMillis()

        if (waypoints.isEmpty()) {
            return AATInteractiveTaskDistance(0.0, emptyList())
        }

        if (waypoints.size == 1) {
            return calculateSingleWaypointInteractiveTask(waypoints[0])
        }

        val segments = mutableListOf<AATInteractiveDistanceSegment>()
        var totalDistance = 0.0

        // Calculate segments between consecutive waypoints using target points
        for (i in 0 until waypoints.size - 1) {
            val fromWaypoint = waypoints[i]
            val toWaypoint = waypoints[i + 1]

            val segment = calculateInteractiveSegmentDistance(
                fromWaypoint = fromWaypoint,
                toWaypoint = toWaypoint,
                fromIndex = i,
                toIndex = i + 1
            )

            segments.add(segment)
            totalDistance += segment.distance
        }

        val calculationTime = System.currentTimeMillis() - startTime

        return AATInteractiveTaskDistance(
            totalDistance = totalDistance,
            segments = segments,
            calculationTime = calculationTime
        )
    }

    /**
     * Calculate real-time distance update when a single target point moves
     * Optimized for frequent updates during dragging operations
     */
    fun calculateDistanceUpdate(
        waypoints: List<AATWaypoint>,
        changedWaypointIndex: Int,
        newTargetPoint: AATLatLng
    ): AATInteractiveTaskDistance {
        // Create updated waypoints list with new target point
        val updatedWaypoints = waypoints.toMutableList()
        updatedWaypoints[changedWaypointIndex] = updatedWaypoints[changedWaypointIndex].copy(
            targetPoint = newTargetPoint
        )

        // Recalculate with updated target point
        return calculateInteractiveTaskDistance(updatedWaypoints)
    }

    /**
     * Calculate single waypoint task for interactive system
     */
    private fun calculateSingleWaypointInteractiveTask(waypoint: AATWaypoint): AATInteractiveTaskDistance {
        // For single waypoint, distance is center to target point
        val distance = haversineDistance(
            waypoint.lat, waypoint.lon,
            waypoint.targetPoint.latitude, waypoint.targetPoint.longitude
        )

        val segment = AATInteractiveDistanceSegment(
            fromPoint = AATLatLng(waypoint.lat, waypoint.lon),
            toPoint = waypoint.targetPoint,
            distance = distance,
            segmentType = AATInteractiveSegmentType.CENTER_TO_TARGET,
            fromWaypointIndex = 0,
            toWaypointIndex = 0
        )

        return AATInteractiveTaskDistance(
            totalDistance = distance,
            segments = listOf(segment)
        )
    }

    /**
     * Calculate distance between two AAT waypoints using their target points
     */
    private fun calculateInteractiveSegmentDistance(
        fromWaypoint: AATWaypoint,
        toWaypoint: AATWaypoint,
        fromIndex: Int,
        toIndex: Int
    ): AATInteractiveDistanceSegment {

        // Use target points for distance calculation
        val fromPoint = fromWaypoint.targetPoint
        val toPoint = toWaypoint.targetPoint

        // Calculate great circle distance
        val distance = haversineDistance(
            fromPoint.latitude, fromPoint.longitude,
            toPoint.latitude, toPoint.longitude
        )

        // Determine segment type based on waypoint roles
        val segmentType = when {
            fromWaypoint.role == AATWaypointRole.START && toWaypoint.role == AATWaypointRole.FINISH ->
                AATInteractiveSegmentType.START_TO_FINISH
            fromWaypoint.role == AATWaypointRole.START ->
                AATInteractiveSegmentType.START_TO_TURNPOINT
            toWaypoint.role == AATWaypointRole.FINISH ->
                AATInteractiveSegmentType.TURNPOINT_TO_FINISH
            else ->
                AATInteractiveSegmentType.TURNPOINT_TO_TURNPOINT
        }

        return AATInteractiveDistanceSegment(
            fromPoint = fromPoint,
            toPoint = toPoint,
            distance = distance,
            segmentType = segmentType,
            fromWaypointIndex = fromIndex,
            toWaypointIndex = toIndex
        )
    }

    /**
     * Calculate optimal target point positions for maximum distance
     * Used by strategic positioning features
     */
    fun optimizeTargetPointsForMaxDistance(waypoints: List<AATWaypoint>): List<AATWaypoint> {
        if (waypoints.size < 2) return waypoints

        val optimized = waypoints.toMutableList()

        // For each waypoint, position target point to maximize distance
        for (i in waypoints.indices) {
            val waypoint = waypoints[i]
            val prevWaypoint = if (i > 0) waypoints[i - 1] else null
            val nextWaypoint = if (i < waypoints.size - 1) waypoints[i + 1] else null

            val optimalTarget = calculateOptimalTargetForMaxDistance(
                waypoint, prevWaypoint, nextWaypoint
            )

            optimized[i] = waypoint.copy(targetPoint = optimalTarget)
        }

        return optimized
    }

    /**
     * Calculate optimal target point position for maximum distance
     */
    private fun calculateOptimalTargetForMaxDistance(
        waypoint: AATWaypoint,
        prevWaypoint: AATWaypoint?,
        nextWaypoint: AATWaypoint?
    ): AATLatLng {
        val areaRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0

        when {
            prevWaypoint == null && nextWaypoint != null -> {
                // First waypoint: position away from next waypoint
                val bearing = calculateBearing(
                    nextWaypoint.lat, nextWaypoint.lon,
                    waypoint.lat, waypoint.lon
                )
                return calculateDestination(waypoint.lat, waypoint.lon, bearing, areaRadiusKm * 0.8)
            }
            prevWaypoint != null && nextWaypoint == null -> {
                // Last waypoint: position away from previous waypoint
                val bearing = calculateBearing(
                    prevWaypoint.lat, prevWaypoint.lon,
                    waypoint.lat, waypoint.lon
                )
                return calculateDestination(waypoint.lat, waypoint.lon, bearing, areaRadiusKm * 0.8)
            }
            prevWaypoint != null && nextWaypoint != null -> {
                // Middle waypoint: position perpendicular for maximum distance
                val bisectorBearing = calculateBisectorBearing(
                    prevWaypoint.lat, prevWaypoint.lon,
                    waypoint.lat, waypoint.lon,
                    nextWaypoint.lat, nextWaypoint.lon
                )
                return calculateDestination(waypoint.lat, waypoint.lon, bisectorBearing, areaRadiusKm * 0.8)
            }
            else -> {
                // Single waypoint: keep at center
                return AATLatLng(waypoint.lat, waypoint.lon)
            }
        }
    }
}

/**
 * Data classes for interactive distance calculations
 */
data class AATInteractiveTaskDistance(
    val totalDistance: Double, // km
    val segments: List<AATInteractiveDistanceSegment>,
    val calculationTime: Long = System.currentTimeMillis()
) {
    val isValid: Boolean get() = totalDistance > 0.0 && segments.isNotEmpty()
    val segmentCount: Int get() = segments.size

    fun getDistanceBreakdown(): String {
        val breakdown = StringBuilder()
        breakdown.append("Total: ${String.format("%.2f", totalDistance)} km\n")

        segments.forEachIndexed { index, segment ->
            breakdown.append("Leg ${index + 1}: ${String.format("%.2f", segment.distance)} km\n")
        }

        return breakdown.toString().trim()
    }
}

data class AATInteractiveDistanceSegment(
    val fromPoint: AATLatLng,
    val toPoint: AATLatLng,
    val distance: Double, // km
    val segmentType: AATInteractiveSegmentType,
    val fromWaypointIndex: Int,
    val toWaypointIndex: Int
)

enum class AATInteractiveSegmentType {
    START_TO_TURNPOINT,
    TURNPOINT_TO_TURNPOINT,
    TURNPOINT_TO_FINISH,
    START_TO_FINISH,
    CENTER_TO_TARGET
}

/**
 * Utility functions for interactive distance calculations
 */

/**
 * Calculate bearing between two points
 */
private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)

    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

    val bearing = atan2(y, x)
    return (Math.toDegrees(bearing) + 360) % 360
}

/**
 * Calculate bisector bearing for optimal turnpoint positioning
 */
private fun calculateBisectorBearing(
    prevLat: Double, prevLon: Double,
    currentLat: Double, currentLon: Double,
    nextLat: Double, nextLon: Double
): Double {
    val bearing1 = calculateBearing(currentLat, currentLon, prevLat, prevLon)
    val bearing2 = calculateBearing(currentLat, currentLon, nextLat, nextLon)

    // Calculate angle bisector (perpendicular to line between prev and next)
    val avgBearing = (bearing1 + bearing2) / 2.0
    return (avgBearing + 90) % 360 // Perpendicular for maximum distance
}

/**
 * Calculate destination point given start point, bearing and distance
 */
private fun calculateDestination(lat: Double, lon: Double, bearing: Double, distanceKm: Double): AATLatLng {
    val earthRadiusKm = 6371.0
    val bearingRad = Math.toRadians(bearing)
    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)
    val angularDistance = distanceKm / earthRadiusKm

    val destLatRad = asin(
        sin(latRad) * cos(angularDistance) +
        cos(latRad) * sin(angularDistance) * cos(bearingRad)
    )

    val destLonRad = lonRad + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(latRad),
        cos(angularDistance) - sin(latRad) * sin(destLatRad)
    )

    return AATLatLng(
        latitude = Math.toDegrees(destLatRad),
        longitude = Math.toDegrees(destLonRad)
    )
}

/**
 * Haversine distance calculation (returns km)
 */
private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
}
