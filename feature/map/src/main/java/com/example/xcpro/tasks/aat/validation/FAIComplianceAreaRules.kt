package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.AssignedArea

internal object FAIComplianceAreaRules {

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
            val areaSizeKm2 = area.getApproximateAreaSizeKm2()

            if (areaSizeKm2 < FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SIZE_KM2) {
                issues.add(
                    AATValidationIssue.warning(
                        "AREA_TOO_SMALL",
                        ValidationCategory.AREA_GEOMETRY,
                        "Area '${area.name}' (${String.format("%.1f", areaSizeKm2)} km) is very small for competitive AAT",
                        "FAI 3.2.3",
                        "Consider increasing area size to at least ${FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SIZE_KM2} km",
                        "Area ${index + 1}"
                    )
                )
            }

            if (areaSizeKm2 > FAIComplianceRules.CoreRequirements.MAXIMUM_AREA_SIZE_KM2) {
                issues.add(
                    AATValidationIssue.warning(
                        "AREA_TOO_LARGE",
                        ValidationCategory.AREA_GEOMETRY,
                        "Area '${area.name}' (${String.format("%.1f", areaSizeKm2)} km) is very large",
                        "FAI 3.2.3",
                        "Consider reducing area size to under ${FAIComplianceRules.CoreRequirements.MAXIMUM_AREA_SIZE_KM2} km",
                        "Area ${index + 1}"
                    )
                )
            }

            competitionClass?.let { cls ->
                when (val geometry = area.geometry) {
                    is AreaGeometry.Circle -> {
                        val radiusKm = geometry.radius / 1000.0
                        if (radiusKm < cls.minAreaRadius) {
                            issues.add(
                                AATValidationIssue.info(
                                    "CLASS_AREA_SMALL",
                                    ValidationCategory.COMPETITION_RULES,
                                    "${cls.displayName} areas typically ${cls.minAreaRadius}km+ radius, current: ${String.format("%.1f", radiusKm)}km",
                                    fix = "Consider ${cls.minAreaRadius}km+ radius for ${cls.displayName}",
                                    component = "Area ${index + 1}"
                                )
                            )
                        }
                        if (radiusKm > cls.maxAreaRadius) {
                            issues.add(
                                AATValidationIssue.info(
                                    "CLASS_AREA_LARGE",
                                    ValidationCategory.COMPETITION_RULES,
                                    "${cls.displayName} areas typically under ${cls.maxAreaRadius}km radius, current: ${String.format("%.1f", radiusKm)}km",
                                    fix = "Consider reducing to under ${cls.maxAreaRadius}km for ${cls.displayName}",
                                    component = "Area ${index + 1}"
                                )
                            )
                        }
                    }

                    is AreaGeometry.Sector -> {
                        val outerRadiusKm = geometry.outerRadius / 1000.0
                        if (outerRadiusKm < cls.minAreaRadius) {
                            issues.add(
                                AATValidationIssue.info(
                                    "CLASS_SECTOR_SMALL",
                                    ValidationCategory.COMPETITION_RULES,
                                    "${cls.displayName} sectors typically ${cls.minAreaRadius}km+ outer radius",
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
                val distanceKm = AATMathUtils.calculateDistanceKm(
                    area1.centerPoint.latitude,
                    area1.centerPoint.longitude,
                    area2.centerPoint.latitude,
                    area2.centerPoint.longitude
                )

                if (distanceKm < FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SEPARATION_KM) {
                    issues.add(
                        AATValidationIssue.critical(
                            "AREAS_TOO_CLOSE",
                            ValidationCategory.AREA_GEOMETRY,
                            "Areas '${area1.name}' and '${area2.name}' are ${String.format("%.2f", distanceKm)}km apart (minimum ${FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SEPARATION_KM}km)",
                            "FAI 3.2.4",
                            "Increase separation to at least ${FAIComplianceRules.CoreRequirements.MINIMUM_AREA_SEPARATION_KM}km",
                            "Areas ${i + 1} and ${j + 1}"
                        )
                    )
                }

                val area1MaxRadius = when (area1.geometry) {
                    is AreaGeometry.Circle -> area1.geometry.radius / 1000.0
                    is AreaGeometry.Sector -> area1.geometry.outerRadius / 1000.0
                }
                val area2MaxRadius = when (area2.geometry) {
                    is AreaGeometry.Circle -> area2.geometry.radius / 1000.0
                    is AreaGeometry.Sector -> area2.geometry.outerRadius / 1000.0
                }

                if (distanceKm < (area1MaxRadius + area2MaxRadius)) {
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
        minDistance: Double,
        maxDistance: Double,
        competitionClass: FAIComplianceRules.CompetitionClass?
    ): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()
        val minDistanceKm = minDistance / 1000.0
        val maxDistanceKm = maxDistance / 1000.0

        if (maxDistanceKm < minDistanceKm) {
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

        val distanceRange = maxDistanceKm - minDistanceKm
        if (distanceRange < 50.0) {
            issues.add(
                AATValidationIssue.warning(
                    "SMALL_DISTANCE_RANGE",
                    ValidationCategory.STRATEGIC_VALIDITY,
                    "Distance range (${String.format("%.1f", distanceRange)}km) is small for strategic AAT flying",
                    fix = "Consider larger areas or different positioning for more strategic options"
                )
            )
        }

        competitionClass?.let { cls ->
            if (minDistanceKm < cls.minDistance) {
                issues.add(
                    AATValidationIssue.warning(
                        "CLASS_MIN_DISTANCE",
                        ValidationCategory.COMPETITION_RULES,
                        "${cls.displayName} minimum distance typically ${cls.minDistance}km+, current: ${String.format("%.1f", minDistanceKm)}km",
                        fix = "Consider increasing task distance for ${cls.displayName}"
                    )
                )
            }

            cls.maxDistance?.let { maxDist ->
                if (maxDistanceKm > maxDist) {
                    issues.add(
                        AATValidationIssue.warning(
                            "CLASS_MAX_DISTANCE",
                            ValidationCategory.COMPETITION_RULES,
                            "${cls.displayName} maximum distance typically ${maxDist}km, current: ${String.format("%.1f", maxDistanceKm)}km",
                            fix = "Consider reducing task distance for ${cls.displayName}"
                        )
                    )
                }
            }
        }

        return issues
    }
}
