package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole

object RacingTaskStructureRules {
    enum class Profile {
        FAI_STRICT,
        XC_PRO_EXTENDED
    }

    sealed class ValidationError {
        data object MissingStart : ValidationError()
        data object MissingFinish : ValidationError()
        data class MultipleStarts(val count: Int) : ValidationError()
        data class MultipleFinishes(val count: Int) : ValidationError()
        data class StartNotFirst(val index: Int) : ValidationError()
        data class FinishNotLast(val index: Int) : ValidationError()
        data class InvalidInteriorRole(
            val index: Int,
            val role: WaypointRole
        ) : ValidationError()
        data class ProhibitedStartType(
            val profile: Profile,
            val startType: RacingStartPointType
        ) : ValidationError()
        data class NotEnoughInteriorTurnpoints(
            val minimumRequired: Int,
            val actual: Int
        ) : ValidationError()
    }

    data class ValidationResult(
        val profile: Profile,
        val errors: List<ValidationError>
    ) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun validate(task: Task, profile: Profile = Profile.FAI_STRICT): ValidationResult {
        val startPointTypes = task.waypoints
            .filter { it.role == WaypointRole.START }
            .map { waypoint ->
                waypoint.customPointType
                    ?.let { raw -> runCatching { RacingStartPointType.valueOf(raw) }.getOrNull() }
                    ?: RacingStartPointType.START_LINE
            }
        return validateRoles(
            roles = task.waypoints.map { it.role },
            profile = profile,
            startPointTypes = startPointTypes
        )
    }

    fun validate(waypoints: List<RacingWaypoint>, profile: Profile = Profile.FAI_STRICT): ValidationResult {
        val startPointTypes = waypoints
            .filter { waypoint -> waypoint.role == RacingWaypointRole.START }
            .map { waypoint -> waypoint.startPointType }
        return validateRoles(
            roles = waypoints.map { waypoint ->
                when (waypoint.role) {
                    RacingWaypointRole.START -> WaypointRole.START
                    RacingWaypointRole.TURNPOINT -> WaypointRole.TURNPOINT
                    RacingWaypointRole.FINISH -> WaypointRole.FINISH
                }
            },
            profile = profile,
            startPointTypes = startPointTypes
        )
    }

    private fun validateRoles(
        roles: List<WaypointRole>,
        profile: Profile,
        startPointTypes: List<RacingStartPointType>
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val startIndices = roles.withIndex().filter { it.value == WaypointRole.START }.map { it.index }
        val finishIndices = roles.withIndex().filter { it.value == WaypointRole.FINISH }.map { it.index }

        when (startIndices.size) {
            0 -> errors += ValidationError.MissingStart
            1 -> if (startIndices.first() != 0) {
                errors += ValidationError.StartNotFirst(index = startIndices.first())
            }
            else -> errors += ValidationError.MultipleStarts(count = startIndices.size)
        }

        when (finishIndices.size) {
            0 -> errors += ValidationError.MissingFinish
            1 -> if (finishIndices.first() != roles.lastIndex) {
                errors += ValidationError.FinishNotLast(index = finishIndices.first())
            }
            else -> errors += ValidationError.MultipleFinishes(count = finishIndices.size)
        }

        val interior = if (roles.size >= 2) roles.subList(1, roles.lastIndex) else emptyList()
        val allowedInteriorRoles = when (profile) {
            Profile.FAI_STRICT -> setOf(WaypointRole.TURNPOINT)
            Profile.XC_PRO_EXTENDED -> setOf(WaypointRole.TURNPOINT, WaypointRole.OPTIONAL)
        }

        interior.forEachIndexed { interiorIndex, role ->
            if (!allowedInteriorRoles.contains(role)) {
                errors += ValidationError.InvalidInteriorRole(
                    index = interiorIndex + 1,
                    role = role
                )
            }
        }

        val interiorTurnpointCount = interior.count { it in allowedInteriorRoles }
        val minimumInteriorTurnpoints = when (profile) {
            Profile.FAI_STRICT -> 2
            Profile.XC_PRO_EXTENDED -> 0
        }
        if (interiorTurnpointCount < minimumInteriorTurnpoints) {
            errors += ValidationError.NotEnoughInteriorTurnpoints(
                minimumRequired = minimumInteriorTurnpoints,
                actual = interiorTurnpointCount
            )
        }
        if (profile == Profile.FAI_STRICT &&
            startPointTypes.any { startType -> startType == RacingStartPointType.START_CYLINDER }
        ) {
            errors += ValidationError.ProhibitedStartType(
                profile = profile,
                startType = RacingStartPointType.START_CYLINDER
            )
        }

        return ValidationResult(profile = profile, errors = errors)
    }

    fun describe(error: ValidationError): String {
        return when (error) {
            ValidationError.MissingStart -> "Missing start waypoint"
            ValidationError.MissingFinish -> "Missing finish waypoint"
            is ValidationError.MultipleStarts -> "Expected exactly one start waypoint (found ${error.count})"
            is ValidationError.MultipleFinishes -> "Expected exactly one finish waypoint (found ${error.count})"
            is ValidationError.StartNotFirst -> "Start waypoint must be first (index ${error.index})"
            is ValidationError.FinishNotLast -> "Finish waypoint must be last (index ${error.index})"
            is ValidationError.InvalidInteriorRole ->
                "Interior waypoint at index ${error.index} has invalid role ${error.role.name}"
            is ValidationError.ProhibitedStartType ->
                "Start type ${error.startType.name} requires XC_PRO_EXTENDED profile"
            is ValidationError.NotEnoughInteriorTurnpoints ->
                "Expected at least ${error.minimumRequired} interior turnpoints (found ${error.actual})"
        }
    }

    fun summarize(validation: ValidationResult): String {
        if (validation.isValid) {
            return "Racing task structure is valid (${validation.profile.name})"
        }
        return validation.errors.joinToString(separator = "; ") { describe(it) }
    }
}
