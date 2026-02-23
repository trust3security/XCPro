package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATStartPoint
import com.example.xcpro.tasks.aat.models.AATFinishPoint
import com.example.xcpro.tasks.aat.models.AATStartType
import com.example.xcpro.tasks.aat.models.AATFinishType
import com.example.xcpro.tasks.aat.models.AssignedArea
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.getAuthorityRadiusMeters
import java.util.UUID

/**
 * AAT Validation Bridge
 *
 * Bridges between AATTaskManager's SimpleAATTask model and the
 * comprehensive validation system. Converts tasks to validation-ready
 * format and provides high-level validation methods.
 *
 * REFACTORED FROM: AATTaskManager.kt (Stage 5 - Validation Extraction)
 * DEPENDENCIES: SimpleAATTask, AATValidationIntegration, AAT models
 */
class AATValidationBridge {

    /**
     * Check if AAT task is valid (basic check)
     *
     * Quick validation for minimum task requirements:
     * - At least 2 waypoints (start + finish)
     * - Minimum time greater than 0
     *
     * @param task The task to validate
     * @return true if task meets basic requirements
     */
    fun isTaskValid(task: SimpleAATTask): Boolean {
        return task.waypoints.size >= 2 && task.minimumTime.toMinutes() > 0
    }

    /**
     * Comprehensive AAT task validation with detailed results
     *
     * Performs full validation including FAI compliance, geometry checks,
     * and competition readiness. Returns detailed UI-friendly results.
     *
     * @param task The task to validate
     * @return Validation results with errors, warnings, and suggestions
     */
    fun validateTask(task: SimpleAATTask): ValidationUIResult {
        val aatTask = convertToAATTask(task)
        return AATValidationIntegration.validateTaskForUI(aatTask)
    }

    /**
     * Check if current task is ready for competition use
     *
     * Validates that the task meets all requirements for official
     * competition flying (FAI rules, minimum distances, etc.)
     *
     * @param task The task to check
     * @return true if task is competition-ready
     */
    fun isCompetitionReady(task: SimpleAATTask): Boolean {
        val aatTask = convertToAATTask(task)
        return AATValidationIntegration.isTaskFlyable(aatTask)
    }

    /**
     * Get task validation grade (A+ to F)
     *
     * Returns a letter grade summarizing overall task quality.
     * Useful for quick feedback to task setters.
     *
     * @param task The task to grade
     * @return Letter grade (A+, A, B, C, D, F)
     */
    fun getTaskGrade(task: SimpleAATTask): String {
        val aatTask = convertToAATTask(task)
        return AATValidationIntegration.getTaskValidationSummary(aatTask).grade
    }

    /**
     * Get validation summary for UI display
     *
     * Returns comprehensive summary including grade, error count,
     * warning count, and pass/fail status for each validation category.
     *
     * @param task The task to summarize
     * @return Validation summary with all metrics
     */
    fun getValidationSummary(task: SimpleAATTask): TaskValidationSummary {
        val aatTask = convertToAATTask(task)
        return AATValidationIntegration.getTaskValidationSummary(aatTask)
    }

    /**
     * Validate current task for specific competition class
     *
     * Checks task against rules for a specific glider class
     * (Club, Standard, 15m, 18m, Open, etc.)
     *
     * @param task The task to validate
     * @param competitionClass The competition class (e.g., "Club", "18m")
     * @return Competition-specific validation results with recommendations
     */
    fun validateForCompetition(task: SimpleAATTask, competitionClass: String): CompetitionValidationResult {
        val aatTask = convertToAATTask(task)
        return AATValidationIntegration.validateForCompetitionWithRecommendations(aatTask, competitionClass)
    }

    /**
     * Get improvement suggestions for current task
     *
     * Analyzes the task and provides actionable recommendations
     * to improve task quality, safety, and competition compliance.
     *
     * @param task The task to analyze
     * @return List of improvement suggestions
     */
    fun getTaskImprovementSuggestions(task: SimpleAATTask): List<String> {
        val aatTask = convertToAATTask(task)
        return AATValidationIntegration.getTaskImprovementSuggestions(aatTask)
    }

    // ==================== Model Conversion (Private) ====================

    /**
     * Convert SimpleAATTask to full AATTask model for validation
     *
     * The validation system requires the complete AATTask model with
     * all geometry and metadata. This method performs the conversion.
     *
     * @param task The simple task to convert
     * @return Full AATTask ready for validation
     */
    private fun convertToAATTask(task: SimpleAATTask): AATTask {
        // Convert waypoints to assigned areas
        val assignedAreas = task.waypoints
            .filter { it.role == AATWaypointRole.TURNPOINT }
            .mapIndexed { index, waypoint ->
                AssignedArea(
                    name = waypoint.title,
                    centerPoint = AATLatLng(waypoint.lat, waypoint.lon),
                    geometry = AreaGeometry.Circle(waypoint.assignedArea.radiusMeters),
                    sequence = index
                )
            }

        val startWaypoint = task.waypoints.firstOrNull { it.role == AATWaypointRole.START }
        val finishWaypoint = task.waypoints.firstOrNull { it.role == AATWaypointRole.FINISH }

        return AATTask(
            id = task.id.ifEmpty { UUID.randomUUID().toString() },
            name = "Current AAT Task",
            minimumTaskTime = task.minimumTime,
            start = AATStartPoint(
                position = AATLatLng(
                    startWaypoint?.lat ?: 0.0,
                    startWaypoint?.lon ?: 0.0
                ),
                type = AATStartType.LINE,
                //  SSOT FIX: Use authority radius instead of removed gateWidth property
                lineLength = startWaypoint?.getAuthorityRadiusMeters() ?: 5000.0
            ),
            assignedAreas = assignedAreas,
            finish = AATFinishPoint(
                position = AATLatLng(
                    finishWaypoint?.lat ?: 0.0,
                    finishWaypoint?.lon ?: 0.0
                ),
                type = AATFinishType.CIRCLE,
                //  SSOT FIX: Use authority radius instead of removed gateWidth property
                radius = finishWaypoint?.getAuthorityRadiusMeters() ?: 3000.0
            )
        )
    }
}
