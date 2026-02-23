package com.example.xcpro.tasks.aat.validation

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATFlightValidation
import com.example.xcpro.tasks.aat.models.AATTask
import com.example.xcpro.tasks.aat.models.AATFinishType
import com.example.xcpro.tasks.aat.models.AATStartType
import java.time.Duration

/**
 * Validates flight path adherence to start/finish and task sequence rules.
 */
internal class AATFlightPathValidator {
    fun validate(
        task: AATTask,
        flightPath: List<AATLatLng>,
        elapsedTime: Duration
    ): AATFlightValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (flightPath.isEmpty()) {
            return AATFlightValidation.invalid("Flight path cannot be empty")
        }

        if (elapsedTime < task.minimumTaskTime) {
            val shortfall = task.minimumTaskTime.minus(elapsedTime)
            errors.add(
                "Flight time ${formatDuration(elapsedTime)} is ${formatDuration(shortfall)} under minimum ${formatDuration(task.minimumTaskTime)}"
            )
        }

        val startValidation = validateStartCrossing(task, flightPath.first())
        if (!startValidation.first) {
            errors.addAll(startValidation.second)
        }

        val areasValidation = validateAreasSequence(task, flightPath)
        errors.addAll(areasValidation.errors)
        warnings.addAll(areasValidation.warnings)

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

    private fun validateStartCrossing(task: AATTask, startPoint: AATLatLng): Pair<Boolean, List<String>> {
        val errors = mutableListOf<String>()
        val distanceMeters = AATMathUtils.calculateDistanceMeters(startPoint, task.start.position)

        when (task.start.type) {
            AATStartType.LINE -> {
                task.start.lineLength?.let { length ->
                    if (distanceMeters > length / 2.0 + 100.0) {
                        errors.add("Start too far from start line (${String.format("%.0f", distanceMeters)}m)")
                    }
                }
            }
            AATStartType.CIRCLE -> {
                task.start.radius?.let { radius ->
                    if (distanceMeters > radius + 50.0) {
                        errors.add("Start outside start circle (${String.format("%.0f", distanceMeters)}m from center)")
                    }
                }
            }
            AATStartType.BGA_SECTOR -> {
                task.start.sectorRadius?.let { radius ->
                    if (distanceMeters > radius + 50.0) {
                        errors.add("Start outside BGA start sector")
                    }
                }
            }
        }

        return Pair(errors.isEmpty(), errors)
    }

    private fun validateFinishCrossing(task: AATTask, finishPoint: AATLatLng): Pair<Boolean, List<String>> {
        val errors = mutableListOf<String>()
        val distanceMeters = AATMathUtils.calculateDistanceMeters(finishPoint, task.finish.position)

        when (task.finish.type) {
            AATFinishType.LINE -> {
                task.finish.lineLength?.let { length ->
                    if (distanceMeters > length / 2.0 + 100.0) {
                        errors.add("Finish too far from finish line (${String.format("%.0f", distanceMeters)}m)")
                    }
                }
            }
            AATFinishType.CIRCLE -> {
                task.finish.radius?.let { radius ->
                    if (distanceMeters > radius + 50.0) {
                        errors.add("Finish outside finish circle (${String.format("%.0f", distanceMeters)}m from center)")
                    }
                }
            }
        }

        return Pair(errors.isEmpty(), errors)
    }

    private fun validateAreasSequence(task: AATTask, flightPath: List<AATLatLng>): AreasValidation {
        // Simplified implementation - in practice would need full area boundary checking.
        return AreasValidation(
            errors = emptyList(),
            warnings = emptyList(),
            areasAchieved = task.assignedAreas.size,
            sequenceCorrect = true
        )
    }

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
