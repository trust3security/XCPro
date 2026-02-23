package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.AssignedArea

internal object FAIComplianceAreaRules {
    private const val METERS_PER_KILOMETER = 1000.0
    private const val SQUARE_METERS_PER_SQUARE_KILOMETER = 1_000_000.0
    private const val STRATEGIC_RANGE_MIN_METERS = 50_000.0

    fun validateAreaGeometry(
        areas: List<AssignedArea>,
        competitionClass: FAIComplianceRules.CompetitionClass?
    ): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        if (areas.size < FAIComplianceRules.CoreRequirements.MINIMUM_AREAS) {
            issues.add(
                AATValidationIssue.critical(
                    "TOO_FEW_AREAS",
                    ValidationCategory.TASK_STRUCTURE,
                    "Task has ${areas.size} areas, FAI requires minimum ${FAIComplianceRules.CoreRequirements.MINIMUM_AREAS}",
                    "FAI 3.2.2",
                    "Add at least ${FAIComplianceRules.CoreRequirements.MINIMUM_AREAS - areas.size} more assigned areas"
                )
            )
        }

        if (areas.size > FAIComplianceRules.CoreRequirements.MAXIMUM_AREAS) {
            issues.add(
                AATValidationIssue.critical(
                    "TOO_MANY_AREAS",
                    ValidationCategory.TASK_STRUCTURE,
                    "Task has ${areas.size} areas, FAI maximum is ${FAIComplianceRules.CoreRequirements.MAXIMUM_AREAS}",
                    "FAI 3.2.2",
                    "Remove ${areas.size - FAIComplianceRules.CoreRequirements.MAXIMUM_AREAS} assigned areas"
                )
            )
        }

        areas.forEachIndexed { index, area ->
            val areaSizeM2 = area.getApproximateAreaSizeM2()
            val areaSizeKm2 = areaSizeM2 / SQUARE_METERS_PER_SQUARE_KILOMETER

            if (areaSizeM2 < FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SIZE_M2) {
                issues.add(
                    AATValidationIssue.warning(
                        "AREA_TOO_SMALL",
                        ValidationCategory.AREA_GEOMETRY,
                        "Area '${area.name}' (${String.format("%.1f", areaSizeKm2)} km2) is very small for competitive AAT",
                        "FAI 3.2.3",
                        "Consider increasing area size to at least ${String.format("%.1f", FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SIZE_M2 / SQUARE_METERS_PER_SQUARE_KILOMETER)} km2",
                        "Area ${index + 1}"
                    )
                )
            }

            if (areaSizeM2 > FAIComplianceRules.CoreRequirements.MAXIMUM_AREA_SIZE_M2) {
                issues.add(
                    AATValidationIssue.warning(
                        "AREA_TOO_LARGE",
                        ValidationCategory.AREA_GEOMETRY,
                        "Area '${area.name}' (${String.format("%.1f", areaSizeKm2)} km2) is very large",
                        "FAI 3.2.3",
                        "Consider reducing area size to under ${String.format("%.1f", FAIComplianceRules.CoreRequirements.MAXIMUM_AREA_SIZE_M2 / SQUARE_METERS_PER_SQUARE_KILOMETER)} km2",
                        "Area ${index + 1}"
                    )
                )
            }

            competitionClass?.let { cls ->
                when (val geometry = area.geometry) {
                    is AreaGeometry.Circle -> {
                        val radiusMeters = geometry.radius
                        if (radiusMeters < cls.minAreaRadiusMeters) {
                            issues.add(
                                AATValidationIssue.info(
                                    "CLASS_AREA_SMALL",
                                    ValidationCategory.COMPETITION_RULES,
                                    "${cls.displayName} areas typically ${String.format("%.1f", cls.minAreaRadiusMeters / METERS_PER_KILOMETER)}km+ radius, current: ${String.format("%.1f", radiusMeters / METERS_PER_KILOMETER)}km",
                                    fix = "Consider ${String.format("%.1f", cls.minAreaRadiusMeters / METERS_PER_KILOMETER)}km+ radius for ${cls.displayName}",
                                    component = "Area ${index + 1}"
                                )
                            )
                        }
                        if (radiusMeters > cls.maxAreaRadiusMeters) {
                            issues.add(
                                AATValidationIssue.info(
                                    "CLASS_AREA_LARGE",
                                    ValidationCategory.COMPETITION_RULES,
                                    "${cls.displayName} areas typically under ${String.format("%.1f", cls.maxAreaRadiusMeters / METERS_PER_KILOMETER)}km radius, current: ${String.format("%.1f", radiusMeters / METERS_PER_KILOMETER)}km",
                                    fix = "Consider reducing to under ${String.format("%.1f", cls.maxAreaRadiusMeters / METERS_PER_KILOMETER)}km for ${cls.displayName}",
                                    component = "Area ${index + 1}"
                                )
                            )
                        }
                    }

                    is AreaGeometry.Sector -> {
                        val outerRadiusMeters = geometry.outerRadius
                        if (outerRadiusMeters < cls.minAreaRadiusMeters) {
                            issues.add(
                                AATValidationIssue.info(
                                    "CLASS_SECTOR_SMALL",
                                    ValidationCategory.COMPETITION_RULES,
                                    "${cls.displayName} sectors typically ${String.format("%.1f", cls.minAreaRadiusMeters / METERS_PER_KILOMETER)}km+ outer radius",
                                    component = "Area ${index + 1}"
                                )
                            )
                        }
                    }
                }
            }
        }

