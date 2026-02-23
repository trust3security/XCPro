package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AssignedArea
import kotlin.math.abs

internal object AATPathOptimizerSupport {

    private const val OPTIMIZATION_TOLERANCE_METERS = 100.0
    private const val MAX_OPTIMIZATION_ITERATIONS = 20

    fun optimizePathForDistanceMeters(
        start: AATLatLng,
        finish: AATLatLng,
        areas: List<AssignedArea>,
        targetDistanceMeters: Double,
        areaBoundaryCalculator: AreaBoundaryCalculator
    ): List<AATLatLng> {
        var minStrategy = 0.0
        var maxStrategy = 1.0
        var bestStrategy = 0.5

        repeat(MAX_OPTIMIZATION_ITERATIONS) {
            val testPoints = calculatePointsForStrategy(
                start = start,
                areas = areas,
                strategy = bestStrategy,
                areaBoundaryCalculator = areaBoundaryCalculator
            )
            val testDistanceMeters = calculatePathDistanceMeters(listOf(start) + testPoints + finish)

            when {
                abs(testDistanceMeters - targetDistanceMeters) <= OPTIMIZATION_TOLERANCE_METERS -> {
                    return testPoints
                }
                testDistanceMeters < targetDistanceMeters -> {
                    minStrategy = bestStrategy
                }
                else -> {
                    maxStrategy = bestStrategy
                }
            }

            bestStrategy = (minStrategy + maxStrategy) / 2.0
        }

        return calculatePointsForStrategy(
            start = start,
            areas = areas,
            strategy = bestStrategy,
            areaBoundaryCalculator = areaBoundaryCalculator
        )
    }

    fun calculatePointsForStrategy(
        start: AATLatLng,
        areas: List<AssignedArea>,
        strategy: Double,
        areaBoundaryCalculator: AreaBoundaryCalculator
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

    fun calculateShortestPathDistanceMeters(
        start: AATLatLng,
        areas: List<AssignedArea>,
        finish: AATLatLng,
        areaBoundaryCalculator: AreaBoundaryCalculator
    ): Double {
        val shortestPoints = calculatePointsForStrategy(
            start = start,
            areas = areas,
            strategy = 0.0,
            areaBoundaryCalculator = areaBoundaryCalculator
        )
        return calculatePathDistanceMeters(listOf(start) + shortestPoints + finish)
    }

    fun calculateLongestPathDistanceMeters(
        start: AATLatLng,
        areas: List<AssignedArea>,
        finish: AATLatLng,
        areaBoundaryCalculator: AreaBoundaryCalculator
    ): Double {
        val longestPoints = calculatePointsForStrategy(
            start = start,
            areas = areas,
            strategy = 1.0,
            areaBoundaryCalculator = areaBoundaryCalculator
        )
        return calculatePathDistanceMeters(listOf(start) + longestPoints + finish)
    }

    fun calculatePathDistanceMeters(waypoints: List<AATLatLng>): Double {
        if (waypoints.size < 2) return 0.0

        var totalDistanceMeters = 0.0
        for (i in 1 until waypoints.size) {
            totalDistanceMeters += AATMathUtils.calculateDistanceMeters(
                waypoints[i - 1],
                waypoints[i]
            )
        }
        return totalDistanceMeters
    }

    fun interpolatePoints(point1: AATLatLng, point2: AATLatLng, fraction: Double): AATLatLng {
        return AATMathUtils.interpolatePosition(point1, point2, fraction)
    }
}
