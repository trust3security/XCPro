package com.example.xcpro.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.aat.models.AATFinishPointType
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.UpdateRacingFinishRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingStartRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingValidationRulesCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

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
    private val useCase: TaskSheetUseCase,
    private val taskManager: TaskManagerCoordinator,
    private val persistedTaskImporter: TaskSheetPersistedTaskImporter
) : ViewModel() {

    val uiState: StateFlow<TaskUiState> = useCase.state
    private val _viewportEffects = MutableSharedFlow<TaskSheetViewportEffect>(extraBufferCapacity = 1)
    val viewportEffects: SharedFlow<TaskSheetViewportEffect> = _viewportEffects.asSharedFlow()
    private var lastObservedActiveLeg: Int? = null

    init {
        taskManager.setProximityHandler { entered, close ->
            onProximityEvent(entered, close)
        }
        viewModelScope.launch {
            useCase.snapshotFlow.collect { snapshot ->
                if (snapshot.taskType == TaskType.AAT &&
                    lastObservedActiveLeg != null &&
                    lastObservedActiveLeg != snapshot.activeLeg) {
                    useCase.armAdvance(false)
                }
                lastObservedActiveLeg = snapshot.activeLeg
                useCase.projectSnapshot(snapshot)
            }
        }
    }

    override fun onCleared() {
        taskManager.clearProximityHandler()
        super.onCleared()
    }

    fun onAddWaypoint(wp: SearchWaypoint) = mutate {
        taskManager.addWaypoint(wp)
        emitViewportEffect(TaskSheetViewportEffect.RequestFitCurrentTask)
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
        taskManager.setAATTargetParam(index, param)
    }

    fun onToggleTargetLock(index: Int) {
        taskManager.toggleAATTargetLock(index)
    }

    fun onAdvanceMode(mode: TaskAdvanceUiSnapshot.Mode) {
        when (uiState.value.taskType) {
            TaskType.RACING -> taskManager.setRacingAdvanceMode(mode.toRacingAdvanceMode())
            TaskType.AAT -> useCase.setAdvanceMode(mode.toTaskAdvanceMode())
        }
    }

    fun onAdvanceArmToggle() {
        when (uiState.value.taskType) {
            TaskType.RACING -> taskManager.toggleRacingAdvanceArmed()
            TaskType.AAT -> useCase.toggleAdvanceArm()
        }
    }

    fun onProximityEvent(hasEnteredOZ: Boolean, closeToTarget: Boolean) = mutate {
        if (uiState.value.taskType == TaskType.RACING) return@mutate
        if (useCase.shouldAutoAdvance(hasEnteredOZ, closeToTarget)) {
            taskManager.advanceToNextLeg()
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
        return distanceToWaypointMeters(legIndex = leg, lat = lat, lon = lon)
    }

    fun distanceToWaypointMeters(legIndex: Int, lat: Double, lon: Double): Double? {
        val state = uiState.value
        if (state.task.waypoints.isEmpty()) return null
        val leg = legIndex.coerceIn(0, state.task.waypoints.lastIndex)
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
        val distanceMeters = useCase.calculateDistanceToNextWaypointMeters(
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
        return useCase.calculateDistanceToNextWaypointMeters(
            fromWaypoint = fromWaypoint,
            nextWaypoint = nextWaypoint,
            useOptimalStartLine = false
        )
    }

    fun onSetActiveLeg(index: Int) = mutate {
        val waypoints = uiState.value.task.waypoints
        if (waypoints.isEmpty()) return@mutate
        taskManager.setActiveLeg(index.coerceIn(0, waypoints.lastIndex))
    }

    fun onUpdateAATParameters(minimumTime: Duration, maximumTime: Duration) = mutate {
        taskManager.updateAATParameters(minimumTime, maximumTime)
    }

    fun importPersistedTask(json: String) = tryImportPersistedTask(json)

    fun tryImportPersistedTask(json: String): Boolean {
        val imported = runCatching {
            mutate { persistedTaskImporter.import(json, taskManager) }
        }.isSuccess
        if (imported) {
            emitViewportEffect(TaskSheetViewportEffect.RequestFitCurrentTask)
        }
        return imported
    }

    fun loadTask(taskName: String) = viewModelScope.launch {
        if (taskManager.loadTask(taskName)) {
            emitViewportEffect(TaskSheetViewportEffect.RequestFitCurrentTask)
        }
    }

    fun onUpdateWaypointPointType(
        index: Int,
        startType: RacingStartPointType?,
        finishType: RacingFinishPointType?,
        turnType: RacingTurnPointType?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        faiQuadrantOuterRadiusMeters: Double?
    ) = mutate {
        taskManager.updateWaypointPointType(
            RacingWaypointTypeUpdate(
                index = index,
                startType = startType,
                finishType = finishType,
                turnType = turnType,
                gateWidthMeters = gateWidthMeters,
                keyholeInnerRadiusMeters = keyholeInnerRadiusMeters,
                keyholeAngle = keyholeAngle,
                faiQuadrantOuterRadiusMeters = faiQuadrantOuterRadiusMeters
            )
        )
    }

    fun onUpdateAATArea(index: Int, radiusMeters: Double) = mutate {
        taskManager.updateAATArea(index, radiusMeters)
    }

    fun onUpdateAATWaypointPointTypeMeters(
        index: Int,
        startType: AATStartPointType?,
        finishType: AATFinishPointType?,
        turnType: AATTurnPointType?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        sectorOuterRadiusMeters: Double?
    ) = mutate {
        taskManager.updateAATWaypointPointType(
            AATWaypointTypeUpdate(
                index = index,
                startType = startType,
                finishType = finishType,
                turnType = turnType,
                gateWidthMeters = gateWidthMeters,
                keyholeInnerRadiusMeters = keyholeInnerRadiusMeters,
                keyholeAngle = keyholeAngle,
                sectorOuterRadiusMeters = sectorOuterRadiusMeters
            )
        )
    }

    fun onSetTaskType(taskType: TaskType) = mutate { taskManager.setTaskType(taskType) }
    fun onUpdateRacingStartRules(command: UpdateRacingStartRulesCommand) = mutate { taskManager.updateRacingStartRules(command) }
    fun onUpdateRacingFinishRules(command: UpdateRacingFinishRulesCommand) = mutate { taskManager.updateRacingFinishRules(command) }
    fun onUpdateRacingValidationRules(command: UpdateRacingValidationRulesCommand) = mutate { taskManager.updateRacingValidationRules(command) }
    fun onClearTask() = mutate { taskManager.clearTask() }

    private fun mutate(block: () -> Unit) = block()

    private fun emitViewportEffect(effect: TaskSheetViewportEffect) { _viewportEffects.tryEmit(effect) }
}
