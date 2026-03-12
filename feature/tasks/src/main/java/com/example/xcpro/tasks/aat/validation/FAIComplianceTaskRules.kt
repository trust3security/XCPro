package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.models.AATTask

internal object FAIComplianceTaskRules {

    fun validateTaskTime(
        task: AATTask,
        competitionClass: FAIComplianceRules.CompetitionClass?
    ): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()
        val taskTimeMinutes = task.minimumTaskTime.toMinutes()

        if (taskTimeMinutes < FAIComplianceRules.CoreRequirements.MINIMUM_TASK_TIME_MINUTES) {
            issues.add(
                AATValidationIssue.critical(
                    "MIN_TIME_TOO_SHORT",
                    ValidationCategory.DISTANCE_TIME,
                    "Minimum task time ${taskTimeMinutes}m is below FAI minimum of ${FAIComplianceRules.CoreRequirements.MINIMUM_TASK_TIME_MINUTES}m",
                    "FAI 3.2.1",
                    "Increase minimum task time to at least ${FAIComplianceRules.CoreRequirements.MINIMUM_TASK_TIME_MINUTES} minutes"
                )
            )
        }

        if (taskTimeMinutes > FAIComplianceRules.CoreRequirements.MAXIMUM_TASK_TIME_HOURS * 60) {
            issues.add(
                AATValidationIssue.critical(
                    "MIN_TIME_TOO_LONG",
                    ValidationCategory.DISTANCE_TIME,
                    "Minimum task time ${taskTimeMinutes}m exceeds FAI maximum of ${FAIComplianceRules.CoreRequirements.MAXIMUM_TASK_TIME_HOURS}h",
                    "FAI 3.2.1",
                    "Reduce minimum task time to maximum ${FAIComplianceRules.CoreRequirements.MAXIMUM_TASK_TIME_HOURS} hours"
                )
            )
        }

        competitionClass?.let { cls ->
            if (task.minimumTaskTime < cls.minTaskTime) {
                issues.add(
                    AATValidationIssue.warning(
                        "CLASS_MIN_TIME",
                        ValidationCategory.COMPETITION_RULES,
                        "${cls.displayName} typically requires minimum ${cls.minTaskTime.toMinutes()}m, current: ${taskTimeMinutes}m",
                        "FAI Annex A",
                        "Consider increasing minimum task time for ${cls.displayName} compliance"
                    )
                )
            }

            cls.maxTaskTime?.let { maxTime ->
                if (task.minimumTaskTime > maxTime) {
                    issues.add(
                        AATValidationIssue.warning(
                            "CLASS_MAX_TIME",
                            ValidationCategory.COMPETITION_RULES,
                            "${cls.displayName} maximum is ${maxTime.toMinutes()}m, current: ${taskTimeMinutes}m",
                            "FAI Annex A",
                            "Consider reducing minimum task time for ${cls.displayName} compliance"
                        )
                    )
                }
            }
        }

        return issues
    }

    fun validateStartFinish(task: AATTask): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        task.maxStartAltitude?.let { maxAlt ->
            if (maxAlt < FAIComplianceRules.CoreRequirements.MINIMUM_START_ALTITUDE_MSL) {
                issues.add(
                    AATValidationIssue.critical(
                        "START_ALT_TOO_LOW",
                        ValidationCategory.START_FINISH,
                        "Maximum start altitude ${maxAlt}m is below minimum ${FAIComplianceRules.CoreRequirements.MINIMUM_START_ALTITUDE_MSL}m MSL",
                        "FAI 3.3.1",
                        "Set realistic start altitude above ${FAIComplianceRules.CoreRequirements.MINIMUM_START_ALTITUDE_MSL}m MSL"
                    )
                )
            }

            if (maxAlt > FAIComplianceRules.CoreRequirements.MAXIMUM_START_ALTITUDE_MSL) {
                issues.add(
                    AATValidationIssue.warning(
                        "START_ALT_VERY_HIGH",
                        ValidationCategory.START_FINISH,
                        "Maximum start altitude ${maxAlt}m is very high for typical competition",
                        "FAI 3.3.1",
                        "Consider typical competition start altitude (1000-2000m MSL)"
                    )
                )
            }
        }

        when {
            task.start.lineLength != null && task.start.lineLength!! < 1000.0 -> {
                issues.add(
                    AATValidationIssue.warning(
                        "START_LINE_SHORT",
                        ValidationCategory.START_FINISH,
                        "Start line ${String.format("%.0f", task.start.lineLength!!)}m is short for competition",
                        fix = "Consider 5-10km start line length for fair starts"
                    )
                )
            }

            task.start.radius != null && task.start.radius!! < 500.0 -> {
                issues.add(
                    AATValidationIssue.warning(
                        "START_CYLINDER_SMALL",
                        ValidationCategory.START_FINISH,
                        "Start cylinder ${String.format("%.0f", task.start.radius!!)}m radius is small",
                        fix = "Consider 1-3km start cylinder radius"
                    )
                )
            }
        }

        when {
            task.finish.lineLength != null && task.finish.lineLength!! < 1000.0 -> {
                issues.add(
                    AATValidationIssue.warning(
                        "FINISH_LINE_SHORT",
                        ValidationCategory.START_FINISH,
                        "Finish line ${String.format("%.0f", task.finish.lineLength!!)}m is short",
                        fix = "Consider 2-5km finish line for clear finish determination"
                    )
                )
            }

            task.finish.radius != null && task.finish.radius!! < 500.0 -> {
                issues.add(
                    AATValidationIssue.warning(
                        "FINISH_CYLINDER_SMALL",
                        ValidationCategory.START_FINISH,
                        "Finish cylinder ${String.format("%.0f", task.finish.radius!!)}m radius is small",
                        fix = "Consider 1-3km finish cylinder radius"
                    )
                )
            }
        }

        return issues
    }
}
