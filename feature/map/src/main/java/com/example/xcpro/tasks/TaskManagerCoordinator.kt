package com.example.xcpro.tasks

import androidx.annotation.VisibleForTesting
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.aat.gestures.AatGestureHandler
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.domain.engine.AATTaskEngine
import com.example.xcpro.tasks.domain.engine.RacingTaskEngine
import com.example.xcpro.tasks.domain.persistence.TaskEnginePersistenceService
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.tasks.racing.gestures.RacingGestureHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes calls to the Racing or AAT task managers; contains no task-specific math.
 */
class TaskManagerCoordinator(
    private val taskEnginePersistenceService: TaskEnginePersistenceService? = null,
    private val racingTaskEngine: RacingTaskEngine? = null,
    private val aatTaskEngine: AATTaskEngine? = null,
    private val racingTaskManager: RacingTaskManager,
    private val aatTaskManager: AATTaskManager
) {
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var racingDelegate = createRacingDelegate()
    private var aatDelegate = createAATDelegate()
    private var proximityHandler: ((Boolean, Boolean) -> Unit)? = null
    private val legChangeHandlers = LinkedHashSet<(Int) -> Unit>()

    private val _taskType = MutableStateFlow(TaskType.RACING)
    val taskType: TaskType get() = _taskType.value
    val taskTypeFlow: StateFlow<TaskType> = _taskType.asStateFlow()
    private val persistenceBridge = TaskCoordinatorPersistenceBridge(
        taskTypeState = _taskType,
        taskEnginePersistenceService = taskEnginePersistenceService,
        racingTaskEngine = racingTaskEngine,
        aatTaskEngine = aatTaskEngine,
        racingTaskManager = racingTaskManager,
        aatTaskManager = aatTaskManager,
        scope = coordinatorScope,
        log = ::log
    )

    private inline fun <T> withCurrentManager(
        racingBlock: RacingTaskManager.() -> T,
        aatBlock: AATTaskManager.() -> T
    ): T = if (_taskType.value == TaskType.RACING) racingTaskManager.racingBlock() else aatTaskManager.aatBlock()

    private inline fun <T> withManager(
        type: TaskType,
        racingBlock: RacingTaskManager.() -> T,
        aatBlock: AATTaskManager.() -> T
    ): T = if (type == TaskType.RACING) racingTaskManager.racingBlock() else aatTaskManager.aatBlock()

    private fun log(@Suppress("UNUSED_PARAMETER") message: String) {}

    private fun currentDelegate(): TaskTypeCoordinatorDelegate = if (_taskType.value == TaskType.RACING) racingDelegate else aatDelegate

    private fun createRacingDelegate() = RacingCoordinatorDelegate(
        taskManager = racingTaskManager,
        log = ::log
    )

    private fun createAATDelegate() = AATCoordinatorDelegate(
        taskManager = aatTaskManager,
        log = ::log
    )

    val currentTask: Task
        get() = if (_taskType.value == TaskType.RACING) racingTaskManager.getCoreTask() else aatTaskManager.getCoreTask()

    val currentLeg: Int
        get() = if (_taskType.value == TaskType.RACING) racingTaskManager.currentLeg else aatTaskManager.currentLeg

    fun switchToTaskType(newTaskType: TaskType) {
        if (newTaskType == _taskType.value) {
            log("Already using ${newTaskType.name} task type"); return
        }
        val currentWaypoints = currentTask.waypoints
        val hasWaypoints = currentWaypoints.isNotEmpty()
        log("Switching from ${_taskType.value.name} to ${newTaskType.name} (preserveWaypoints=$hasWaypoints)")

        clearCurrentTask()
        _taskType.value = newTaskType

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
        persistenceBridge.persistTaskType(newTaskType)
        persistenceBridge.syncEngineFromManager(newTaskType)
    }

    fun initializeTask(waypoints: List<SearchWaypoint>) =
        withCurrentManager(racingBlock = { initializeRacingTask(waypoints) }, aatBlock = { initializeAATTask(waypoints) })

    fun addWaypoint(searchWaypoint: SearchWaypoint) =
        withCurrentManager(racingBlock = { addRacingWaypoint(searchWaypoint) }, aatBlock = { addAATWaypoint(searchWaypoint) })

    fun removeWaypoint(index: Int) =
        withCurrentManager(racingBlock = { removeRacingWaypoint(index) }, aatBlock = { removeAATWaypoint(index) })

    fun clearTask() = withCurrentManager(racingBlock = { clearRacingTask() }, aatBlock = { clearAATTask() })

    fun getTaskSummary(): String = withCurrentManager(racingBlock = { getRacingTaskSummary() }, aatBlock = { getAATTaskSummary() })

    fun isTaskValid(): Boolean = withCurrentManager(racingBlock = { isRacingTaskValid() }, aatBlock = { isAATTaskValid() })

    fun createGestureHandler(callbacks: TaskGestureCallbacks): TaskGestureHandler {
        return if (_taskType.value == TaskType.AAT) {
            AatGestureHandler(
                waypointsProvider = { currentTask.waypoints },
                callbacks = callbacks
            )
        } else {
            RacingGestureHandler()
        }
    }

    fun getCurrentLegWaypoint(): TaskWaypoint? = currentTask.waypoints.getOrNull(currentLeg)

    fun reorderWaypoints(fromIndex: Int, toIndex: Int) =
        withCurrentManager(racingBlock = { reorderRacingWaypoints(fromIndex, toIndex) }, aatBlock = { reorderAATWaypoints(fromIndex, toIndex) })

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
        when (_taskType.value) {
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

    fun replaceWaypoint(index: Int, newWaypoint: SearchWaypoint) =
        withCurrentManager(racingBlock = { replaceRacingWaypoint(index, newWaypoint) }, aatBlock = { replaceAATWaypoint(index, newWaypoint) })

    suspend fun loadSavedTasks() {
        persistenceBridge.loadSavedTasks()
    }

    fun advanceToNextLeg() {
        val before = currentLeg
        withCurrentManager(racingBlock = { advanceToNextLeg() }, aatBlock = { advanceToNextLeg() })
        val after = currentLeg
        if (after != before) {
            legChangeHandlers.forEach { handler -> handler.invoke(after) }
        }
    }

    fun goToPreviousLeg() {
        val before = currentLeg
        withCurrentManager(racingBlock = { goToPreviousLeg() }, aatBlock = { goToPreviousLeg() })
        val after = currentLeg
        if (after != before) {
            legChangeHandlers.forEach { handler -> handler.invoke(after) }
        }
    }
    fun setActiveLeg(index: Int) = withCurrentManager(
        racingBlock = { setRacingLeg(index) },
        aatBlock = { setAATLeg(index) }
    ).also { legChangeHandlers.forEach { handler -> handler.invoke(currentLeg) } }

    fun getActiveLeg(): Int = currentLeg

    fun calculateTaskDistanceForTask(task: Task): Double {
        if (task.waypoints.size < 2) {
            return 0.0
        }
        val delegate = currentDelegate()
        return task.waypoints
            .zipWithNext()
            .sumOf { (from, to) -> delegate.calculateSegmentDistance(from, to) }
    }

    fun calculateSimpleSegmentDistance(from: TaskWaypoint, to: TaskWaypoint): Double =
        currentDelegate().calculateSegmentDistance(from, to)

    fun calculateDistanceToCurrentWaypoint(currentLat: Double, currentLon: Double): Double? = when (_taskType.value) {
        TaskType.RACING -> racingTaskManager.calculateDistanceToCurrentWaypointEntry(currentLat, currentLon)
        TaskType.AAT -> aatTaskManager.calculateDistanceToCurrentTargetPoint(currentLat, currentLon)
    }

    fun calculateOptimalStartLineCrossingPoint(startWaypoint: TaskWaypoint, nextWaypoint: TaskWaypoint): Pair<Double, Double> {
        if (_taskType.value == TaskType.RACING) {
            racingTaskManager.currentRacingTask.waypoints.firstOrNull { it.id == startWaypoint.id }?.let { wp ->
                return racingTaskManager.calculateOptimalLineCrossingPoint(
                    wp.lat, wp.lon, nextWaypoint.lat, nextWaypoint.lon, wp.gateWidth
                )
            }
        }
        return Pair(startWaypoint.lat, startWaypoint.lon)
    }

    suspend fun getSavedTasks(): List<String> {
        return persistenceBridge.getSavedTasks()
    }

    suspend fun saveTask(taskName: String): Boolean {
        return persistenceBridge.saveTask(taskName)
    }

    suspend fun loadTask(taskName: String): Boolean {
        return persistenceBridge.loadTask(taskName)
    }

    suspend fun deleteTask(taskName: String): Boolean {
        return persistenceBridge.deleteTask(taskName)
    }

    fun setTaskType(taskType: TaskType) = switchToTaskType(taskType)

    @VisibleForTesting
    internal fun setTaskTypeForTesting(taskType: TaskType) {
        _taskType.value = taskType
    }

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
        if (_taskType.value != TaskType.AAT) {
            log("Cannot update AAT point type - current task type is ${_taskType.value}"); return
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

    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot update AAT target point - current task type is ${_taskType.value}"); return
        }
        aatDelegate.updateTargetPoint(index, lat, lon)
    }

    fun updateAATParameters(minimumTime: java.time.Duration, maximumTime: java.time.Duration) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot update AAT parameters - current task type is ${_taskType.value}"); return
        }
        aatDelegate.updateParameters(minimumTime, maximumTime)
    }

    fun updateAATArea(index: Int, radiusMeters: Double) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot update AAT area - current task type is ${_taskType.value}"); return
        }
        aatDelegate.updateArea(index, radiusMeters)
    }

    private fun clearCurrentTask() {
        currentDelegate().clearTask()
    }

    fun setProximityHandler(handler: (Boolean, Boolean) -> Unit) {
        proximityHandler = handler
    }

    fun reportProximity(hasEnteredOZ: Boolean, closeToTarget: Boolean) {
        proximityHandler?.invoke(hasEnteredOZ, closeToTarget)
    }

    fun setLegChangeHandler(handler: (Int) -> Unit) {
        legChangeHandlers.clear()
        legChangeHandlers.add(handler)
    }

    fun addLegChangeListener(handler: (Int) -> Unit) {
        legChangeHandlers.add(handler)
    }

    fun removeLegChangeListener(handler: (Int) -> Unit) {
        legChangeHandlers.remove(handler)
    }

    @VisibleForTesting internal fun replaceAATDelegateForTesting(delegate: AATCoordinatorDelegate) { aatDelegate = delegate }
    @VisibleForTesting internal fun replaceRacingDelegateForTesting(delegate: RacingCoordinatorDelegate) { racingDelegate = delegate }

    fun checkAATAreaTap(lat: Double, lon: Double): Pair<Int, Any>? = if (_taskType.value == TaskType.AAT) aatDelegate.checkAreaTap(lat, lon) else null
    fun enterAATEditMode(waypointIndex: Int) { if (_taskType.value == TaskType.AAT) aatDelegate.enterEditMode(waypointIndex) }
    fun exitAATEditMode() { if (_taskType.value == TaskType.AAT) aatDelegate.exitEditMode() }
    fun isInAATEditMode(): Boolean = _taskType.value == TaskType.AAT && aatDelegate.isInEditMode()
    fun getAATEditWaypointIndex(): Int? = if (_taskType.value == TaskType.AAT) aatDelegate.editWaypointIndex() else null

}
