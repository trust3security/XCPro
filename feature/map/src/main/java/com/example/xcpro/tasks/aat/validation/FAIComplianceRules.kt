package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AssignedArea
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import java.time.Duration

/**
 * FAI Section 3 compliance rules for AAT tasks
 * Based on FAI Sporting Code Section 3 (Soaring) current regulations
 */
object FAIComplianceRules {

    /**
     * Official FAI AAT competition class specifications
     */
    enum class CompetitionClass(
        val displayName: String,
        val minTaskTime: Duration,
        val maxTaskTime: Duration?,
        val minDistance: Double,  // km
        val maxDistance: Double?, // km
        val minAreaRadius: Double, // km
        val maxAreaRadius: Double  // km
    ) {
        CLUB(
            "Club Class",
            Duration.ofHours(2),
            Duration.ofHours(5),
            100.0, 400.0,
            5.0, 25.0
        ),
        STANDARD(
            "Standard Class",
            Duration.ofMinutes(150), // 2.5 hours
            Duration.ofHours(6),
            150.0, 500.0,
            8.0, 30.0
        ),
        OPEN(
            "Open Class",
            Duration.ofMinutes(150), // 2.5 hours
            Duration.ofHours(7),
            200.0, 750.0,
            10.0, 40.0
        ),
        WORLD_CLASS(
            "World Class",
            Duration.ofMinutes(150), // 2.5 hours
            Duration.ofHours(6),
            150.0, 500.0,
            8.0, 25.0
        ),
        TWO_SEATER(
            "Two-Seater",
            Duration.ofMinutes(150), // 2.5 hours
            Duration.ofHours(6),
            150.0, 500.0,
            10.0, 30.0
        )
    }

    /**
     * Core FAI AAT requirements that must be met
     */
    object CoreRequirements {
        const val MINIMUM_AREAS = 1
        const val MAXIMUM_AREAS = 8
        const val MINIMUM_AREA_SEPARATION_KM = 1.0
        const val MINIMUM_TASK_TIME_MINUTES = 30
        const val MAXIMUM_TASK_TIME_HOURS = 8
        const val MINIMUM_START_ALTITUDE_MSL = 0
        const val MAXIMUM_START_ALTITUDE_MSL = 3000 // meters
        const val MINIMUM_AREA_SIZE_KM2 = 5.0
        const val MAXIMUM_AREA_SIZE_KM2 = 2000.0
    }

    /**
     * Validate minimum task time requirements
     */
    fun validateTaskTime(task: AATTask, competitionClass: CompetitionClass? = null): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()
        val taskTimeMinutes = task.minimumTaskTime.toMinutes()

        // Absolute minimum check
        if (taskTimeMinutes < CoreRequirements.MINIMUM_TASK_TIME_MINUTES) {
            issues.add(AATValidationIssue.critical(
                "MIN_TIME_TOO_SHORT",
                ValidationCategory.DISTANCE_TIME,
                "Minimum task time ${taskTimeMinutes}m is below FAI minimum of ${CoreRequirements.MINIMUM_TASK_TIME_MINUTES}m",
                "FAI 3.2.1",
                "Increase minimum task time to at least ${CoreRequirements.MINIMUM_TASK_TIME_MINUTES} minutes"
            ))
        }

        // Absolute maximum check
        if (taskTimeMinutes > CoreRequirements.MAXIMUM_TASK_TIME_HOURS * 60) {
            issues.add(AATValidationIssue.critical(
                "MIN_TIME_TOO_LONG",
                ValidationCategory.DISTANCE_TIME,
                "Minimum task time ${taskTimeMinutes}m exceeds FAI maximum of ${CoreRequirements.MAXIMUM_TASK_TIME_HOURS}h",
                "FAI 3.2.1",
                "Reduce minimum task time to maximum ${CoreRequirements.MAXIMUM_TASK_TIME_HOURS} hours"
            ))
        }

