package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.example.xcpro.tasks.aat.calculations.AATSpeedCalculator
import com.example.xcpro.tasks.aat.models.AATFlightStatus
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATResult
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AreaGeometry
import java.time.Duration
import java.time.LocalDateTime

class AATTaskCalculator {
    private val areaBoundaryCalculator = AreaBoundaryCalculator()
    private val speedCalculator = AATSpeedCalculator()
    private val pathOptimizer = AATPathOptimizer()
    private val validator = AATTaskValidator()
    private val display = AATTaskDisplay()

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

    fun calculateFlightResult(
        task: AATTask,
        flightPath: List<AATLatLng>,
        startTime: LocalDateTime,
        finishTime: LocalDateTime,
        pilotName: String = ""
    ): AATResult {
        val flightValidation = validator.validateFlight(task, flightPath)
        val creditedFixes = calculateCreditedFixes(task, flightPath)
        val actualDistance = 100.0

        val elapsedTime = Duration.between(startTime, finishTime)
        val scoringTime = maxOf(elapsedTime, task.minimumTaskTime)
        val averageSpeedMs = speedCalculator.calculateAATSpeedMs(actualDistance, elapsedTime, task.minimumTaskTime)

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
            averageSpeedMs = averageSpeedMs,
            creditedFixes = creditedFixes,
            flightPath = flightPath,
            startTime = startTime,
            finishTime = finishTime,
            taskStatus = taskStatus
        )
    }

    fun calculateCreditedFixes(task: AATTask, flightPath: List<AATLatLng>): List<AATLatLng> {
        val creditedFixes = mutableListOf<AATLatLng>()

        for (area in task.assignedAreas) {
            val creditedFix = areaBoundaryCalculator.calculateCreditedFix(flightPath, area)
            if (creditedFix != null) {
                creditedFixes.add(creditedFix)
            } else {
                creditedFixes.add(area.centerPoint)
            }
        }

        return creditedFixes
    }

    fun calculateRealTimeRecommendation(
        task: AATTask,
        currentPosition: AATLatLng,
        elapsedTime: Duration,
        groundSpeed: Double,
        areasCompleted: Int = 0
    ) = pathOptimizer.calculateRealTimeRecommendation(
        task,
        currentPosition,
        elapsedTime,
        groundSpeed,
        areasCompleted
    )

    fun calculatePathForDistanceMeters(
        task: AATTask,
        currentPosition: AATLatLng,
        targetDistanceMeters: Double
    ): List<AATLatLng> {
        return pathOptimizer.calculatePathForTargetDistanceMeters(
            task = task,
            currentPosition = currentPosition,
            targetDistanceMeters = targetDistanceMeters
        )
    }

    fun calculateDistanceForTargetSpeedMs(
        task: AATTask,
        elapsedTime: Duration,
        targetSpeedMs: Double
    ): Double {
        return speedCalculator.calculateDistanceForTargetSpeedMs(
            targetSpeedMs,
            elapsedTime,
            task.minimumTaskTime
        )
    }

    fun analyzeFlightPerformance(result: AATResult, task: AATTask) =
        buildPerformanceAnalysis(result, task, areaBoundaryCalculator)

    fun updateAreaRadius(task: AATTask, areaIndex: Int, newRadiusMeters: Double): AATTask {
        if (areaIndex < 0 || areaIndex >= task.assignedAreas.size) {
            return task
        }

        if (newRadiusMeters <= 0) {
            return task
        }

        if (newRadiusMeters > 100000) {
            return task
        }

        val area = task.assignedAreas[areaIndex]
        val updatedArea = when (area.geometry) {
            is AreaGeometry.Circle -> {
                area.copy(geometry = area.geometry.copy(radius = newRadiusMeters))
            }
            is AreaGeometry.Sector -> {
                area.copy(geometry = area.geometry.copy(outerRadius = newRadiusMeters))
            }
        }

        val updatedAreas = task.assignedAreas.toMutableList()
        updatedAreas[areaIndex] = updatedArea
        return task.copy(assignedAreas = updatedAreas)
    }

    fun updateAreaRadii(task: AATTask, radiusUpdates: Map<Int, Double>): AATTask {
        var updatedTask = task
        for ((areaIndex, newRadius) in radiusUpdates) {
            updatedTask = updateAreaRadius(updatedTask, areaIndex, newRadius)
        }
        return updatedTask
    }

    fun calculateDistanceToTargetPointMeters(
        gpsLat: Double,
        gpsLon: Double,
        waypointIndex: Int,
        waypoints: List<AATWaypoint>
    ): Double? {
        if (waypointIndex < 0 || waypointIndex >= waypoints.size) {
            return null
        }

        val currentWaypoint = waypoints[waypointIndex]
        val targetLat = currentWaypoint.targetPoint.latitude
        val targetLon = currentWaypoint.targetPoint.longitude

        return com.example.xcpro.tasks.aat.calculations.AATMathUtils.calculateDistanceMeters(
            gpsLat,
            gpsLon,
            targetLat,
            targetLon
        )
    }

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

    fun createSampleTask(): AATTask = createSampleAatTask()
}
