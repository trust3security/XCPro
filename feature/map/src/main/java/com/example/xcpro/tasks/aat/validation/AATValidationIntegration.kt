package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.AATTaskValidator
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATTaskValidation

/**
 * Integration utilities for AAT validation with existing task management system.
 * Provides easy-to-use validation methods for UI and task creation workflows.
 */
object AATValidationIntegration {

    private val validator = AATTaskValidator()

    /**
     * Validate task during creation/editing with user-friendly results
     */
    fun validateTaskForUI(task: AATTask): ValidationUIResult {
        val comprehensive = validator.validateTaskComprehensive(task)

        return ValidationUIResult(
            status = comprehensive.getValidationStatus(),
            summary = comprehensive.getSummary(),
            criticalIssues = comprehensive.criticalErrors.map { it.getFormattedMessage() },
            warnings = comprehensive.warnings.map { it.getFormattedMessage() },
            suggestions = comprehensive.infoSuggestions.map { it.getFormattedMessage() },
            score = comprehensive.validationScore.getGrade(),
            competitionReady = comprehensive.isCompetitionReady()
        )
    }

    /**
     * Quick validation check for task operations
     */
    fun isTaskValid(task: AATTask): Boolean {
        return validator.validateTask(task).isValid
    }

    /**
     * Get simple pass/fail status with basic error message
     */
    fun getValidationStatus(task: AATTask): Pair<Boolean, String> {
        val result = validator.validateTask(task)
        return if (result.isValid) {
            Pair(true, "Task is valid")
        } else {
            val firstError = result.errors.firstOrNull() ?: "Unknown validation error"
            Pair(false, firstError)
        }
    }

    /**
     * Validate for specific competition with recommendations
     */
    fun validateForCompetitionWithRecommendations(
        task: AATTask,
        competitionClass: String
    ): CompetitionValidationResult {
        val result = validator.validateForCompetition(task, competitionClass)

        val recommendations = mutableListOf<String>()

        // Generate specific recommendations based on issues
        result.criticalErrors.forEach { issue ->
            issue.suggestedFix?.let { fix ->
                recommendations.add("CRITICAL: $fix")
            }
        }

        result.warnings.forEach { issue ->
            issue.suggestedFix?.let { fix ->
                recommendations.add("WARNING: $fix")
            }
        }

        result.infoSuggestions.forEach { issue ->
            issue.suggestedFix?.let { fix ->
                recommendations.add("SUGGESTION: $fix")
            }
        }

        return CompetitionValidationResult(
            suitable = result.isCompetitionReady(),
            competitionClass = competitionClass,
            compliance = result.competitionCompliance.getCompliancePercentage(),
            grade = result.validationScore.getGrade(),
            recommendations = recommendations,
            issues = result.criticalErrors.size + result.warnings.size
        )
    }

    /**
     * Get validation summary for task list display
     */
    fun getTaskValidationSummary(task: AATTask): TaskValidationSummary {
        val quick = validator.validateTask(task)
        val grade = validator.getTaskGrade(task)
        val competitionReady = validator.isCompetitionReady(task)

        return TaskValidationSummary(
            valid = quick.isValid,
            grade = grade,
            competitionReady = competitionReady,
            issueCount = quick.errors.size + quick.warnings.size,
            distance = quick.taskDistance?.getDistanceRangeFormatted() ?: "Unknown"
        )
    }

    /**
     * Check if task meets minimum requirements for flying
     */
    fun isTaskFlyable(task: AATTask): Boolean {
        val result = validator.validateTaskComprehensive(task)
        // Task is flyable if no critical errors (warnings are acceptable)
        return result.criticalErrors.isEmpty()
    }

    /**
     * Get improvement suggestions for task setter
     */
    fun getTaskImprovementSuggestions(task: AATTask): List<String> {
        return validator.getTaskImprovementSuggestions(task)
    }
}

/**
 * UI-friendly validation result
 */
data class ValidationUIResult(
    val status: ValidationStatus,
    val summary: String,
    val criticalIssues: List<String>,
    val warnings: List<String>,
    val suggestions: List<String>,
    val score: String,
    val competitionReady: Boolean
) {
    fun hasIssues(): Boolean = criticalIssues.isNotEmpty() || warnings.isNotEmpty()
    fun getIssueCount(): Int = criticalIssues.size + warnings.size
}

/**
 * Competition-specific validation result
 */
data class CompetitionValidationResult(
    val suitable: Boolean,
    val competitionClass: String,
    val compliance: Int, // Percentage 0-100
    val grade: String,
    val recommendations: List<String>,
    val issues: Int
) {
    fun getComplianceDescription(): String {
        return when {
            compliance >= 95 -> "Excellent compliance"
            compliance >= 85 -> "Good compliance"
            compliance >= 70 -> "Acceptable compliance"
            else -> "Poor compliance"
        }
    }
}

/**
 * Summary for task list display
 */
data class TaskValidationSummary(
    val valid: Boolean,
    val grade: String,
    val competitionReady: Boolean,
    val issueCount: Int,
    val distance: String
) {
    fun getStatusIcon(): String {
        return when {
            !valid -> "❌"
            competitionReady -> "✅"
            issueCount == 0 -> "✅"
            issueCount <= 2 -> "⚠️"
            else -> "❌"
        }
    }

    fun getStatusText(): String {
        return when {
            !valid -> "Invalid"
            competitionReady -> "Competition Ready"
            issueCount == 0 -> "Valid"
            issueCount <= 2 -> "Valid with warnings"
            else -> "Issues present"
        }
    }
}
