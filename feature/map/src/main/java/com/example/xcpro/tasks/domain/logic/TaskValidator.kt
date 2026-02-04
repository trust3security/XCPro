package com.example.xcpro.tasks.domain.logic

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.model.*
import javax.inject.Inject

/**
 * Validates task structure and OZ shapes for Racing and AAT modes.
 * AI-NOTE: This mirrors XC behaviour at a high level; detailed distance
 * envelopes will be layered on later.
 */
class TaskValidator @Inject constructor() {

    data class ValidationSummary(
        val hasTargets: Boolean,
        val pointCount: Int
    )

    sealed class ValidationError {
        object MissingStart : ValidationError()
        object MissingFinish : ValidationError()
        object NotEnoughPoints : ValidationError()
        data class TooManyPoints(val maxAllowed: Int) : ValidationError()
        data class InvalidObservationZone(
            val role: WaypointRole,
            val zone: String,
            val allowed: Set<String>
        ) : ValidationError()
        object AATRequiresAdjustablePoint : ValidationError()
        data class NonPositiveDimension(val field: String) : ValidationError()
    }

    sealed class ValidationResult {
        data class Valid(val summary: ValidationSummary) : ValidationResult()
        data class Invalid(val errors: List<ValidationError>) : ValidationResult()
    }

    private val racingAllowedStart = setOf("LINE", "CYLINDER", "SECTOR")
    private val racingAllowedTurn = setOf("CYLINDER", "SECTOR", "KEYHOLE")
    private val racingAllowedFinish = setOf("LINE", "CYLINDER", "SECTOR")

    private val aatAllowedStart = racingAllowedStart
    private val aatAllowedTurn = setOf("CYLINDER", "SEGMENT", "ANNULAR_SECTOR", "KEYHOLE")
    private val aatAllowedFinish = racingAllowedFinish

    fun validate(taskType: TaskType, points: List<TaskPointDef>): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        val hasStart = points.any { it.role == WaypointRole.START }
        val hasFinish = points.any { it.role == WaypointRole.FINISH }

        if (!hasStart) errors += ValidationError.MissingStart
        if (!hasFinish) errors += ValidationError.MissingFinish

        val minPoints = 2
        val maxPoints = if (taskType == TaskType.AAT) 13 else 30
        if (points.size < minPoints) errors += ValidationError.NotEnoughPoints
        if (points.size > maxPoints) errors += ValidationError.TooManyPoints(maxPoints)

        points.forEach { tp ->
            // dimension sanity
            when (val oz = tp.zone) {
                is LineOZ -> {
                    if (oz.lengthMeters <= 0 || oz.widthMeters <= 0) {
                        errors += ValidationError.NonPositiveDimension("line_length_or_width")
                    }
                }
                is CylinderOZ -> if (oz.radiusMeters <= 0) {
                    errors += ValidationError.NonPositiveDimension("radius")
                }
                is SectorOZ -> if (oz.radiusMeters <= 0 || oz.angleDeg <= 0) {
                    errors += ValidationError.NonPositiveDimension("sector")
                }
                is KeyholeOZ -> if (oz.innerRadiusMeters <= 0 || oz.outerRadiusMeters <= 0 || oz.angleDeg <= 0) {
                    errors += ValidationError.NonPositiveDimension("keyhole")
                }
                is AnnularSectorOZ -> if (oz.innerRadiusMeters <= 0 || oz.outerRadiusMeters <= 0 || oz.angleDeg <= 0) {
                    errors += ValidationError.NonPositiveDimension("annular_sector")
                }
                is SegmentOZ -> if (oz.radiusMeters <= 0 || oz.angleDeg <= 0) {
                    errors += ValidationError.NonPositiveDimension("segment")
                }
            }

            val allowed = when (taskType) {
                TaskType.RACING -> when (tp.role) {
                    WaypointRole.START -> racingAllowedStart
                    WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> racingAllowedTurn
                    WaypointRole.FINISH -> racingAllowedFinish
                }
                TaskType.AAT -> when (tp.role) {
                    WaypointRole.START -> aatAllowedStart
                    WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> aatAllowedTurn
                    WaypointRole.FINISH -> aatAllowedFinish
                }
            }

            if (!allowed.contains(tp.zone.label)) {
                errors += ValidationError.InvalidObservationZone(tp.role, tp.zone.label, allowed)
            }
        }

        if (taskType == TaskType.AAT) {
            val hasTargetCapable = points.any { it.role == WaypointRole.TURNPOINT && it.allowsTarget }
            if (!hasTargetCapable) {
                errors += ValidationError.AATRequiresAdjustablePoint
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid(
                ValidationSummary(
                    hasTargets = points.any { it.allowsTarget },
                    pointCount = points.size
                )
            )
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}
