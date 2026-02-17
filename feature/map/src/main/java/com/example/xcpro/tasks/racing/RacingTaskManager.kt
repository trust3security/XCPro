package com.example.xcpro.tasks.racing

import android.content.Context
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.RacingWaypointCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimpleRacingTask(
    val id: String = "",
    val waypoints: List<RacingWaypoint> = emptyList()
)

data class CourseLineValidation(
    val isValid: Boolean,
    val message: String,
    val touchPointResults: List<TouchPointResult> = emptyList()
)

data class TouchPointResult(
    val isValid: Boolean,
    val message: String,
    val distanceFromCenter: Double = 0.0
)

class RacingTaskManager(val context: Context? = null) : RacingTaskCalculatorInterface {

    private val racingTaskPersistence = context?.let { RacingTaskPersistence(it) }

    private val racingTaskCalculator = RacingTaskCalculator()
    private val racingTaskValidator = RacingTaskValidator()

    private val waypointManager = RacingWaypointManager()

    private val racingTaskInitializer = RacingTaskInitializer()

    private val currentRacingTaskState = MutableStateFlow(SimpleRacingTask())
    private val currentLegState = MutableStateFlow(0)
    val currentRacingTaskFlow: StateFlow<SimpleRacingTask> = currentRacingTaskState.asStateFlow()
    val currentLegFlow: StateFlow<Int> = currentLegState.asStateFlow()
    internal var _currentRacingTask: SimpleRacingTask
        get() = currentRacingTaskState.value
        set(value) {
            currentRacingTaskState.value = value
        }
    internal var _currentLeg: Int
        get() = currentLegState.value
        set(value) {
            currentLegState.value = value
        }

    val currentRacingTask: SimpleRacingTask get() = _currentRacingTask
    val currentLeg: Int get() = _currentLeg

    fun getCoreTask(): Task {
        return Task(
            id = _currentRacingTask.id,
            waypoints = _currentRacingTask.waypoints.map { waypoint ->
                TaskWaypoint(
                    id = waypoint.id,
                    title = waypoint.title,
                    subtitle = waypoint.subtitle,
                    lat = waypoint.lat,
                    lon = waypoint.lon,
                    role = when (waypoint.role) {
                        RacingWaypointRole.START -> WaypointRole.START
                        RacingWaypointRole.TURNPOINT -> WaypointRole.TURNPOINT
                        RacingWaypointRole.FINISH -> WaypointRole.FINISH
                    },
                    customRadius = waypoint.gateWidth,
                    customPointType = when (waypoint.role) {
                        RacingWaypointRole.START -> waypoint.startPointType.name
                        RacingWaypointRole.FINISH -> waypoint.finishPointType.name
                        else -> waypoint.turnPointType.name
                    },
                    customParameters = mutableMapOf<String, Any>().apply {
                        RacingWaypointCustomParams(
                            keyholeInnerRadius = waypoint.keyholeInnerRadius,
                            keyholeAngle = waypoint.keyholeAngle,
                            faiQuadrantOuterRadius = waypoint.faiQuadrantOuterRadius
                        ).applyTo(this)
                    }
                )
            }
        )
    }

    fun initializeRacingTask(waypoints: List<SearchWaypoint>) {
        _currentRacingTask = racingTaskInitializer.initializeRacingTask(waypoints)
        _currentLeg = 0
    }

    fun initializeFromGenericWaypoints(genericWaypoints: List<com.example.xcpro.tasks.core.TaskWaypoint>) {
        _currentRacingTask = racingTaskInitializer.initializeFromGenericWaypoints(genericWaypoints)
        _currentLeg = 0
    }

    fun calculateRacingDistance(): Double {
        if (_currentRacingTask.waypoints.size < 2) return 0.0

        val optimalPath = racingTaskCalculator.findOptimalFAIPath(_currentRacingTask.waypoints)

        if (optimalPath.size < 2) return 0.0

        var totalDistance = 0.0
        for (i in 0 until optimalPath.size - 1) {
            val from = optimalPath[i]
            val to = optimalPath[i + 1]
            totalDistance += RacingGeometryUtils.haversineDistance(from.first, from.second, to.first, to.second)
        }
        return totalDistance
    }

    fun getOptimalRacingPath(): List<Pair<Double, Double>> {
        return racingTaskCalculator.findOptimalFAIPath(_currentRacingTask.waypoints)
    }

    override fun findOptimalFAIPath(waypoints: List<RacingWaypoint>): List<Pair<Double, Double>> {
        return racingTaskCalculator.findOptimalFAIPath(waypoints)
    }

    fun addRacingWaypoint(searchWaypoint: SearchWaypoint) {
        _currentRacingTask = waypointManager.addWaypoint(_currentRacingTask, searchWaypoint)
        _currentLeg = 0
        saveRacingTask()
    }

    fun removeRacingWaypoint(index: Int) {
        val waypointCountBefore = _currentRacingTask.waypoints.size
        _currentRacingTask = waypointManager.removeWaypoint(_currentRacingTask, index)
        val waypointCountAfter = _currentRacingTask.waypoints.size

        if (waypointCountAfter < waypointCountBefore) {
            _currentLeg = waypointManager.calculateLegAfterRemoval(_currentLeg, index, waypointCountAfter)
        }

        saveRacingTask()
    }

