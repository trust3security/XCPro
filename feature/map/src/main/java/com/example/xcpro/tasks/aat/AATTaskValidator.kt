package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.*
import com.example.xcpro.tasks.aat.validation.*
import java.time.Duration

/**
 * Enhanced AAT Task Validator with comprehensive FAI compliance checking.
 * This class is completely autonomous and validates AAT tasks and flights
 * according to FAI rules without dependencies on other task modules.
 *
 * Features:
 * - Comprehensive FAI Section 3 rule validation
 * - Competition class specific checks
 * - Detailed validation reports with fix suggestions
 * - Integration with existing AAT task management
 */
class AATTaskValidator {

    private val areaBoundaryCalculator = AreaBoundaryCalculator()
    private val comprehensiveValidator = ComprehensiveAATValidator()
    
    /**
     * Quick validation for backward compatibility with existing code
     *
     * @param task The AAT task to validate
     * @return Simple validation result with errors and warnings
     */
    fun validateTask(task: AATTask): AATTaskValidation {
        return validateTaskQuick(task)
    }

    /**
     * Comprehensive FAI-compliant validation with detailed categorization
     *
     * @param task The AAT task to validate
     * @param competitionClass Optional competition class for specific validation
     * @return Detailed validation result with categorized issues
     */
    fun validateTaskComprehensive(
        task: AATTask,
        competitionClass: FAIComplianceRules.CompetitionClass? = null
    ): AATValidationResult {
        return comprehensiveValidator.validateTask(task, competitionClass)
    }

    /**
     * Quick validation for existing integrations
     */
    private fun validateTaskQuick(task: AATTask): AATTaskValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Basic structure validation
        errors.addAll(task.validateBasicStructure())
        
        // Minimum task time validation
        validateMinimumTaskTime(task, errors, warnings)
        
        // Area validation
        validateAreas(task, errors, warnings)
        
        // Start/Finish validation
        validateStartFinish(task, errors, warnings)
        
        val taskDistance = null
        
