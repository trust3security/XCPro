package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATTaskDistance

/**
 * Determines competition compliance from validation issues and task metrics.
 */
internal class AATCompetitionComplianceEvaluator {

    fun assess(
        criticalErrors: List<AATValidationIssue>,
        warnings: List<AATValidationIssue>,
        task: AATTask,
        taskDistance: AATTaskDistance?,
        competitionClass: FAIComplianceRules.CompetitionClass?
    ): CompetitionCompliance {
        val faiCompliant = criticalErrors.none { it.faiReference != null }

        val minimumDistanceCompliant = taskDistance?.let { distance ->
            competitionClass?.let { cls ->
                (distance.minimumDistance / 1000.0) >= cls.minDistance
            } ?: true
        } ?: false

        val timeRequirementCompliant = competitionClass?.let { cls ->
            task.minimumTaskTime >= cls.minTaskTime &&
                (cls.maxTaskTime?.let { task.minimumTaskTime <= it } ?: true)
        } ?: (task.minimumTaskTime.toMinutes() >= 150)

        val areaConfigurationCompliant = task.assignedAreas.isNotEmpty() &&
            task.assignedAreas.size <= FAIComplianceRules.CoreRequirements.MAXIMUM_AREAS

        val startFinishCompliant = criticalErrors.none {
            it.category == ValidationCategory.START_FINISH && it.severity == ValidationSeverity.CRITICAL
        }

        val nonComplianceReasons = mutableListOf<String>()
        if (!faiCompliant) nonComplianceReasons.add("FAI rule violations")
        if (!minimumDistanceCompliant) nonComplianceReasons.add("Minimum distance requirements")
        if (!timeRequirementCompliant) nonComplianceReasons.add("Time requirements")
        if (!areaConfigurationCompliant) nonComplianceReasons.add("Area configuration")
        if (!startFinishCompliant) nonComplianceReasons.add("Start/finish configuration")

        return CompetitionCompliance(
            faiCompliant = faiCompliant,
            minimumDistanceCompliant = minimumDistanceCompliant,
            timeRequirementCompliant = timeRequirementCompliant,
            areaConfigurationCompliant = areaConfigurationCompliant,
            startFinishCompliant = startFinishCompliant,
            competitionClass = competitionClass?.displayName,
            nonComplianceReasons = nonComplianceReasons
        )
    }
}
