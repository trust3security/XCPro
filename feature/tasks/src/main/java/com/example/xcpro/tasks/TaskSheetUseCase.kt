package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import com.example.xcpro.tasks.domain.logic.TaskProximityDecision
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import com.example.xcpro.tasks.racing.UpdateRacingFinishRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingStartRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingValidationRulesCommand
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

data class TaskCoordinatorSnapshot(
    val task: Task,
    val taskType: TaskType,
    val activeLeg: Int,
    val racingValidationProfile: RacingTaskStructureRules.Profile = RacingTaskStructureRules.Profile.FAI_STRICT,
    val racingAdvanceSnapshot: RacingAdvanceState.Snapshot = RacingAdvanceState().snapshot()
)

/**
 * Task-sheet screen seam. It composes task runtime authority from
 * [TaskManagerCoordinator] with task-sheet projection and sheet-local policy so
 * the ViewModel stays on a single screen-facing use-case boundary.
 */
class TaskSheetUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator,
    private val repository: TaskRepository,
    private val proximityEvaluator: TaskProximityEvaluator,
    private val persistedTaskImporter: TaskSheetPersistedTaskImporter
) {
    val snapshotFlow: Flow<TaskCoordinatorSnapshot> = combine(
        taskManager.taskSnapshotFlow,
        taskManager.racingAdvanceSnapshotFlow
    ) { snapshot, racingAdvanceSnapshot ->
        TaskCoordinatorSnapshot(
            task = snapshot.task,
            taskType = snapshot.taskType,
            activeLeg = snapshot.activeLeg,
            racingValidationProfile = taskManager.getRacingValidationProfile(),
            racingAdvanceSnapshot = racingAdvanceSnapshot
        )
    }

    private val aatAdvanceState = TaskAdvanceState()
    private var latestTaskType: TaskType = TaskType.RACING
    private var latestRacingAdvanceSnapshot: RacingAdvanceState.Snapshot = RacingAdvanceState().snapshot()
    private val _state = MutableStateFlow(repository.state.value.copy(advanceSnapshot = currentAdvanceSnapshot()))
    val state: StateFlow<TaskUiState> = _state.asStateFlow()

    fun projectSnapshot(snapshot: TaskCoordinatorSnapshot) {
        latestTaskType = snapshot.taskType
        latestRacingAdvanceSnapshot = snapshot.racingAdvanceSnapshot
        repository.updateFrom(
            task = snapshot.task,
            taskType = snapshot.taskType,
            activeIndex = snapshot.activeLeg,
            racingValidationProfile = snapshot.racingValidationProfile
        )
        publishState()
    }

    fun shouldAutoAdvance(hasEntered: Boolean, closeToTarget: Boolean): Boolean =
        aatAdvanceState.shouldAdvance(hasEntered, closeToTarget)

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

    fun calculateDistanceToNextWaypointMeters(
        fromWaypoint: TaskWaypoint,
        nextWaypoint: TaskWaypoint,
        useOptimalStartLine: Boolean
    ): Double {
        return if (useOptimalStartLine) {
            calculateOptimalStartLineDistanceMeters(
                startWaypoint = fromWaypoint,
                nextWaypoint = nextWaypoint
            )
        } else {
            taskManager.calculateSimpleSegmentDistanceMeters(
                from = fromWaypoint,
                to = nextWaypoint
            )
        }
    }

    fun setAdvanceMode(mode: TaskAdvanceState.Mode) {
        aatAdvanceState.setMode(mode)
        publishState()
    }

    fun armAdvance(armed: Boolean) {
        aatAdvanceState.setArmed(armed)
        publishState()
    }

    fun toggleAdvanceArm() {
        aatAdvanceState.toggleArmed()
        publishState()
    }

    fun bindProximityHandler(handler: (Boolean, Boolean) -> Unit) {
        taskManager.setProximityHandler(handler)
    }

    fun clearProximityHandler() {
        taskManager.clearProximityHandler()
    }

    fun addWaypoint(waypoint: SearchWaypoint) {
        taskManager.addWaypoint(waypoint)
    }

    fun removeWaypoint(index: Int) {
        taskManager.removeWaypoint(index)
    }

    fun reorderWaypoints(from: Int, to: Int) {
        taskManager.reorderWaypoints(from, to)
    }

    fun replaceWaypoint(index: Int, waypoint: SearchWaypoint) {
        taskManager.replaceWaypoint(index, waypoint)
    }

    fun setAATTargetParam(index: Int, targetParam: Double) {
        taskManager.setAATTargetParam(index, targetParam)
    }

    fun toggleAATTargetLock(index: Int) {
        taskManager.toggleAATTargetLock(index)
    }

    fun setRacingAdvanceMode(mode: RacingAdvanceState.Mode) {
        taskManager.setRacingAdvanceMode(mode)
    }

    fun toggleRacingAdvanceArmed(): Boolean = taskManager.toggleRacingAdvanceArmed()

    fun advanceToNextLeg() {
        taskManager.advanceToNextLeg()
    }

    fun setActiveLeg(index: Int) {
        taskManager.setActiveLeg(index)
    }

    fun updateAATParameters(minimumTime: Duration, maximumTime: Duration) {
        taskManager.updateAATParameters(minimumTime, maximumTime)
    }

    fun importPersistedTask(json: String): Boolean =
        runCatching {
            persistedTaskImporter.import(json, taskManager)
        }.isSuccess

    suspend fun loadTask(taskName: String): Boolean = taskManager.loadTask(taskName)

    fun updateWaypointPointType(update: RacingWaypointTypeUpdate) {
        taskManager.updateWaypointPointType(update)
    }

    fun updateAATArea(index: Int, radiusMeters: Double) {
        taskManager.updateAATArea(index, radiusMeters)
    }

    fun updateAATWaypointPointType(update: AATWaypointTypeUpdate) {
        taskManager.updateAATWaypointPointType(update)
    }

    fun setTaskType(taskType: TaskType) {
        taskManager.setTaskType(taskType)
    }

    fun updateRacingStartRules(command: UpdateRacingStartRulesCommand) {
        taskManager.updateRacingStartRules(command)
    }

    fun updateRacingFinishRules(command: UpdateRacingFinishRulesCommand) {
        taskManager.updateRacingFinishRules(command)
    }

    fun updateRacingValidationRules(command: UpdateRacingValidationRulesCommand) {
        taskManager.updateRacingValidationRules(command)
    }

    fun clearTask() {
        taskManager.clearTask()
    }

    private fun publishState() {
        _state.value = repository.state.value.copy(
            advanceSnapshot = currentAdvanceSnapshot()
        )
    }

    private fun calculateOptimalStartLineDistanceMeters(startWaypoint: TaskWaypoint, nextWaypoint: TaskWaypoint): Double {
        val optimal = taskManager.calculateOptimalStartLineCrossingPoint(startWaypoint, nextWaypoint)
        val projectedStart = TaskWaypoint(
            id = "optimal-start",
            title = "Optimal Start Crossing",
            subtitle = "",
            lat = optimal.first,
            lon = optimal.second,
            role = WaypointRole.START
        )
        return taskManager.calculateSimpleSegmentDistanceMeters(projectedStart, nextWaypoint)
    }

    private fun currentAdvanceSnapshot(): TaskAdvanceUiSnapshot {
        return when (latestTaskType) {
            TaskType.RACING -> latestRacingAdvanceSnapshot.toUiSnapshot()
            TaskType.AAT -> aatAdvanceState.snapshot().toUiSnapshot()
        }
    }
}
