package com.example.xcpro.tasks.racing

import android.content.Context
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import kotlin.math.*
import java.util.UUID
import java.util.*

// Racing-specific imports - NO cross-contamination with AAT/DHT
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.turnpoints.TaskContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple Racing Task model for manager use (avoiding conflicts with complex models)
 */
data class SimpleRacingTask(
    val id: String = "",
    val waypoints: List<RacingWaypoint> = emptyList()
)

/**
 * Course line validation result for racing tasks
 */
data class CourseLineValidation(
    val isValid: Boolean,
    val message: String,
    val touchPointResults: List<TouchPointResult> = emptyList()
)

data class TouchPointResult(
    val isValid: Boolean,
    val message: String,
    val distanceFromCenter: Double = 0.0 // meters
)

/**
 * Racing Task Manager - Completely independent racing task management
 *
 * ZERO DEPENDENCIES on AAT or DHT modules - maintains complete separation
 * All Racing logic is self-contained within this manager
 */
class RacingTaskManager(val context: Context? = null) : RacingTaskCalculatorInterface {

    // Racing task persistence - handles all storage operations
    private val racingTaskPersistence = context?.let { RacingTaskPersistence(it) }

    // Racing-specific calculators - completely autonomous
    private val racingTaskCalculator = RacingTaskCalculator()
    private val racingTaskValidator = RacingTaskValidator()

    // Racing waypoint manager - handles waypoint collection operations
    private val waypointManager = RacingWaypointManager()

    // Racing task initializer - handles task initialization operations
    private val racingTaskInitializer = RacingTaskInitializer()

    // Racing task state (SSOT in manager; non-UI reactive model)
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

    // Public properties
    val currentRacingTask: SimpleRacingTask get() = _currentRacingTask
    val currentLeg: Int get() = _currentLeg