        // Competition class specific checks
        competitionClass?.let { cls ->
            if (task.minimumTaskTime < cls.minTaskTime) {
                issues.add(AATValidationIssue.warning(
                    "CLASS_MIN_TIME",
                    ValidationCategory.COMPETITION_RULES,
                    "${cls.displayName} typically requires minimum ${cls.minTaskTime.toMinutes()}m, current: ${taskTimeMinutes}m",
                    "FAI Annex A",
                    "Consider increasing minimum task time for ${cls.displayName} compliance"
                ))
            }

            cls.maxTaskTime?.let { maxTime ->
                if (task.minimumTaskTime > maxTime) {
                    issues.add(AATValidationIssue.warning(
                        "CLASS_MAX_TIME",
                        ValidationCategory.COMPETITION_RULES,
                        "${cls.displayName} maximum is ${maxTime.toMinutes()}m, current: ${taskTimeMinutes}m",
                        "FAI Annex A",
                        "Consider reducing minimum task time for ${cls.displayName} compliance"
                    ))
                }
            }
        }

        return issues
    }

    /**
     * Validate assigned area geometry and sizes
     */
    fun validateAreaGeometry(areas: List<AssignedArea>, competitionClass: CompetitionClass? = null): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        // Check area count
        if (areas.size < CoreRequirements.MINIMUM_AREAS) {
            issues.add(AATValidationIssue.critical(
                "TOO_FEW_AREAS",
                ValidationCategory.TASK_STRUCTURE,
                "Task has ${areas.size} areas, FAI requires minimum ${CoreRequirements.MINIMUM_AREAS}",
                "FAI 3.2.2",
                "Add at least ${CoreRequirements.MINIMUM_AREAS - areas.size} more assigned areas"
            ))
        }

        if (areas.size > CoreRequirements.MAXIMUM_AREAS) {
            issues.add(AATValidationIssue.critical(
                "TOO_MANY_AREAS",
                ValidationCategory.TASK_STRUCTURE,
                "Task has ${areas.size} areas, FAI maximum is ${CoreRequirements.MAXIMUM_AREAS}",
                "FAI 3.2.2",
                "Remove ${areas.size - CoreRequirements.MAXIMUM_AREAS} assigned areas"
            ))
        }

        // Check individual area properties
        areas.forEachIndexed { index, area ->
            val areaSizeKm2 = area.getApproximateAreaSizeKm2()

            // Area size validation
            if (areaSizeKm2 < CoreRequirements.MINIMUM_AREA_SIZE_KM2) {
                issues.add(AATValidationIssue.warning(
                    "AREA_TOO_SMALL",
                    ValidationCategory.AREA_GEOMETRY,
                    "Area '${area.name}' (${String.format("%.1f", areaSizeKm2)} km²) is very small for competitive AAT",
                    "FAI 3.2.3",
                    "Consider increasing area size to at least ${CoreRequirements.MINIMUM_AREA_SIZE_KM2} km²",
                    "Area ${index + 1}"
                ))
            }

            if (areaSizeKm2 > CoreRequirements.MAXIMUM_AREA_SIZE_KM2) {
                issues.add(AATValidationIssue.warning(
                    "AREA_TOO_LARGE",
                    ValidationCategory.AREA_GEOMETRY,
                    "Area '${area.name}' (${String.format("%.1f", areaSizeKm2)} km²) is very large",
                    "FAI 3.2.3",
                    "Consider reducing area size to under ${CoreRequirements.MAXIMUM_AREA_SIZE_KM2} km²",
                    "Area ${index + 1}"
                ))
            }

            // Competition class specific area size checks
            competitionClass?.let { cls ->
                when (val geometry = area.geometry) {
                    is AreaGeometry.Circle -> {
                        val radiusKm = geometry.radius / 1000.0
                        if (radiusKm < cls.minAreaRadius) {
                            issues.add(AATValidationIssue.info(
                                "CLASS_AREA_SMALL",
                                ValidationCategory.COMPETITION_RULES,
                                "${cls.displayName} areas typically ${cls.minAreaRadius}km+ radius, current: ${String.format("%.1f", radiusKm)}km",
                                fix = "Consider ${cls.minAreaRadius}km+ radius for ${cls.displayName}",
                                component = "Area ${index + 1}"
                            ))
                        }
                        if (radiusKm > cls.maxAreaRadius) {
                            issues.add(AATValidationIssue.info(
                                "CLASS_AREA_LARGE",
                                ValidationCategory.COMPETITION_RULES,
                                "${cls.displayName} areas typically under ${cls.maxAreaRadius}km radius, current: ${String.format("%.1f", radiusKm)}km",
                                fix = "Consider reducing to under ${cls.maxAreaRadius}km for ${cls.displayName}",
                                component = "Area ${index + 1}"
                            ))
                        }
                    }
                    is AreaGeometry.Sector -> {
                        val outerRadiusKm = geometry.outerRadius / 1000.0
                        if (outerRadiusKm < cls.minAreaRadius) {
                            issues.add(AATValidationIssue.info(
                                "CLASS_SECTOR_SMALL",
                                ValidationCategory.COMPETITION_RULES,
                                "${cls.displayName} sectors typically ${cls.minAreaRadius}km+ outer radius",
                                component = "Area ${index + 1}"
                            ))
                        }
                    }
                }
            }
        }

        return issues
    }

    /**
     * Validate area separation requirements
     */
    fun validateAreaSeparation(areas: List<AssignedArea>): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        for (i in areas.indices) {
            for (j in i + 1 until areas.size) {
                val area1 = areas[i]
                val area2 = areas[j]

                // Calculate center-to-center distance
                val distanceKm = AATMathUtils.calculateDistanceKm(
                    area1.centerPoint.latitude, area1.centerPoint.longitude,
                    area2.centerPoint.latitude, area2.centerPoint.longitude
                )

                if (distanceKm < CoreRequirements.MINIMUM_AREA_SEPARATION_KM) {
                    issues.add(AATValidationIssue.critical(
                        "AREAS_TOO_CLOSE",
                        ValidationCategory.AREA_GEOMETRY,
                        "Areas '${area1.name}' and '${area2.name}' are ${String.format("%.2f", distanceKm)}km apart (minimum ${CoreRequirements.MINIMUM_AREA_SEPARATION_KM}km)",
                        "FAI 3.2.4",
                        "Increase separation to at least ${CoreRequirements.MINIMUM_AREA_SEPARATION_KM}km",
                        "Areas ${i + 1} and ${j + 1}"
                    ))
                }

                // Check for area overlap (basic check)
                val area1MaxRadius = when (area1.geometry) {
                    is AreaGeometry.Circle -> area1.geometry.radius / 1000.0
                    is AreaGeometry.Sector -> area1.geometry.outerRadius / 1000.0
                }
                val area2MaxRadius = when (area2.geometry) {
                    is AreaGeometry.Circle -> area2.geometry.radius / 1000.0
                    is AreaGeometry.Sector -> area2.geometry.outerRadius / 1000.0
                }

                if (distanceKm < (area1MaxRadius + area2MaxRadius)) {
                    issues.add(AATValidationIssue.warning(
                        "AREAS_MAY_OVERLAP",
                        ValidationCategory.AREA_GEOMETRY,
                        "Areas '${area1.name}' and '${area2.name}' boundaries may overlap",
                        "FAI 3.2.4",
                        "Verify areas do not overlap or increase separation",
                        "Areas ${i + 1} and ${j + 1}"
                    ))
                }
            }
        }

        return issues
    }

    /**
     * Validate task distance requirements
     */
    fun validateTaskDistance(
        minDistance: Double,
        maxDistance: Double,
        competitionClass: CompetitionClass? = null
    ): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()
        val minDistanceKm = minDistance / 1000.0
        val maxDistanceKm = maxDistance / 1000.0

        // Basic distance validation
        if (maxDistanceKm < minDistanceKm) {
            issues.add(AATValidationIssue.critical(
                "INVALID_DISTANCE_RANGE",
                ValidationCategory.DISTANCE_TIME,
                "Maximum distance (${String.format("%.1f", maxDistanceKm)}km) less than minimum (${String.format("%.1f", minDistanceKm)}km)",
                "FAI 3.2.5",
                "Check area positions and sizes"
            ))
        }

        val distanceRange = maxDistanceKm - minDistanceKm
        if (distanceRange < 50.0) {
            issues.add(AATValidationIssue.warning(
                "SMALL_DISTANCE_RANGE",
                ValidationCategory.STRATEGIC_VALIDITY,
                "Distance range (${String.format("%.1f", distanceRange)}km) is small for strategic AAT flying",
                fix = "Consider larger areas or different positioning for more strategic options"
            ))
        }

        // Competition class specific checks
        competitionClass?.let { cls ->
            if (minDistanceKm < cls.minDistance) {
                issues.add(AATValidationIssue.warning(
                    "CLASS_MIN_DISTANCE",
                    ValidationCategory.COMPETITION_RULES,
                    "${cls.displayName} minimum distance typically ${cls.minDistance}km+, current: ${String.format("%.1f", minDistanceKm)}km",
                    fix = "Consider increasing task distance for ${cls.displayName}"
                ))
            }

            cls.maxDistance?.let { maxDist ->
                if (maxDistanceKm > maxDist) {
                    issues.add(AATValidationIssue.warning(
                        "CLASS_MAX_DISTANCE",
                        ValidationCategory.COMPETITION_RULES,
                        "${cls.displayName} maximum distance typically ${maxDist}km, current: ${String.format("%.1f", maxDistanceKm)}km",
                        fix = "Consider reducing task distance for ${cls.displayName}"
                    ))
                }
            }
        }

        return issues
    }

    /**
     * Validate start/finish configuration
     */
    fun validateStartFinish(task: AATTask): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        // Start altitude validation
        task.maxStartAltitude?.let { maxAlt ->
            if (maxAlt < CoreRequirements.MINIMUM_START_ALTITUDE_MSL) {
                issues.add(AATValidationIssue.critical(
                    "START_ALT_TOO_LOW",
                    ValidationCategory.START_FINISH,
                    "Maximum start altitude ${maxAlt}m is below minimum ${CoreRequirements.MINIMUM_START_ALTITUDE_MSL}m MSL",
                    "FAI 3.3.1",
                    "Set realistic start altitude above ${CoreRequirements.MINIMUM_START_ALTITUDE_MSL}m MSL"
                ))
            }

            if (maxAlt > CoreRequirements.MAXIMUM_START_ALTITUDE_MSL) {
                issues.add(AATValidationIssue.warning(
                    "START_ALT_VERY_HIGH",
                    ValidationCategory.START_FINISH,
                    "Maximum start altitude ${maxAlt}m is very high for typical competition",
                    "FAI 3.3.1",
                    "Consider typical competition start altitude (1000-2000m MSL)"
                ))
            }
        }

        // Start/finish line configuration
        when {
            task.start.lineLength != null && task.start.lineLength!! < 1000.0 -> {
                issues.add(AATValidationIssue.warning(
                    "START_LINE_SHORT",
                    ValidationCategory.START_FINISH,
                    "Start line ${String.format("%.0f", task.start.lineLength!!)}m is short for competition",
                    fix = "Consider 5-10km start line length for fair starts"
                ))
            }
            task.start.radius != null && task.start.radius!! < 500.0 -> {
                issues.add(AATValidationIssue.warning(
                    "START_CYLINDER_SMALL",
                    ValidationCategory.START_FINISH,
                    "Start cylinder ${String.format("%.0f", task.start.radius!!)}m radius is small",
                    fix = "Consider 1-3km start cylinder radius"
                ))
            }
        }

        when {
            task.finish.lineLength != null && task.finish.lineLength!! < 1000.0 -> {
                issues.add(AATValidationIssue.warning(
                    "FINISH_LINE_SHORT",
                    ValidationCategory.START_FINISH,
                    "Finish line ${String.format("%.0f", task.finish.lineLength!!)}m is short",
                    fix = "Consider 2-5km finish line for clear finish determination"
                ))
            }
            task.finish.radius != null && task.finish.radius!! < 500.0 -> {
                issues.add(AATValidationIssue.warning(
                    "FINISH_CYLINDER_SMALL",
                    ValidationCategory.START_FINISH,
                    "Finish cylinder ${String.format("%.0f", task.finish.radius!!)}m radius is small",
                    fix = "Consider 1-3km finish cylinder radius"
                ))
            }
        }

        return issues
    }
}