    fun updateRacingWaypointType(
        index: Int,
        startType: RacingStartPointType? = null,
        finishType: RacingFinishPointType? = null,
        turnType: RacingTurnPointType? = null,
        gateWidth: Double? = null,
        keyholeInnerRadius: Double? = null,
        keyholeAngle: Double? = null,
        faiQuadrantOuterRadius: Double? = null
    ) {
        _currentRacingTask = waypointManager.updateWaypointType(
            _currentRacingTask,
            index,
            startType,
            finishType,
            turnType,
            gateWidth,
            keyholeInnerRadius,
            keyholeAngle,
            faiQuadrantOuterRadius
        )
        saveRacingTask()
    }

    fun updateWaypointPointTypeBridge(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidth: Double?,
        keyholeInnerRadius: Double?,
        keyholeAngle: Double?,
        faiQuadrantOuterRadius: Double?
    ) {
        updateRacingWaypointType(
            index = index,
            startType = startType as? RacingStartPointType,
            finishType = finishType as? RacingFinishPointType,
            turnType = turnType as? RacingTurnPointType,
            gateWidth = gateWidth,
            keyholeInnerRadius = keyholeInnerRadius,
            keyholeAngle = keyholeAngle,
            faiQuadrantOuterRadius = faiQuadrantOuterRadius
        )
    }

    fun calculateOptimalLineCrossingPoint(
        lineLat: Double,
        lineLon: Double,
        targetLat: Double,
        targetLon: Double,
        lineWidth: Double
    ): Pair<Double, Double> {
        return RacingGeometryUtils.calculateOptimalLineCrossingPoint(lineLat, lineLon, targetLat, targetLon, lineWidth)
    }

    fun replaceRacingWaypoint(index: Int, newWaypoint: SearchWaypoint) {
        _currentRacingTask = waypointManager.replaceWaypoint(_currentRacingTask, index, newWaypoint)
        saveRacingTask()
    }

    fun reorderRacingWaypoints(fromIndex: Int, toIndex: Int) {
        _currentRacingTask = waypointManager.reorderWaypoints(_currentRacingTask, fromIndex, toIndex)
        saveRacingTask()
    }

    fun validateRacingCourse(): CourseLineValidation {
        return if (_currentRacingTask.waypoints.size >= 2) {
            CourseLineValidation(
                isValid = true,
                message = "Racing course is valid with ${_currentRacingTask.waypoints.size} waypoints",
                touchPointResults = _currentRacingTask.waypoints.map {
                    TouchPointResult(isValid = true, message = "Waypoint ${it.title} is valid")
                }
            )
        } else {
            CourseLineValidation(
                isValid = false,
                message = "Racing course needs at least 2 waypoints",
                touchPointResults = emptyList()
            )
        }
    }

    fun clearRacingTask() {
        _currentRacingTask = SimpleRacingTask()
        _currentLeg = 0
        saveRacingTask()
    }

    fun advanceToNextLeg() {
        val task = _currentRacingTask
        if (task.waypoints.isNotEmpty() && _currentLeg < task.waypoints.size - 1) {
            _currentLeg++
        }
    }

    fun goToPreviousLeg() {
        if (_currentLeg > 0) {
            _currentLeg--
        }
    }

    fun setRacingLeg(index: Int) {
        if (_currentRacingTask.waypoints.isEmpty()) return
        val clamped = index.coerceIn(0, _currentRacingTask.waypoints.lastIndex)
        _currentLeg = clamped
    }

    fun saveRacingTask() {
        racingTaskPersistence?.saveRacingTask(_currentRacingTask)
    }

    fun loadRacingTask(): SimpleRacingTask? {
        return racingTaskPersistence?.loadRacingTask()?.also { task ->
            _currentRacingTask = task
            _currentLeg = 0
        }
    }

    fun getRacingTaskSummary(): String {
        return racingTaskPersistence?.getRacingTaskSummary(_currentRacingTask) { calculateRacingDistance() } ?: "Racing Task: No persistence available"
    }

    fun calculateRacingTaskDistance(): Double {
        return racingTaskPersistence?.calculateRacingTaskDistance(_currentRacingTask) { calculateRacingDistance() } ?: 0.0
    }

    fun getSavedRacingTasks(): List<String> {
        return racingTaskPersistence?.getSavedRacingTasks() ?: emptyList()
    }

    fun saveRacingTask(taskName: String): Boolean {
        return racingTaskPersistence?.saveRacingTask(_currentRacingTask, taskName) ?: false
    }

    fun loadRacingTaskFromFile(taskName: String): Boolean {
        return racingTaskPersistence?.loadRacingTaskFromFile(taskName)?.let { task ->
            _currentRacingTask = task
            _currentLeg = 0
            saveRacingTask()
            true
        } ?: false
    }

    fun deleteRacingTask(taskName: String): Boolean {
        return racingTaskPersistence?.deleteRacingTask(taskName) ?: false
    }

    fun isRacingTaskValid(): Boolean {
        return _currentRacingTask.waypoints.size >= 2
    }

    fun calculateSegmentDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return RacingGeometryUtils.haversineDistance(lat1, lon1, lat2, lon2)
    }

    fun calculateDistanceToCurrentWaypointEntry(gpsLat: Double, gpsLon: Double): Double? {
        return racingTaskCalculator.calculateDistanceToOptimalEntry(
            gpsLat = gpsLat,
            gpsLon = gpsLon,
            waypointIndex = currentLeg,
            waypoints = _currentRacingTask.waypoints
        )
    }

    fun getRacingTaskParameters(): String {
        return racingTaskPersistence?.getRacingTaskParameters(_currentRacingTask) { calculateRacingDistance() } ?: "Racing Task: No persistence available"
    }
}
