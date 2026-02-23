package com.example.xcpro.tasks.domain.engine

import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.geometry.AATGeometryGenerator
import com.example.xcpro.tasks.aat.validation.AATValidationBridge
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.core.TaskWaypointParamKeys
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
/** Pure AAT task engine backed by StateFlow and core task models. */
class DefaultAATTaskEngine(
    private val geometryGenerator: AATGeometryGenerator = AATGeometryGenerator(),
    private val validationBridge: AATValidationBridge = AATValidationBridge()
) : AATTaskEngine {

    private val _state = MutableStateFlow(
        AATTaskEngineState(
            base = TaskEngineState(taskType = TaskType.AAT),
            minimumTime = DEFAULT_MINIMUM_TIME,
            maximumTime = DEFAULT_MAXIMUM_TIME
        )
    )
    override val state: StateFlow<AATTaskEngineState> = _state.asStateFlow()
    override fun setTask(task: Task) {
        publish(
            task = task.copy(waypoints = AATTaskWaypointCodec.normalizeWaypointsForAAT(task.waypoints)),
            requestedActiveLeg = _state.value.base.activeLegIndex,
            minimumTime = _state.value.minimumTime,
            maximumTime = _state.value.maximumTime,
            editWaypointIndex = _state.value.editWaypointIndex
        )
    }

    override fun addWaypoint(waypoint: TaskWaypoint) {
        val current = _state.value.base.task
        val updated = current.copy(waypoints = current.waypoints + waypoint)
        publish(
            task = updated.copy(waypoints = AATTaskWaypointCodec.normalizeWaypointsForAAT(updated.waypoints)),
            requestedActiveLeg = _state.value.base.activeLegIndex,
            minimumTime = _state.value.minimumTime,
            maximumTime = _state.value.maximumTime,
            editWaypointIndex = _state.value.editWaypointIndex
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
        val editWaypointIndex = _state.value.editWaypointIndex?.let { existing ->
            when {
                updatedWaypoints.isEmpty() -> null
                existing == index -> null
                existing > index -> existing - 1
                else -> existing
            }
        }
        publish(
            task = current.copy(waypoints = AATTaskWaypointCodec.normalizeWaypointsForAAT(updatedWaypoints)),
            requestedActiveLeg = adjustedLeg,
            minimumTime = _state.value.minimumTime,
            maximumTime = _state.value.maximumTime,
            editWaypointIndex = editWaypointIndex
        )
    }

    override fun reorderWaypoints(fromIndex: Int, toIndex: Int) {
        val current = _state.value.base.task
        if (fromIndex !in current.waypoints.indices || toIndex !in current.waypoints.indices) return
        val updatedWaypoints = current.waypoints.toMutableList().apply {
            val moved = removeAt(fromIndex)
            add(toIndex, moved)
        }
        val updatedEditIndex = _state.value.editWaypointIndex?.let { editIndex ->
            when (editIndex) {
                fromIndex -> toIndex
                in minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex) -> {
                    if (fromIndex < toIndex) editIndex - 1 else editIndex + 1
                }
                else -> editIndex
            }
        }
        publish(
            task = current.copy(waypoints = AATTaskWaypointCodec.normalizeWaypointsForAAT(updatedWaypoints)),
            requestedActiveLeg = _state.value.base.activeLegIndex,
            minimumTime = _state.value.minimumTime,
            maximumTime = _state.value.maximumTime,
            editWaypointIndex = updatedEditIndex
        )
    }

    override fun setActiveLeg(index: Int) {
        publish(
            task = _state.value.base.task,
            requestedActiveLeg = index,
            minimumTime = _state.value.minimumTime,
            maximumTime = _state.value.maximumTime,
            editWaypointIndex = _state.value.editWaypointIndex
        )
    }

    override fun clearTask() {
        publish(
            task = Task(id = _state.value.base.task.id.ifBlank { "new-task" }, waypoints = emptyList()),
            requestedActiveLeg = 0,
            minimumTime = _state.value.minimumTime,
            maximumTime = _state.value.maximumTime,
            editWaypointIndex = null
        )
    }

    override fun updateTargetPoint(index: Int, lat: Double, lon: Double) {
        val current = _state.value.base.task
        if (index !in current.waypoints.indices) return
        val updatedWaypoints = current.waypoints.toMutableList()
        val targetWaypoint = updatedWaypoints[index]
        val updatedParams = targetWaypoint.customParameters.toMutableMap().apply {
            this[TaskWaypointParamKeys.TARGET_LAT] = lat
            this[TaskWaypointParamKeys.TARGET_LON] = lon
            this[TaskWaypointParamKeys.IS_TARGET_POINT_CUSTOMIZED] = true
        }
        updatedWaypoints[index] = targetWaypoint.copy(customParameters = updatedParams)
        publish(
            task = current.copy(waypoints = AATTaskWaypointCodec.normalizeWaypointsForAAT(updatedWaypoints)),
            requestedActiveLeg = _state.value.base.activeLegIndex,
            minimumTime = _state.value.minimumTime,
            maximumTime = _state.value.maximumTime,
            editWaypointIndex = index
        )
    }

    override fun updateParameters(minimumTime: Duration, maximumTime: Duration) {
        publish(
            task = _state.value.base.task,
            requestedActiveLeg = _state.value.base.activeLegIndex,
            minimumTime = minimumTime,
            maximumTime = maximumTime,
            editWaypointIndex = _state.value.editWaypointIndex
        )
    }

    override fun updateAreaRadiusMeters(index: Int, radiusMeters: Double) {
        if (radiusMeters <= 0.0) return
        val current = _state.value.base.task
        if (index !in current.waypoints.indices) return
        val updatedWaypoints = current.waypoints.toMutableList()
        val targetWaypoint = updatedWaypoints[index]
        val updatedParams = targetWaypoint.customParameters.toMutableMap().apply {
            this[TaskWaypointParamKeys.RADIUS_METERS] = radiusMeters
            this[TaskWaypointParamKeys.OUTER_RADIUS_METERS] = radiusMeters
        }
        updatedWaypoints[index] = targetWaypoint
            .withCustomRadiusMeters(radiusMeters)
            .copy(customParameters = updatedParams)
        publish(
            task = current.copy(waypoints = AATTaskWaypointCodec.normalizeWaypointsForAAT(updatedWaypoints)),
            requestedActiveLeg = _state.value.base.activeLegIndex,
            minimumTime = _state.value.minimumTime,
            maximumTime = _state.value.maximumTime,
            editWaypointIndex = _state.value.editWaypointIndex
        )
    }

    fun calculateTaskDistanceMeters(task: Task = _state.value.base.task): Double {
        val waypoints = AATTaskWaypointCodec.normalizeWaypointsForAAT(task.waypoints)
        if (waypoints.size < 2) return 0.0
        val aatWaypoints = waypoints.map(AATTaskWaypointCodec::toAATWaypoint)
        val pathPoints = geometryGenerator.calculateOptimalAATPath(aatWaypoints)
        if (pathPoints.size < 2) return 0.0

        var totalMeters = 0.0
        for (i in 0 until pathPoints.lastIndex) {
            val from = pathPoints[i]
            val to = pathPoints[i + 1]
            totalMeters += AATMathUtils.calculateDistanceMeters(from[1], from[0], to[1], to[0])
        }
        return totalMeters
    }

    private fun publish(
        task: Task,
        requestedActiveLeg: Int,
        minimumTime: Duration,
        maximumTime: Duration,
        editWaypointIndex: Int?
    ) {
        val clampedLeg = if (task.waypoints.isEmpty()) {
            0
        } else {
            requestedActiveLeg.coerceIn(0, task.waypoints.lastIndex)
        }
        val valid = validationBridge.isTaskValid(
            SimpleAATTask(
                id = task.id,
                waypoints = task.waypoints.map(AATTaskWaypointCodec::toAATWaypoint),
                minimumTime = minimumTime,
                maximumTime = maximumTime.takeIf { !it.isZero && !it.isNegative }
            )
        )
        _state.value = AATTaskEngineState(
            base = TaskEngineState(
                taskType = TaskType.AAT,
                task = task,
                activeLegIndex = clampedLeg,
                isTaskValid = valid
            ),
            minimumTime = minimumTime,
            maximumTime = maximumTime,
            editWaypointIndex = editWaypointIndex
        )
    }
    companion object {
        private val DEFAULT_MINIMUM_TIME: Duration = Duration.ofHours(3)
        private val DEFAULT_MAXIMUM_TIME: Duration = Duration.ofHours(6)
    }
}