        return issues
    }

    fun validateAreaSeparation(areas: List<AssignedArea>): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        for (i in areas.indices) {
            for (j in i + 1 until areas.size) {
                val area1 = areas[i]
                val area2 = areas[j]
                val distanceMeters = AATMathUtils.calculateDistanceMeters(
                    area1.centerPoint.latitude,
                    area1.centerPoint.longitude,
                    area2.centerPoint.latitude,
                    area2.centerPoint.longitude
                )

                if (distanceMeters < FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SEPARATION_METERS) {
                    issues.add(
                        AATValidationIssue.critical(
                            "AREAS_TOO_CLOSE",
                            ValidationCategory.AREA_GEOMETRY,
                            "Areas '${area1.name}' and '${area2.name}' are ${String.format("%.2f", distanceMeters / METERS_PER_KILOMETER)}km apart (minimum ${FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SEPARATION_METERS / METERS_PER_KILOMETER}km)",
                            "FAI 3.2.4",
                            "Increase separation to at least ${FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SEPARATION_METERS / METERS_PER_KILOMETER}km",
                            "Areas ${i + 1} and ${j + 1}"
                        )
                    )
                }

                val area1MaxRadiusMeters = when (area1.geometry) {
                    is AreaGeometry.Circle -> area1.geometry.radius
                    is AreaGeometry.Sector -> area1.geometry.outerRadius
                }
                val area2MaxRadiusMeters = when (area2.geometry) {
                    is AreaGeometry.Circle -> area2.geometry.radius
                    is AreaGeometry.Sector -> area2.geometry.outerRadius
                }

                if (distanceMeters < (area1MaxRadiusMeters + area2MaxRadiusMeters)) {
                    issues.add(
                        AATValidationIssue.warning(
                            "AREAS_MAY_OVERLAP",
                            ValidationCategory.AREA_GEOMETRY,
                            "Areas '${area1.name}' and '${area2.name}' boundaries may overlap",
                            "FAI 3.2.4",
                            "Verify areas do not overlap or increase separation",
                            "Areas ${i + 1} and ${j + 1}"
                        )
                    )
                }
            }
        }

        return issues
    }

    fun validateTaskDistance(
        minDistanceMeters: Double,
        maxDistanceMeters: Double,
        competitionClass: FAIComplianceRules.CompetitionClass?
    ): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()
        val minDistanceKm = minDistanceMeters / METERS_PER_KILOMETER
        val maxDistanceKm = maxDistanceMeters / METERS_PER_KILOMETER

        if (maxDistanceMeters < minDistanceMeters) {
            issues.add(
                AATValidationIssue.critical(
                    "INVALID_DISTANCE_RANGE",
                    ValidationCategory.DISTANCE_TIME,
                    "Maximum distance (${String.format("%.1f", maxDistanceKm)}km) less than minimum (${String.format("%.1f", minDistanceKm)}km)",
                    "FAI 3.2.5",
                    "Check area positions and sizes"
                )
            )
        }

        val distanceRangeMeters = maxDistanceMeters - minDistanceMeters
        val distanceRangeKm = distanceRangeMeters / METERS_PER_KILOMETER
        if (distanceRangeMeters < STRATEGIC_RANGE_MIN_METERS) {
            issues.add(
                AATValidationIssue.warning(
                    "SMALL_DISTANCE_RANGE",
                    ValidationCategory.STRATEGIC_VALIDITY,
                    "Distance range (${String.format("%.1f", distanceRangeKm)}km) is small for strategic AAT flying",
                    fix = "Consider larger areas or different positioning for more strategic options"
                )
            )
        }

        competitionClass?.let { cls ->
            if (minDistanceMeters < cls.minDistanceMeters) {
                issues.add(
                    AATValidationIssue.warning(
                        "CLASS_MIN_DISTANCE",
                        ValidationCategory.COMPETITION_RULES,
                        "${cls.displayName} minimum distance typically ${String.format("%.1f", cls.minDistanceMeters / METERS_PER_KILOMETER)}km+, current: ${String.format("%.1f", minDistanceKm)}km",
                        fix = "Consider increasing task distance for ${cls.displayName}"
                    )
                )
            }

            cls.maxDistanceMeters?.let { maxDistMeters ->
                if (maxDistanceMeters > maxDistMeters) {
                    issues.add(
                        AATValidationIssue.warning(
                            "CLASS_MAX_DISTANCE",
                            ValidationCategory.COMPETITION_RULES,
                            "${cls.displayName} maximum distance typically ${String.format("%.1f", maxDistMeters / METERS_PER_KILOMETER)}km, current: ${String.format("%.1f", maxDistanceKm)}km",
                            fix = "Consider reducing task distance for ${cls.displayName}"
                        )
                    )
                }
            }
        }

        return issues
    }
}
