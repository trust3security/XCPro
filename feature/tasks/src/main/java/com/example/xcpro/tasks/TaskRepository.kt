package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TargetStateCustomParams
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.AATTargetOptimizer
import com.example.xcpro.tasks.domain.logic.TaskValidator
import com.example.xcpro.tasks.domain.model.GeoPoint
import com.example.xcpro.tasks.domain.model.ObservationZone
import com.example.xcpro.tasks.domain.model.TaskPointDef
import com.example.xcpro.tasks.domain.model.TaskStats
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Task-sheet UI projector. It derives validation, envelope, and target snapshots
 * from the canonical task payload owned by TaskManagerCoordinator.
 */
class TaskRepository @Inject constructor(
    private val validator: TaskValidator
) {

    private val _state = MutableStateFlow(TaskUiState())
    val state: StateFlow<TaskUiState> = _state.asStateFlow()
    private val observationZoneResolver = TaskObservationZoneResolver

    fun updateFrom(
        task: Task,
        taskType: TaskType,
        activeIndex: Int = 0,
        racingValidationProfile: RacingTaskStructureRules.Profile = RacingTaskStructureRules.Profile.FAI_STRICT
    ) {
        val domain = task.waypoints.toDomainPoints(taskType)
        val validation = validator.validate(taskType, domain.points)

        val (dMin, dNom, dMax) = computeEnvelope(taskType, domain.points)
        val stats = TaskStats(
            distanceNominal = dNom,
            distanceMin = dMin,
            distanceMax = dMax,
            activeIndex = activeIndex,
            hasTargets = domain.hasTargets,
            isTaskValid = validation is TaskValidator.ValidationResult.Valid
        )

        _state.value = when (validation) {
            is TaskValidator.ValidationResult.Valid -> TaskUiState(
                task = task,
                taskType = taskType,
                stats = stats,
                validationErrors = emptyList(),
                targets = domain.targetSnapshots,
                racingValidationProfile = racingValidationProfile
            )
            is TaskValidator.ValidationResult.Invalid -> TaskUiState(
                task = task,
                taskType = taskType,
                stats = stats.copy(isTaskValid = false),
                validationErrors = validation.errors,
                targets = domain.targetSnapshots,
                racingValidationProfile = racingValidationProfile
            )
        }
    }

    private data class DomainBuildResult(
        val points: List<TaskPointDef>,
        val hasTargets: Boolean,
        val targetSnapshots: List<TaskTargetSnapshot>
    )

    private fun List<TaskWaypoint>.toDomainPoints(taskType: TaskType): DomainBuildResult {
        val basePoints = mapIndexed { index, waypoint ->
            val role = waypoint.role
            val targetState = TargetStateCustomParams.from(waypoint.customParameters)
            val allowsTarget = taskType == TaskType.AAT &&
                (role == WaypointRole.TURNPOINT || role == WaypointRole.OPTIONAL)
            val resolvedTarget = if (targetState.targetLat != null && targetState.targetLon != null) {
                GeoPoint(targetState.targetLat, targetState.targetLon)
            } else {
                null
            }
            TaskPointDef(
                id = waypoint.id,
                name = waypoint.title,
                role = role,
                location = GeoPoint(waypoint.lat, waypoint.lon),
                zone = observationZoneResolver.resolve(
                    taskType = taskType,
                    waypoint = waypoint,
                    role = role
                ),
                allowsTarget = allowsTarget,
                target = resolvedTarget,
                targetParam = targetState.targetParam,
                targetLocked = targetState.targetLocked
            )
        }

        val pointsWithResolvedTargets = basePoints.mapIndexed { index, point ->
            if (taskType == TaskType.AAT && point.allowsTarget && index in 1 until lastIndex) {
                if (point.targetLocked && point.target != null) {
                    point
                } else {
                    val previous = GeoPoint(this[index - 1].lat, this[index - 1].lon)
                    val next = GeoPoint(this[index + 1].lat, this[index + 1].lon)
                    val result = AATTargetOptimizer.moveTarget(previous, point, next, point.targetParam)
                    point.copy(target = result.target, targetParam = result.rangeParam)
                }
            } else {
                point
            }
        }

        return DomainBuildResult(
            points = pointsWithResolvedTargets,
            hasTargets = pointsWithResolvedTargets.any { it.allowsTarget },
            targetSnapshots = pointsWithResolvedTargets.mapIndexed { index, point ->
                TaskTargetSnapshot(
                    index = index,
                    id = point.id,
                    name = point.name,
                    allowsTarget = point.allowsTarget,
                    targetParam = point.targetParam,
                    isLocked = point.targetLocked,
                    target = point.target
                )
            }
        )
    }

    private fun computeEnvelope(
        taskType: TaskType,
        points: List<TaskPointDef>
    ): Triple<Double, Double, Double> {
        if (points.size < 2) return Triple(0.0, 0.0, 0.0)
        var dMin = 0.0
        var dNom = 0.0
        var dMax = 0.0

        for (i in 0 until points.lastIndex) {
            val from = points[i]
            val to = points[i + 1]
            val fromPoint = from.target ?: from.location
            val toPoint = to.target ?: to.location
            val base = haversine(fromPoint, toPoint)
            val fromRadius = effectiveRadius(from.zone)
            val toRadius = effectiveRadius(to.zone)
            dNom += base
            if (taskType == TaskType.AAT) {
                dMin += max(0.0, base - (fromRadius + toRadius))
                dMax += base + (fromRadius + toRadius)
            } else {
                dMin += base
                dMax += base
            }
        }
        return Triple(dMin, dNom, dMax)
    }

    private fun haversine(from: GeoPoint, to: GeoPoint): Double {
        val radiusMeters = 6_371_000.0
        val deltaLat = Math.toRadians(to.lat - from.lat)
        val deltaLon = Math.toRadians(to.lon - from.lon)
        val fromLat = Math.toRadians(from.lat)
        val toLat = Math.toRadians(to.lat)
        val haversine = sin(deltaLat / 2).pow(2) + cos(fromLat) * cos(toLat) * sin(deltaLon / 2).pow(2)
        return 2 * radiusMeters * asin(sqrt(haversine))
    }

    private fun effectiveRadius(zone: ObservationZone): Double = when (zone) {
        is com.example.xcpro.tasks.domain.model.LineOZ -> zone.widthMeters / 2.0
        is com.example.xcpro.tasks.domain.model.CylinderOZ -> zone.radiusMeters
        is com.example.xcpro.tasks.domain.model.SectorOZ -> zone.radiusMeters
        is com.example.xcpro.tasks.domain.model.KeyholeOZ -> zone.outerRadiusMeters
        is com.example.xcpro.tasks.domain.model.AnnularSectorOZ -> zone.outerRadiusMeters
        is com.example.xcpro.tasks.domain.model.SegmentOZ -> zone.radiusMeters
    }
}
