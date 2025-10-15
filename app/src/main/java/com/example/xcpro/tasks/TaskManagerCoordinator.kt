package com.example.xcpro.tasks

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import com.example.xcpro.SearchWaypoint
import com.google.gson.Gson
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.maplibre.android.maps.MapLibreMap
import java.util.UUID

// Coordinator imports - NO task-specific imports to avoid circular dependencies
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.tasks.aat.AATTaskManager

/**
 * Task Manager Coordinator - Routes to appropriate task-specific managers
 *
 * ARCHITECTURE: This class acts as a router only - NO calculation logic
 * All task-specific logic is delegated to independent task managers
 *
 * SEPARATION: Zero cross-contamination between Racing/AAT
 */
class TaskManagerCoordinator(val context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("task_coordinator_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Task-specific managers - completely independent
    private val racingTaskManager = RacingTaskManager(context)
    private val aatTaskManager = AATTaskManager(context)

    // Current task type state
    internal var _taskType by mutableStateOf(TaskType.RACING)
    val taskType: TaskType get() = _taskType

    /**
     * Current task - routes to appropriate manager and extracts full customization data
     */
    val currentTask: Task get() = when (_taskType) {
        TaskType.RACING -> racingTaskManager.getCoreTask()
        TaskType.AAT -> aatTaskManager.getCoreTask()
    }

    /**
     * Current leg - routes to appropriate manager
     */
    val currentLeg: Int get() = when (_taskType) {
        TaskType.RACING -> racingTaskManager.currentLeg
        TaskType.AAT -> aatTaskManager.currentLeg
    }

    /**
     * Switch task type - preserves waypoints while maintaining task type separation
     */
    fun switchToTaskType(newTaskType: TaskType) {
        if (newTaskType == _taskType) {
            println("🎯 COORDINATOR: Already using ${newTaskType.name} task type")
            return
        }

        println("🎯 COORDINATOR: Switching from ${_taskType.name} to ${newTaskType.name}")

        // Preserve current waypoints as generic TaskWaypoints (if any exist)
        val currentWaypoints = currentTask.waypoints
        val hasWaypoints = currentWaypoints.isNotEmpty()

        if (hasWaypoints) {
            println("🔄 COORDINATOR: Preserving ${currentWaypoints.size} waypoints during task type switch")
        } else {
            println("🔄 COORDINATOR: No waypoints to preserve - creating empty task")
        }

        // Clear current task type (maintain separation)
        // CRITICAL FIX: Clear map visuals AND data when switching task types
        val mapInstance = getMapInstance()
        when (_taskType) {
            TaskType.RACING -> {
                // Clear Racing visuals from map first (prevents old task overlap)
                if (mapInstance != null) {
                    racingTaskManager.clearRacingFromMap(mapInstance)
                    println("🏁 COORDINATOR: Cleared Racing visuals from map")
                } else {
                    println("❌ COORDINATOR: Cannot clear Racing visuals - map instance is null")
                }
                // Then clear Racing data
                racingTaskManager.clearRacingTask()
            }
            TaskType.AAT -> {
                // Clear AAT visuals from map first (prevents old task overlap)
                if (mapInstance != null) {
                    aatTaskManager.clearAATFromMap(mapInstance)
                    println("🟢 COORDINATOR: Cleared AAT visuals from map")
                } else {
                    println("❌ COORDINATOR: Cannot clear AAT visuals - map instance is null")
                }
                // Then clear AAT data
                aatTaskManager.clearAATTask()
            }
        }

        // Switch to the new task type
        _taskType = newTaskType

        when (newTaskType) {
            TaskType.RACING -> {
                println("🎯 COORDINATOR: Switched to Racing task management")
                if (hasWaypoints) {
                    racingTaskManager.initializeFromGenericWaypoints(currentWaypoints)
                }
            }
            TaskType.AAT -> {
                println("🎯 COORDINATOR: Switched to AAT task management")
                if (hasWaypoints) {
                    aatTaskManager.initializeFromGenericWaypoints(currentWaypoints)
                }
            }
        }

        saveTaskType()

        if (hasWaypoints) {
            println("✅ COORDINATOR: Successfully preserved ${currentWaypoints.size} waypoints in ${newTaskType.name} task")
        }
    }

    /**
     * Initialize task with waypoints - routes to appropriate manager
     */
    fun initializeTask(waypoints: List<SearchWaypoint>) {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.initializeRacingTask(waypoints)
            TaskType.AAT -> aatTaskManager.initializeAATTask(waypoints)
        }
    }

    /**
     * Add waypoint - routes to appropriate manager
     */
    fun addWaypoint(searchWaypoint: SearchWaypoint) {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.addRacingWaypoint(searchWaypoint)
            TaskType.AAT -> aatTaskManager.addAATWaypoint(searchWaypoint)
        }
    }

    /**
     * Remove waypoint - routes to appropriate manager
     */
    fun removeWaypoint(index: Int) {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.removeRacingWaypoint(index)
            TaskType.AAT -> aatTaskManager.removeAATWaypoint(index)
        }
    }

    /**
     * Plot task on map - routes to appropriate manager
     */
    fun plotOnMap(map: MapLibreMap?) {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.plotRacingOnMap(map)
            TaskType.AAT -> aatTaskManager.plotAATOnMap(map)
        }
    }

    /**
     * Clear current task - routes to appropriate manager
     */
    fun clearTask() {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.clearRacingTask()
            TaskType.AAT -> aatTaskManager.clearAATTask()
        }
    }

    /**
     * Get task summary - routes to appropriate manager
     */
    fun getTaskSummary(): String {
        return when (_taskType) {
            TaskType.RACING -> racingTaskManager.getRacingTaskSummary()
            TaskType.AAT -> aatTaskManager.getAATTaskSummary()
        }
    }

    /**
     * Check if current task is valid - routes to appropriate manager
     */
    fun isTaskValid(): Boolean {
        return when (_taskType) {
            TaskType.RACING -> racingTaskManager.isRacingTaskValid()
            TaskType.AAT -> aatTaskManager.isAATTaskValid()
        }
    }

    /**
     * Save current task type to preferences
     */
    private fun saveTaskType() {
        prefs?.let { preferences ->
            val editor = preferences.edit()
            editor.putString("current_task_type", _taskType.name)
            editor.apply()
            println("🎯 COORDINATOR: Saved task type: ${_taskType.name}")
        }
    }

    /**
     * Load task type from preferences
     */
    fun loadTaskType() {
        prefs?.let { preferences ->
            val taskTypeName = preferences.getString("current_task_type", TaskType.RACING.name)
            _taskType = try {
                TaskType.valueOf(taskTypeName ?: TaskType.RACING.name)
            } catch (e: Exception) {
                TaskType.RACING
            }
            println("🎯 COORDINATOR: Loaded task type: ${_taskType.name}")
        }
    }

    /**
     * Get access to specific task managers (for specialized operations)
     */
    fun getRacingTaskManager(): RacingTaskManager = racingTaskManager
    fun getAATTaskManager(): AATTaskManager = aatTaskManager

    /**
     * Plot task on map - routes to appropriate manager
     * Note: Using plotTaskOnMap to avoid ambiguity with specific manager methods
     */
    fun plotTaskOnMap(map: MapLibreMap?) {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.plotRacingOnMap(map)
            TaskType.AAT -> aatTaskManager.plotAATOnMap(map)
        }
    }

    /**
     * Get current leg waypoint - routes to appropriate manager
     */
    fun getCurrentLegWaypoint(): TaskWaypoint? {
        val waypoints = currentTask.waypoints
        return if (currentLeg in waypoints.indices) waypoints[currentLeg] else null
    }

    /**
     * Reorder waypoints - routes to appropriate manager
     */
    fun reorderWaypoints(fromIndex: Int, toIndex: Int) {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.reorderRacingWaypoints(fromIndex, toIndex)
            TaskType.AAT -> aatTaskManager.reorderAATWaypoints(fromIndex, toIndex)
        }
    }

    /**
     * Update waypoint point type - routes to appropriate manager
     */
    fun updateWaypointPointType(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidth: Double?,
        keyholeInnerRadius: Double?,
        keyholeAngle: Double?,
        faiQuadrantOuterRadius: Double?
    ) {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.updateWaypointPointTypeBridge(
                index = index,
                startType = startType,
                finishType = finishType,
                turnType = turnType,
                gateWidth = gateWidth,
                keyholeInnerRadius = keyholeInnerRadius,
                keyholeAngle = keyholeAngle,
                faiQuadrantOuterRadius = faiQuadrantOuterRadius
            )
            TaskType.AAT -> aatTaskManager.updateWaypointPointTypeBridge(
                index = index,
                startType = startType,
                finishType = finishType,
                turnType = turnType,
                gateWidth = gateWidth,
                keyholeInnerRadius = keyholeInnerRadius,
                keyholeAngle = keyholeAngle,
                sectorOuterRadius = faiQuadrantOuterRadius
            )
        }
    }

    /**
     * Replace waypoint - routes to appropriate manager
     */
    fun replaceWaypoint(index: Int, newWaypoint: SearchWaypoint) {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.replaceRacingWaypoint(index, newWaypoint)
            TaskType.AAT -> aatTaskManager.replaceAATWaypoint(index, newWaypoint)
        }
    }

    /**
     * Get task-specific waypoint at index for detailed editing
     */
    fun getTaskSpecificWaypoint(index: Int): Any? {
        return when (_taskType) {
            TaskType.RACING -> {
                val waypoints = racingTaskManager.currentRacingTask.waypoints
                if (index < waypoints.size) waypoints[index] else null
            }
            TaskType.AAT -> {
                val waypoints = aatTaskManager.currentAATTask.waypoints
                if (index < waypoints.size) waypoints[index] else null
            }
        }
    }
    /**
     * Load saved tasks from all managers
     */
    fun loadSavedTasks() {
        println("🎯 COORDINATOR: Loading saved tasks...")
        println("🎯 COORDINATOR: DEBUG - About to call loadTaskType()")
        loadTaskType()
        println("🎯 COORDINATOR: DEBUG - Finished loadTaskType()")
        println("🎯 COORDINATOR: Loading racing task...")
        println("🎯 COORDINATOR: Racing manager available: ${racingTaskManager != null}")
        val racingResult = racingTaskManager.loadRacingTask()
        println("🎯 COORDINATOR: Racing task load result: $racingResult")
        println("🎯 COORDINATOR: Loading AAT task...")
        println("🎯 COORDINATOR: AAT manager available: ${aatTaskManager != null}")
        val aatResult = aatTaskManager.loadAATTask()
        println("🎯 COORDINATOR: AAT task load result: $aatResult")
        println("🎯 COORDINATOR: Finished loading saved tasks")
    }

    /**
     * Navigation methods - routes to appropriate manager
     */
    fun advanceToNextLeg() {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.advanceToNextLeg()
            TaskType.AAT -> aatTaskManager.advanceToNextLeg()
        }
    }

    fun goToPreviousLeg() {
        when (_taskType) {
            TaskType.RACING -> racingTaskManager.goToPreviousLeg()
            TaskType.AAT -> aatTaskManager.goToPreviousLeg()
        }
    }

    /**
     * Distance calculation methods - routes to appropriate manager
     */
    fun calculateTaskDistanceForTask(task: Task): Double {
        return when (_taskType) {
            TaskType.RACING -> {
                // Convert generic task back to racing task for calculation
                racingTaskManager.calculateRacingTaskDistance()
            }
            TaskType.AAT -> {
                // Convert generic task back to AAT task for calculation
                aatTaskManager.calculateAATTaskDistance()
            }
        }
    }

    /**
     * Calculate simple segment distance - ROUTES to task-specific calculators
     * NO calculation logic here - pure routing only
     */
    fun calculateSimpleSegmentDistance(from: TaskWaypoint, to: TaskWaypoint): Double {
        return when (_taskType) {
            TaskType.RACING -> racingTaskManager.calculateSegmentDistance(from.lat, from.lon, to.lat, to.lon)
            TaskType.AAT -> aatTaskManager.calculateSegmentDistance(from.lat, from.lon, to.lat, to.lon)
        }
    }

    /**
     * Calculate real-time distance from current GPS position to active waypoint
     * ROUTES to task-specific calculators - maintains complete task type separation
     *
     * CRITICAL: Uses SAME geometry calculators as visual display for competition safety!
     *
     * Returns:
     * - Racing: Distance to OPTIMAL ENTRY POINT (cylinder edge/sector boundary/line crossing)
     * - AAT: Distance to TARGET POINT (movable pin inside assigned area)
     * - null: If no waypoint is active or GPS unavailable
     */
    fun calculateDistanceToCurrentWaypoint(currentLat: Double, currentLon: Double): Double? {
        return when (_taskType) {
            TaskType.RACING -> {
                // Racing: Calculate distance to optimal entry point using same calculators as visual display
                // CRITICAL: This matches the visual course line displayed on map
                racingTaskManager.calculateDistanceToCurrentWaypointEntry(currentLat, currentLon)
            }
            TaskType.AAT -> {
                // AAT: Calculate distance to TARGET POINT (not area center!)
                // CRITICAL: This matches the visual course line connecting target points
                aatTaskManager.calculateDistanceToCurrentTargetPoint(currentLat, currentLon)
            }
        }
    }

    /**
     * Calculate optimal start line crossing point for Racing tasks
     * Returns the optimal lat/lon on the start line for reaching the next waypoint
     */
    fun calculateOptimalStartLineCrossingPoint(startWaypoint: TaskWaypoint, nextWaypoint: TaskWaypoint): Pair<Double, Double> {
        // For Racing tasks, delegate to the racing manager for optimal crossing calculation
        if (_taskType == TaskType.RACING) {
            // Get the racing-specific waypoint with gate width
            val racingWaypoint = racingTaskManager.currentRacingTask.waypoints.firstOrNull { it.id == startWaypoint.id }
            if (racingWaypoint != null) {
                // Use the racing calculator for optimal line crossing
                return racingTaskManager.calculateOptimalLineCrossingPoint(
                    racingWaypoint.lat,
                    racingWaypoint.lon,
                    nextWaypoint.lat,
                    nextWaypoint.lon,
                    racingWaypoint.gateWidth
                )
            }
        }
        // Fallback to waypoint center if not a racing task or calculation fails
        return Pair(startWaypoint.lat, startWaypoint.lon)
    }

    /**
     * Distance calculation - ROUTES to task-specific calculators
     * DEPRECATED: Use calculateSimpleSegmentDistance instead for clarity
     * Kept for backward compatibility only
     */
    @Deprecated("Use calculateSimpleSegmentDistance for clarity", ReplaceWith("calculateSimpleSegmentDistance"))
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return when (_taskType) {
            TaskType.RACING -> racingTaskManager.calculateSegmentDistance(lat1, lon1, lat2, lon2)
            TaskType.AAT -> aatTaskManager.calculateSegmentDistance(lat1, lon1, lat2, lon2)
        }
    }

    /**
     * File operations - routes to appropriate manager
     */
    fun getSavedTasks(context: Context): List<String> {
        return when (_taskType) {
            TaskType.RACING -> racingTaskManager.getSavedRacingTasks()
            TaskType.AAT -> aatTaskManager.getSavedAATTasks(context)
        }
    }

    fun saveTask(context: Context, taskName: String): Boolean {
        return when (_taskType) {
            TaskType.RACING -> racingTaskManager.saveRacingTask(taskName)
            TaskType.AAT -> aatTaskManager.saveAATTask(context, taskName)
        }
    }

    fun loadTask(context: Context, taskName: String): Boolean {
        return when (_taskType) {
            TaskType.RACING -> racingTaskManager.loadRacingTaskFromFile(taskName)
            TaskType.AAT -> aatTaskManager.loadAATTaskFromFile(context, taskName)
        }
    }

    fun deleteTask(context: Context, taskName: String): Boolean {
        return when (_taskType) {
            TaskType.RACING -> racingTaskManager.deleteRacingTask(taskName)
            TaskType.AAT -> aatTaskManager.deleteAATTask(context, taskName)
        }
    }

    /**
     * Set task type for compatibility
     */
    fun setTaskType(taskType: TaskType) {
        switchToTaskType(taskType)
    }

    /**
     * Update AAT waypoint point types - routes to AAT manager only
     * Maintains complete separation from Racing point types
     */
    fun updateAATWaypointPointType(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidth: Double?,
        keyholeInnerRadius: Double?,
        keyholeAngle: Double?,
        sectorOuterRadius: Double?
    ) {
        if (_taskType != TaskType.AAT) {
            println("COORDINATOR: Cannot update AAT point type - current task type is $_taskType")
            return
        }

        println("COORDINATOR: AAT waypoint point type update - Index: $index")
        listOf(
            "start" to startType,
            "finish" to finishType,
            "turn" to turnType,
            "gateWidthKm" to gateWidth,
            "keyholeInnerRadiusKm" to keyholeInnerRadius,
            "keyholeAngle" to keyholeAngle,
            "sectorOuterRadiusKm" to sectorOuterRadius
        ).forEach { (label, value) ->
            if (value != null) {
                println("  $label: $value")
            }
        }

        aatTaskManager.updateWaypointPointTypeBridge(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidth = gateWidth,
            keyholeInnerRadius = keyholeInnerRadius,
            keyholeAngle = keyholeAngle,
            sectorOuterRadius = sectorOuterRadius
        )

        getMapInstance()?.let {
            println("COORDINATOR: Re-plotting AAT task after point type update")
            aatTaskManager.plotAATOnMap(it)
        } ?: println("COORDINATOR: Cannot re-plot AAT task - map instance is null")
    }

    /**
     * Update AAT target point position within assigned area.
     */
    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        if (_taskType != TaskType.AAT) {
            println("COORDINATOR: Cannot update AAT target point - current task type is $_taskType")
            return
        }

        println("COORDINATOR: AAT target point update - index=$index, lat=$lat, lon=$lon")
        aatTaskManager.updateTargetPoint(index, lat, lon)

        getMapInstance()?.let {
            println("COORDINATOR: Re-plotting AAT task after target point update")
            aatTaskManager.plotAATOnMap(it)
        } ?: println("COORDINATOR: Cannot re-plot AAT task - map instance is null")
    }

    /**
     * Update AAT task parameters
     */
    fun updateAATParameters(minimumTime: java.time.Duration, maximumTime: java.time.Duration) {
        if (_taskType == TaskType.AAT) {
            aatTaskManager.updateAATTimes(minimumTime, maximumTime)
            println("🎯 COORDINATOR: Updated AAT parameters - min: ${minimumTime.toHours()}h, max: ${maximumTime.toHours()}h")
        } else {
            println("🎯 COORDINATOR: Cannot update AAT parameters - current task type is $_taskType")
        }
    }

    /**
     * Update AAT area radius for a specific waypoint
     * NOTE: Caller is responsible for re-plotting map if needed
     */
    fun updateAATArea(index: Int, radiusMeters: Double) {
        if (_taskType == TaskType.AAT) {
            // Get current waypoint and update its assigned area
            val waypoint = aatTaskManager.currentAATTask.waypoints.getOrNull(index)
            if (waypoint != null) {
                val newArea = waypoint.assignedArea.copy(radiusMeters = radiusMeters)
                aatTaskManager.updateAATArea(index, newArea)
                println("🎯 COORDINATOR: Updated AAT area radius at index $index to ${radiusMeters/1000.0}km")
                // ✅ REMOVED: Map re-plotting - caller controls when to re-plot
                // This prevents double plotting when UI already calls plotOnMap()
            }
        } else {
            println("🎯 COORDINATOR: Cannot update AAT area - current task type is $_taskType")
        }
    }

    // Helper to get map instance for re-plotting
    private var mapInstance: org.maplibre.android.maps.MapLibreMap? = null

    fun setMapInstance(map: org.maplibre.android.maps.MapLibreMap?) {
        mapInstance = map
    }

    fun getMapInstance(): org.maplibre.android.maps.MapLibreMap? {
        return mapInstance
    }

    // ==================== AAT INTERACTIVE FEATURES ====================

    /**
     * Check if a map tap hit an AAT area for double-tap detection
     * Returns (waypointIndex, areaData) if hit, null if no hit
     */
    fun checkAATAreaTap(lat: Double, lon: Double): Pair<Int, Any>? {
        if (_taskType != TaskType.AAT) {
            return null
        }

        return aatTaskManager.checkAreaTap(lat, lon)
    }

    /**
     * Enter AAT edit mode for a specific waypoint
     * Enables target point manipulation within the assigned area
     */
    fun enterAATEditMode(waypointIndex: Int) {
        if (_taskType == TaskType.AAT) {
            println("🎯 COORDINATOR: Entering AAT edit mode for waypoint $waypointIndex")
            aatTaskManager.setEditMode(waypointIndex, true)

            // Update map to show edit state
            val mapInstance = getMapInstance()
            if (mapInstance != null) {
                aatTaskManager.plotAATEditOverlay(mapInstance, waypointIndex)
                // Re-plot task to update target point color to RED and increase size
                aatTaskManager.plotAATOnMap(mapInstance)
            }
        }
    }

    /**
     * Exit AAT edit mode
     */
    fun exitAATEditMode() {
        if (_taskType == TaskType.AAT) {
            println("🎯 COORDINATOR: Exiting AAT edit mode")
            aatTaskManager.setEditMode(-1, false)

            // Update map to remove edit overlays
            val mapInstance = getMapInstance()
            if (mapInstance != null) {
                aatTaskManager.clearAATEditOverlay(mapInstance)
                // Re-plot task to update target point color back to GREEN and reduce size
                aatTaskManager.plotAATOnMap(mapInstance)
            }
        }
    }

    /**
     * Check if currently in AAT edit mode
     */
    fun isInAATEditMode(): Boolean {
        return if (_taskType == TaskType.AAT) {
            aatTaskManager.isInEditMode()
        } else {
            false
        }
    }

    /**
     * Get current AAT edit waypoint index
     */
    fun getAATEditWaypointIndex(): Int? {
        return if (_taskType == TaskType.AAT) {
            aatTaskManager.getEditWaypointIndex()
        } else {
            null
        }
    }

    /**
     * Check if a map click hit an AAT target point pin for drag handling
     */
    fun checkAATTargetPointHit(screenX: Float, screenY: Float): Int? {
        if (_taskType != TaskType.AAT) {
            return null
        }

        val mapInstance = getMapInstance()
        return if (mapInstance != null) {
            aatTaskManager.checkTargetPointHit(mapInstance, screenX, screenY)
        } else {
            println("❌ COORDINATOR: Cannot check target point hit - map instance is null")
            null
        }
    }
}

