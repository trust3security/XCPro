package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATAreaAchievement
import com.example.xcpro.tasks.aat.models.AATFinishPoint
import com.example.xcpro.tasks.aat.models.AATFinishType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATPerformanceAnalysis
import com.example.xcpro.tasks.aat.models.AATResult
import com.example.xcpro.tasks.aat.models.AATSpeedAnalysis
import com.example.xcpro.tasks.aat.models.AATStartPoint
import com.example.xcpro.tasks.aat.models.AATStartType
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATTimeAnalysis
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.AssignedArea
import java.time.Duration

internal fun calculateAreaAchievementsForResult(
    result: AATResult,
    task: AATTask,
    areaBoundaryCalculator: AreaBoundaryCalculator
): List<AATAreaAchievement> {
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
            entryTime = null,
            distanceFromCenter = creditedFix?.let {
                AATMathUtils.calculateDistanceMeters(it, area.centerPoint)
            },
            timeSpentInArea = null,
            optimalPoint = null
        )
    }
}

internal fun calculateTimeAnalysisForResult(result: AATResult, task: AATTask): AATTimeAnalysis {
    val timeUnderMinimum = if (result.elapsedTime < task.minimumTaskTime) {
        task.minimumTaskTime - result.elapsedTime
    } else {
        null
    }

    val timeOverMinimum = if (result.elapsedTime > task.minimumTaskTime) {
        result.elapsedTime - task.minimumTaskTime
    } else {
        null
    }

    return AATTimeAnalysis(
        minimumTaskTime = task.minimumTaskTime,
        actualElapsedTime = result.elapsedTime,
        timeUnderMinimum = timeUnderMinimum,
        timeOverMinimum = timeOverMinimum,
        timeInAreas = Duration.ZERO,
        timeToReachAreas = Duration.ZERO
    )
}

internal fun calculateSpeedAnalysisForResult(result: AATResult): AATSpeedAnalysis {
    return AATSpeedAnalysis(
        averageTaskSpeedMs = result.averageSpeedMs,
        averageInterAreaSpeedMs = result.averageSpeedMs,
        maxSpeedSegmentMs = result.averageSpeedMs * 1.2,
        minSpeedSegmentMs = result.averageSpeedMs * 0.8,
        speedConsistency = 0.1
    )
}

internal fun generateOptimizationSuggestionsForResult(result: AATResult, task: AATTask): List<String> {
    val suggestions = mutableListOf<String>()

    if (result.elapsedTime < task.minimumTaskTime) {
        suggestions.add("Flight finished before minimum time. Consider flying farther into areas to maximize distance.")
    }

    if (result.elapsedTime > task.minimumTaskTime.plusMinutes(30)) {
        suggestions.add("Flight took significantly longer than minimum time. Consider more direct routing.")
    }

    return suggestions
}

internal fun createSampleAatTask(): AATTask {
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
                geometry = AreaGeometry.Circle(radius = 20000.0),
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
                geometry = AreaGeometry.Circle(radius = 15000.0),
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

internal fun buildPerformanceAnalysis(
    result: AATResult,
    task: AATTask,
    areaBoundaryCalculator: AreaBoundaryCalculator
): AATPerformanceAnalysis {
    return AATPerformanceAnalysis(
        result = result,
        areaAchievements = calculateAreaAchievementsForResult(result, task, areaBoundaryCalculator),
        timeAnalysis = calculateTimeAnalysisForResult(result, task),
        speedAnalysis = calculateSpeedAnalysisForResult(result),
        optimizationSuggestions = generateOptimizationSuggestionsForResult(result, task)
    )
}
