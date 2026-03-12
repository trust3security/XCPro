package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TargetStateCustomParams
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
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
    private val observationZoneResolver = TaskObservationZoneResolver

    // Keeps user-chosen target params/locks by waypoint identity so they survive refresh.
    private val targetStateByKey = mutableMapOf<TargetMemoryKey, TargetMemory>()
    private val advanceState = TaskAdvanceState()

    private data class TargetMemoryKey(
        val index: Int,
        val waypointId: String
    )

    private data class TargetMemory(
        var param: Double,
        var locked: Boolean,
        var target: GeoPoint?
    )

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
                racingValidationProfile = racingValidationProfile,
                advanceSnapshot = advanceState.snapshot()
            )
            is TaskValidator.ValidationResult.Invalid -> TaskUiState(
                task = task,
                taskType = taskType,
                stats = stats.copy(isTaskValid = false),
                validationErrors = validation.errors,
                targets = domain.targetSnapshots,
                racingValidationProfile = racingValidationProfile,
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
        val validKeys = mutableSetOf<TargetMemoryKey>()

        forEachIndexed { index, wp ->
            val memoryKey = TargetMemoryKey(index = index, waypointId = wp.id)
            validKeys += memoryKey
            val role = wp.role
            val zone = observationZoneResolver.resolve(
                taskType = taskType,
                waypoint = wp,
                role = role
            )
            val allowsTarget = taskType == TaskType.AAT &&
                (role == WaypointRole.TURNPOINT || role == WaypointRole.OPTIONAL)
            hasTargetsFlag = hasTargetsFlag || allowsTarget

            val remembered = targetStateByKey[memoryKey]
            val targetParams = TargetStateCustomParams.from(
                source = wp.customParameters,
                fallbackTargetParam = remembered?.param ?: 0.5,
                fallbackTargetLocked = remembered?.locked ?: false
            )
            val targetParam = targetParams.targetParam
            val targetLocked = targetParams.targetLocked
            val targetLat = targetParams.targetLat
            val targetLon = targetParams.targetLon
            val seedTarget = when {
                targetLat != null && targetLon != null -> GeoPoint(targetLat, targetLon)
                else -> remembered?.target
            }

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
        targetStateByKey.keys.retainAll(validKeys)

        // Second pass to place targets along isoline inside the OZ arc.
        val withTargets = basePoints.mapIndexed { index, point ->
            if (taskType == TaskType.AAT && point.allowsTarget && index in 1 until lastIndex) {
                val memoryKey = TargetMemoryKey(index = index, waypointId = point.id)
                val preservedTarget = targetStateByKey[memoryKey]?.target ?: point.target
                if (point.targetLocked && preservedTarget != null) {
                    targetStateByKey[memoryKey] = TargetMemory(
                        param = point.targetParam,
                        locked = true,
                        target = preservedTarget
                    )
                    point.copy(target = preservedTarget)
                } else {
                    val prev = GeoPoint(this[index - 1].lat, this[index - 1].lon)
                    val next = GeoPoint(this[index + 1].lat, this[index + 1].lon)
                    val result = AATTargetOptimizer.moveTarget(prev, point, next, point.targetParam)
                    targetStateByKey[memoryKey] = TargetMemory(
                        param = result.rangeParam,
                        locked = point.targetLocked,
                        target = result.target
                    )
                    point.copy(target = result.target, targetParam = result.rangeParam)
                }
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
        val key = TargetMemoryKey(index = index, waypointId = wp.id)
        val existing = targetStateByKey[key]
        targetStateByKey[key] = TargetMemory(
            param = param,
            locked = existing?.locked ?: false,
            target = existing?.target ?: current.targets.getOrNull(index)?.target
        )
        updateFrom(
            task = current.task,
            taskType = current.taskType,
            activeIndex = current.stats.activeIndex,
            racingValidationProfile = current.racingValidationProfile
        )
    }

    fun toggleTargetLock(index: Int) {
        val current = _state.value
        val wp = current.task.waypoints.getOrNull(index) ?: return
        val key = TargetMemoryKey(index = index, waypointId = wp.id)
        val existing = targetStateByKey[key]
        val locked = !(existing?.locked ?: false)
        targetStateByKey[key] = TargetMemory(
            param = existing?.param ?: 0.5,
            locked = locked,
            target = if (locked) {
                existing?.target ?: current.targets.getOrNull(index)?.target
            } else {
                existing?.target
            }
        )
        updateFrom(
            task = current.task,
            taskType = current.taskType,
            activeIndex = current.stats.activeIndex,
            racingValidationProfile = current.racingValidationProfile
        )
    }

    fun setTargetLock(index: Int, locked: Boolean) {
        val current = _state.value
        val wp = current.task.waypoints.getOrNull(index) ?: return
        val key = TargetMemoryKey(index = index, waypointId = wp.id)
        val existing = targetStateByKey[key]
        targetStateByKey[key] = TargetMemory(
            param = existing?.param ?: 0.5,
            locked = locked,
            target = if (locked) {
                existing?.target ?: current.targets.getOrNull(index)?.target
            } else {
                existing?.target
            }
        )
        updateFrom(
            task = current.task,
            taskType = current.taskType,
            activeIndex = current.stats.activeIndex,
            racingValidationProfile = current.racingValidationProfile
        )
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
