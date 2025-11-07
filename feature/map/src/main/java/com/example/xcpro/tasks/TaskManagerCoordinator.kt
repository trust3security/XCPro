package com.example.xcpro.tasks

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.*
import com.example.xcpro.common.waypoint.SearchWaypoint
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

    // Task-specific managers - completely independent
    private val racingTaskManager = RacingTaskManager(context)
    private val aatTaskManager = AATTaskManager(context)
    private val persistenceStore = TaskCoordinatorPersistence(
        prefs,
        loadRacingTask = { racingTaskManager.loadRacingTask() != null },
        loadAATTask = { aatTaskManager.loadAATTask() != null },
        log = ::log
    )
    private var mapInstance: MapLibreMap? = null
    private var racingDelegate = createRacingDelegate()
    private var aatDelegate = createAATDelegate()

    // Current task type state
    internal var _taskType by mutableStateOf(TaskType.RACING)
    val taskType: TaskType get() = _taskType

    private inline fun <T> withCurrentManager(
        racingBlock: RacingTaskManager.() -> T,
        aatBlock: AATTaskManager.() -> T
    ): T = when (_taskType) {
        TaskType.RACING -> racingTaskManager.racingBlock()
        TaskType.AAT -> aatTaskManager.aatBlock()
    }

    private inline fun <T> withManager(
        type: TaskType,
        racingBlock: RacingTaskManager.() -> T,
        aatBlock: AATTaskManager.() -> T
    ): T = when (type) {
        TaskType.RACING -> racingTaskManager.racingBlock()
        TaskType.AAT -> aatTaskManager.aatBlock()
    }

    private fun log(message: String) {
        println("TaskManagerCoordinator: $message")
    }

    private fun currentDelegate(): TaskTypeCoordinatorDelegate = when (_taskType) {
        TaskType.RACING -> racingDelegate
        TaskType.AAT -> aatDelegate
    }

    private fun createRacingDelegate(): RacingCoordinatorDelegate = RacingCoordinatorDelegate(
        taskManager = racingTaskManager,
        mapProvider = { mapInstance },
        log = ::log
    )

    private fun createAATDelegate(): AATCoordinatorDelegate = AATCoordinatorDelegate(
        taskManager = aatTaskManager,
        mapProvider = { mapInstance },
        log = ::log
    )
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
        log("Already using ${newTaskType.name} task type")
        return
    }

    val currentWaypoints = currentTask.waypoints
    val hasWaypoints = currentWaypoints.isNotEmpty()
    log("Switching from ${_taskType.name} to ${newTaskType.name} (preserveWaypoints=$hasWaypoints)")

    clearCurrentTask(mapInstance)

    _taskType = newTaskType

    if (hasWaypoints) {
        withManager(
            newTaskType,
            racingBlock = { initializeFromGenericWaypoints(currentWaypoints) },
            aatBlock = { initializeFromGenericWaypoints(currentWaypoints) }
        )
        log("Preserved ${currentWaypoints.size} waypoints for ${newTaskType.name} task")
    } else {
        log("No waypoints to preserve during task switch")
    }

    persistenceStore.saveTaskType(newTaskType)
}


    /**
     * Initialize task with waypoints - routes to appropriate manager
     */
    fun initializeTask(waypoints: List<SearchWaypoint>) {
        withCurrentManager(
            racingBlock = { initializeRacingTask(waypoints) },
            aatBlock = { initializeAATTask(waypoints) }
        )
    }

    /**
     * Add waypoint - routes to appropriate manager
     */
    fun addWaypoint(searchWaypoint: SearchWaypoint) {
        withCurrentManager(
            racingBlock = { addRacingWaypoint(searchWaypoint) },
            aatBlock = { addAATWaypoint(searchWaypoint) }
        )
    }

    /**
     * Remove waypoint - routes to appropriate manager
     */
    fun removeWaypoint(index: Int) {
        withCurrentManager(
            racingBlock = { removeRacingWaypoint(index) },
            aatBlock = { removeAATWaypoint(index) }
        )
    }

    /**
     * Plot task on map - routes to appropriate manager
     */
    fun plotOnMap(map: MapLibreMap?) {
        currentDelegate().plotOnMap(map)
    }

    /**
     * Clear current task - routes to appropriate manager
     */
    fun clearTask() {
        withCurrentManager(
            racingBlock = { clearRacingTask() },
            aatBlock = { clearAATTask() }
        )
    }

    /**
     * Get task summary - routes to appropriate manager
     */
    fun getTaskSummary(): String = withCurrentManager(
        racingBlock = { getRacingTaskSummary() },
        aatBlock = { getAATTaskSummary() }
    )

    /**
     * Check if current task is valid - routes to appropriate manager
     */
    fun isTaskValid(): Boolean = withCurrentManager(
        racingBlock = { isRacingTaskValid() },
        aatBlock = { isAATTaskValid() }
    )

    /**
     * Load persisted task type if available.
     */
    fun loadTaskType() {
        _taskType = persistenceStore.loadTaskType(_taskType)
    }

    /**
     * Get access to specific task managers (for specialized operations)
     */
    fun getRacingTaskManager(): RacingTaskManager = racingTaskManager
    fun getAATTaskManager(): AATTaskManager = aatTaskManager

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
        withCurrentManager(
            racingBlock = { reorderRacingWaypoints(fromIndex, toIndex) },
            aatBlock = { reorderAATWaypoints(fromIndex, toIndex) }
        )
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
        withCurrentManager(
            racingBlock = { replaceRacingWaypoint(index, newWaypoint) },
            aatBlock = { replaceAATWaypoint(index, newWaypoint) }
        )
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
        loadTaskType()
        val result = persistenceStore.loadSavedTasks()
        log("Finished loading saved tasks (racing=${result.racingLoaded}, aat=${result.aatLoaded})")
    }


    /**
     * Navigation methods - routes to appropriate manager
     */
    fun advanceToNextLeg() {
        withCurrentManager(
            racingBlock = { advanceToNextLeg() },
            aatBlock = { advanceToNextLeg() }
        )
    }

    fun goToPreviousLeg() {
        withCurrentManager(
            racingBlock = { goToPreviousLeg() },
            aatBlock = { goToPreviousLeg() }
        )
    }

    /**
     * Distance calculation methods - routes to appropriate manager
     */
    fun calculateTaskDistanceForTask(task: Task): Double {
        return currentDelegate().calculateDistance()
    }

    /**
     * Calculate simple segment distance - ROUTES to task-specific calculators
     * NO calculation logic here - pure routing only
     */
    fun calculateSimpleSegmentDistance(from: TaskWaypoint, to: TaskWaypoint): Double {
        return currentDelegate().calculateSegmentDistance(from, to)
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
            log("Cannot update AAT point type - current task type is $_taskType")
            return
        }
        aatDelegate.updateWaypointPointType(
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

    /**
     * Update AAT target point position within assigned area.
     */
    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        if (_taskType != TaskType.AAT) {
            log("Cannot update AAT target point - current task type is $_taskType")
            return
        }
        aatDelegate.updateTargetPoint(index, lat, lon)
    }

    /**
     * Update AAT task parameters
     */
    fun updateAATParameters(minimumTime: java.time.Duration, maximumTime: java.time.Duration) {
        if (_taskType != TaskType.AAT) {
            log("Cannot update AAT parameters - current task type is $_taskType")
            return
        }
        aatDelegate.updateParameters(minimumTime, maximumTime)
    }

    /**
     * Update AAT area radius for a specific waypoint
     * NOTE: Caller is responsible for re-plotting map if needed
     */
    fun updateAATArea(index: Int, radiusMeters: Double) {
        if (_taskType != TaskType.AAT) {
            log("Cannot update AAT area - current task type is $_taskType")
            return
        }
        aatDelegate.updateArea(index, radiusMeters)
    }


    private fun clearCurrentTask(map: MapLibreMap?) {
        currentDelegate().clearFromMap(map)
        currentDelegate().clearTask()
    }

    fun setMapInstance(map: MapLibreMap?) {
        mapInstance = map
    }

    fun getMapInstance(): MapLibreMap? = mapInstance

    @VisibleForTesting
    internal fun replaceAATDelegateForTesting(delegate: AATCoordinatorDelegate) {
        aatDelegate = delegate
    }

    @VisibleForTesting
    internal fun replaceRacingDelegateForTesting(delegate: RacingCoordinatorDelegate) {
        racingDelegate = delegate
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

        return aatDelegate.checkAreaTap(lat, lon)
    }

    /**
     * Enter AAT edit mode for a specific waypoint
     * Enables target point manipulation within the assigned area
     */
    fun enterAATEditMode(waypointIndex: Int) {
        if (_taskType == TaskType.AAT) {
            aatDelegate.enterEditMode(waypointIndex)
        }
    }

    /**
     * Exit AAT edit mode
     */
    fun exitAATEditMode() {
        if (_taskType == TaskType.AAT) {
            aatDelegate.exitEditMode()
        }
    }

    /**
     * Check if currently in AAT edit mode
     */
    fun isInAATEditMode(): Boolean =
        _taskType == TaskType.AAT && aatDelegate.isInEditMode()

    /**
     * Get current AAT edit waypoint index
     */
    fun getAATEditWaypointIndex(): Int? =
        if (_taskType == TaskType.AAT) aatDelegate.editWaypointIndex() else null

    /**
     * Check if a map click hit an AAT target point pin for drag handling
     */
    fun checkAATTargetPointHit(screenX: Float, screenY: Float): Int? {
        if (_taskType != TaskType.AAT) {
            return null
        }

        return aatDelegate.checkTargetPointHit(screenX, screenY)
    }
}

// TaskType enum is already defined in TaskManager.kt - removed duplicate

// Compatibility helpers moved to TaskManagerCompat.kt








