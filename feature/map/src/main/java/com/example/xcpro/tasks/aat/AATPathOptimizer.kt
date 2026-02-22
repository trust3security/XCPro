package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AssignedArea
import java.time.Duration
import kotlin.math.max

class AATPathOptimizer {
    private val areaBoundaryCalculator = AreaBoundaryCalculator()
    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
    }

    fun calculateOptimalPath(
        task: AATTask,
        currentPosition: AATLatLng,
        elapsedTime: Duration,
        expectedSpeed: Double
    ): List<AATLatLng> {
        val remainingTime = task.minimumTaskTime - elapsedTime
        if (remainingTime <= Duration.ZERO) return calculateShortestRemainingPath(task, currentPosition)

        val remainingTimeHours = remainingTime.toMinutes() / 60.0
        val targetDistanceMeters = expectedSpeed * remainingTimeHours * METERS_PER_KILOMETER
        return calculatePathForTargetDistanceMeters(task, currentPosition, targetDistanceMeters)
    }

    fun calculatePathForTargetDistanceMeters(
        task: AATTask,
        currentPosition: AATLatLng,
        targetDistanceMeters: Double
    ): List<AATLatLng> {
        if (task.assignedAreas.isEmpty()) return listOf(task.finish.position)
        return AATPathOptimizerSupport.optimizePathForDistanceMeters(
            start = currentPosition,
            finish = task.finish.position,
            areas = task.assignedAreas,
            targetDistanceMeters = targetDistanceMeters,
            areaBoundaryCalculator = areaBoundaryCalculator
        )
    }

    fun calculateShortestRemainingPath(task: AATTask, currentPosition: AATLatLng): List<AATLatLng> =
        calculateBoundaryPath(task, currentPosition) { currentPoint, area ->
            areaBoundaryCalculator.nearestPointOnBoundary(currentPoint, area)
        }

    fun calculateLongestRemainingPath(task: AATTask, currentPosition: AATLatLng): List<AATLatLng> =
        calculateBoundaryPath(task, currentPosition) { currentPoint, area ->
            areaBoundaryCalculator.farthestPointOnBoundary(currentPoint, area)
        }

    fun calculateOptimalStrategy(
        task: AATTask,
        currentPosition: AATLatLng,
        elapsedTime: Duration,
        expectedSpeed: Double
    ): Double {
        val remainingTime = task.minimumTaskTime - elapsedTime
        if (remainingTime <= Duration.ZERO) return 0.0

        val remainingTimeHours = remainingTime.toMinutes() / 60.0
        val targetDistanceMeters = expectedSpeed * remainingTimeHours * METERS_PER_KILOMETER

        val shortestDistanceMeters = AATPathOptimizerSupport.calculatePathDistanceMeters(
            calculateShortestRemainingPath(task, currentPosition)
        )
        val longestDistanceMeters = AATPathOptimizerSupport.calculatePathDistanceMeters(
            calculateLongestRemainingPath(task, currentPosition)
        )

        if (longestDistanceMeters <= shortestDistanceMeters) return 0.0
        return (
            (targetDistanceMeters - shortestDistanceMeters) /
                (longestDistanceMeters - shortestDistanceMeters)
            )
            .coerceIn(0.0, 1.0)
    }

    fun calculateRecommendedPoints(
        task: AATTask,
        currentPosition: AATLatLng,
        strategy: Double
    ): List<AATLatLng> {
        val clampedStrategy = strategy.coerceIn(0.0, 1.0)
        val waypoints = mutableListOf<AATLatLng>()
        var currentPoint = currentPosition

        for (index in task.assignedAreas.indices) {
            val area = task.assignedAreas[index]
            val nearestPoint = areaBoundaryCalculator.nearestPointOnBoundary(currentPoint, area)
            val farthestPoint = areaBoundaryCalculator.farthestPointOnBoundary(currentPoint, area)
            val recommendedPoint = AATPathOptimizerSupport.interpolatePoints(
                point1 = nearestPoint,
                point2 = farthestPoint,
                fraction = clampedStrategy
            )
            waypoints.add(recommendedPoint)
            currentPoint = recommendedPoint
        }

        return waypoints
    }

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
            return PathRecommendation(
                nextWaypoint = task.finish.position,
                recommendedStrategy = 0.0,
                timeToMinimum = remainingTime,
                distanceRemainingMeters = AATMathUtils.calculateDistance(
                    currentPosition,
                    task.finish.position
                ) * METERS_PER_KILOMETER
            )
        }

        val shortestRemainingMeters = AATPathOptimizerSupport.calculateShortestPathDistanceMeters(
            start = currentPosition,
            areas = remainingAreas,
            finish = task.finish.position,
            areaBoundaryCalculator = areaBoundaryCalculator
        )
        val longestRemainingMeters = AATPathOptimizerSupport.calculateLongestPathDistanceMeters(
            start = currentPosition,
            areas = remainingAreas,
            finish = task.finish.position,
            areaBoundaryCalculator = areaBoundaryCalculator
        )

        val remainingTimeHours = max(remainingTime.toMinutes() / 60.0, 0.0)
        val targetDistanceMeters = if (remainingTimeHours > 0 && groundSpeed > 0) {
            groundSpeed * remainingTimeHours * METERS_PER_KILOMETER
        } else {
            shortestRemainingMeters
        }

        val strategy = if (longestRemainingMeters > shortestRemainingMeters) {
            (targetDistanceMeters - shortestRemainingMeters) /
                (longestRemainingMeters - shortestRemainingMeters)
        } else {
            0.0
        }.coerceIn(0.0, 1.0)

        val nextArea = remainingAreas.first()
        val nearestPoint = areaBoundaryCalculator.nearestPointOnBoundary(currentPosition, nextArea)
        val farthestPoint = areaBoundaryCalculator.farthestPointOnBoundary(currentPosition, nextArea)
        val recommendedPoint = AATPathOptimizerSupport.interpolatePoints(
            point1 = nearestPoint,
            point2 = farthestPoint,
            fraction = strategy
        )

        val recommendedPath = calculateRecommendedPoints(
            task = task.copy(assignedAreas = remainingAreas),
            currentPosition = currentPosition,
            strategy = strategy
        )
        val remainingPathDistanceMeters = AATPathOptimizerSupport.calculatePathDistanceMeters(
            listOf(currentPosition) + recommendedPath + task.finish.position
        )

        return PathRecommendation(
            nextWaypoint = recommendedPoint,
            recommendedStrategy = strategy,
            timeToMinimum = remainingTime,
            distanceRemainingMeters = remainingPathDistanceMeters
        )
    }

    private fun calculateBoundaryPath(
        task: AATTask,
        currentPosition: AATLatLng,
        pickPoint: (AATLatLng, AssignedArea) -> AATLatLng
    ): List<AATLatLng> {
        val waypoints = mutableListOf<AATLatLng>()
        var currentPoint = currentPosition
        for (area in task.assignedAreas) {
            val areaPoint = pickPoint(currentPoint, area)
            waypoints.add(areaPoint)
            currentPoint = areaPoint
        }
        waypoints.add(task.finish.position)
        return waypoints
    }
}

data class PathRecommendation(
    val nextWaypoint: AATLatLng,
    val recommendedStrategy: Double,
    val timeToMinimum: Duration,
    val distanceRemainingMeters: Double
)
