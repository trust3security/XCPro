package com.trust3.xcpro.tasks.aat

import com.trust3.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.trust3.xcpro.tasks.aat.models.AATFlightValidation
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATTask
import com.trust3.xcpro.tasks.aat.models.AATTaskValidation
import com.trust3.xcpro.tasks.aat.validation.AATValidationResult
import com.trust3.xcpro.tasks.aat.validation.ComprehensiveAATValidator
import com.trust3.xcpro.tasks.aat.validation.FAIComplianceRules

class AATTaskValidator {

    private val areaBoundaryCalculator = AreaBoundaryCalculator()
    private val comprehensiveValidator = ComprehensiveAATValidator()
    private val quickValidationEngine = AATTaskQuickValidationEngine(areaBoundaryCalculator)

    fun validateTask(task: AATTask): AATTaskValidation {
        return quickValidationEngine.validateTaskQuick(task)
    }

    fun validateTaskComprehensive(
        task: AATTask,
        competitionClass: FAIComplianceRules.CompetitionClass? = null
    ) = comprehensiveValidator.validateTask(task, competitionClass)

    fun validateFlight(
        task: AATTask,
        flightPath: List<AATLatLng>
    ): AATFlightValidation {
        return quickValidationEngine.validateFlight(task, flightPath)
    }

    fun validateForCompetition(
        task: AATTask,
        competitionClass: String
    ): AATValidationResult {
        val cls = when (competitionClass.uppercase()) {
            "CLUB" -> FAIComplianceRules.CompetitionClass.CLUB
            "STANDARD" -> FAIComplianceRules.CompetitionClass.STANDARD
            "OPEN" -> FAIComplianceRules.CompetitionClass.OPEN
            "WORLD" -> FAIComplianceRules.CompetitionClass.WORLD_CLASS
            "TWO_SEATER" -> FAIComplianceRules.CompetitionClass.TWO_SEATER
            else -> null
        }
        return comprehensiveValidator.validateTask(task, cls)
    }

    fun getTaskImprovementSuggestions(task: AATTask): List<String> {
        val result = comprehensiveValidator.validateTask(task)
        return result.infoSuggestions.map { it.suggestedFix ?: it.message }
    }

    fun isCompetitionReady(task: AATTask): Boolean {
        val result = comprehensiveValidator.validateTask(task)
        return result.isCompetitionReady()
    }

    fun getTaskGrade(task: AATTask): String {
        val result = comprehensiveValidator.validateTask(task)
        return result.validationScore.getGrade()
    }
}
