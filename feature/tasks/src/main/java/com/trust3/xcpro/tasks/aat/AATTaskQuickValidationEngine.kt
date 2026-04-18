package com.trust3.xcpro.tasks.aat

import com.trust3.xcpro.tasks.aat.areas.AreaBoundaryCalculator
import com.trust3.xcpro.tasks.aat.calculations.AATMathUtils
import com.trust3.xcpro.tasks.aat.models.AATFinishType
import com.trust3.xcpro.tasks.aat.models.AATFlightValidation
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATStartType
import com.trust3.xcpro.tasks.aat.models.AATTask
import com.trust3.xcpro.tasks.aat.models.AATTaskValidation
import com.trust3.xcpro.tasks.aat.models.AreaGeometry
import com.trust3.xcpro.tasks.aat.models.AreaValidation
import com.trust3.xcpro.tasks.aat.models.AssignedArea

internal class AATTaskQuickValidationEngine(
    private val areaBoundaryCalculator: AreaBoundaryCalculator
) {
    private companion object {
        const val METERS2_PER_KM2 = 1_000_000.0
        const val SMALL_AREA_THRESHOLD_METERS2 = 10.0 * METERS2_PER_KM2
        const val LARGE_AREA_THRESHOLD_METERS2 = 5000.0 * METERS2_PER_KM2
    }

    fun validateTaskQuick(task: AATTask): AATTaskValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        errors.addAll(task.validateBasicStructure())
        validateMinimumTaskTime(task, errors, warnings)
        validateAreas(task, errors, warnings)
        validateStartFinish(task, errors, warnings)

        val taskDistance = null

        return AATTaskValidation(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            taskDistance = taskDistance
        )
    }

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

        val startValidation = validateStart(task, flightPath.first())
        if (!startValidation.isValid) {
            errors.addAll(startValidation.errors)
        }

        val areaValidation = validateAreasAchieved(task, flightPath)
        errors.addAll(areaValidation.errors)
        warnings.addAll(areaValidation.warnings)

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

    private fun validateAreas(
        task: AATTask,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        if (task.assignedAreas.isEmpty()) {
            errors.add("Task must have at least one assigned area")
            return
        }

        for (i in task.assignedAreas.indices) {
            for (j in i + 1 until task.assignedAreas.size) {
                val area1 = task.assignedAreas[i]
                val area2 = task.assignedAreas[j]
                val distanceMeters = AATMathUtils.calculateDistanceMeters(
                    area1.centerPoint,
                    area2.centerPoint
                )

                if (distanceMeters < 1000.0) {
                    errors.add(
                        "Areas '${area1.name}' and '${area2.name}' are less than 1km apart (${String.format("%.0f", distanceMeters)}m)"
                    )
                }
            }
        }

        task.assignedAreas.forEachIndexed { index, area ->
            val areaValidation = validateArea(area)
            if (!areaValidation.isValid) {
                errors.addAll(areaValidation.errors.map { "Area ${index + 1} (${area.name}): $it" })
            }
            warnings.addAll(areaValidation.warnings.map { "Area ${index + 1} (${area.name}): $it" })
        }

        task.assignedAreas.forEach { area ->
            val areaSizeMeters2 = areaBoundaryCalculator.calculateAreaSizeMeters2(area)
            val areaSizeKm2 = areaSizeMeters2 / METERS2_PER_KM2
            when {
                areaSizeMeters2 < SMALL_AREA_THRESHOLD_METERS2 ->
                    warnings.add("Area '${area.name}' is very small (${String.format("%.1f", areaSizeKm2)} km2)")
                areaSizeMeters2 > LARGE_AREA_THRESHOLD_METERS2 ->
                    warnings.add("Area '${area.name}' is very large (${String.format("%.1f", areaSizeKm2)} km2)")
            }
        }
    }

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

    private fun validateStart(task: AATTask, startPoint: AATLatLng): ValidationResult {
        val errors = mutableListOf<String>()
        val distanceMeters = AATMathUtils.calculateDistanceMeters(startPoint, task.start.position)

        when (task.start.type) {
            AATStartType.LINE -> {
                val lineLength = task.start.lineLength ?: 0.0
                if (distanceMeters > lineLength / 2.0 + 100.0) {
                    errors.add("Start point too far from start line (${String.format("%.0f", distanceMeters)}m)")
                }
            }
            AATStartType.CIRCLE -> {
                val radius = task.start.radius ?: 0.0
                if (distanceMeters > radius + 50.0) {
                    errors.add("Start point outside start circle (${String.format("%.0f", distanceMeters)}m from center)")
                }
            }
            AATStartType.BGA_SECTOR -> {
                val sectorRadius = task.start.sectorRadius ?: 0.0
                if (distanceMeters > sectorRadius + 50.0) {
                    errors.add("Start point outside BGA start sector (${String.format("%.0f", distanceMeters)}m from center)")
                }
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    private fun validateFinish(task: AATTask, finishPoint: AATLatLng): ValidationResult {
        val errors = mutableListOf<String>()
        val distanceMeters = AATMathUtils.calculateDistanceMeters(finishPoint, task.finish.position)

        when (task.finish.type) {
            AATFinishType.LINE -> {
                val lineLength = task.finish.lineLength ?: 0.0
                if (distanceMeters > lineLength / 2.0 + 100.0) {
                    errors.add("Finish point too far from finish line (${String.format("%.0f", distanceMeters)}m)")
                }
            }
            AATFinishType.CIRCLE -> {
                val radius = task.finish.radius ?: 0.0
                if (distanceMeters > radius + 50.0) {
                    errors.add("Finish point outside finish circle (${String.format("%.0f", distanceMeters)}m from center)")
                }
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    private fun validateAreasAchieved(
        task: AATTask,
        flightPath: List<AATLatLng>
    ): AreasValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var sequenceCorrect = true

        val areaAchievements = mutableMapOf<Int, Int>()

        flightPath.forEachIndexed { trackIndex, point ->
            task.assignedAreas.forEachIndexed { areaIndex, area ->
                if (areaBoundaryCalculator.isInsideArea(point, area) && !areaAchievements.containsKey(areaIndex)) {
                    areaAchievements[areaIndex] = trackIndex
                }
            }
        }

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

        val missedAreas = (0 until task.assignedAreas.size).filter { it !in areaAchievements.keys }
        missedAreas.forEach { areaIndex ->
            errors.add("Area ${areaIndex + 1} (${task.assignedAreas[areaIndex].name}) not achieved")
        }

        return AreasValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            areasAchieved = areaAchievements.size,
            sequenceCorrect = sequenceCorrect
        )
    }

    private fun validateStartFinish(
        task: AATTask,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
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

        val startFinishDistanceMeters = AATMathUtils.calculateDistanceMeters(
            task.start.position,
            task.finish.position
        )
        if (startFinishDistanceMeters < 100.0) {
            warnings.add("Start and finish are very close (${String.format("%.0f", startFinishDistanceMeters)}m)")
        }
    }
}

private data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

private data class AreasValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val areasAchieved: Int,
    val sequenceCorrect: Boolean
)
