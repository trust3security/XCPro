package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AssignedArea
import java.time.Duration

/**
 * FAI Section 3 compliance rules for AAT tasks.
 */
object FAIComplianceRules {

    enum class CompetitionClass(
        val displayName: String,
        val minTaskTime: Duration,
        val maxTaskTime: Duration?,
        val minDistance: Double,
        val maxDistance: Double?,
        val minAreaRadius: Double,
        val maxAreaRadius: Double
    ) {
        CLUB(
            "Club Class",
            Duration.ofHours(2),
            Duration.ofHours(5),
            100.0,
            400.0,
            5.0,
            25.0
        ),
        STANDARD(
            "Standard Class",
            Duration.ofMinutes(150),
            Duration.ofHours(6),
            150.0,
            500.0,
            8.0,
            30.0
        ),
        OPEN(
            "Open Class",
            Duration.ofMinutes(150),
            Duration.ofHours(7),
            200.0,
            750.0,
            10.0,
            40.0
        ),
        WORLD_CLASS(
            "World Class",
            Duration.ofMinutes(150),
            Duration.ofHours(6),
            150.0,
            500.0,
            8.0,
            25.0
        ),
        TWO_SEATER(
            "Two-Seater",
            Duration.ofMinutes(150),
            Duration.ofHours(6),
            150.0,
            500.0,
            10.0,
            30.0
        )
    }

    object CoreRequirements {
        const val MINIMUM_AREAS = 1
        const val MAXIMUM_AREAS = 8
        const val MINIMUM_AREA_SEPARATION_KM = 1.0
        const val MINIMUM_TASK_TIME_MINUTES = 30
        const val MAXIMUM_TASK_TIME_HOURS = 8
        const val MINIMUM_START_ALTITUDE_MSL = 0
        const val MAXIMUM_START_ALTITUDE_MSL = 3000
        const val MINIMUM_AREA_SIZE_KM2 = 5.0
        const val MAXIMUM_AREA_SIZE_KM2 = 2000.0
    }

    fun validateTaskTime(task: AATTask, competitionClass: CompetitionClass? = null): List<AATValidationIssue> {
        return FAIComplianceTaskRules.validateTaskTime(task, competitionClass)
    }

    fun validateAreaGeometry(
        areas: List<AssignedArea>,
        competitionClass: CompetitionClass? = null
    ): List<AATValidationIssue> {
        return FAIComplianceAreaRules.validateAreaGeometry(areas, competitionClass)
    }

    fun validateAreaSeparation(areas: List<AssignedArea>): List<AATValidationIssue> {
        return FAIComplianceAreaRules.validateAreaSeparation(areas)
    }

    fun validateTaskDistance(
        minDistance: Double,
        maxDistance: Double,
        competitionClass: CompetitionClass? = null
    ): List<AATValidationIssue> {
        return FAIComplianceAreaRules.validateTaskDistance(minDistance, maxDistance, competitionClass)
    }

    fun validateStartFinish(task: AATTask): List<AATValidationIssue> {
        return FAIComplianceTaskRules.validateStartFinish(task)
    }
}
