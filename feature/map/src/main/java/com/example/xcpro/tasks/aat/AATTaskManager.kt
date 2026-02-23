package com.example.xcpro.tasks.aat

import android.content.Context
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.AATTaskTimeCustomParams
import com.example.xcpro.tasks.core.AATWaypointCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import java.time.Duration

import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.getAuthorityRadiusMeters

import com.example.xcpro.tasks.aat.geometry.AATGeometryGenerator
import com.example.xcpro.tasks.aat.persistence.AATTaskFileIO
import com.example.xcpro.tasks.aat.navigation.AATNavigationManager
import com.example.xcpro.tasks.aat.interaction.AATEditModeManager
import com.example.xcpro.tasks.aat.validation.AATValidationBridge
import com.example.xcpro.tasks.aat.waypoints.AATWaypointManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimpleAATTask(
    val id: String = "",
    val waypoints: List<AATWaypoint> = emptyList(),
    val minimumTime: Duration = Duration.ofHours(3),
    val maximumTime: Duration? = null
)

class AATTaskManager(val context: Context? = null) {

    private val geometryGenerator = AATGeometryGenerator()
    private val fileIO = context?.let { AATTaskFileIO(it) }
    private val navigationManager = AATNavigationManager()
    private val editModeManager = AATEditModeManager()
    private val validationBridge = AATValidationBridge()
    private val waypointManager = AATWaypointManager()
    private val pointTypeConfigurator = AATPointTypeConfigurator()
    private val validationWrapper = AATTaskValidationWrapper(validationBridge)
    private val fileOperationsWrapper = AATFileOperationsWrapper(fileIO)

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

