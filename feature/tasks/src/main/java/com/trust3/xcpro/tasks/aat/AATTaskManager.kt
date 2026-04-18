package com.trust3.xcpro.tasks.aat

import com.trust3.xcpro.common.waypoint.SearchWaypoint
import com.trust3.xcpro.tasks.AATWaypointTypeUpdate
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.aat.models.AATWaypoint
import com.trust3.xcpro.tasks.aat.models.AATAssignedArea
import com.trust3.xcpro.tasks.aat.calculations.AATMathUtils
import com.trust3.xcpro.tasks.aat.geometry.AATGeometryGenerator
import com.trust3.xcpro.tasks.aat.navigation.AATNavigationManager
import com.trust3.xcpro.tasks.aat.interaction.AATEditModeManager
import com.trust3.xcpro.tasks.aat.validation.AATValidationBridge
import com.trust3.xcpro.tasks.aat.waypoints.AATWaypointManager
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimpleAATTask(
    val id: String = "",
    val waypoints: List<AATWaypoint> = emptyList(),
    val minimumTime: Duration = Duration.ofHours(3),
    val maximumTime: Duration? = null
)

class AATTaskManager {

    private val geometryGenerator = AATGeometryGenerator()
    private val navigationManager = AATNavigationManager()
    private val editModeManager = AATEditModeManager()
    private val validationBridge = AATValidationBridge()
    private val waypointManager = AATWaypointManager()
    private val pointTypeConfigurator = AATPointTypeConfigurator()
    private val validationWrapper = AATTaskValidationWrapper(validationBridge)
    private val targetStateMutator = AATTargetStateMutator()

    private val aatTaskCalculator = AATTaskCalculator()

    private val currentAATTaskState = MutableStateFlow(SimpleAATTask())
    private val currentLegState = MutableStateFlow(0)
    val currentAATTaskFlow: StateFlow<SimpleAATTask> = currentAATTaskState.asStateFlow()
    val currentLegFlow: StateFlow<Int> = currentLegState.asStateFlow()
    internal var _currentAATTask: SimpleAATTask
        get() = currentAATTaskState.value
        set(value) {
            currentAATTaskState.value = value
        }
    internal var _currentLeg: Int
        get() = currentLegState.value
        set(value) {
            currentLegState.value = value
        }

    // Public properties
    val currentAATTask: SimpleAATTask get() = _currentAATTask
    val currentLeg: Int get() = _currentLeg

    fun getCoreTask(): Task = _currentAATTask.toCoreTask()

    fun goToPreviousLeg() {
        navigationManager.goToPreviousLeg(_currentAATTask)
        _currentLeg = navigationManager.currentLeg
    }

    fun advanceToNextLeg() {
        navigationManager.advanceToNextLeg(_currentAATTask)
        _currentLeg = navigationManager.currentLeg
    }

    fun setAATLeg(index: Int) {
        if (_currentAATTask.waypoints.isEmpty()) return
        navigationManager.setCurrentLeg(index.coerceIn(0, _currentAATTask.waypoints.lastIndex))
        _currentLeg = navigationManager.currentLeg
    }

    fun initializeAATTask(waypoints: List<SearchWaypoint>) {
        _currentAATTask = waypointManager.initializeTask(waypoints)
    }

    fun initializeFromGenericWaypoints(genericWaypoints: List<com.trust3.xcpro.tasks.core.TaskWaypoint>) {
        _currentAATTask = waypointManager.initializeFromGenericWaypoints(genericWaypoints)
        _currentLeg = 0
    }

    fun initializeFromCoreTask(task: Task, activeLegIndex: Int = 0) {
        _currentAATTask = waypointManager.initializeFromCoreTask(task)
        _currentLeg = if (_currentAATTask.waypoints.isEmpty()) {
            0
        } else {
            activeLegIndex.coerceIn(0, _currentAATTask.waypoints.lastIndex)
        }
    }

    fun calculateAATDistanceMeters(): Double {
        if (_currentAATTask.waypoints.size < 2) return 0.0

        val pathPoints = geometryGenerator.calculateOptimalAATPath(_currentAATTask.waypoints)

        var totalDistance = 0.0
        for (i in 0 until pathPoints.size - 1) {
            val from = pathPoints[i] // [lon, lat]
            val to = pathPoints[i + 1] // [lon, lat]
            totalDistance += AATMathUtils.calculateDistanceMeters(from[1], from[0], to[1], to[0]) // Convert back to lat, lon
        }
        return totalDistance
    }

    fun addAATWaypoint(searchWaypoint: SearchWaypoint) {
        _currentAATTask = waypointManager.addWaypoint(_currentAATTask, searchWaypoint)
    }

    fun removeAATWaypoint(index: Int) {
        val (updatedTask, newCurrentLeg) = waypointManager.removeWaypoint(_currentAATTask, _currentLeg, index)
        _currentAATTask = updatedTask
        _currentLeg = newCurrentLeg
    }

    fun updateAATArea(index: Int, newArea: AATAssignedArea) {
        _currentAATTask = waypointManager.updateArea(_currentAATTask, index, newArea)
    }

    fun updateAATTimes(minTime: Duration, maxTime: Duration?) {
        _currentAATTask = waypointManager.updateTimes(_currentAATTask, minTime, maxTime)
    }

