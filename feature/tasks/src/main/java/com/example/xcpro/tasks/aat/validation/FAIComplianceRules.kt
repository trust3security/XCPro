package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AssignedArea
import java.time.Duration

/**
 * FAI Section 3 compliance rules for AAT tasks.
 */
object FAIComplianceRules {
    private const val METERS_PER_KILOMETER = 1000.0
    private const val SQUARE_METERS_PER_SQUARE_KILOMETER = 1_000_000.0

    enum class CompetitionClass(
        val displayName: String,
        val minTaskTime: Duration,
        val maxTaskTime: Duration?,
        val minDistanceMeters: Double,
        val maxDistanceMeters: Double?,
        val minAreaRadiusMeters: Double,
        val maxAreaRadiusMeters: Double
    ) {
        CLUB(
            "Club Class",
            Duration.ofHours(2),
            Duration.ofHours(5),
            100.0 * METERS_PER_KILOMETER,
            400.0 * METERS_PER_KILOMETER,
            5.0 * METERS_PER_KILOMETER,
            25.0 * METERS_PER_KILOMETER
        ),
        STANDARD(
            "Standard Class",
            Duration.ofMinutes(150),
            Duration.ofHours(6),
            150.0 * METERS_PER_KILOMETER,
            500.0 * METERS_PER_KILOMETER,
            8.0 * METERS_PER_KILOMETER,
            30.0 * METERS_PER_KILOMETER
        ),
        OPEN(
            "Open Class",
            Duration.ofMinutes(150),
            Duration.ofHours(7),
            200.0 * METERS_PER_KILOMETER,
            750.0 * METERS_PER_KILOMETER,
            10.0 * METERS_PER_KILOMETER,
            40.0 * METERS_PER_KILOMETER
        ),
        WORLD_CLASS(
            "World Class",
            Duration.ofMinutes(150),
            Duration.ofHours(6),
            150.0 * METERS_PER_KILOMETER,
            500.0 * METERS_PER_KILOMETER,
            8.0 * METERS_PER_KILOMETER,
            25.0 * METERS_PER_KILOMETER
        ),
        TWO_SEATER(
            "Two-Seater",
            Duration.ofMinutes(150),
            Duration.ofHours(6),
            150.0 * METERS_PER_KILOMETER,
            500.0 * METERS_PER_KILOMETER,
            10.0 * METERS_PER_KILOMETER,
            30.0 * METERS_PER_KILOMETER
        )
    }

    object CoreRequirements {
        const val MINIMUM_AREAS = 1
        const val MAXIMUM_AREAS = 8
        const val MINIMUM_AREA_SEPARATION_METERS = 1_000.0
        const val MINIMUM_TASK_TIME_MINUTES = 30
        const val MAXIMUM_TASK_TIME_HOURS = 8
        const val MINIMUM_START_ALTITUDE_MSL = 0
        const val MAXIMUM_START_ALTITUDE_MSL = 3000
        const val MINIMUM_AREA_SIZE_M2 = 5.0 * SQUARE_METERS_PER_SQUARE_KILOMETER
        const val MAXIMUM_AREA_SIZE_M2 = 2000.0 * SQUARE_METERS_PER_SQUARE_KILOMETER
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
        minDistanceMeters: Double,
        maxDistanceMeters: Double,
        competitionClass: CompetitionClass? = null
    ): List<AATValidationIssue> {
        return FAIComplianceAreaRules.validateTaskDistance(minDistanceMeters, maxDistanceMeters, competitionClass)
    }

    fun validateStartFinish(task: AATTask): List<AATValidationIssue> {
        return FAIComplianceTaskRules.validateStartFinish(task)
    }
}