        return AATTaskValidation(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            taskDistance = taskDistance
        )
    }
    
    /**
     * Validate a flight path against an AAT task.
     * 
     * @param task The AAT task
     * @param flightPath Complete flight path (chronologically ordered)
     * @return Flight validation result
     */
    fun validateFlight(
        task: AATTask,
        flightPath: List<AATLatLng>
    ): AATFlightValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        if (flightPath.isEmpty()) {
            errors.add("Flight path cannot be empty")
            return AATFlightValidation.invalid(*errors.toTypedArray())
        }
        
        // Validate start
        val startValidation = validateStart(task, flightPath.first())
        if (!startValidation.isValid) {
            errors.addAll(startValidation.errors)
        }
        
        // Validate areas achieved and sequence
        val areaValidation = validateAreasAchieved(task, flightPath)
        errors.addAll(areaValidation.errors)
        warnings.addAll(areaValidation.warnings)
        
        // Validate finish
        val finishValidation = validateFinish(task, flightPath.last())
        if (!finishValidation.isValid) {
            errors.addAll(finishValidation.errors)
        }
        
        return AATFlightValidation(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            areasAchieved = areaValidation.areasAchieved,
            totalAreas = task.assignedAreas.size,
            sequenceCorrect = areaValidation.sequenceCorrect
        )
    }
    
    /**
     * Validate minimum task time requirements.
     */
    private fun validateMinimumTaskTime(
        task: AATTask,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val minTimeMinutes = task.minimumTaskTime.toMinutes()
        
        when {
            minTimeMinutes < 30 -> warnings.add("Minimum task time less than 30 minutes may not be suitable for AAT")
            minTimeMinutes > 480 -> warnings.add("Minimum task time over 8 hours is unusually long for AAT")
        }
    }
    
    /**
     * Validate assigned areas.
     */
    private fun validateAreas(
        task: AATTask,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        if (task.assignedAreas.isEmpty()) {
            errors.add("Task must have at least one assigned area")
            return
        }
        
        // Check minimum separation between areas (FAI requirement: >= 1km)
        for (i in task.assignedAreas.indices) {
            for (j in i + 1 until task.assignedAreas.size) {
                val area1 = task.assignedAreas[i]
                val area2 = task.assignedAreas[j]
                val distance = AATMathUtils.calculateDistance(area1.centerPoint, area2.centerPoint)
                
                if (distance < 1000.0) {
                    errors.add("Areas '${area1.name}' and '${area2.name}' are less than 1km apart (${String.format("%.0f", distance)}m)")
                }
            }
        }
        
        // Validate individual areas
        task.assignedAreas.forEachIndexed { index, area ->
            val areaValidation = validateArea(area)
            if (!areaValidation.isValid) {
                errors.addAll(areaValidation.errors.map { "Area ${index + 1} (${area.name}): $it" })
            }
            warnings.addAll(areaValidation.warnings.map { "Area ${index + 1} (${area.name}): $it" })
        }
        
        // Check for reasonable area sizes
        task.assignedAreas.forEach { area ->
            val areaSizeKm2 = areaBoundaryCalculator.calculateAreaSizeKm2(area)
            when {
                areaSizeKm2 < 10.0 -> warnings.add("Area '${area.name}' is very small (${String.format("%.1f", areaSizeKm2)} km)")
                areaSizeKm2 > 5000.0 -> warnings.add("Area '${area.name}' is very large (${String.format("%.1f", areaSizeKm2)} km)")
            }
        }
    }
    
    /**
     * Validate individual area geometry.
     */
    private fun validateArea(area: AssignedArea): AreaValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        when (val geometry = area.geometry) {
            is AreaGeometry.Circle -> {
                if (geometry.radius < 100.0) {
                    warnings.add("Circle radius is very small (${String.format("%.0f", geometry.radius)}m)")
                }
                if (geometry.radius > 50000.0) {
                    warnings.add("Circle radius is very large (${String.format("%.1f", geometry.radius / 1000)}km)")
                }
            }
            
            is AreaGeometry.Sector -> {
                if (geometry.outerRadius < 100.0) {
                    warnings.add("Outer radius is very small (${String.format("%.0f", geometry.outerRadius)}m)")
                }
                
                val angularSpan = geometry.getAngularSpan()
                if (angularSpan < 10.0) {
                    warnings.add("Sector angular span is very narrow (${String.format("%.1f", angularSpan)})")
                }
                if (angularSpan > 300.0) {
                    warnings.add("Sector angular span is very wide (${String.format("%.1f", angularSpan)})")
                }
                
                geometry.innerRadius?.let { inner ->
                    val ratio = inner / geometry.outerRadius
                    if (ratio > 0.9) {
                        warnings.add("Inner radius is very close to outer radius")
                    }
                }
            }
        }
        
        return AreaValidation(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Validate start point crossing.
     */
    private fun validateStart(task: AATTask, startPoint: AATLatLng): ValidationResult {
        val errors = mutableListOf<String>()
        val distance = AATMathUtils.calculateDistance(startPoint, task.start.position)
        
        when (task.start.type) {
            AATStartType.LINE -> {
                val lineLength = task.start.lineLength ?: 0.0
                if (distance > lineLength / 2.0 + 100.0) { // 100m tolerance
                    errors.add("Start point too far from start line (${String.format("%.0f", distance)}m)")
                }
            }
            AATStartType.CIRCLE -> {
                val radius = task.start.radius ?: 0.0
                if (distance > radius + 50.0) { // 50m tolerance
                    errors.add("Start point outside start circle (${String.format("%.0f", distance)}m from center)")
                }
            }
            AATStartType.BGA_SECTOR -> {
                val sectorRadius = task.start.sectorRadius ?: 0.0
                if (distance > sectorRadius + 50.0) { // 50m tolerance
                    errors.add("Start point outside BGA start sector (${String.format("%.0f", distance)}m from center)")
                }
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Validate finish point crossing.
     */
    private fun validateFinish(task: AATTask, finishPoint: AATLatLng): ValidationResult {
        val errors = mutableListOf<String>()
        val distance = AATMathUtils.calculateDistance(finishPoint, task.finish.position)
        
        when (task.finish.type) {
            AATFinishType.LINE -> {
                val lineLength = task.finish.lineLength ?: 0.0
                if (distance > lineLength / 2.0 + 100.0) { // 100m tolerance
                    errors.add("Finish point too far from finish line (${String.format("%.0f", distance)}m)")
                }
            }
            AATFinishType.CIRCLE -> {
                val radius = task.finish.radius ?: 0.0
                if (distance > radius + 50.0) { // 50m tolerance
                    errors.add("Finish point outside finish circle (${String.format("%.0f", distance)}m from center)")
                }
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Validate that areas are achieved in the correct sequence.
     */
    private fun validateAreasAchieved(
        task: AATTask,
        flightPath: List<AATLatLng>
    ): AreasValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var areasAchieved = 0
        var sequenceCorrect = true
        
        // Track when each area was first achieved
        val areaAchievements = mutableMapOf<Int, Int>() // area index -> track point index
        
        flightPath.forEachIndexed { trackIndex, point ->
            task.assignedAreas.forEachIndexed { areaIndex, area ->
                if (areaBoundaryCalculator.isInsideArea(point, area)) {
                    if (!areaAchievements.containsKey(areaIndex)) {
                        areaAchievements[areaIndex] = trackIndex
                    }
                }
            }
        }
        
        areasAchieved = areaAchievements.size
        
        // Check sequence
        if (areaAchievements.size > 1) {
            val sortedAchievements = areaAchievements.toList().sortedBy { it.second }
            for (i in 1 until sortedAchievements.size) {
                val prevAreaIndex = sortedAchievements[i - 1].first
                val currAreaIndex = sortedAchievements[i].first
                
                if (currAreaIndex <= prevAreaIndex) {
                    sequenceCorrect = false
                    errors.add("Area ${currAreaIndex + 1} achieved after area ${prevAreaIndex + 1}, violating sequence")
                }
            }
        }
        
        // Check for missing areas
        val missedAreas = (0 until task.assignedAreas.size).filter { it !in areaAchievements.keys }
        missedAreas.forEach { areaIndex ->
            errors.add("Area ${areaIndex + 1} (${task.assignedAreas[areaIndex].name}) not achieved")
        }
        
        return AreasValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            areasAchieved = areasAchieved,
            sequenceCorrect = sequenceCorrect
        )
    }

    /**
     * Validate task for specific competition class
     */
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

    /**
     * Get validation suggestions for improving task
     */
    fun getTaskImprovementSuggestions(task: AATTask): List<String> {
        val result = comprehensiveValidator.validateTask(task)
        return result.infoSuggestions.map { it.suggestedFix ?: it.message }
    }

    /**
     * Check if task is competition ready
     */
    fun isCompetitionReady(task: AATTask): Boolean {
        val result = comprehensiveValidator.validateTask(task)
        return result.isCompetitionReady()
    }

    /**
     * Get task validation grade (A+ to F)
     */
    fun getTaskGrade(task: AATTask): String {
        val result = comprehensiveValidator.validateTask(task)
        return result.validationScore.getGrade()
    }
    
    /**
     * Validate start/finish configuration.
     */
    private fun validateStartFinish(
        task: AATTask,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        // Check start configuration
        when (task.start.type) {
            AATStartType.LINE -> {
                if (task.start.lineLength == null || task.start.lineLength <= 0) {
                    errors.add("Start line must have positive length")
                }
            }
            AATStartType.CIRCLE -> {
                if (task.start.radius == null || task.start.radius <= 0) {
                    errors.add("Start circle must have positive radius")
                }
            }
            AATStartType.BGA_SECTOR -> {
                if (task.start.sectorRadius == null || task.start.sectorRadius <= 0) {
                    errors.add("BGA start sector must have positive sector radius")
                }
            }
        }
        
        // Check finish configuration
        when (task.finish.type) {
            AATFinishType.LINE -> {
                if (task.finish.lineLength == null || task.finish.lineLength <= 0) {
                    errors.add("Finish line must have positive length")
                }
            }
            AATFinishType.CIRCLE -> {
                if (task.finish.radius == null || task.finish.radius <= 0) {
                    errors.add("Finish circle must have positive radius")
                }
            }
        }
        
        // Check start/finish separation
        val startFinishDistance = AATMathUtils.calculateDistance(task.start.position, task.finish.position)
        if (startFinishDistance < 100.0) {
            warnings.add("Start and finish are very close (${String.format("%.0f", startFinishDistance)}m)")
        }
    }
}

/**
 * Simple validation result
 */
private data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

/**
 * Areas validation result with additional information
 */
private data class AreasValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val areasAchieved: Int,
    val sequenceCorrect: Boolean
)
