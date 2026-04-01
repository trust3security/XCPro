package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.aat.models.AATFinishPointType
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import com.example.xcpro.tasks.racing.UpdateRacingFinishRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingStartRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingValidationRulesCommand
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class TaskCoordinatorSnapshot(
    val task: Task,
    val taskType: TaskType,
    val activeLeg: Int,
    val racingValidationProfile: RacingTaskStructureRules.Profile = RacingTaskStructureRules.Profile.FAI_STRICT,
    val racingAdvanceSnapshot: RacingAdvanceState.Snapshot = RacingAdvanceState().snapshot()
)

class TaskSheetCoordinatorUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator
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

    fun setProximityHandler(handler: (Boolean, Boolean) -> Unit) {
        taskManager.setProximityHandler(handler)
    }

    fun clearProximityHandler() {
        taskManager.clearProximityHandler()
    }

    fun addLegChangeListener(handler: (Int) -> Unit) {
        taskManager.addLegChangeListener(handler)
    }

    fun removeLegChangeListener(handler: (Int) -> Unit) {
        taskManager.removeLegChangeListener(handler)
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

    fun setTaskType(taskType: TaskType) {
        taskManager.setTaskType(taskType)
    }

    fun clearTask() {
        taskManager.clearTask()
    }

    fun advanceToNextLeg() {
        taskManager.advanceToNextLeg()
    }

    fun setActiveLeg(index: Int) {
        taskManager.setActiveLeg(index)
    }

    fun calculateSimpleSegmentDistanceMeters(from: TaskWaypoint, to: TaskWaypoint): Double =
        taskManager.calculateSimpleSegmentDistanceMeters(from, to)

    fun calculateOptimalStartLineDistanceMeters(startWaypoint: TaskWaypoint, nextWaypoint: TaskWaypoint): Double {
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
            calculateSimpleSegmentDistanceMeters(
                from = fromWaypoint,
                to = nextWaypoint
            )
        }
    }

    fun updateWaypointPointType(
        index: Int,
        startType: RacingStartPointType?,
        finishType: RacingFinishPointType?,
        turnType: RacingTurnPointType?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        faiQuadrantOuterRadiusMeters: Double?
    ) {
        taskManager.updateWaypointPointType(
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

    fun updateAATWaypointPointTypeMeters(
        index: Int,
        startType: AATStartPointType?,
        finishType: AATFinishPointType?,
        turnType: AATTurnPointType?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        sectorOuterRadiusMeters: Double?
    ) {
        taskManager.updateAATWaypointPointTypeMeters(
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

    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        taskManager.updateAATTargetPoint(index, lat, lon)
    }

    fun setAATTargetParam(index: Int, targetParam: Double) {
        taskManager.setAATTargetParam(index, targetParam)
    }

    fun toggleAATTargetLock(index: Int) {
        taskManager.toggleAATTargetLock(index)
    }

    fun setAATTargetLock(index: Int, locked: Boolean) {
        taskManager.setAATTargetLock(index, locked)
    }

    fun applyAATTargetState(
        index: Int,
        targetParam: Double,
        targetLocked: Boolean,
        targetLat: Double?,
        targetLon: Double?
    ) {
        taskManager.applyAATTargetState(
            index = index,
            targetParam = targetParam,
            targetLocked = targetLocked,
            targetLat = targetLat,
            targetLon = targetLon
        )
    }

    fun updateAATArea(index: Int, radiusMeters: Double) {
        taskManager.updateAATArea(index, radiusMeters)
    }

    internal fun updateRacingStartRules(command: UpdateRacingStartRulesCommand) {
        taskManager.updateRacingStartRules(command)
    }

    internal fun updateRacingFinishRules(command: UpdateRacingFinishRulesCommand) {
        taskManager.updateRacingFinishRules(command)
    }

    internal fun updateRacingValidationRules(command: UpdateRacingValidationRulesCommand) {
        taskManager.updateRacingValidationRules(command)
    }

    fun updateAATParameters(minimumTime: Duration, maximumTime: Duration) {
        taskManager.updateAATParameters(minimumTime, maximumTime)
    }

    suspend fun loadTask(taskName: String): Boolean = taskManager.loadTask(taskName)

    fun setRacingAdvanceMode(mode: RacingAdvanceState.Mode) {
        taskManager.setRacingAdvanceMode(mode)
    }

    fun toggleRacingAdvanceArm(): Boolean = taskManager.toggleRacingAdvanceArmed()
}