// TaskType enum is already defined in TaskManager.kt - removed duplicate

/**
 * Global coordinator instance for app-wide access
 */
private var globalTaskManagerCoordinator: TaskManagerCoordinator? = null

/**
 * Get global task manager coordinator
 */
fun getGlobalTaskManagerCoordinator(context: Context? = null): TaskManagerCoordinator? {
    if (globalTaskManagerCoordinator == null && context != null) {
        globalTaskManagerCoordinator = TaskManagerCoordinator(context)
    }
    return globalTaskManagerCoordinator
}

/**
 * Composable function to remember task manager coordinator
 * Note: Use rememberTaskManager() for compatibility, this is for direct coordinator access
 */
@Composable
fun rememberTaskManagerCoordinator(context: Context? = null): TaskManagerCoordinator {
    return remember {
        if (globalTaskManagerCoordinator == null) {
            globalTaskManagerCoordinator = TaskManagerCoordinator(context)
            globalTaskManagerCoordinator?.loadSavedTasks()
        }
        globalTaskManagerCoordinator!!
    }
}

// ==================== COMPATIBILITY LAYER ====================
// These functions provide backward compatibility for code using old TaskManager

/**
 * TaskManager is now an alias for TaskManagerCoordinator
 */
typealias TaskManager = TaskManagerCoordinator

/**
 * Get global task manager - for backward compatibility
 */
fun getGlobalTaskManager(context: Context? = null): TaskManagerCoordinator? {
    return getGlobalTaskManagerCoordinator(context)
}

/**
 * Composable function to remember task manager - for backward compatibility
 */
@Composable
fun rememberTaskManager(context: Context? = null): TaskManagerCoordinator {
    return rememberTaskManagerCoordinator(context)
}