    /**
     * Convert current racing task to core task representation for coordinator consumption.
     * Keeps conversion logic owned by Racing module to avoid leaking Racing types.
     */
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
                    customParameters = mapOf(
                        "keyholeInnerRadius" to waypoint.keyholeInnerRadius,
                        "keyholeAngle" to waypoint.keyholeAngle,
                        "faiQuadrantOuterRadius" to waypoint.faiQuadrantOuterRadius
                    )
                )
            }
        )
    }

    /**
     * Initialize Racing task with waypoints
     */
    fun initializeRacingTask(waypoints: List<SearchWaypoint>) {
        _currentRacingTask = racingTaskInitializer.initializeRacingTask(waypoints)
        _currentLeg = 0
    }

    /**
     * Initialize Racing task from generic waypoints with smart conversion and value preservation
     * Preserves user customizations while applying standardized defaults (10km start, 3km finish)
     */
    fun initializeFromGenericWaypoints(genericWaypoints: List<com.example.xcpro.tasks.core.TaskWaypoint>) {
        _currentRacingTask = racingTaskInitializer.initializeFromGenericWaypoints(genericWaypoints)
        _currentLeg = 0
    }

    /**
     * Calculate Racing task distance - uses Racing-specific FAI calculations
     * FIXED: Now uses optimal FAI path that touches cylinder edges, not center-to-center
     */
    fun calculateRacingDistance(): Double {
        if (_currentRacingTask.waypoints.size < 2) return 0.0

        // Get optimal FAI path that touches turnpoint edges at shortest points
        val optimalPath = racingTaskCalculator.findOptimalFAIPath(_currentRacingTask.waypoints)

        if (optimalPath.size < 2) return 0.0

        // Calculate total distance along optimal path
        var totalDistance = 0.0
        for (i in 0 until optimalPath.size - 1) {
            val from = optimalPath[i]
            val to = optimalPath[i + 1]
            totalDistance += RacingGeometryUtils.haversineDistance(from.first, from.second, to.first, to.second)
        }
        return totalDistance
    }

    /**
     * Get optimal FAI racing path
     */
    fun getOptimalRacingPath(): List<Pair<Double, Double>> {
        return racingTaskCalculator.findOptimalFAIPath(_currentRacingTask.waypoints)
    }

    /**
     * Interface implementation - delegate to racing task calculator
     */
    override fun findOptimalFAIPath(waypoints: List<RacingWaypoint>): List<Pair<Double, Double>> {
        return racingTaskCalculator.findOptimalFAIPath(waypoints)
    }

    /**
     * Add waypoint to Racing task
     */
    fun addRacingWaypoint(searchWaypoint: SearchWaypoint) {
        _currentRacingTask = waypointManager.addWaypoint(_currentRacingTask, searchWaypoint)
        // Update current leg - for racing tasks, start at beginning (start waypoint)
        _currentLeg = 0
        saveRacingTask()
    }

    /**
     * Remove waypoint from Racing task
     */
    fun removeRacingWaypoint(index: Int) {
        val waypointCountBefore = _currentRacingTask.waypoints.size
        _currentRacingTask = waypointManager.removeWaypoint(_currentRacingTask, index)
        val waypointCountAfter = _currentRacingTask.waypoints.size

        // Update current leg if waypoint was actually removed
        if (waypointCountAfter < waypointCountBefore) {
            _currentLeg = waypointManager.calculateLegAfterRemoval(_currentLeg, index, waypointCountAfter)
        }

        saveRacingTask()
    }

    /**
     * Update racing waypoint properties
     */
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

    /**
     * Bridge for coordinator updates that receive generic point type values.
     * Casting stays inside the racing module to keep coordinator feature-agnostic.
     */
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

    /**
     * Calculate optimal crossing point on a start/finish line
     * Returns the optimal lat/lon on the line perpendicular to the leg
     */
    fun calculateOptimalLineCrossingPoint(
        lineLat: Double,
        lineLon: Double,
        targetLat: Double,
        targetLon: Double,
        lineWidth: Double
    ): Pair<Double, Double> {
        return RacingGeometryUtils.calculateOptimalLineCrossingPoint(lineLat, lineLon, targetLat, targetLon, lineWidth)
    }


    /**
     * Replace waypoint in Racing task
     */
    fun replaceRacingWaypoint(index: Int, newWaypoint: SearchWaypoint) {
        _currentRacingTask = waypointManager.replaceWaypoint(_currentRacingTask, index, newWaypoint)
        saveRacingTask()
    }

    /**
     * Reorder waypoints in Racing task
     */
    fun reorderRacingWaypoints(fromIndex: Int, toIndex: Int) {
        _currentRacingTask = waypointManager.reorderWaypoints(_currentRacingTask, fromIndex, toIndex)
        saveRacingTask()
    }

    /**
     * Validate Racing task course line
     */
    fun validateRacingCourse(): CourseLineValidation {
        // Simple validation for now - check if we have enough waypoints
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

    /**
     * Clear Racing task
     */
    fun clearRacingTask() {
        _currentRacingTask = SimpleRacingTask()
        _currentLeg = 0
        saveRacingTask()
    }

    /**
     * Advance to next leg in racing task
     */
    fun advanceToNextLeg() {
        val task = _currentRacingTask
        if (task.waypoints.isNotEmpty() && _currentLeg < task.waypoints.size - 1) {
            _currentLeg++
        } else {
        }
    }

    /**
     * Go to previous leg in racing task
     */
    fun goToPreviousLeg() {
        if (_currentLeg > 0) {
            _currentLeg--
        } else {
        }
    }

    fun setRacingLeg(index: Int) {
        if (_currentRacingTask.waypoints.isEmpty()) return
        val clamped = index.coerceIn(0, _currentRacingTask.waypoints.lastIndex)
        _currentLeg = clamped
    }

    // REMOVED: convertAATToRacing() function
    // REASON: Violates CLAUDE.md ZERO cross-contamination rule
    // AAT imports are FORBIDDEN in Racing modules

    /**
     * Save Racing task to preferences
     */
    fun saveRacingTask() {
        racingTaskPersistence?.saveRacingTask(_currentRacingTask) ?: run {
        }
    }

    /**
     * Load Racing task from preferences
     */
    fun loadRacingTask(): SimpleRacingTask? {
        return racingTaskPersistence?.loadRacingTask()?.also { task ->
            _currentRacingTask = task
            // Update current leg after loading - start at beginning
            _currentLeg = 0
        } ?: run {
            null
        }
    }

    /**
     * Get Racing task summary
     */
    fun getRacingTaskSummary(): String {
        return racingTaskPersistence?.getRacingTaskSummary(_currentRacingTask) { calculateRacingDistance() } ?: "Racing Task: No persistence available"
    }

    /**
     * Calculate racing task distance
     */
    fun calculateRacingTaskDistance(): Double {
        return racingTaskPersistence?.calculateRacingTaskDistance(_currentRacingTask) { calculateRacingDistance() } ?: 0.0
    }

    /**
     * Get list of saved Racing tasks
     */
    fun getSavedRacingTasks(): List<String> {
        return racingTaskPersistence?.getSavedRacingTasks() ?: emptyList()
    }

    /**
     * Save Racing task to file
     */
    fun saveRacingTask(taskName: String): Boolean {
        return racingTaskPersistence?.saveRacingTask(_currentRacingTask, taskName) ?: false
    }

    /**
     * Load Racing task from file
     */
    fun loadRacingTaskFromFile(taskName: String): Boolean {
        return racingTaskPersistence?.loadRacingTaskFromFile(taskName)?.let { task ->
            _currentRacingTask = task
            _currentLeg = 0
            saveRacingTask() // Save to preferences
            true
        } ?: false
    }

    /**
     * Delete Racing task file
     */
    fun deleteRacingTask(taskName: String): Boolean {
        return racingTaskPersistence?.deleteRacingTask(taskName) ?: false
    }

    /**
     * Check if Racing task is valid
     */
    fun isRacingTaskValid(): Boolean {
        return _currentRacingTask.waypoints.size >= 2
    }


    /**
     * Calculate segment distance for routing from TaskManagerCoordinator
     * Racing-specific implementation using RacingGeometryUtils
     *
     * @return Distance in kilometers
     */
    fun calculateSegmentDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return RacingGeometryUtils.haversineDistance(lat1, lon1, lat2, lon2)
    }

    /**
     * Calculate distance from GPS to optimal entry point of current waypoint
     * CRITICAL: Uses same geometry calculators as visual display for accuracy
     *
     * @param gpsLat Current GPS latitude
     * @param gpsLon Current GPS longitude
     * @return Distance in km to optimal entry point, or null if no waypoint active
     */
    fun calculateDistanceToCurrentWaypointEntry(gpsLat: Double, gpsLon: Double): Double? {
        return racingTaskCalculator.calculateDistanceToOptimalEntry(
            gpsLat = gpsLat,
            gpsLon = gpsLon,
            waypointIndex = currentLeg,
            waypoints = _currentRacingTask.waypoints
        )
    }

    /**
     * Get task parameters string for Racing task
     */
    fun getRacingTaskParameters(): String {
        return racingTaskPersistence?.getRacingTaskParameters(_currentRacingTask) { calculateRacingDistance() } ?: "Racing Task: No persistence available"
    }

    // REMOVED: generateCircleCoordinates() - moved to RacingGeometryUtils


}
