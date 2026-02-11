package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.AATTargetOptimizer
import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import com.example.xcpro.tasks.domain.logic.TaskProximityDecision
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.example.xcpro.tasks.domain.logic.TaskValidator
import com.example.xcpro.tasks.domain.model.*
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * SSOT for task state and validation; bridges legacy TaskManager data into
 * the new domain layer.
 */
class TaskRepository @Inject constructor(
    private val validator: TaskValidator,
    private val proximityEvaluator: TaskProximityEvaluator
) {

    private val _state = MutableStateFlow(TaskUiState())
    val state: StateFlow<TaskUiState> = _state.asStateFlow()

    // Keeps user-chosen target params/locks by waypoint id so they survive refresh.
    private val targetStateById = mutableMapOf<String, TargetMemory>()
    private val advanceState = TaskAdvanceState()

    private data class TargetMemory(
        var param: Double,
        var locked: Boolean
    )

    fun updateFrom(task: Task, taskType: TaskType, activeIndex: Int = 0) {
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
                advanceSnapshot = advanceState.snapshot()
            )
            is TaskValidator.ValidationResult.Invalid -> TaskUiState(
                task = task,
                taskType = taskType,
                stats = stats.copy(isTaskValid = false),
                validationErrors = validation.errors,
                targets = domain.targetSnapshots,
                advanceSnapshot = advanceState.snapshot()
            )
        }
    }

    private data class DomainBuildResult(
        val points: List<TaskPointDef>,
        val hasTargets: Boolean,
        val targetSnapshots: List<TaskTargetSnapshot>
    )

    private fun List<TaskWaypoint>.toDomainPoints(taskType: TaskType): DomainBuildResult {
        val basePoints = mutableListOf<TaskPointDef>()
        var hasTargetsFlag = false

        forEachIndexed { index, wp ->
            val role = when (index) {
                0 -> WaypointRole.START
                lastIndex -> WaypointRole.FINISH
                else -> WaypointRole.TURNPOINT
            }
            val zone: ObservationZone = when (role) {
                WaypointRole.START -> LineOZ()
                WaypointRole.FINISH -> CylinderOZ(radiusMeters = 3000.0)
                WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> {
                    if (taskType == TaskType.AAT) {
                        SegmentOZ(radiusMeters = 5000.0, angleDeg = 90.0)
                    } else {
                        CylinderOZ(radiusMeters = 500.0)
                    }
                }
            }
            val allowsTarget = taskType == TaskType.AAT && role == WaypointRole.TURNPOINT
            hasTargetsFlag = hasTargetsFlag || allowsTarget

            val remembered = targetStateById[wp.id]
            val targetParam = (wp.customParameters["targetParam"] as? Double)
                ?: remembered?.param ?: 0.5
            val targetLocked = (wp.customParameters["targetLocked"] as? Boolean)
                ?: remembered?.locked ?: false
            val targetLat = wp.customParameters["targetLat"] as? Double
            val targetLon = wp.customParameters["targetLon"] as? Double
            val seedTarget = if (targetLat != null && targetLon != null) GeoPoint(targetLat, targetLon) else null

            basePoints += TaskPointDef(
                id = wp.id,
                name = wp.title,
                role = role,
                location = GeoPoint(wp.lat, wp.lon),
                zone = zone,
                allowsTarget = allowsTarget,
                target = seedTarget, // may be overwritten in second pass
                targetParam = targetParam,
                targetLocked = targetLocked
            )
        }

        // Second pass to place targets along isoline inside the OZ arc.
        val withTargets = basePoints.mapIndexed { index, point ->
            if (taskType == TaskType.AAT && point.allowsTarget && index in 1 until lastIndex) {
                val prev = GeoPoint(this[index - 1].lat, this[index - 1].lon)
                val next = GeoPoint(this[index + 1].lat, this[index + 1].lon)
                val result = AATTargetOptimizer.moveTarget(prev, point, next, point.targetParam)
                targetStateById[point.id] = TargetMemory(result.rangeParam, point.targetLocked)
                point.copy(target = result.target, targetParam = result.rangeParam)
            } else point
        }

        val snapshots = withTargets.mapIndexed { idx, p ->
            TaskTargetSnapshot(
                index = idx,
                id = p.id,
                name = p.name,
                allowsTarget = p.allowsTarget,
                targetParam = p.targetParam,
                isLocked = p.targetLocked,
                target = p.target
            )
        }

        return DomainBuildResult(
            points = withTargets,
            hasTargets = hasTargetsFlag,
            targetSnapshots = snapshots
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
            val a = points[i]
            val b = points[i + 1]
            val aLoc = a.target ?: a.location
            val bLoc = b.target ?: b.location
            val base = haversine(aLoc, bLoc)
            val rA = effectiveRadius(a.zone)
            val rB = effectiveRadius(b.zone)
            dNom += base
            if (taskType == TaskType.AAT) {
                // Envelope rides along the OZ boundary defined by target placement.
                dMin += maxOf(0.0, base - (rA + rB))
                dMax += base + (rA + rB)
            } else {
                dMin += base
                dMax += base
            }
        }
        return Triple(dMin, dNom, dMax)
    }

    private fun haversine(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    private fun effectiveRadius(zone: ObservationZone): Double = when (zone) {
        is LineOZ -> zone.widthMeters / 2.0
        is CylinderOZ -> zone.radiusMeters
        is SectorOZ -> zone.radiusMeters
        is KeyholeOZ -> zone.outerRadiusMeters
        is AnnularSectorOZ -> zone.outerRadiusMeters
        is SegmentOZ -> zone.radiusMeters
    }

    fun setTargetParam(index: Int, param: Double) {
        val current = _state.value
        val wp = current.task.waypoints.getOrNull(index) ?: return
        targetStateById[wp.id] = TargetMemory(param, targetStateById[wp.id]?.locked ?: false)
        updateFrom(current.task, current.taskType)
    }

    fun toggleTargetLock(index: Int) {
        val current = _state.value
        val wp = current.task.waypoints.getOrNull(index) ?: return
        val existing = targetStateById[wp.id]
        val locked = !(existing?.locked ?: false)
        targetStateById[wp.id] = TargetMemory(existing?.param ?: 0.5, locked)
        updateFrom(current.task, current.taskType)
    }

    fun setTargetLock(index: Int, locked: Boolean) {
        val current = _state.value
        val wp = current.task.waypoints.getOrNull(index) ?: return
        val existing = targetStateById[wp.id]
        targetStateById[wp.id] = TargetMemory(existing?.param ?: 0.5, locked)
        updateFrom(current.task, current.taskType)
    }

    fun shouldAutoAdvance(hasEntered: Boolean, closeToTarget: Boolean): Boolean =
        advanceState.shouldAdvance(hasEntered, closeToTarget)

    fun evaluateProximity(
        taskType: TaskType,
        waypointRole: WaypointRole,
        aircraftLat: Double,
        aircraftLon: Double,
        targetLat: Double,
        targetLon: Double
    ): TaskProximityDecision =
        proximityEvaluator.evaluate(
            taskType = taskType,
            waypointRole = waypointRole,
            aircraftLat = aircraftLat,
            aircraftLon = aircraftLon,
            targetLat = targetLat,
            targetLon = targetLon
        )

    fun distanceMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Double = proximityEvaluator.distanceMeters(fromLat, fromLon, toLat, toLon)

    fun setAdvanceMode(mode: TaskAdvanceState.Mode) {
        advanceState.setMode(mode)
        refreshAdvanceSnapshot()
    }

    fun armAdvance(doArm: Boolean) {
        advanceState.setArmed(doArm)
        refreshAdvanceSnapshot()
    }

    fun toggleAdvanceArm() {
        advanceState.toggleArmed()
        refreshAdvanceSnapshot()
    }

    private fun refreshAdvanceSnapshot() {
        _state.value = _state.value.copy(advanceSnapshot = advanceState.snapshot())
    }
}
