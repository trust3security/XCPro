package com.trust3.xcpro.tasks.aat.validation

import com.trust3.xcpro.tasks.aat.models.AATTask

/**
 * Validates basic task structure requirements (naming, IDs, minimum time, assigned areas).
 */
internal class AATTaskStructureValidator {

    fun validate(task: AATTask): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        if (task.name.isBlank()) {
            issues.add(
                AATValidationIssue.critical(
                    "MISSING_TASK_NAME",
                    ValidationCategory.TASK_STRUCTURE,
                    "Task must have a name",
                    fix = "Provide a descriptive task name"
                )
            )
        }

        if (task.id.isBlank()) {
            issues.add(
                AATValidationIssue.critical(
                    "MISSING_TASK_ID",
                    ValidationCategory.TASK_STRUCTURE,
                    "Task must have a unique identifier",
                    fix = "Generate or assign a unique task ID"
                )
            )
        }

        if (task.minimumTaskTime.isNegative || task.minimumTaskTime.isZero) {
            issues.add(
                AATValidationIssue.critical(
                    "INVALID_MIN_TIME",
                    ValidationCategory.TASK_STRUCTURE,
                    "Minimum task time must be positive",
                    "FAI 3.2.1",
                    "Set minimum task time to valid duration (e.g., 2.5 hours)"
                )
            )
        }

        if (task.assignedAreas.isEmpty()) {
            issues.add(
                AATValidationIssue.critical(
                    "NO_ASSIGNED_AREAS",
                    ValidationCategory.TASK_STRUCTURE,
                    "AAT task must have at least one assigned area",
                    "FAI 3.2.2",
                    "Add assigned areas to create valid AAT task"
                )
            )
        }

        val areaNames = task.assignedAreas.map { it.name }
        val duplicates = areaNames.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            issues.add(
                AATValidationIssue.warning(
                    "DUPLICATE_AREA_NAMES",
                    ValidationCategory.TASK_STRUCTURE,
                    "Duplicate area names found: ${duplicates.joinToString(", ")}",
                    fix = "Use unique names for all assigned areas"
                )
            )
        }

        return issues
    }
}
