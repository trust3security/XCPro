package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATTaskDistance

/**
 * Evaluates strategic validity of a task based on distance range and speeds.
 */
internal class AATTaskStrategicValidator {

    fun validate(task: AATTask, taskDistance: AATTaskDistance?): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        taskDistance?.let { distance ->
            val minTimeHours = task.minimumTaskTime.toMinutes() / 60.0
            val minSpeed = (distance.minimumDistance / 1000.0) / minTimeHours
            val maxSpeed = (distance.maximumDistance / 1000.0) / minTimeHours

            if (maxSpeed < 40.0) {
                issues.add(
                    AATValidationIssue.warning(
                        "MAX_SPEED_LOW",
                        ValidationCategory.STRATEGIC_VALIDITY,
                        "Maximum achievable speed (${String.format("%.1f", maxSpeed)} km/h) is low for competitive gliding",
                        fix = "Consider larger areas or longer distances"
                    )
                )
            }

            if (minSpeed > 150.0) {
                issues.add(
                    AATValidationIssue.warning(
                        "MIN_SPEED_HIGH",
                        ValidationCategory.STRATEGIC_VALIDITY,
                        "Minimum required speed (${String.format("%.1f", minSpeed)} km/h) is very high",
                        fix = "Consider shorter minimum distance or longer task time"
                    )
                )
            }

            val distanceRangeKm = (distance.maximumDistance - distance.minimumDistance) / 1000.0
            val percentageRange = (distanceRangeKm / (distance.minimumDistance / 1000.0)) * 100.0

            if (percentageRange < 15.0) {
                issues.add(
                    AATValidationIssue.info(
                        "LIMITED_STRATEGIC_OPTIONS",
                        ValidationCategory.STRATEGIC_VALIDITY,
                        "Distance range (${String.format("%.1f", percentageRange)}%) provides limited strategic options",
                        fix = "Consider larger areas or different positioning for more strategic choice"
                    )
                )
            }

            if (percentageRange > 50.0) {
                issues.add(
                    AATValidationIssue.info(
                        "WIDE_STRATEGIC_OPTIONS",
                        ValidationCategory.STRATEGIC_VALIDITY,
                        "Distance range (${String.format("%.1f", percentageRange)}%) is very wide - excellent strategic options",
                    )
                )
            }
        }

        return issues
    }
}
