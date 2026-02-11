package com.example.xcpro.tasks.domain.engine

import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.geometry.AATGeometryGenerator
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATFinishPointType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATRadiusAuthority
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.validation.AATValidationBridge
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure AAT task engine backed by StateFlow and core task models.
 */
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
            task = task.copy(waypoints = normalizeWaypointsForAAT(task.waypoints)),
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
            task = updated.copy(waypoints = normalizeWaypointsForAAT(updated.waypoints)),
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
            task = current.copy(waypoints = normalizeWaypointsForAAT(updatedWaypoints)),
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
            task = current.copy(waypoints = normalizeWaypointsForAAT(updatedWaypoints)),
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
            this["targetLat"] = lat
            this["targetLon"] = lon
            this["isTargetPointCustomized"] = true
        }
        updatedWaypoints[index] = targetWaypoint.copy(customParameters = updatedParams)
        publish(
            task = current.copy(waypoints = normalizeWaypointsForAAT(updatedWaypoints)),
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
            this["radiusMeters"] = radiusMeters
            this["outerRadiusMeters"] = radiusMeters
        }
        updatedWaypoints[index] = targetWaypoint.copy(
            customRadius = radiusMeters / 1000.0,
            customParameters = updatedParams
        )
        publish(
            task = current.copy(waypoints = normalizeWaypointsForAAT(updatedWaypoints)),
            requestedActiveLeg = _state.value.base.activeLegIndex,
            minimumTime = _state.value.minimumTime,
            maximumTime = _state.value.maximumTime,
            editWaypointIndex = _state.value.editWaypointIndex
        )
    }

    fun calculateTaskDistanceMeters(task: Task = _state.value.base.task): Double {
        val waypoints = normalizeWaypointsForAAT(task.waypoints)
        if (waypoints.size < 2) return 0.0
        val aatWaypoints = waypoints.map { it.toAATWaypoint() }
        val pathPoints = geometryGenerator.calculateOptimalAATPath(aatWaypoints)
        if (pathPoints.size < 2) return 0.0

        var totalMeters = 0.0
        for (i in 0 until pathPoints.lastIndex) {
            val from = pathPoints[i]
            val to = pathPoints[i + 1]
            val distanceKm = AATMathUtils.calculateDistanceKm(from[1], from[0], to[1], to[0])
            totalMeters += distanceKm * 1000.0
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
                waypoints = task.waypoints.map { it.toAATWaypoint() },
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

    private fun normalizeWaypointsForAAT(waypoints: List<TaskWaypoint>): List<TaskWaypoint> {
        if (waypoints.isEmpty()) return emptyList()
        return waypoints.mapIndexed { index, waypoint ->
            val normalizedRole = when {
                waypoints.size == 1 -> WaypointRole.START
                index == 0 -> WaypointRole.START
                index == waypoints.lastIndex -> WaypointRole.FINISH
                else -> WaypointRole.TURNPOINT
            }
            val normalizedType = normalizePointType(waypoint.customPointType, normalizedRole)
            val defaultRadius = defaultRadiusKm(normalizedRole)
            val normalizedRadius = if (waypoint.role != normalizedRole) {
                defaultRadius
            } else {
                waypoint.customRadius?.takeIf { it > 0.0 } ?: defaultRadius
            }
            val normalizedParams = waypoint.customParameters.toMutableMap().apply {
                if (!containsKey("targetLat")) this["targetLat"] = waypoint.lat
                if (!containsKey("targetLon")) this["targetLon"] = waypoint.lon
            }
            waypoint.copy(
                role = normalizedRole,
                customPointType = normalizedType,
                customRadius = normalizedRadius,
                customParameters = normalizedParams
            )
        }
    }

    private fun normalizePointType(customPointType: String?, role: WaypointRole): String {
        return when (role) {
            WaypointRole.START -> {
                val valid = setOf(
                    AATStartPointType.AAT_START_LINE.name,
                    AATStartPointType.AAT_START_CYLINDER.name,
                    AATStartPointType.AAT_START_SECTOR.name
                )
                if (customPointType in valid) customPointType!! else AATStartPointType.AAT_START_LINE.name
            }
            WaypointRole.FINISH -> {
                val valid = setOf(
                    AATFinishPointType.AAT_FINISH_CYLINDER.name,
                    AATFinishPointType.AAT_FINISH_LINE.name
                )
                if (customPointType in valid) customPointType!! else AATFinishPointType.AAT_FINISH_CYLINDER.name
            }
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> {
                val valid = setOf(
                    AATTurnPointType.AAT_CYLINDER.name,
                    AATTurnPointType.AAT_SECTOR.name,
                    AATTurnPointType.AAT_KEYHOLE.name
                )
                if (customPointType in valid) customPointType!! else AATTurnPointType.AAT_CYLINDER.name
            }
        }
    }

    private fun defaultRadiusKm(role: WaypointRole): Double {
        val aatRole = when (role) {
            WaypointRole.START -> AATWaypointRole.START
            WaypointRole.FINISH -> AATWaypointRole.FINISH
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> AATWaypointRole.TURNPOINT
        }
        return AATRadiusAuthority.getRadiusForRole(aatRole)
    }

    private fun TaskWaypoint.toAATWaypoint(): AATWaypoint {
        val normalizedRole = when (role) {
            WaypointRole.START -> AATWaypointRole.START
            WaypointRole.FINISH -> AATWaypointRole.FINISH
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> AATWaypointRole.TURNPOINT
        }
        val startType = customPointType
            ?.let { runCatching { AATStartPointType.valueOf(it) }.getOrNull() }
            ?: AATStartPointType.AAT_START_LINE
        val finishType = customPointType
            ?.let { runCatching { AATFinishPointType.valueOf(it) }.getOrNull() }
            ?: AATFinishPointType.AAT_FINISH_CYLINDER
        val turnType = customPointType
            ?.let { runCatching { AATTurnPointType.valueOf(it) }.getOrNull() }
            ?: AATTurnPointType.AAT_CYLINDER

        val radiusMeters = ((customRadius?.takeIf { it > 0.0 }
            ?: AATRadiusAuthority.getRadiusForRole(normalizedRole)) * 1000.0)
        val innerRadiusMeters = (customParameters["innerRadiusMeters"] as? Number)?.toDouble() ?: 0.0
        val outerRadiusMeters = (customParameters["outerRadiusMeters"] as? Number)?.toDouble() ?: radiusMeters
        val startAngleDegrees = (customParameters["startAngleDegrees"] as? Number)?.toDouble() ?: 0.0
        val endAngleDegrees = (customParameters["endAngleDegrees"] as? Number)?.toDouble() ?: 90.0
        val lineWidthMeters = (customParameters["lineWidthMeters"] as? Number)?.toDouble() ?: radiusMeters
        val targetLat = (customParameters["targetLat"] as? Number)?.toDouble() ?: lat
        val targetLon = (customParameters["targetLon"] as? Number)?.toDouble() ?: lon
        val isTargetPointCustomized =
            (customParameters["isTargetPointCustomized"] as? Boolean) ?: (targetLat != lat || targetLon != lon)

        val areaShape = when (normalizedRole) {
            AATWaypointRole.START -> {
                if (startType == AATStartPointType.AAT_START_LINE) AATAreaShape.LINE else AATAreaShape.CIRCLE
            }
            AATWaypointRole.FINISH -> {
                if (finishType == AATFinishPointType.AAT_FINISH_LINE) AATAreaShape.LINE else AATAreaShape.CIRCLE
            }
            AATWaypointRole.TURNPOINT -> {
                if (turnType == AATTurnPointType.AAT_SECTOR || turnType == AATTurnPointType.AAT_KEYHOLE) {
                    AATAreaShape.SECTOR
                } else {
                    AATAreaShape.CIRCLE
                }
            }
        }

        return AATWaypoint(
            id = id,
            title = title,
            subtitle = subtitle,
            lat = lat,
            lon = lon,
            role = normalizedRole,
            assignedArea = AATAssignedArea(
                shape = areaShape,
                radiusMeters = radiusMeters,
                innerRadiusMeters = innerRadiusMeters,
                outerRadiusMeters = outerRadiusMeters,
                startAngleDegrees = startAngleDegrees,
                endAngleDegrees = endAngleDegrees,
                lineWidthMeters = lineWidthMeters
            ),
            startPointType = startType,
            finishPointType = finishType,
            turnPointType = turnType,
            targetPoint = AATLatLng(targetLat, targetLon),
            isTargetPointCustomized = isTargetPointCustomized
        )
    }

    companion object {
        private val DEFAULT_MINIMUM_TIME: Duration = Duration.ofHours(3)
        private val DEFAULT_MAXIMUM_TIME: Duration = Duration.ofHours(6)
    }
}
