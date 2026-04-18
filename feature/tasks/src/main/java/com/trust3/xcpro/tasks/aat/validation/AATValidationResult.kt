package com.trust3.xcpro.tasks.aat.validation

import com.trust3.xcpro.tasks.aat.models.AATTaskDistance

/**
 * Comprehensive AAT validation result with detailed categorization
 * following FAI Section 3 competition rules for gliding.
 */
data class AATValidationResult(
    val isValid: Boolean,
    val criticalErrors: List<AATValidationIssue> = emptyList(),
    val warnings: List<AATValidationIssue> = emptyList(),
    val infoSuggestions: List<AATValidationIssue> = emptyList(),
    val validationScore: AATValidationScore,
    val taskDistance: AATTaskDistance? = null,
    val competitionCompliance: CompetitionCompliance
) {
    /**
     * Get overall validation status
     */
    fun getValidationStatus(): ValidationStatus {
        return when {
            criticalErrors.isNotEmpty() -> ValidationStatus.CRITICAL
            warnings.isNotEmpty() -> ValidationStatus.WARNING
            infoSuggestions.isNotEmpty() -> ValidationStatus.INFO
            else -> ValidationStatus.SUCCESS
        }
    }

    /**
     * Check if task is ready for competition use
     */
    fun isCompetitionReady(): Boolean {
        return criticalErrors.isEmpty() &&
               competitionCompliance.faiCompliant &&
               competitionCompliance.minimumDistanceCompliant &&
               competitionCompliance.timeRequirementCompliant
    }

    /**
     * Get formatted summary
     */
    fun getSummary(): String {
        val status = getValidationStatus()
        val issueCount = criticalErrors.size + warnings.size + infoSuggestions.size

        return when (status) {
            ValidationStatus.SUCCESS -> " Task valid - Competition ready"
            ValidationStatus.INFO -> " Task valid - $issueCount suggestions"
            ValidationStatus.WARNING -> " Task flyable - ${warnings.size} warnings"
            ValidationStatus.CRITICAL -> " Task invalid - ${criticalErrors.size} critical errors"
        }
    }

    companion object {
        fun success(
            taskDistance: AATTaskDistance,
            compliance: CompetitionCompliance,
            score: AATValidationScore,
            suggestions: List<AATValidationIssue> = emptyList()
        ) = AATValidationResult(
            isValid = true,
            infoSuggestions = suggestions,
            validationScore = score,
            taskDistance = taskDistance,
            competitionCompliance = compliance
        )

        fun critical(
            vararg errors: AATValidationIssue,
            score: AATValidationScore,
            compliance: CompetitionCompliance
        ) = AATValidationResult(
            isValid = false,
            criticalErrors = errors.toList(),
            validationScore = score,
            competitionCompliance = compliance
        )

        fun warnings(
            taskDistance: AATTaskDistance,
            compliance: CompetitionCompliance,
            score: AATValidationScore,
            vararg warnings: AATValidationIssue
        ) = AATValidationResult(
            isValid = true,
            warnings = warnings.toList(),
            validationScore = score,
            taskDistance = taskDistance,
            competitionCompliance = compliance
        )
    }
}

/**
 * Individual validation issue with detailed information
 */
data class AATValidationIssue(
    val ruleId: String,
    val severity: ValidationSeverity,
    val category: ValidationCategory,
    val message: String,
    val suggestedFix: String? = null,
    val affectedComponent: String? = null,
    val faiReference: String? = null
) {
    /**
     * Get formatted message with severity indicator
     */
    fun getFormattedMessage(): String {
        val icon = when (severity) {
            ValidationSeverity.CRITICAL -> ""
            ValidationSeverity.WARNING -> ""
            ValidationSeverity.INFO -> ""
        }
        return "$icon $message"
    }

    companion object {
        fun critical(
            ruleId: String,
            category: ValidationCategory,
            message: String,
            faiRef: String? = null,
            fix: String? = null,
            component: String? = null
        ) = AATValidationIssue(
            ruleId = ruleId,
            severity = ValidationSeverity.CRITICAL,
            category = category,
            message = message,
            suggestedFix = fix,
            affectedComponent = component,
            faiReference = faiRef
        )

        fun warning(
            ruleId: String,
            category: ValidationCategory,
            message: String,
            faiRef: String? = null,
            fix: String? = null,
            component: String? = null
        ) = AATValidationIssue(
            ruleId = ruleId,
            severity = ValidationSeverity.WARNING,
            category = category,
            message = message,
            suggestedFix = fix,
            affectedComponent = component,
            faiReference = faiRef
        )

        fun info(
            ruleId: String,
            category: ValidationCategory,
            message: String,
            fix: String? = null,
            component: String? = null
        ) = AATValidationIssue(
            ruleId = ruleId,
            severity = ValidationSeverity.INFO,
            category = category,
            message = message,
            suggestedFix = fix,
            affectedComponent = component
        )
    }
}