    fun updateTargetParam(index: Int, targetParam: Double) {
        applyTaskUpdate(targetStateMutator.updateTargetParam(_currentAATTask, index, targetParam))
    }

    fun toggleTargetLock(index: Int) {
        applyTaskUpdate(targetStateMutator.toggleTargetLock(_currentAATTask, index))
    }

    fun setTargetLock(index: Int, locked: Boolean) {
        applyTaskUpdate(targetStateMutator.setTargetLock(_currentAATTask, index, locked))
    }

    fun applyTargetState(
        index: Int,
        targetParam: Double,
        targetLocked: Boolean,
        targetLat: Double?,
        targetLon: Double?
    ) {
        applyTaskUpdate(
            targetStateMutator.applyTargetState(
                task = _currentAATTask,
                index = index,
                targetParam = targetParam,
                targetLocked = targetLocked,
                targetLat = targetLat,
                targetLon = targetLon
            )
        )
    }

    fun updateAATWaypointPointTypeMeters(update: AATWaypointTypeUpdate) {
        val currentWaypoints = _currentAATTask.waypoints.toMutableList()
        if (update.index in currentWaypoints.indices) {
            val currentWaypoint = currentWaypoints[update.index]

            val updatedWaypoint = pointTypeConfigurator.updateWaypointPointType(
                waypoint = currentWaypoint,
                allWaypoints = currentWaypoints,
                waypointIndex = update.index,
                update = update
            )

            currentWaypoints[update.index] = updatedWaypoint
            _currentAATTask = _currentAATTask.copy(waypoints = currentWaypoints)
        }
    }

    /** Clear AAT task */
    fun clearAATTask() {
        _currentAATTask = SimpleAATTask()
        _currentLeg = 0
    }

    fun reorderAATWaypoints(fromIndex: Int, toIndex: Int) {
        _currentAATTask = waypointManager.reorderWaypoints(_currentAATTask, fromIndex, toIndex)
    }

    fun replaceAATWaypoint(index: Int, newWaypoint: SearchWaypoint) {
        _currentAATTask = waypointManager.replaceWaypoint(_currentAATTask, index, newWaypoint)
    }

    fun getAATTaskSummary(): String {
        val waypoints = _currentAATTask.waypoints.size
        val minTime = _currentAATTask.minimumTime.toHours()
        val maxTime = _currentAATTask.maximumTime?.toHours()

        return buildString {
            append("AAT Task: $waypoints waypoints")
            append(", Min: ${minTime}h")
            if (maxTime != null) {
                append(", Max: ${maxTime}h")
            }
        }
    }

    fun calculateAATTaskDistanceMeters(): Double = calculateAATDistanceMeters()

    fun calculateSegmentDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        AATMathUtils.calculateDistanceMeters(lat1, lon1, lat2, lon2)

    fun calculateDistanceToCurrentTargetPointMeters(gpsLat: Double, gpsLon: Double): Double? {
        return aatTaskCalculator.calculateDistanceToTargetPointMeters(
            gpsLat = gpsLat,
            gpsLon = gpsLon,
            waypointIndex = _currentLeg,
            waypoints = _currentAATTask.waypoints
        )
    }

    fun isAATTaskValid(): Boolean = validationWrapper.isTaskValid(_currentAATTask)

    fun validateAATTask(): com.trust3.xcpro.tasks.aat.validation.ValidationUIResult =
        validationWrapper.validateTask(_currentAATTask)

    fun isCompetitionReady(): Boolean = validationWrapper.isCompetitionReady(_currentAATTask)

    fun getTaskGrade(): String = validationWrapper.getTaskGrade(_currentAATTask)

    fun getValidationSummary(): com.trust3.xcpro.tasks.aat.validation.TaskValidationSummary =
        validationWrapper.getValidationSummary(_currentAATTask)

    fun validateForCompetition(competitionClass: String): com.trust3.xcpro.tasks.aat.validation.CompetitionValidationResult =
        validationWrapper.validateForCompetition(_currentAATTask, competitionClass)

    fun getTaskImprovementSuggestions(): List<String> =
        validationWrapper.getTaskImprovementSuggestions(_currentAATTask)

    fun checkAreaTap(lat: Double, lon: Double): Pair<Int, AATWaypoint>? =
        editModeManager.checkAreaTap(_currentAATTask, lat, lon)

    fun setEditMode(waypointIndex: Int, enabled: Boolean) =
        editModeManager.setEditMode(waypointIndex, enabled)

    fun isInEditMode(): Boolean = editModeManager.isInEditMode

    fun getEditWaypointIndex(): Int? = editModeManager.editWaypointIndex

    fun updateTargetPoint(index: Int, lat: Double, lon: Double) {
        editModeManager.updateTargetPoint(_currentAATTask, index, lat, lon)?.let { updatedWaypoint ->
            applyTaskUpdate(targetStateMutator.applyEditedTargetPoint(_currentAATTask, index, updatedWaypoint))
        }
    }

    private fun applyTaskUpdate(updatedTask: SimpleAATTask) {
        if (updatedTask == _currentAATTask) {
            return
        }
        _currentAATTask = updatedTask
    }

}
