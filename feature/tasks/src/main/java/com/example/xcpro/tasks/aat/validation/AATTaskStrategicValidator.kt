package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATTaskDistance

/**
 * Evaluates strategic validity of a task based on distance range and speeds.
 */
internal class AATTaskStrategicValidator {
    private companion object {
        const val KMH_PER_MS = 3.6
        const val LOW_COMPETITIVE_SPEED_MS = 40.0 / KMH_PER_MS
        const val HIGH_COMPETITIVE_SPEED_MS = 150.0 / KMH_PER_MS
    }

    fun validate(task: AATTask, taskDistance: AATTaskDistance?): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        taskDistance?.let { distance ->
            val minTimeSeconds = task.minimumTaskTime.seconds.toDouble().coerceAtLeast(1.0)
            val minSpeedMs = distance.minimumDistance / minTimeSeconds
            val maxSpeedMs = distance.maximumDistance / minTimeSeconds

            if (maxSpeedMs < LOW_COMPETITIVE_SPEED_MS) {
                issues.add(
                    AATValidationIssue.warning(
                        "MAX_SPEED_LOW",
                        ValidationCategory.STRATEGIC_VALIDITY,
                        "Maximum achievable speed (${String.format("%.1f", maxSpeedMs * KMH_PER_MS)} km/h) is low for competitive gliding",
                        fix = "Consider larger areas or longer distances"
                    )
                )
            }

            if (minSpeedMs > HIGH_COMPETITIVE_SPEED_MS) {
                issues.add(
                    AATValidationIssue.warning(
                        "MIN_SPEED_HIGH",
                        ValidationCategory.STRATEGIC_VALIDITY,
                        "Minimum required speed (${String.format("%.1f", minSpeedMs * KMH_PER_MS)} km/h) is very high",
                        fix = "Consider shorter minimum distance or longer task time"
                    )
                )
            }

            val distanceRangeMeters = (distance.maximumDistance - distance.minimumDistance).coerceAtLeast(0.0)
            val percentageRange = if (distance.minimumDistance > 0.0) {
                (distanceRangeMeters / distance.minimumDistance) * 100.0
            } else {
                0.0
            }

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
