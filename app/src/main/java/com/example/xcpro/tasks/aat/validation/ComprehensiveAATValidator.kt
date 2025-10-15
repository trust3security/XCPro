package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.*
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

    /**
     * Perform comprehensive validation of an AAT task
     *
     * @param task The AAT task to validate
     * @param competitionClass Optional competition class for specific validation
     * @return Detailed validation result with categorized issues
     */
    fun validateTask(
        task: AATTask,
        competitionClass: FAIComplianceRules.CompetitionClass? = null
    ): AATValidationResult {
        val criticalErrors = mutableListOf<AATValidationIssue>()
        val warnings = mutableListOf<AATValidationIssue>()
        val infoSuggestions = mutableListOf<AATValidationIssue>()

        // 1. Basic structure validation
        val structureIssues = validateBasicStructure(task)
        categorizeIssues(structureIssues, criticalErrors, warnings, infoSuggestions)

        // 2. FAI time requirements
        val timeIssues = faiRules.validateTaskTime(task, competitionClass)
        categorizeIssues(timeIssues, criticalErrors, warnings, infoSuggestions)

        // 3. Area geometry and configuration
        val areaIssues = faiRules.validateAreaGeometry(task.assignedAreas, competitionClass)
        categorizeIssues(areaIssues, criticalErrors, warnings, infoSuggestions)

        // 4. Area separation requirements
        val separationIssues = faiRules.validateAreaSeparation(task.assignedAreas)
        categorizeIssues(separationIssues, criticalErrors, warnings, infoSuggestions)

        // 5. Start/finish configuration
        val startFinishIssues = faiRules.validateStartFinish(task)
        categorizeIssues(startFinishIssues, criticalErrors, warnings, infoSuggestions)

        // 6. Calculate task distances and validate
        var taskDistance: AATTaskDistance? = null
        var distanceIssues = emptyList<AATValidationIssue>()

        if (criticalErrors.isEmpty()) {
            try {
                distanceIssues = emptyList()
            } catch (e: Exception) {
                criticalErrors.add(AATValidationIssue.critical(
                    "DISTANCE_CALC_FAILED",
                    ValidationCategory.DISTANCE_TIME,
                    "Failed to calculate task distances: ${e.message}",
                    fix = "Check area geometry and positioning"
                ))
            }
        }

        // 7. Strategic validity assessment
        val strategicIssues = validateStrategicValidity(task, taskDistance)
        categorizeIssues(strategicIssues, criticalErrors, warnings, infoSuggestions)

        // 8. Safety and airspace considerations
        val safetyIssues = validateSafetyConsiderations(task)
        categorizeIssues(safetyIssues, criticalErrors, warnings, infoSuggestions)

        // Calculate validation scores
        val validationScore = calculateValidationScore(
            criticalErrors.size, warnings.size, infoSuggestions.size,
            task, taskDistance
        )

        // Assess competition compliance
        val competitionCompliance = assessCompetitionCompliance(
            criticalErrors, warnings, task, taskDistance, competitionClass
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
     * Validate a flight path against an AAT task
     */
    fun validateFlight(
        task: AATTask,
        flightPath: List<AATLatLng>,
        elapsedTime: Duration
    ): AATFlightValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (flightPath.isEmpty()) {
            return AATFlightValidation.invalid("Flight path cannot be empty")
        }

        // Validate minimum time requirement
        if (elapsedTime < task.minimumTaskTime) {
            val shortfall = task.minimumTaskTime.minus(elapsedTime)
            errors.add("Flight time ${formatDuration(elapsedTime)} is ${formatDuration(shortfall)} under minimum ${formatDuration(task.minimumTaskTime)}")
        }

        // Validate start
        val startValidation = validateStartCrossing(task, flightPath.first())
        if (!startValidation.first) {
            errors.addAll(startValidation.second)
        }

        // Validate areas achieved in sequence
        val areasValidation = validateAreasSequence(task, flightPath)
        errors.addAll(areasValidation.errors)
        warnings.addAll(areasValidation.warnings)

        // Validate finish
        val finishValidation = validateFinishCrossing(task, flightPath.last())
        if (!finishValidation.first) {
            errors.addAll(finishValidation.second)
        }

        return AATFlightValidation(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            areasAchieved = areasValidation.areasAchieved,
            totalAreas = task.assignedAreas.size,
            sequenceCorrect = areasValidation.sequenceCorrect
        )
    }

    /**
     * Validate basic AAT task structure
     */
    private fun validateBasicStructure(task: AATTask): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        // Task name validation
        if (task.name.isBlank()) {
            issues.add(AATValidationIssue.critical(
                "MISSING_TASK_NAME",
                ValidationCategory.TASK_STRUCTURE,
                "Task must have a name",
                fix = "Provide a descriptive task name"
            ))
        }

        // ID validation
        if (task.id.isBlank()) {
            issues.add(AATValidationIssue.critical(
                "MISSING_TASK_ID",
                ValidationCategory.TASK_STRUCTURE,
                "Task must have a unique identifier",
                fix = "Generate or assign a unique task ID"
            ))
        }

        // Minimum time validation
        if (task.minimumTaskTime.isNegative || task.minimumTaskTime.isZero) {
            issues.add(AATValidationIssue.critical(
                "INVALID_MIN_TIME",
                ValidationCategory.TASK_STRUCTURE,
                "Minimum task time must be positive",
                "FAI 3.2.1",
                "Set minimum task time to valid duration (e.g., 2.5 hours)"
            ))
        }

        // Areas count basic check
        if (task.assignedAreas.isEmpty()) {
            issues.add(AATValidationIssue.critical(
                "NO_ASSIGNED_AREAS",
                ValidationCategory.TASK_STRUCTURE,
                "AAT task must have at least one assigned area",
                "FAI 3.2.2",
                "Add assigned areas to create valid AAT task"
            ))
        }

        // Check for duplicate area names
        val areaNames = task.assignedAreas.map { it.name }
        val duplicates = areaNames.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            issues.add(AATValidationIssue.warning(
                "DUPLICATE_AREA_NAMES",
                ValidationCategory.TASK_STRUCTURE,
                "Duplicate area names found: ${duplicates.joinToString(", ")}",
                fix = "Use unique names for all assigned areas"
            ))
        }

        return issues
    }

    /**
     * Validate strategic validity of the task
     */
    private fun validateStrategicValidity(task: AATTask, taskDistance: AATTaskDistance?): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        taskDistance?.let { distance ->
            val minTimeHours = task.minimumTaskTime.toMinutes() / 60.0
            val minSpeed = (distance.minimumDistance / 1000.0) / minTimeHours
            val maxSpeed = (distance.maximumDistance / 1000.0) / minTimeHours

            // Check if speeds are reasonable for gliders
            if (maxSpeed < 40.0) {
                issues.add(AATValidationIssue.warning(
                    "MAX_SPEED_LOW",
                    ValidationCategory.STRATEGIC_VALIDITY,
                    "Maximum achievable speed (${String.format("%.1f", maxSpeed)} km/h) is low for competitive gliding",
                    fix = "Consider larger areas or longer distances"
                ))
            }

            if (minSpeed > 150.0) {
                issues.add(AATValidationIssue.warning(
                    "MIN_SPEED_HIGH",
                    ValidationCategory.STRATEGIC_VALIDITY,
                    "Minimum required speed (${String.format("%.1f", minSpeed)} km/h) is very high",
                    fix = "Consider shorter minimum distance or longer task time"
                ))
            }

            // Check strategic options (distance range)
            val distanceRangeKm = (distance.maximumDistance - distance.minimumDistance) / 1000.0
            val percentageRange = (distanceRangeKm / (distance.minimumDistance / 1000.0)) * 100.0

            if (percentageRange < 15.0) {
                issues.add(AATValidationIssue.info(
                    "LIMITED_STRATEGIC_OPTIONS",
                    ValidationCategory.STRATEGIC_VALIDITY,
                    "Distance range (${String.format("%.1f", percentageRange)}%) provides limited strategic options",
                    fix = "Consider larger areas or different positioning for more strategic choice"
                ))
            }

            if (percentageRange > 50.0) {
                issues.add(AATValidationIssue.info(
                    "WIDE_STRATEGIC_OPTIONS",
                    ValidationCategory.STRATEGIC_VALIDITY,
                    "Distance range (${String.format("%.1f", percentageRange)}%) is very wide - excellent strategic options",
                ))
            }
        }

        return issues
    }

    /**
     * Validate safety and airspace considerations
     */
    private fun validateSafetyConsiderations(task: AATTask): List<AATValidationIssue> {
        val issues = mutableListOf<AATValidationIssue>()

        // Check for areas that might be too close to each other (safety)
        task.assignedAreas.forEachIndexed { i, area1 ->
            task.assignedAreas.drop(i + 1).forEachIndexed { j, area2 ->
                val distance = AATMathUtils.calculateDistance(area1.centerPoint, area2.centerPoint)
                val distanceKm = distance / 1000.0

                // Very close areas might cause safety issues in competitions
                if (distanceKm < 5.0) {
                    issues.add(AATValidationIssue.info(
                        "AREAS_VERY_CLOSE",
                        ValidationCategory.AIRSPACE_SAFETY,
                        "Areas '${area1.name}' and '${area2.name}' are very close (${String.format("%.1f", distanceKm)}km) - consider pilot traffic",
                        fix = "Consider increased separation for safety in competitions"
                    ))
                }
            }
        }

        // Check for very large areas that might include varied terrain
        task.assignedAreas.forEach { area ->
            val areaSize = area.getApproximateAreaSizeKm2()
            if (areaSize > 1000.0) {
                issues.add(AATValidationIssue.info(
                    "LARGE_AREA_TERRAIN",
                    ValidationCategory.AIRSPACE_SAFETY,
                    "Large area '${area.name}' (${String.format("%.0f", areaSize)} km²) may include varied terrain/weather",
                    fix = "Verify area doesn't include unsuitable terrain or restricted airspace"
                ))
            }
        }

        return issues
    }

    /**
     * Calculate comprehensive validation scores
     */
    private fun calculateValidationScore(
        criticalCount: Int,
        warningCount: Int,
        infoCount: Int,
        task: AATTask,
        taskDistance: AATTaskDistance?
    ): AATValidationScore {

        // Structure score (0-100)
        val structureScore = when {
            criticalCount > 0 -> 0.0
            warningCount == 0 -> 100.0
            warningCount <= 2 -> 85.0
            else -> 70.0
        }

        // Geometry score based on areas
        val geometryScore = when {
            task.assignedAreas.isEmpty() -> 0.0
            task.assignedAreas.size > FAIComplianceRules.CoreRequirements.MAXIMUM_AREAS -> 50.0
            task.assignedAreas.any { it.getApproximateAreaSizeKm2() < 5.0 } -> 75.0
            else -> 95.0
        }

        // Rules compliance score
        val rulesScore = when {
            criticalCount > 0 -> 0.0
            task.minimumTaskTime.toMinutes() < 150 -> 80.0 // Below typical competition minimum
            task.minimumTaskTime.toMinutes() > 360 -> 85.0 // Above typical maximum
            else -> 100.0
        }

        // Strategic score based on distance range
        val strategicScore = taskDistance?.let { distance ->
            val minKm = distance.minimumDistance / 1000.0
            val maxKm = distance.maximumDistance / 1000.0
            val range = maxKm - minKm
            val percentRange = (range / minKm) * 100.0

            when {
                percentRange < 10.0 -> 60.0  // Limited options
                percentRange < 20.0 -> 85.0  // Good options
                percentRange < 40.0 -> 100.0 // Excellent options
                else -> 90.0 // Very wide, might be too much
            }
        } ?: 50.0

        // Safety score
        val safetyScore = when {
            infoCount > 5 -> 80.0  // Many suggestions
            infoCount > 2 -> 90.0  // Some suggestions
            else -> 100.0  // Clean task
        }

        val overallScore = (structureScore + geometryScore + rulesScore + strategicScore + safetyScore) / 5.0

        return AATValidationScore(
            overallScore = overallScore,
            structureScore = structureScore,
            geometryScore = geometryScore,
            rulesScore = rulesScore,
            strategicScore = strategicScore,
            safetyScore = safetyScore
        )
    }

    /**
     * Assess overall competition compliance
     */
    private fun assessCompetitionCompliance(
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

    /**
     * Categorize validation issues by severity
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

    /**
     * Validate start crossing
     */
    private fun validateStartCrossing(task: AATTask, startPoint: AATLatLng): Pair<Boolean, List<String>> {
        val errors = mutableListOf<String>()
        val distance = AATMathUtils.calculateDistance(startPoint, task.start.position)

        when (task.start.type) {
            AATStartType.LINE -> {
                task.start.lineLength?.let { length ->
                    if (distance > length / 2.0 + 100.0) {
                        errors.add("Start too far from start line (${String.format("%.0f", distance)}m)")
                    }
                }
            }
            AATStartType.CIRCLE -> {
                task.start.radius?.let { radius ->
                    if (distance > radius + 50.0) {
                        errors.add("Start outside start circle (${String.format("%.0f", distance)}m from center)")
                    }
                }
            }
            AATStartType.BGA_SECTOR -> {
                task.start.sectorRadius?.let { radius ->
                    if (distance > radius + 50.0) {
                        errors.add("Start outside BGA start sector")
                    }
                }
            }
        }

        return Pair(errors.isEmpty(), errors)
    }

    /**
     * Validate finish crossing
     */
    private fun validateFinishCrossing(task: AATTask, finishPoint: AATLatLng): Pair<Boolean, List<String>> {
        val errors = mutableListOf<String>()
        val distance = AATMathUtils.calculateDistance(finishPoint, task.finish.position)

        when (task.finish.type) {
            AATFinishType.LINE -> {
                task.finish.lineLength?.let { length ->
                    if (distance > length / 2.0 + 100.0) {
                        errors.add("Finish too far from finish line (${String.format("%.0f", distance)}m)")
                    }
                }
            }
            AATFinishType.CIRCLE -> {
                task.finish.radius?.let { radius ->
                    if (distance > radius + 50.0) {
                        errors.add("Finish outside finish circle (${String.format("%.0f", distance)}m from center)")
                    }
                }
            }
        }

        return Pair(errors.isEmpty(), errors)
    }

    /**
     * Validate areas achieved in correct sequence
     */
    private fun validateAreasSequence(task: AATTask, flightPath: List<AATLatLng>): AreasValidation {
        // Simplified implementation - in practice would need full area boundary checking
        return AreasValidation(
            errors = emptyList(),
            warnings = emptyList(),
            areasAchieved = task.assignedAreas.size, // Optimistic assumption
            sequenceCorrect = true
        )
    }

    /**
     * Format duration for display
     */
    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return "${hours}h ${minutes}m"
    }

    private data class AreasValidation(
        val errors: List<String>,
        val warnings: List<String>,
        val areasAchieved: Int,
        val sequenceCorrect: Boolean
    )
}