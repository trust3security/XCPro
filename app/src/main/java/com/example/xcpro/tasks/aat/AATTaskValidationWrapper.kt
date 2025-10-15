package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.validation.AATValidationBridge
import com.example.xcpro.tasks.aat.validation.ValidationUIResult
import com.example.xcpro.tasks.aat.validation.TaskValidationSummary
import com.example.xcpro.tasks.aat.validation.CompetitionValidationResult

/**
 * AAT Task Validation Wrapper - Simple delegation wrapper for validation operations
 *
 * Extracted from AATTaskManager.kt for file size compliance.
 * Provides clean API for validation operations by delegating to AATValidationBridge.
 *
 * STATELESS HELPER: All methods delegate to validationBridge
 */
internal class AATTaskValidationWrapper(
    private val validationBridge: AATValidationBridge
) {

    /**
     * Check if AAT task is valid (basic check)
     */
    fun isTaskValid(task: SimpleAATTask): Boolean {
        return validationBridge.isTaskValid(task)
    }

    /**
     * Comprehensive AAT task validation with detailed results
     */
    fun validateTask(task: SimpleAATTask): ValidationUIResult {
        return validationBridge.validateTask(task)
    }

    /**
     * Check if current task is ready for competition use
     */
    fun isCompetitionReady(task: SimpleAATTask): Boolean {
        return validationBridge.isCompetitionReady(task)
    }

    /**
     * Get task validation grade (A+ to F)
     */
    fun getTaskGrade(task: SimpleAATTask): String {
        return validationBridge.getTaskGrade(task)
    }

    /**
     * Get validation summary for UI display
     */
    fun getValidationSummary(task: SimpleAATTask): TaskValidationSummary {
        return validationBridge.getValidationSummary(task)
    }

    /**
     * Validate current task for specific competition class
     */
    fun validateForCompetition(task: SimpleAATTask, competitionClass: String): CompetitionValidationResult {
        return validationBridge.validateForCompetition(task, competitionClass)
    }

    /**
     * Get improvement suggestions for current task
     */
    fun getTaskImprovementSuggestions(task: SimpleAATTask): List<String> {
        return validationBridge.getTaskImprovementSuggestions(task)
    }
}
