package com.example.xcpro.tasks.domain.engine

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.RacingWaypointCustomParams
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.RacingTaskCalculator
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure Racing task engine backed by StateFlow and core task models.
 */
class DefaultRacingTaskEngine(
    private val calculator: RacingTaskCalculator = RacingTaskCalculator()
) : RacingTaskEngine {

    private val _state = MutableStateFlow(
        RacingTaskEngineState(
            base = TaskEngineState(taskType = TaskType.RACING)
        )
    )
    override val state: StateFlow<RacingTaskEngineState> = _state.asStateFlow()

    override fun setTask(task: Task) {
        publish(
            task = task.copy(waypoints = normalizeWaypointsForRacing(task.waypoints)),
            requestedActiveLeg = _state.value.base.activeLegIndex
        )
    }

    override fun addWaypoint(waypoint: TaskWaypoint) {
        val current = _state.value.base.task
        val updated = current.copy(waypoints = current.waypoints + waypoint)
        publish(
            task = updated.copy(waypoints = normalizeWaypointsForRacing(updated.waypoints)),
            requestedActiveLeg = 0
        )
    }

    override fun removeWaypoint(index: Int) {
        val current = _state.value.base.task
        if (index !in current.waypoints.indices) return
        val updatedWaypoints = current.waypoints.toMutableList().apply { removeAt(index) }
        val adjustedLeg = when {
            updatedWaypoints.isEmpty() -> 0
            index < _state.value.base.activeLegIndex -> _state.value.base.activeLegIndex - 1
            _state.value.base.activeLegIndex >= updatedWaypoints.size -> updatedWaypoints.lastIndex
            else -> _state.value.base.activeLegIndex
        }
        publish(
            task = current.copy(waypoints = normalizeWaypointsForRacing(updatedWaypoints)),
            requestedActiveLeg = adjustedLeg
        )
    }

    override fun reorderWaypoints(fromIndex: Int, toIndex: Int) {
        val current = _state.value.base.task
        if (fromIndex !in current.waypoints.indices || toIndex !in current.waypoints.indices) return
        val updatedWaypoints = current.waypoints.toMutableList().apply {
            val moved = removeAt(fromIndex)
            add(toIndex, moved)
        }
        publish(
            task = current.copy(waypoints = normalizeWaypointsForRacing(updatedWaypoints)),
            requestedActiveLeg = _state.value.base.activeLegIndex
        )
    }

    override fun setActiveLeg(index: Int) {
        publish(task = _state.value.base.task, requestedActiveLeg = index)
    }

    override fun clearTask() {
        publish(
            task = Task(id = _state.value.base.task.id.ifBlank { "new-task" }, waypoints = emptyList()),
            requestedActiveLeg = 0
        )
    }

    override fun calculateTaskDistanceMeters(task: Task): Double {
        val waypoints = normalizeWaypointsForRacing(task.waypoints)
        if (waypoints.size < 2) return 0.0

        val racingWaypoints = waypoints.map { waypoint -> waypoint.toRacingWaypoint() }
        val path = calculator.findOptimalFAIPath(racingWaypoints)
        if (path.size < 2) return 0.0

        var totalMeters = 0.0
        for (i in 0 until path.lastIndex) {
            val from = path[i]
            val to = path[i + 1]
            totalMeters += RacingGeometryUtils.haversineDistanceMeters(from.first, from.second, to.first, to.second)
        }
        return totalMeters
    }

    override fun calculateSegmentDistanceMeters(from: TaskWaypoint, to: TaskWaypoint): Double {
        return RacingGeometryUtils.haversineDistanceMeters(from.lat, from.lon, to.lat, to.lon)
    }

    private fun publish(task: Task, requestedActiveLeg: Int) {
        val clampedLeg = if (task.waypoints.isEmpty()) {
            0
        } else {
            requestedActiveLeg.coerceIn(0, task.waypoints.lastIndex)
        }
        val valid = task.waypoints.size >= 2
        _state.value = RacingTaskEngineState(
            base = TaskEngineState(
                taskType = TaskType.RACING,
                task = task,
                activeLegIndex = clampedLeg,
                isTaskValid = valid
            ),
            taskDistanceMeters = calculateTaskDistanceMeters(task)
        )
    }

    private fun normalizeWaypointsForRacing(waypoints: List<TaskWaypoint>): List<TaskWaypoint> {
        if (waypoints.isEmpty()) return emptyList()
        return waypoints.mapIndexed { index, waypoint ->
            val normalizedRole = when {
                waypoints.size == 1 -> WaypointRole.START
                index == 0 -> WaypointRole.START
                index == waypoints.lastIndex -> WaypointRole.FINISH
                else -> WaypointRole.TURNPOINT
            }
            val normalizedType = normalizePointType(waypoint.customPointType, normalizedRole)
            val defaultRadiusMeters = defaultRadiusMeters(normalizedRole, normalizedType)
            val normalizedRadiusMeters = if (waypoint.role != normalizedRole) {
                defaultRadiusMeters
            } else {
                waypoint.resolvedCustomRadiusMeters()?.takeIf { it > 0.0 } ?: defaultRadiusMeters
            }
            waypoint
                .withCustomRadiusMeters(normalizedRadiusMeters)
                .copy(
                    role = normalizedRole,
                    customPointType = normalizedType
                )
        }
    }

    private fun normalizePointType(customPointType: String?, role: WaypointRole): String {
        return when (role) {
            WaypointRole.START -> {
                val valid = setOf(
                    RacingStartPointType.START_LINE.name,
                    RacingStartPointType.START_CYLINDER.name,
                    RacingStartPointType.FAI_START_SECTOR.name
                )
                if (customPointType in valid) customPointType!! else RacingStartPointType.START_LINE.name
            }
            WaypointRole.FINISH -> {
                val valid = setOf(
                    RacingFinishPointType.FINISH_CYLINDER.name,
                    RacingFinishPointType.FINISH_LINE.name
                )
                if (customPointType in valid) customPointType!! else RacingFinishPointType.FINISH_CYLINDER.name
            }
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> {
                val valid = setOf(
                    RacingTurnPointType.TURN_POINT_CYLINDER.name,
                    RacingTurnPointType.FAI_QUADRANT.name,
                    RacingTurnPointType.KEYHOLE.name
                )
                if (customPointType in valid) customPointType!! else RacingTurnPointType.TURN_POINT_CYLINDER.name
            }
        }
    }

    private fun defaultRadiusMeters(role: WaypointRole, customPointType: String): Double {
        return when (role) {
            WaypointRole.START -> 10_000.0
            WaypointRole.FINISH -> 3_000.0
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> {
                if (customPointType == RacingTurnPointType.KEYHOLE.name) 10_000.0 else 500.0
            }
        }
    }

    private fun TaskWaypoint.toRacingWaypoint(): RacingWaypoint {
        val normalizedRole = when (role) {
            WaypointRole.START -> RacingWaypointRole.START
            WaypointRole.FINISH -> RacingWaypointRole.FINISH
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> RacingWaypointRole.TURNPOINT
        }
        val startType = customPointType
            ?.let { runCatching { RacingStartPointType.valueOf(it) }.getOrNull() }
            ?: RacingStartPointType.START_LINE
        val finishType = customPointType
            ?.let { runCatching { RacingFinishPointType.valueOf(it) }.getOrNull() }
            ?: RacingFinishPointType.FINISH_CYLINDER
        val turnType = customPointType
            ?.let { runCatching { RacingTurnPointType.valueOf(it) }.getOrNull() }
            ?: RacingTurnPointType.TURN_POINT_CYLINDER
        val racingParams = RacingWaypointCustomParams.from(customParameters)

        return RacingWaypoint.createWithStandardizedDefaults(
            id = id,
            title = title,
            subtitle = subtitle,
            lat = lat,
            lon = lon,
            role = normalizedRole,
            startPointType = startType,
            finishPointType = finishType,
            turnPointType = turnType,
            customGateWidthMeters = resolvedCustomRadiusMeters()?.takeIf { it > 0.0 },
            keyholeInnerRadiusMeters = racingParams.keyholeInnerRadiusMeters,
            keyholeAngle = racingParams.keyholeAngle,
            faiQuadrantOuterRadiusMeters = racingParams.faiQuadrantOuterRadiusMeters
        )
    }
}
