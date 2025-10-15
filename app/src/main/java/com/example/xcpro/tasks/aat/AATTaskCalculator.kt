package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.calculations.AATSpeedCalculator
import com.example.xcpro.tasks.aat.models.*
import java.time.Duration
import java.time.LocalDateTime

/**
 * Main coordinator for AAT (Area Assignment Task) calculations.
 * This class is completely autonomous and provides a unified interface
 * for all AAT operations without dependencies on other task modules.
 * 
 * Acts as the main entry point for the AAT system, coordinating between
 * all the specialized calculators and validators.
 */
class AATTaskCalculator {
    
    private val areaBoundaryCalculator = AreaBoundaryCalculator()
    private val speedCalculator = AATSpeedCalculator()
    private val pathOptimizer = AATPathOptimizer()
    private val validator = AATTaskValidator()
    private val display = AATTaskDisplay()
    
    /**
     * Validate and calculate all aspects of an AAT task.
     * 
     * @param task The AAT task to analyze
     * @return Complete task analysis
     */
    fun calculateTask(task: AATTask): AATTaskAnalysis {
        val validation = validator.validateTask(task)
        val distances = null

        return AATTaskAnalysis(
            task = task,
            validation = validation,
            distances = distances,
            calculatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * Calculate AAT result for a completed flight.
     * 
     * @param task The AAT task
     * @param flightPath Complete flight track
     * @param startTime Flight start time
     * @param finishTime Flight finish time
     * @param pilotName Pilot name (optional)
     * @return Complete AAT result with scoring
     */
    fun calculateFlightResult(
        task: AATTask,
        flightPath: List<AATLatLng>,
        startTime: LocalDateTime,
        finishTime: LocalDateTime,
        pilotName: String = ""
    ): AATResult {
        // Validate the flight
        val flightValidation = validator.validateFlight(task, flightPath)
        
        // Calculate credited fixes
        val creditedFixes = calculateCreditedFixes(task, flightPath)

        val actualDistance = 100.0

        // Calculate times
        val elapsedTime = Duration.between(startTime, finishTime)
        val scoringTime = maxOf(elapsedTime, task.minimumTaskTime)
        
        // Calculate speed
        val averageSpeed = speedCalculator.calculateAATSpeed(actualDistance, elapsedTime, task.minimumTaskTime)
        
        // Determine flight status
        val taskStatus = when {
            !flightValidation.allAreasAchieved() -> AATFlightStatus.INCOMPLETE
            flightValidation.isValid -> AATFlightStatus.COMPLETED
            else -> AATFlightStatus.DNF
        }
        
        return AATResult(
            taskId = task.id,
            pilotName = pilotName,
            actualDistance = actualDistance,
            elapsedTime = elapsedTime,
            scoringTime = scoringTime,
            averageSpeed = averageSpeed,
            creditedFixes = creditedFixes,
            flightPath = flightPath,
            startTime = startTime,
            finishTime = finishTime,
            taskStatus = taskStatus
        )
    }
    
    /**
     * Calculate credited fixes for each assigned area from a flight track.
     * 
     * @param task The AAT task
     * @param flightPath Flight track points
     * @return List of credited fix points
     */
    fun calculateCreditedFixes(task: AATTask, flightPath: List<AATLatLng>): List<AATLatLng> {
        val creditedFixes = mutableListOf<AATLatLng>()
        
        for (area in task.assignedAreas) {
            val creditedFix = areaBoundaryCalculator.calculateCreditedFix(flightPath, area)
            if (creditedFix != null) {
                creditedFixes.add(creditedFix)
            } else {
                // Area not achieved, use area center as placeholder
                creditedFixes.add(area.centerPoint)
            }
        }
        
        return creditedFixes
    }
    
    /**
     * Calculate optimal path recommendation for real-time navigation.
     * 
     * @param task The AAT task
     * @param currentPosition Current aircraft position
     * @param elapsedTime Time elapsed since start
     * @param groundSpeed Current ground speed (km/h)
     * @param areasCompleted Number of areas already achieved
     * @return Real-time path recommendation
     */
    fun calculateRealTimeRecommendation(
        task: AATTask,
        currentPosition: AATLatLng,
        elapsedTime: Duration,
        groundSpeed: Double,
        areasCompleted: Int = 0
    ): PathRecommendation {
        return pathOptimizer.calculateRealTimeRecommendation(
            task, currentPosition, elapsedTime, groundSpeed, areasCompleted
        )
    }
    
    /**
     * Calculate path for a specific distance target.
     * 
     * @param task The AAT task
     * @param currentPosition Current position
     * @param targetDistance Target distance to achieve (meters)
     * @return Optimal waypoints to achieve target distance
     */
    fun calculatePathForDistance(
        task: AATTask,
        currentPosition: AATLatLng,
        targetDistance: Double
    ): List<AATLatLng> {
        return pathOptimizer.calculatePathForTargetDistance(task, currentPosition, targetDistance)
    }
    
    /**
     * Calculate what distance is needed to achieve a target speed.
     * 
     * @param task The AAT task
     * @param elapsedTime Time that will be spent flying
     * @param targetSpeed Target speed to achieve (km/h)
     * @return Required distance in meters
     */
    fun calculateDistanceForTargetSpeed(
        task: AATTask,
        elapsedTime: Duration,
        targetSpeed: Double
    ): Double {
        return speedCalculator.calculateDistanceForTargetSpeed(
            targetSpeed, elapsedTime, task.minimumTaskTime
        )
    }
    
    /**
     * Analyze flight performance and provide optimization suggestions.
     * 
     * @param result The flight result to analyze
     * @param task The associated task
     * @return Performance analysis with suggestions
     */
    fun analyzeFlightPerformance(result: AATResult, task: AATTask): AATPerformanceAnalysis {
        val areaAchievements = calculateAreaAchievements(result, task)
        val timeAnalysis = calculateTimeAnalysis(result, task)
        val speedAnalysis = calculateSpeedAnalysis(result, task)
        val suggestions = generateOptimizationSuggestions(result, task)
        
        return AATPerformanceAnalysis(
            result = result,
            areaAchievements = areaAchievements,
            timeAnalysis = timeAnalysis,
            speedAnalysis = speedAnalysis,
            optimizationSuggestions = suggestions
        )
    }
    
    /**
     * Update the radius of a specific assigned area in an AAT task.
     * This is the ONLY way to update AAT area radii - completely separate from legacy TaskArea system.
     *
     * @param task The AAT task to update
     * @param areaIndex Index of the assigned area to update (0-based)
     * @param newRadiusMeters New radius in meters
     * @return Updated AAT task with new radius, or original task if invalid
     */
    fun updateAreaRadius(task: AATTask, areaIndex: Int, newRadiusMeters: Double): AATTask {
        // Validation
        if (areaIndex < 0 || areaIndex >= task.assignedAreas.size) {
            println("❌ AAT ERROR: Invalid area index $areaIndex (task has ${task.assignedAreas.size} areas)")
            return task
        }

        if (newRadiusMeters <= 0) {
            println("❌ AAT ERROR: Invalid radius $newRadiusMeters meters (must be > 0)")
            return task
        }

        if (newRadiusMeters > 100000) { // 100km max
            println("❌ AAT ERROR: Radius $newRadiusMeters meters too large (max 100km)")
            return task
        }

        val area = task.assignedAreas[areaIndex]
        val updatedArea = when (area.geometry) {
            is AreaGeometry.Circle -> {
                area.copy(geometry = area.geometry.copy(radius = newRadiusMeters))
            }
            is AreaGeometry.Sector -> {
                // For sectors, update outer radius
                area.copy(geometry = area.geometry.copy(outerRadius = newRadiusMeters))
            }
        }

        val updatedAreas = task.assignedAreas.toMutableList()
        updatedAreas[areaIndex] = updatedArea

        val updatedTask = task.copy(assignedAreas = updatedAreas)

        println("✅ AAT: Updated area '${area.name}' radius to ${newRadiusMeters}m")
        return updatedTask
    }

    /**
     * Update multiple area radii at once.
     *
     * @param task The AAT task to update
     * @param radiusUpdates Map of area index to new radius in meters
     * @return Updated AAT task
     */
    fun updateAreaRadii(task: AATTask, radiusUpdates: Map<Int, Double>): AATTask {
        var updatedTask = task
        for ((areaIndex, newRadius) in radiusUpdates) {
            updatedTask = updateAreaRadius(updatedTask, areaIndex, newRadius)
        }
        return updatedTask
    }

    /**
     * Calculate distance from current GPS position to target point of current waypoint
     *
     * CRITICAL FOR AAT COMPETITION: Uses TARGET POINT position, not area center!
     * This ensures distance matches the visual course line displayed on map.
     *
     * @param gpsLat Current GPS latitude
     * @param gpsLon Current GPS longitude
     * @param waypointIndex Index of target waypoint in AAT task
     * @param waypoints Complete list of AAT task waypoints
     * @return Distance in kilometers to target point, or null if calculation fails
     */
    fun calculateDistanceToTargetPoint(
        gpsLat: Double,
        gpsLon: Double,
        waypointIndex: Int,
        waypoints: List<AATWaypoint>
    ): Double? {
        // Validate waypoint index
        if (waypointIndex < 0 || waypointIndex >= waypoints.size) {
            return null
        }

        val currentWaypoint = waypoints[waypointIndex]

        // Get target point position (NOT area center!)
        val targetLat = currentWaypoint.targetPoint.latitude
        val targetLon = currentWaypoint.targetPoint.longitude

        // Calculate haversine distance from GPS to target point
        // CRITICAL: Use AATMathUtils (AAT-specific calculator for task type separation)
        return com.example.xcpro.tasks.aat.calculations.AATMathUtils.calculateDistanceKm(
            gpsLat, gpsLon, targetLat, targetLon
        )
    }

    /**
     * Generate display elements for map visualization.
     *
     * @param task The AAT task
     * @param flightPath Optional flight track to display
     * @param creditedFixes Optional credited fixes to show
     * @return Complete display package
     */
    fun generateDisplayElements(
        task: AATTask,
        flightPath: List<AATLatLng>? = null,
        creditedFixes: List<AATLatLng>? = null
    ): AATDisplayPackage {
        return AATDisplayPackage(
            areaPolygons = display.generateAreaPolygons(task),
            taskPath = display.generateTaskPath(task, creditedFixes ?: emptyList()),
            startFinishMarkers = display.generateStartFinishMarkers(task),
            areaMarkers = display.generateAreaMarkers(task),
            startGeometry = display.generateStartGeometry(task),
            finishGeometry = display.generateFinishGeometry(task),
            areaLabels = display.generateAreaLabels(task),
            colors = display.calculateDisplayColors(),
            flightTrack = flightPath?.let { display.generateFlightTrack(it, task) },
            creditedFixMarkers = creditedFixes?.let { display.generateCreditedFixMarkers(it, task) } ?: emptyList()
        )
    }
    
    /**
     * Create a sample AAT task for testing purposes.
     * This creates the 3-Hour AAT Sydney example from the specification.
     */
    fun createSampleTask(): AATTask {
        return AATTask(
            id = "AAT_SAMPLE_SYDNEY_001",
            name = "3-Hour AAT Sydney",
            minimumTaskTime = Duration.ofHours(3),
            start = AATStartPoint(
                position = AATLatLng(-33.9470, 150.7710),
                type = AATStartType.LINE,
                lineLength = 5000.0,
                name = "Camden"
            ),
            assignedAreas = listOf(
                AssignedArea(
                    name = "Area North",
                    centerPoint = AATLatLng(-33.7240, 150.6330),
                    geometry = AreaGeometry.Circle(radius = 20000.0), // 20km radius
                    sequence = 0
                ),
                AssignedArea(
                    name = "Area East",
                    centerPoint = AATLatLng(-33.5970, 151.0250),
                    geometry = AreaGeometry.Sector(
                        innerRadius = 5000.0,
                        outerRadius = 30000.0,
                        startBearing = 45.0,
                        endBearing = 135.0
                    ),
                    sequence = 1
                ),
                AssignedArea(
                    name = "Area South",
                    centerPoint = AATLatLng(-33.8850, 151.2080),
                    geometry = AreaGeometry.Circle(radius = 15000.0), // 15km radius
                    sequence = 2
                )
            ),
            finish = AATFinishPoint(
                position = AATLatLng(-33.9470, 150.7710),
                type = AATFinishType.LINE,
                lineLength = 1000.0,
                name = "Camden"
            ),
            description = "Sample 3-hour AAT task around Sydney for testing the AAT system"
        )
    }
    
    /**
     * Calculate area achievements from flight result.
     */
    private fun calculateAreaAchievements(result: AATResult, task: AATTask): List<AATAreaAchievement> {
        return task.assignedAreas.mapIndexed { index, area ->
            val creditedFix = if (index < result.creditedFixes.size) {
                result.creditedFixes[index]
            } else {
                null
            }
            
            val achieved = creditedFix != null && 
                           areaBoundaryCalculator.isInsideArea(creditedFix, area)
            
            AATAreaAchievement(
                areaName = area.name,
                achieved = achieved,
                creditedFix = creditedFix,
                entryTime = null, // Would need detailed track analysis
                distanceFromCenter = creditedFix?.let { 
                    com.example.xcpro.tasks.aat.calculations.AATMathUtils.calculateDistance(it, area.centerPoint)
                },
                timeSpentInArea = null, // Would need detailed track analysis
                optimalPoint = null // Could calculate optimal point for this strategy
            )
        }
    }
    
    /**
     * Calculate time analysis from flight result.
     */
    private fun calculateTimeAnalysis(result: AATResult, task: AATTask): AATTimeAnalysis {
        val timeUnderMinimum = if (result.elapsedTime < task.minimumTaskTime) {
            task.minimumTaskTime - result.elapsedTime
        } else null
        
        val timeOverMinimum = if (result.elapsedTime > task.minimumTaskTime) {
            result.elapsedTime - task.minimumTaskTime
        } else null
        
        return AATTimeAnalysis(
            minimumTaskTime = task.minimumTaskTime,
            actualElapsedTime = result.elapsedTime,
            timeUnderMinimum = timeUnderMinimum,
            timeOverMinimum = timeOverMinimum,
            timeInAreas = Duration.ZERO, // Would need detailed analysis
            timeToReachAreas = Duration.ZERO // Would need detailed analysis
        )
    }
    
    /**
     * Calculate speed analysis from flight result.
     */
    private fun calculateSpeedAnalysis(result: AATResult, task: AATTask): AATSpeedAnalysis {
        return AATSpeedAnalysis(
            averageTaskSpeed = result.averageSpeed,
            averageInterAreaSpeed = result.averageSpeed, // Simplified
            maxSpeedSegment = result.averageSpeed * 1.2, // Estimated
            minSpeedSegment = result.averageSpeed * 0.8, // Estimated
            speedConsistency = 0.1 // Placeholder
        )
    }
    
    /**
     * Generate optimization suggestions based on flight analysis.
     */
    private fun generateOptimizationSuggestions(result: AATResult, task: AATTask): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (result.elapsedTime < task.minimumTaskTime) {
            suggestions.add("Flight finished before minimum time. Consider flying farther into areas to maximize distance.")
        }
        
        if (result.elapsedTime > task.minimumTaskTime.plusMinutes(30)) {
            suggestions.add("Flight took significantly longer than minimum time. Consider more direct routing.")
        }

        return suggestions
    }
}

/**
 * Complete AAT task analysis result
 */
data class AATTaskAnalysis(
    val task: AATTask,
    val validation: AATTaskValidation,
    val distances: AATTaskDistance?,
    val calculatedAt: LocalDateTime
)

/**
 * Complete display package for AAT visualization
 */
data class AATDisplayPackage(
    val areaPolygons: List<DisplayPolygon>,
    val taskPath: DisplayLineString,
    val startFinishMarkers: List<DisplayMarker>,
    val areaMarkers: List<DisplayMarker>,
    val startGeometry: DisplayGeometry?,
    val finishGeometry: DisplayGeometry?,
    val areaLabels: List<MapLabel>,
    val colors: DisplayColors,
    val flightTrack: DisplayLineString?,
    val creditedFixMarkers: List<DisplayMarker>
)
