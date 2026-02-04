package com.example.xcpro.tasks

import androidx.lifecycle.ViewModel
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import com.example.xcpro.tasks.domain.model.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow

/**
 * Bridges UI intents to task use-cases while maintaining
 * a domain TaskRepository for validation/stats.
 */
@HiltViewModel
class TaskSheetViewModel @Inject constructor(
    private val taskCoordinator: TaskSheetCoordinatorUseCase,
    private val useCase: TaskSheetUseCase
) : ViewModel() {

    val uiState: StateFlow<TaskUiState> = useCase.state

    private val legChangeListener: (Int) -> Unit = {
        useCase.armAdvance(false) // manual leg change disarms auto-advance
        sync()
    }

    init {
        taskCoordinator.setProximityHandler { entered, close ->
            onProximityEvent(entered, close)
        }
        taskCoordinator.addLegChangeListener(legChangeListener)
        sync()
    }

    override fun onCleared() {
        taskCoordinator.removeLegChangeListener(legChangeListener)
        super.onCleared()
    }

    fun onAddWaypoint(wp: SearchWaypoint) = mutate {
        taskCoordinator.addWaypoint(wp)
    }

    fun onRemoveWaypoint(index: Int) = mutate {
        taskCoordinator.removeWaypoint(index)
    }

    fun onReorderWaypoint(from: Int, to: Int) = mutate {
        taskCoordinator.reorderWaypoints(from, to)
    }

    fun onReplaceWaypoint(index: Int, wp: SearchWaypoint) = mutate {
        taskCoordinator.replaceWaypoint(index, wp)
    }

    fun onSetTargetParam(index: Int, param: Double) {
        useCase.setTargetParam(index, param)
        // Relay to legacy manager so map overlays stay in sync.
        useCase.state.value.targets.getOrNull(index)?.target?.let { target ->
            taskCoordinator.updateAATTargetPoint(index, target.lat, target.lon)
        }
    }

    fun onToggleTargetLock(index: Int) {
        useCase.toggleTargetLock(index)
        useCase.state.value.targets.getOrNull(index)?.target?.let { target ->
            taskCoordinator.updateAATTargetPoint(index, target.lat, target.lon)
        }
    }

    fun onAdvanceMode(mode: TaskAdvanceState.Mode) {
        useCase.setAdvanceMode(mode)
    }

    fun onAdvanceArmToggle() {
        useCase.toggleAdvanceArm()
    }

    fun onProximityEvent(hasEnteredOZ: Boolean, closeToTarget: Boolean) = mutate {
        if (useCase.shouldAutoAdvance(hasEnteredOZ, closeToTarget)) {
            taskCoordinator.advanceToNextLeg()
        }
    }

    fun onLocationUpdate(lat: Double, lon: Double) {
        val state = uiState.value
        val leg = taskCoordinator.currentLeg
        val waypoint = state.task.waypoints.getOrNull(leg) ?: return
        val target = state.targets.getOrNull(leg)?.target
        val activePoint = target?.let { GeoPoint(it.lat, it.lon) } ?: GeoPoint(waypoint.lat, waypoint.lon)
        val distance = haversineMeters(lat, lon, activePoint.lat, activePoint.lon)
        val radius = effectiveRadius(taskCoordinator.taskType, waypoint.role)
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
        taskCoordinator.setTaskType(persisted.taskType)
        taskCoordinator.clearTask()
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
                useCase.setTargetParam(t.index, t.targetParam)
                useCase.setTargetLock(t.index, t.isLocked)
                t.target?.let { target -> taskCoordinator.updateAATTargetPoint(t.index, target.lat, target.lon) }
            }
        }
        // apply OZ params where present (basic radius support)
        persisted.waypoints.forEachIndexed { index, wp ->
            val radius = wp.ozParams["radiusMeters"] ?: wp.ozParams["outerRadiusMeters"]
            if (radius != null && persisted.taskType == TaskType.AAT) {
                taskCoordinator.updateAATArea(index, radius)
            }
            if (persisted.taskType == TaskType.AAT) {
                val keyholeInnerKm = wp.ozParams["innerRadiusMeters"]?.div(1000.0)
                val angleDeg = wp.ozParams["angleDeg"]
                val sectorOuterKm = wp.ozParams["outerRadiusMeters"]?.div(1000.0)
                taskCoordinator.updateAATWaypointPointType(
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
                taskCoordinator.updateWaypointPointType(
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
        taskCoordinator.setActiveLeg(0)
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
        taskCoordinator.updateWaypointPointType(
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
        taskCoordinator.updateAATArea(index, radiusMeters)
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
        taskCoordinator.updateAATWaypointPointType(
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
        taskCoordinator.setTaskType(taskType)
    }

    fun onClearTask() = mutate {
        taskCoordinator.clearTask()
    }

    private fun mutate(block: () -> Unit) {
        block()
        sync()
    }

    private fun sync() {
        useCase.updateFrom(
            task = taskCoordinator.currentTask,
            taskType = taskCoordinator.taskType,
            activeIndex = taskCoordinator.currentLeg
        )
    }
}
