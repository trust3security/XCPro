package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATTask

/**
 * Validates safety and airspace considerations for AAT tasks.
 */
internal class AATTaskSafetyValidator {

    fun validate(task: AATTask): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        task.assignedAreas.forEachIndexed { i, area1 ->
            task.assignedAreas.drop(i + 1).forEach { area2 ->
                val distance = AATMathUtils.calculateDistance(area1.centerPoint, area2.centerPoint)
                val distanceKm = distance / 1000.0

                if (distanceKm < 5.0) {
                    issues.add(
                        AATValidationIssue.info(
                            "AREAS_VERY_CLOSE",
                            ValidationCategory.AIRSPACE_SAFETY,
                            "Areas '${area1.name}' and '${area2.name}' are very close (${String.format("%.1f", distanceKm)}km) - consider pilot traffic",
                            fix = "Consider increased separation for safety in competitions"
                        )
                    )
                }
            }
        }

        task.assignedAreas.forEach { area ->
            val areaSize = area.getApproximateAreaSizeKm2()
            if (areaSize > 1000.0) {
                issues.add(
                    AATValidationIssue.info(
                        "LARGE_AREA_TERRAIN",
                        ValidationCategory.AIRSPACE_SAFETY,
                        "Large area '${area.name}' (${String.format("%.0f", areaSize)} km2) may include varied terrain/weather",
                        fix = "Verify area doesn't include unsuitable terrain or restricted airspace"
                    )
                )
            }
        }

        return issues
    }
}
