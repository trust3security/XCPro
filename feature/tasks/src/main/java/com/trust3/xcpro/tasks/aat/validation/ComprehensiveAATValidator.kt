package com.trust3.xcpro.tasks.aat.validation

import com.trust3.xcpro.tasks.aat.models.AATFlightValidation
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATTask
import com.trust3.xcpro.tasks.aat.models.AATTaskDistance
import java.time.Duration

/**
 * Comprehensive AAT Task Validator following FAI Section 3 competition rules.
 *
 * This validator provides detailed analysis of AAT tasks against official
 * FAI regulations and competitive gliding best practices, ensuring tasks
 * are suitable for competition use.
 *
 * ZERO DEPENDENCIES on Racing/DHT modules - maintains complete separation.
 */
class ComprehensiveAATValidator {

    private val faiRules = FAIComplianceRules
    private val structureValidator = AATTaskStructureValidator()
    private val strategicValidator = AATTaskStrategicValidator()
    private val safetyValidator = AATTaskSafetyValidator()
    private val scoreCalculator = AATValidationScoreCalculator()
    private val complianceEvaluator = AATCompetitionComplianceEvaluator()
    private val flightPathValidator = AATFlightPathValidator()

    /**
     * Perform comprehensive validation of an AAT task.
     */
    fun validateTask(
        task: AATTask,
        competitionClass: FAIComplianceRules.CompetitionClass? = null
    ): AATValidationResult {
        val criticalErrors = mutableListOf<AATValidationIssue>()
        val warnings = mutableListOf<AATValidationIssue>()
        val infoSuggestions = mutableListOf<AATValidationIssue>()

        val structureIssues = structureValidator.validate(task)
        categorizeIssues(structureIssues, criticalErrors, warnings, infoSuggestions)

        val timeIssues = faiRules.validateTaskTime(task, competitionClass)
        categorizeIssues(timeIssues, criticalErrors, warnings, infoSuggestions)

        val areaIssues = faiRules.validateAreaGeometry(task.assignedAreas, competitionClass)
        categorizeIssues(areaIssues, criticalErrors, warnings, infoSuggestions)

        val separationIssues = faiRules.validateAreaSeparation(task.assignedAreas)
        categorizeIssues(separationIssues, criticalErrors, warnings, infoSuggestions)

        val startFinishIssues = faiRules.validateStartFinish(task)
        categorizeIssues(startFinishIssues, criticalErrors, warnings, infoSuggestions)

        var taskDistance: AATTaskDistance? = null
        var distanceIssues = emptyList<AATValidationIssue>()

        if (criticalErrors.isEmpty()) {
            try {
                distanceIssues = emptyList()
            } catch (e: Exception) {
                criticalErrors.add(
                    AATValidationIssue.critical(
                        "DISTANCE_CALC_FAILED",
                        ValidationCategory.DISTANCE_TIME,
                        "Failed to calculate task distances: ${e.message}",
                        fix = "Check area geometry and positioning"
                    )
                )
            }
        }

        val strategicIssues = strategicValidator.validate(task, taskDistance)
        categorizeIssues(strategicIssues, criticalErrors, warnings, infoSuggestions)

        val safetyIssues = safetyValidator.validate(task)
        categorizeIssues(safetyIssues, criticalErrors, warnings, infoSuggestions)

        val validationScore = scoreCalculator.calculate(
            criticalErrors.size,
            warnings.size,
            infoSuggestions.size,
            task,
            taskDistance
        )

        val competitionCompliance = complianceEvaluator.assess(
            criticalErrors,
            warnings,
            task,
            taskDistance,
            competitionClass
        )

        val isValid = criticalErrors.isEmpty()

        return AATValidationResult(
            isValid = isValid,
            criticalErrors = criticalErrors,
            warnings = warnings,
            infoSuggestions = infoSuggestions,
            validationScore = validationScore,
            taskDistance = taskDistance,
            competitionCompliance = competitionCompliance
        )
    }

    /**
     * Validate a flight path against an AAT task.
     */
    fun validateFlight(
        task: AATTask,
        flightPath: List<AATLatLng>,
        elapsedTime: Duration
    ): AATFlightValidation = flightPathValidator.validate(task, flightPath, elapsedTime)

    /**
     * Categorize validation issues by severity.
     */
    private fun categorizeIssues(
        issues: List<AATValidationIssue>,
        criticalErrors: MutableList<AATValidationIssue>,
        warnings: MutableList<AATValidationIssue>,
        infoSuggestions: MutableList<AATValidationIssue>
    ) {
        issues.forEach { issue ->
            when (issue.severity) {
                ValidationSeverity.CRITICAL -> criticalErrors.add(issue)
                ValidationSeverity.WARNING -> warnings.add(issue)
                ValidationSeverity.INFO -> infoSuggestions.add(issue)
            }
        }
    }
}