/**
 * Validation severity levels
 */
enum class ValidationSeverity {
    CRITICAL,   // Task cannot be used in competition
    WARNING,    // Task is flyable but may have issues
    INFO        // Suggestions for optimization
}

/**
 * Overall validation status
 */
enum class ValidationStatus {
    SUCCESS,    // All checks passed
    INFO,       // Valid with suggestions
    WARNING,    // Valid with warnings
    CRITICAL    // Invalid - critical errors present
}

/**
 * Validation categories for organizing issues
 */
enum class ValidationCategory {
    TASK_STRUCTURE,     // Basic task validity
    AREA_GEOMETRY,      // Assigned area rules
    DISTANCE_TIME,      // Distance and time requirements
    START_FINISH,       // Start/finish configurations
    COMPETITION_RULES,  // FAI competition compliance
    STRATEGIC_VALIDITY, // Task difficulty and fairness
    AIRSPACE_SAFETY    // Airspace and safety considerations
}

/**
 * Competition compliance assessment
 */
data class CompetitionCompliance(
    val faiCompliant: Boolean,
    val minimumDistanceCompliant: Boolean,
    val timeRequirementCompliant: Boolean,
    val areaConfigurationCompliant: Boolean,
    val startFinishCompliant: Boolean,
    val competitionClass: String? = null,
    val nonComplianceReasons: List<String> = emptyList()
) {
    /**
     * Get overall compliance percentage
     */
    fun getCompliancePercentage(): Int {
        val checks = listOf(
            faiCompliant,
            minimumDistanceCompliant,
            timeRequirementCompliant,
            areaConfigurationCompliant,
            startFinishCompliant
        )
        val passed = checks.count { it }
        return (passed * 100) / checks.size
    }

    /**
     * Check if suitable for specific competition class
     */
    fun isSuitableForClass(competitionClass: String): Boolean {
        return when (competitionClass.uppercase()) {
            "CLUB", "REGIONAL" -> getCompliancePercentage() >= 70
            "NATIONAL" -> getCompliancePercentage() >= 85
            "WORLD", "EUROPEAN" -> getCompliancePercentage() == 100
            else -> faiCompliant
        }
    }
}

/**
 * Numerical validation scoring
 */
data class AATValidationScore(
    val overallScore: Double,        // 0.0 to 100.0
    val structureScore: Double,      // Task structure validity
    val geometryScore: Double,       // Area geometry compliance
    val rulesScore: Double,          // FAI rules compliance
    val strategicScore: Double,      // Strategic validity
    val safetyScore: Double         // Safety considerations
) {
    /**
     * Get grade letter based on overall score
     */
    fun getGrade(): String {
        return when {
            overallScore >= 95.0 -> "A+"
            overallScore >= 90.0 -> "A"
            overallScore >= 85.0 -> "B+"
            overallScore >= 80.0 -> "B"
            overallScore >= 75.0 -> "C+"
            overallScore >= 70.0 -> "C"
            overallScore >= 60.0 -> "D"
            else -> "F"
        }
    }

    /**
     * Get detailed score breakdown
     */
    fun getScoreBreakdown(): String {
        return """
            Overall: ${String.format("%.1f", overallScore)}% (${getGrade()})
            Structure: ${String.format("%.1f", structureScore)}%
            Geometry: ${String.format("%.1f", geometryScore)}%
            FAI Rules: ${String.format("%.1f", rulesScore)}%
            Strategy: ${String.format("%.1f", strategicScore)}%
            Safety: ${String.format("%.1f", safetyScore)}%
        """.trimIndent()
    }
}