    fun getCoreTask(): Task {
        val minTimeSeconds = _currentAATTask.minimumTime.seconds.toDouble()
        val maxTimeSeconds = _currentAATTask.maximumTime?.seconds?.toDouble()
        return Task(
            id = _currentAATTask.id,
            waypoints = _currentAATTask.waypoints.map { waypoint ->
                val customParameters = mutableMapOf<String, Any>()
                AATWaypointCustomParams(
                    radiusMeters = waypoint.assignedArea.radiusMeters,
                    innerRadiusMeters = waypoint.assignedArea.innerRadiusMeters,
                    outerRadiusMeters = waypoint.assignedArea.outerRadiusMeters,
                    startAngleDegrees = waypoint.assignedArea.startAngleDegrees,
                    endAngleDegrees = waypoint.assignedArea.endAngleDegrees,
                    lineWidthMeters = waypoint.assignedArea.lineWidthMeters,
                    targetLat = waypoint.targetPoint.latitude,
                    targetLon = waypoint.targetPoint.longitude,
                    isTargetPointCustomized = waypoint.isTargetPointCustomized
                ).applyTo(customParameters)
                AATTaskTimeCustomParams(
                    minimumTimeSeconds = minTimeSeconds,
                    maximumTimeSeconds = maxTimeSeconds
                ).applyTo(customParameters)
                TaskWaypoint(
                    id = waypoint.id,
                    title = waypoint.title,
                    subtitle = waypoint.subtitle,
                    lat = waypoint.lat,
                    lon = waypoint.lon,
                    role = when (waypoint.role) {
                        com.example.xcpro.tasks.aat.models.AATWaypointRole.START -> WaypointRole.START
                        com.example.xcpro.tasks.aat.models.AATWaypointRole.TURNPOINT -> WaypointRole.TURNPOINT
                        com.example.xcpro.tasks.aat.models.AATWaypointRole.FINISH -> WaypointRole.FINISH
                    },
                    customRadius = null,
                    customRadiusMeters = waypoint.getAuthorityRadiusMeters(),
                    customPointType = when (waypoint.role) {
                        com.example.xcpro.tasks.aat.models.AATWaypointRole.START -> waypoint.startPointType.name
                        com.example.xcpro.tasks.aat.models.AATWaypointRole.TURNPOINT -> waypoint.turnPointType.name
                        com.example.xcpro.tasks.aat.models.AATWaypointRole.FINISH -> waypoint.finishPointType.name
                    },
                    customParameters = customParameters
                )
            }
        )
    }

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
        saveAATTask()
    }

    fun initializeFromGenericWaypoints(genericWaypoints: List<com.example.xcpro.tasks.core.TaskWaypoint>) {
        _currentAATTask = waypointManager.initializeFromGenericWaypoints(genericWaypoints)
        _currentLeg = 0
        saveAATTask()
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
        saveAATTask()
    }

    fun removeAATWaypoint(index: Int) {
        val (updatedTask, newCurrentLeg) = waypointManager.removeWaypoint(_currentAATTask, _currentLeg, index)
        _currentAATTask = updatedTask
        _currentLeg = newCurrentLeg
        saveAATTask()
    }

    fun updateAATArea(index: Int, newArea: AATAssignedArea) {
        _currentAATTask = waypointManager.updateArea(_currentAATTask, index, newArea)
        saveAATTask()
    }

    fun updateAATTimes(minTime: Duration, maxTime: Duration?) {
        _currentAATTask = waypointManager.updateTimes(_currentAATTask, minTime, maxTime)
        saveAATTask()
    }

    fun updateAATWaypointPointTypeMeters(
        index: Int,
        startType: com.example.xcpro.tasks.aat.models.AATStartPointType?,
        finishType: com.example.xcpro.tasks.aat.models.AATFinishPointType?,
        turnType: com.example.xcpro.tasks.aat.models.AATTurnPointType?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        sectorOuterRadiusMeters: Double?
    ) {
        val currentWaypoints = _currentAATTask.waypoints.toMutableList()
        if (index in currentWaypoints.indices) {
            val currentWaypoint = currentWaypoints[index]

            val updatedWaypoint = pointTypeConfigurator.updateWaypointPointType(
                waypoint = currentWaypoint,
                allWaypoints = currentWaypoints,
                waypointIndex = index,
                startType = startType,
                finishType = finishType,
                turnType = turnType,
                gateWidthMeters = gateWidthMeters,
                keyholeInnerRadiusMeters = keyholeInnerRadiusMeters,
                keyholeAngle = keyholeAngle,
                sectorOuterRadiusMeters = sectorOuterRadiusMeters
            )

            currentWaypoints[index] = updatedWaypoint
            _currentAATTask = _currentAATTask.copy(waypoints = currentWaypoints)
            saveAATTask()
        }
    }

    fun updateWaypointPointTypeBridge(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        sectorOuterRadiusMeters: Double?
    ) {
        updateAATWaypointPointTypeMeters(
            index = index,
            startType = startType as? com.example.xcpro.tasks.aat.models.AATStartPointType,
            finishType = finishType as? com.example.xcpro.tasks.aat.models.AATFinishPointType,
            turnType = turnType as? com.example.xcpro.tasks.aat.models.AATTurnPointType,
            gateWidthMeters = gateWidthMeters,
            keyholeInnerRadiusMeters = keyholeInnerRadiusMeters,
            keyholeAngle = keyholeAngle,
            sectorOuterRadiusMeters = sectorOuterRadiusMeters
        )
    }

    /** Clear AAT task */
    fun clearAATTask() {
        _currentAATTask = SimpleAATTask()
        _currentLeg = 0
        saveAATTask()
    }

    fun reorderAATWaypoints(fromIndex: Int, toIndex: Int) {
        _currentAATTask = waypointManager.reorderWaypoints(_currentAATTask, fromIndex, toIndex)
        saveAATTask()
    }

    fun replaceAATWaypoint(index: Int, newWaypoint: SearchWaypoint) {
        _currentAATTask = waypointManager.replaceWaypoint(_currentAATTask, index, newWaypoint)
        saveAATTask()
    }

    fun saveAATTask() {
        fileOperationsWrapper.saveToPreferences(_currentAATTask)
    }

    fun loadAATTask(): SimpleAATTask? {
        return fileOperationsWrapper.loadFromPreferences()?.also { task ->
            _currentAATTask = task
        }
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

    fun getSavedAATTasks(context: Context): List<String> {
        return fileOperationsWrapper.getSavedTaskFiles()
    }

    fun saveAATTask(context: Context, taskName: String): Boolean {
        return fileOperationsWrapper.saveTaskToFile(_currentAATTask, taskName)
    }

    fun loadAATTaskFromFile(context: Context, taskName: String): Boolean {
        return fileOperationsWrapper.loadTaskFromFile(taskName)?.let { task ->
            _currentAATTask = task
            _currentLeg = 0
            saveAATTask()
            true
        } ?: false
    }

    fun deleteAATTask(context: Context, taskName: String): Boolean {
        return fileOperationsWrapper.deleteTaskFile(taskName)
    }

    fun isAATTaskValid(): Boolean = validationWrapper.isTaskValid(_currentAATTask)

    fun validateAATTask(): com.example.xcpro.tasks.aat.validation.ValidationUIResult =
        validationWrapper.validateTask(_currentAATTask)

    fun isCompetitionReady(): Boolean = validationWrapper.isCompetitionReady(_currentAATTask)

    fun getTaskGrade(): String = validationWrapper.getTaskGrade(_currentAATTask)

    fun getValidationSummary(): com.example.xcpro.tasks.aat.validation.TaskValidationSummary =
        validationWrapper.getValidationSummary(_currentAATTask)

    fun validateForCompetition(competitionClass: String): com.example.xcpro.tasks.aat.validation.CompetitionValidationResult =
        validationWrapper.validateForCompetition(_currentAATTask, competitionClass)

    fun getTaskImprovementSuggestions(): List<String> =
        validationWrapper.getTaskImprovementSuggestions(_currentAATTask)

    fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>? =
        editModeManager.checkAreaTap(_currentAATTask, lat, lon)

    fun setEditMode(waypointIndex: Int, enabled: Boolean) =
        editModeManager.setEditMode(waypointIndex, enabled)

    fun isInEditMode(): Boolean = editModeManager.isInEditMode

    fun getEditWaypointIndex(): Int? = editModeManager.editWaypointIndex

    fun updateTargetPoint(index: Int, lat: Double, lon: Double) {
        editModeManager.updateTargetPoint(_currentAATTask, index, lat, lon)?.let { updatedWaypoint ->
            val updatedWaypoints = _currentAATTask.waypoints.toMutableList()
            updatedWaypoints[index] = updatedWaypoint
            _currentAATTask = _currentAATTask.copy(waypoints = updatedWaypoints)
            saveAATTask()
        }
    }
}
