package com.trust3.xcpro.tasks.aat.validation

import com.trust3.xcpro.tasks.aat.calculations.AATMathUtils
import com.trust3.xcpro.tasks.aat.models.AATTask

/**
 * Validates safety and airspace considerations for AAT tasks.
 */
internal class AATTaskSafetyValidator {
    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
        const val SQUARE_METERS_PER_SQUARE_KILOMETER = 1_000_000.0
        const val LARGE_AREA_THRESHOLD_M2 = 1_000.0 * SQUARE_METERS_PER_SQUARE_KILOMETER
    }

    fun validate(task: AATTask): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        task.assignedAreas.forEachIndexed { i, area1 ->
            task.assignedAreas.drop(i + 1).forEach { area2 ->
                val distanceMeters = AATMathUtils.calculateDistanceMeters(
                    area1.centerPoint,
                    area2.centerPoint
                )
                val distanceKm = distanceMeters / METERS_PER_KILOMETER

                if (distanceMeters < 5000.0) {
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
            val areaSizeM2 = area.getApproximateAreaSizeM2()
            if (areaSizeM2 > LARGE_AREA_THRESHOLD_M2) {
                val areaSizeKm2 = areaSizeM2 / SQUARE_METERS_PER_SQUARE_KILOMETER
                issues.add(
                    AATValidationIssue.info(
                        "LARGE_AREA_TERRAIN",
                        ValidationCategory.AIRSPACE_SAFETY,
                        "Large area '${area.name}' (${String.format("%.0f", areaSizeKm2)} km2) may include varied terrain/weather",
                        fix = "Verify area doesn't include unsuitable terrain or restricted airspace"
                    )
                )
            }
        }

        return issues
    }
}
