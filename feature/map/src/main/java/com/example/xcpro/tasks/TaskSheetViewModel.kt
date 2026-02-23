package com.example.xcpro.tasks

import androidx.lifecycle.ViewModel
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.PersistedOzParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridges UI intents to task use-cases while maintaining
 * a domain TaskRepository for validation/stats.
 */
data class RacingStartDistanceUi(
    val distanceMeters: Double,
    val isOptimalCrossing: Boolean
)

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
        syncAatTargetAt(index)
    }

    fun onToggleTargetLock(index: Int) {
        useCase.toggleTargetLock(index)
        syncAatTargetAt(index)
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
        val leg = state.stats.activeIndex
        val waypoint = state.task.waypoints.getOrNull(leg) ?: return
        val target = state.targets.getOrNull(leg)?.target
        val proximity = useCase.evaluateProximity(
            taskType = state.taskType,
            waypointRole = waypoint.role,
            aircraftLat = lat,
            aircraftLon = lon,
            targetLat = target?.lat ?: waypoint.lat,
            targetLon = target?.lon ?: waypoint.lon
        )
        onProximityEvent(
            hasEnteredOZ = proximity.hasEnteredObservationZone,
            closeToTarget = proximity.isCloseToTarget
        )
    }

    fun distanceToActiveWaypointMeters(lat: Double, lon: Double): Double? {
        val state = uiState.value
        val leg = state.stats.activeIndex
        val waypoint = state.task.waypoints.getOrNull(leg) ?: return null
        val target = state.targets.getOrNull(leg)?.target
        return useCase.distanceMeters(
            fromLat = lat,
            fromLon = lon,
            toLat = target?.lat ?: waypoint.lat,
            toLon = target?.lon ?: waypoint.lon
        )
    }

    fun resolveRacingStartDistanceUi(
        selectedStartType: RacingStartPointType,
        startWaypoint: TaskWaypoint,
        nextWaypoint: TaskWaypoint
    ): RacingStartDistanceUi {
        val useOptimalCrossing = selectedStartType == RacingStartPointType.START_LINE
        val distanceMeters = taskCoordinator.calculateDistanceToNextWaypointMeters(
            fromWaypoint = startWaypoint,
            nextWaypoint = nextWaypoint,
            useOptimalStartLine = useOptimalCrossing
        )
        return RacingStartDistanceUi(
            distanceMeters = distanceMeters,
            isOptimalCrossing = useOptimalCrossing
        )
    }

    fun calculateDistanceToNextWaypointMeters(
        fromWaypoint: TaskWaypoint,
        nextWaypoint: TaskWaypoint
    ): Double {
        return taskCoordinator.calculateDistanceToNextWaypointMeters(
            fromWaypoint = fromWaypoint,
            nextWaypoint = nextWaypoint,
            useOptimalStartLine = false
        )
    }

    fun onSetActiveLeg(index: Int) = mutate {
        val waypoints = uiState.value.task.waypoints
        if (waypoints.isEmpty()) return@mutate
        taskCoordinator.setActiveLeg(index.coerceIn(0, waypoints.lastIndex))
    }

    fun onUpdateAATParameters(minimumTime: Duration, maximumTime: Duration) = mutate {
        taskCoordinator.updateAATParameters(minimumTime, maximumTime)
    }

    fun importPersistedTask(json: String) {
        tryImportPersistedTask(json)
    }

    fun tryImportPersistedTask(json: String): Boolean {
        val persisted = runCatching { TaskPersistSerializer.deserialize(json) }.getOrNull() ?: return false
        return runCatching {
            mutate { applyPersistedTask(persisted) }
        }.isSuccess
    }

    private fun applyPersistedTask(persisted: TaskPersistSerializer.PersistedTask) {
        val (importedTask, targets) = TaskPersistSerializer.toTask(persisted)
        taskCoordinator.setTaskType(persisted.taskType)
        taskCoordinator.clearTask()
        importWaypoints(importedTask)
        applyImportedTargets(taskType = persisted.taskType, targets = targets)
        applyImportedObservationZones(
            persisted = persisted,
            taskType = persisted.taskType,
            importedTask = importedTask
        )
        taskCoordinator.setActiveLeg(0)
    }

    fun onUpdateWaypointPointType(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        faiQuadrantOuterRadiusMeters: Double?
    ) = mutate {
        taskCoordinator.updateWaypointPointType(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidthMeters = gateWidthMeters,
            keyholeInnerRadiusMeters = keyholeInnerRadiusMeters,
            keyholeAngle = keyholeAngle,
            faiQuadrantOuterRadiusMeters = faiQuadrantOuterRadiusMeters
        )
    }

    fun onUpdateAATArea(index: Int, radiusMeters: Double) = mutate {
        taskCoordinator.updateAATArea(index, radiusMeters)
    }

    fun onUpdateAATWaypointPointTypeMeters(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        sectorOuterRadiusMeters: Double?
    ) = mutate {
        taskCoordinator.updateAATWaypointPointTypeMeters(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidthMeters = gateWidthMeters,
            keyholeInnerRadiusMeters = keyholeInnerRadiusMeters,
            keyholeAngle = keyholeAngle,
            sectorOuterRadiusMeters = sectorOuterRadiusMeters
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

    private fun syncAatTargetAt(index: Int) {
        useCase.state.value.targets.getOrNull(index)?.target?.let { target ->
            taskCoordinator.updateAATTargetPoint(index, target.lat, target.lon)
        }
    }

    private fun importWaypoints(importedTask: Task) {
        importedTask.waypoints.forEach { waypoint ->
            taskCoordinator.addWaypoint(
                SearchWaypoint(
                    id = waypoint.id,
                    title = waypoint.title,
                    subtitle = waypoint.subtitle,
                    lat = waypoint.lat,
                    lon = waypoint.lon
                )
            )
        }
    }

    private fun applyImportedTargets(taskType: TaskType, targets: List<TaskTargetSnapshot>) {
        if (taskType != TaskType.AAT) return
        targets.forEach { targetSnapshot ->
            useCase.setTargetParam(targetSnapshot.index, targetSnapshot.targetParam)
            useCase.setTargetLock(targetSnapshot.index, targetSnapshot.isLocked)
            targetSnapshot.target?.let { target ->
                taskCoordinator.updateAATTargetPoint(targetSnapshot.index, target.lat, target.lon)
            }
        }
    }

    private fun applyImportedObservationZones(
        persisted: TaskPersistSerializer.PersistedTask,
        taskType: TaskType,
        importedTask: Task
    ) {
        persisted.waypoints.forEachIndexed { index, waypoint ->
            val ozParams = PersistedOzParams.from(waypoint.ozParams)
            val radiusMeters = ozParams.effectiveRadiusMeters()
            if (taskType == TaskType.AAT) {
                applyAatObservationZone(index, ozParams, radiusMeters)
            }
            if (taskType == TaskType.RACING) {
                applyRacingObservationZone(index, radiusMeters, importedTask)
            }
        }
    }

    private fun applyAatObservationZone(
        index: Int,
        ozParams: PersistedOzParams,
        radiusMeters: Double?
    ) {
        if (radiusMeters != null) {
            taskCoordinator.updateAATArea(index, radiusMeters)
        }
        taskCoordinator.updateAATWaypointPointTypeMeters(
            index = index,
            startType = null,
            finishType = null,
            turnType = null,
            gateWidthMeters = radiusMeters,
            keyholeInnerRadiusMeters = ozParams.innerRadiusMeters,
            keyholeAngle = ozParams.angleDeg,
            sectorOuterRadiusMeters = ozParams.outerRadiusMeters
        )
    }

    private fun applyRacingObservationZone(
        index: Int,
        radiusMeters: Double?,
        importedTask: Task
    ) {
        if (radiusMeters == null) return
        if (index <= 0 || index >= importedTask.waypoints.lastIndex) return
        // Best-effort import for racing turnpoint cylinder radius.
        taskCoordinator.updateWaypointPointType(
            index = index,
            startType = null,
            finishType = null,
            turnType = null,
            gateWidthMeters = radiusMeters,
            keyholeInnerRadiusMeters = null,
            keyholeAngle = null,
            faiQuadrantOuterRadiusMeters = null
        )
    }

    private fun sync() {
        val snapshot = taskCoordinator.snapshot()
        useCase.updateFrom(
            task = snapshot.task,
            taskType = snapshot.taskType,
            activeIndex = snapshot.activeLeg
        )
    }
}
