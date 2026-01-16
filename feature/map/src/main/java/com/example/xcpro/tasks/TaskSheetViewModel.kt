package com.example.xcpro.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import com.example.xcpro.tasks.domain.model.GeoPoint
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.pow

/**
 * Bridges UI intents to TaskManagerCoordinator while maintaining
 * a domain TaskRepository for validation/stats.
 * AI-NOTE: This keeps existing TaskManager-driven flows intact while we
 * migrate logic into the domain layer.
 */
class TaskSheetViewModel(
    private val taskManager: TaskManagerCoordinator,
    private val repository: TaskRepository = TaskRepository()
) : ViewModel() {

    val uiState: StateFlow<TaskUiState> = repository.state

    private var map: MapLibreMap? = null

    init {
        taskManager.setProximityHandler { entered, close ->
            onProximityEvent(entered, close)
        }
        taskManager.addLegChangeListener { _ ->
            repository.armAdvance(false) // manual leg change disarms auto-advance
            sync()
        }
        sync()
    }

    fun setMap(map: MapLibreMap?) {
        this.map = map
        taskManager.setMapInstance(map)
    }

    fun onAddWaypoint(wp: SearchWaypoint) = mutate {
        taskManager.addWaypoint(wp)
    }

    fun onRemoveWaypoint(index: Int) = mutate {
        taskManager.removeWaypoint(index)
    }

    fun onReorderWaypoint(from: Int, to: Int) = mutate {
        taskManager.reorderWaypoints(from, to)
    }

    fun onReplaceWaypoint(index: Int, wp: SearchWaypoint) = mutate {
        taskManager.replaceWaypoint(index, wp)
    }

    fun onSetTargetParam(index: Int, param: Double) {
        repository.setTargetParam(index, param)
        // Relay to legacy manager so map overlays stay in sync.
        repository.state.value.targets.getOrNull(index)?.target?.let { target ->
            taskManager.updateAATTargetPoint(index, target.lat, target.lon)
            taskManager.plotOnMap(map)
        }
    }

    fun onToggleTargetLock(index: Int) {
        repository.toggleTargetLock(index)
        repository.state.value.targets.getOrNull(index)?.target?.let { target ->
            taskManager.updateAATTargetPoint(index, target.lat, target.lon)
            taskManager.plotOnMap(map)
        }
    }

    fun onAdvanceMode(mode: TaskAdvanceState.Mode) {
        repository.setAdvanceMode(mode)
    }

    fun onAdvanceArmToggle() {
        repository.toggleAdvanceArm()
    }

    fun onProximityEvent(hasEnteredOZ: Boolean, closeToTarget: Boolean) = mutate {
        if (repository.shouldAutoAdvance(hasEnteredOZ, closeToTarget)) {
            taskManager.advanceToNextLeg()
        }
    }

    fun onLocationUpdate(lat: Double, lon: Double) {
        val state = uiState.value
        val leg = taskManager.currentLeg
        val waypoint = state.task.waypoints.getOrNull(leg) ?: return
        val target = state.targets.getOrNull(leg)?.target
        val activePoint = target?.let { GeoPoint(it.lat, it.lon) } ?: GeoPoint(waypoint.lat, waypoint.lon)
        val distance = haversineMeters(lat, lon, activePoint.lat, activePoint.lon)
        val radius = effectiveRadius(taskManager.taskType, waypoint.role)
        val hasEntered = distance <= radius + 30.0 // 30 m buffer
        val closeToTarget = distance <= 200.0
        onProximityEvent(hasEntered, closeToTarget)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).pow(2.0) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).pow(2.0)
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
    }

    private fun effectiveRadius(taskType: TaskType, role: com.example.xcpro.tasks.core.WaypointRole): Double =
        when (role) {
            com.example.xcpro.tasks.core.WaypointRole.START -> 100.0 // half width of 200m line
            com.example.xcpro.tasks.core.WaypointRole.FINISH -> 3000.0
            com.example.xcpro.tasks.core.WaypointRole.TURNPOINT,
            com.example.xcpro.tasks.core.WaypointRole.OPTIONAL ->
                if (taskType == TaskType.AAT) 5000.0 else 500.0
        }

    fun importPersistedTask(json: String) = mutate {
        val persisted = TaskPersistSerializer.deserialize(json)
        val (importedTask, targets) = TaskPersistSerializer.toTask(persisted)
        taskManager.setTaskType(persisted.taskType)
        taskManager.clearTask()
        importedTask.waypoints.forEach { wp ->
            onAddWaypoint(
                SearchWaypoint(
                    id = wp.id,
                    title = wp.title,
                    subtitle = wp.subtitle,
                    lat = wp.lat,
                    lon = wp.lon
                )
            )
        }
        // apply targets after waypoints are present
        if (persisted.taskType == TaskType.AAT) {
            targets.forEach { t ->
                repository.setTargetParam(t.index, t.targetParam)
                repository.setTargetLock(t.index, t.isLocked)
                t.target?.let { target -> taskManager.updateAATTargetPoint(t.index, target.lat, target.lon) }
            }
        }
        // apply OZ params where present (basic radius support)
        persisted.waypoints.forEachIndexed { index, wp ->
            val radius = wp.ozParams["radiusMeters"] ?: wp.ozParams["outerRadiusMeters"]
            if (radius != null && persisted.taskType == TaskType.AAT) {
                taskManager.updateAATArea(index, radius)
            }
        if (persisted.taskType == TaskType.AAT) {
            val keyholeInnerKm = wp.ozParams["innerRadiusMeters"]?.div(1000.0)
            val angleDeg = wp.ozParams["angleDeg"]
            val sectorOuterKm = wp.ozParams["outerRadiusMeters"]?.div(1000.0)
            taskManager.updateAATWaypointPointType(
                index = index,
                startType = null,
                finishType = null,
                turnType = null,
                gateWidth = radius?.div(1000.0),
                keyholeInnerRadius = keyholeInnerKm,
                keyholeAngle = angleDeg,
                sectorOuterRadius = sectorOuterKm
            )
        }
            if (radius != null && persisted.taskType == TaskType.RACING && index > 0 && index < importedTask.waypoints.lastIndex) {
                // best-effort: use gateWidth for turnpoints (km)
                taskManager.updateWaypointPointType(
                    index = index,
                    startType = null,
                    finishType = null,
                    turnType = null,
                    gateWidth = radius / 1000.0,
                    keyholeInnerRadius = null,
                    keyholeAngle = null,
                    faiQuadrantOuterRadius = null
                )
            }
        }
        taskManager.setActiveLeg(0)
    }

    fun onUpdateWaypointPointType(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidth: Double?,
        keyholeInnerRadius: Double?,
        keyholeAngle: Double?,
        faiQuadrantOuterRadius: Double?
    ) = mutate {
        taskManager.updateWaypointPointType(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidth = gateWidth,
            keyholeInnerRadius = keyholeInnerRadius,
            keyholeAngle = keyholeAngle,
            faiQuadrantOuterRadius = faiQuadrantOuterRadius
        )
    }

    fun onUpdateAATArea(index: Int, radiusMeters: Double) = mutate {
        taskManager.updateAATArea(index, radiusMeters)
    }

    fun onUpdateAATWaypointPointType(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidth: Double?,
        keyholeInnerRadius: Double?,
        keyholeAngle: Double?,
        sectorOuterRadius: Double?
    ) = mutate {
        taskManager.updateAATWaypointPointType(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidth = gateWidth,
            keyholeInnerRadius = keyholeInnerRadius,
            keyholeAngle = keyholeAngle,
            sectorOuterRadius = sectorOuterRadius
        )
    }

    fun onSetTaskType(taskType: TaskType) = mutate {
        taskManager.setTaskType(taskType)
    }

    fun onClearTask() = mutate {
        taskManager.clearTask()
    }

    private fun mutate(block: () -> Unit) {
        block()
        taskManager.plotOnMap(map)
        sync()
    }

    private fun sync() {
        repository.updateFrom(
            task = taskManager.currentTask,
            taskType = taskManager.taskType,
            activeIndex = taskManager.currentLeg
        )
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun factory(taskManager: TaskManagerCoordinator): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TaskSheetViewModel(taskManager) as T
                }
            }
    }
}
