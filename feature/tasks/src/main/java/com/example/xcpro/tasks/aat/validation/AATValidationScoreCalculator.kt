package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATTaskDistance

/**
 * Computes aggregate validation scores for AAT tasks.
 */
internal class AATValidationScoreCalculator {
    private companion object {
        const val MIN_AREA_SIZE_M2_FOR_GEOMETRY_SCORE = 5_000_000.0
    }

    fun calculate(
        criticalCount: Int,
        warningCount: Int,
        infoCount: Int,
        task: AATTask,
        taskDistance: AATTaskDistance?
    ): AATValidationScore {

        val structureScore = when {
            criticalCount > 0 -> 0.0
            warningCount == 0 -> 100.0
            warningCount <= 2 -> 85.0
            else -> 70.0
        }

        val geometryScore = when {
            task.assignedAreas.isEmpty() -> 0.0
            task.assignedAreas.size > FAIComplianceRules.CoreRequirements.MAXIMUM_AREAS -> 50.0
            task.assignedAreas.any { it.getApproximateAreaSizeM2() < MIN_AREA_SIZE_M2_FOR_GEOMETRY_SCORE } -> 75.0
            else -> 95.0
        }

        val rulesScore = when {
            criticalCount > 0 -> 0.0
            task.minimumTaskTime.toMinutes() < 150 -> 80.0
            task.minimumTaskTime.toMinutes() > 360 -> 85.0
            else -> 100.0
        }

        val strategicScore = taskDistance?.let { distance ->
            val rangeMeters = (distance.maximumDistance - distance.minimumDistance).coerceAtLeast(0.0)
            val percentRange = if (distance.minimumDistance > 0.0) {
                (rangeMeters / distance.minimumDistance) * 100.0
            } else {
                0.0
            }

            when {
                percentRange < 10.0 -> 60.0
                percentRange < 20.0 -> 85.0
                percentRange < 40.0 -> 100.0
                else -> 90.0
            }
        } ?: 50.0

        val safetyScore = when {
            infoCount > 5 -> 80.0
            infoCount > 2 -> 90.0
            else -> 100.0
        }

        val overallScore = (structureScore + geometryScore + rulesScore + strategicScore + safetyScore) / 5.0

        return AATValidationScore(
            overallScore = overallScore,
            structureScore = structureScore,
            geometryScore = geometryScore,
            rulesScore = rulesScore,
            strategicScore = strategicScore,
            safetyScore = safetyScore
        )
    }
}
