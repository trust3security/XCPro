package com.example.xcpro.tasks

import androidx.annotation.VisibleForTesting
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.aat.models.AATFinishPointType
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.domain.engine.AATTaskEngine
import com.example.xcpro.tasks.domain.engine.RacingTaskEngine
import com.example.xcpro.tasks.domain.persistence.TaskEnginePersistenceService
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import com.example.xcpro.tasks.racing.UpdateRacingFinishRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingStartRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingValidationRulesCommand
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEventType
import com.example.xcpro.tasks.racing.toRacingWaypoints
import kotlinx.coroutines.CoroutineScope
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
    private val aatTaskManager: AATTaskManager,
    private val coordinatorScope: CoroutineScope
) {
    private var racingDelegate = createRacingDelegate()
    private var aatDelegate = createAATDelegate()
    private var proximityHandler: ((Boolean, Boolean) -> Unit)? = null
    private val legChangeHandlers = LinkedHashSet<(Int) -> Unit>()
    private val racingAdvanceState = RacingAdvanceState()

    private val _taskType = MutableStateFlow(TaskType.RACING)
    val taskType: TaskType get() = _taskType.value
    val taskTypeFlow: StateFlow<TaskType> = _taskType.asStateFlow()
    private val _aatEditWaypointIndex = MutableStateFlow<Int?>(null)
    val aatEditWaypointIndexFlow: StateFlow<Int?> = _aatEditWaypointIndex.asStateFlow()
    private val _taskSnapshot = MutableStateFlow(buildSnapshot())
    val taskSnapshotFlow: StateFlow<TaskRuntimeSnapshot> = _taskSnapshot.asStateFlow()
    private val _racingAdvanceSnapshot = MutableStateFlow(racingAdvanceState.snapshot())
    val racingAdvanceSnapshotFlow: StateFlow<RacingAdvanceState.Snapshot> = _racingAdvanceSnapshot.asStateFlow()
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

    private fun publishRacingAdvanceSnapshot() {
        _racingAdvanceSnapshot.value = racingAdvanceState.snapshot()
    }

    private fun createRacingDelegate() = RacingCoordinatorDelegate(
        taskManager = racingTaskManager,
        log = ::log
    )

    private fun createAATDelegate() = AATCoordinatorDelegate(
        taskManager = aatTaskManager,
        log = ::log
    )

    private fun currentCoreTask(taskType: TaskType = _taskType.value): Task =
        if (taskType == TaskType.RACING) racingTaskManager.getCoreTask() else aatTaskManager.getCoreTask()

    private fun currentActiveLeg(taskType: TaskType = _taskType.value): Int =
        if (taskType == TaskType.RACING) racingTaskManager.currentLeg else aatTaskManager.currentLeg

    /**
     * Canonical synchronous cross-feature read seam.
     *
     * Callers outside `feature:tasks` must derive task and active-leg state
     * from this snapshot instead of reading lower manager task/leg fields
     * directly.
     */
    fun currentSnapshot(): TaskRuntimeSnapshot = taskSnapshotFlow.value

    private fun buildSnapshot(taskType: TaskType = _taskType.value): TaskRuntimeSnapshot =
        TaskRuntimeSnapshot(
            taskType = taskType,
            task = currentCoreTask(taskType),
            activeLeg = currentActiveLeg(taskType)
        )

    private fun clearAatEditMode() {
        if (aatDelegate.isInEditMode()) {
            aatDelegate.exitEditMode()
        }
        _aatEditWaypointIndex.value = null
    }

    private fun syncAatEditModeState(taskType: TaskType = _taskType.value) {
        if (taskType != TaskType.AAT) {
            _aatEditWaypointIndex.value = null
            return
        }
        val editWaypointIndex = aatDelegate.editWaypointIndex()
        val waypointCount = aatTaskManager.getCoreTask().waypoints.size
        val validEditWaypointIndex = editWaypointIndex?.takeIf { it in 0 until waypointCount }
        if (editWaypointIndex != validEditWaypointIndex && aatDelegate.isInEditMode()) {
            aatDelegate.exitEditMode()
        }
        _aatEditWaypointIndex.value = validEditWaypointIndex
    }

    private fun updateTaskSnapshot(taskType: TaskType = _taskType.value) {
        syncAatEditModeState(taskType)
        _taskSnapshot.value = buildSnapshot(taskType)
    }

    private fun publishTaskMutation(taskType: TaskType = _taskType.value) {
        persistenceBridge.syncAndAutosave(taskType)
        updateTaskSnapshot(taskType)
    }

    fun switchToTaskType(newTaskType: TaskType) {
        if (newTaskType == _taskType.value) {
            log("Already using ${newTaskType.name} task type"); return
        }
        val sourceTask = currentCoreTask()
        val hasWaypoints = sourceTask.waypoints.isNotEmpty()
        log("Switching from ${_taskType.value.name} to ${newTaskType.name} (preserveWaypoints=$hasWaypoints)")

        clearCurrentTask()
        clearAatEditMode()
        _taskType.value = newTaskType

        if (hasWaypoints) {
            withManager(
                newTaskType,
                racingBlock = { initializeFromCoreTask(sourceTask) },
                aatBlock = { initializeFromCoreTask(sourceTask) }
            )
            log("Preserved ${sourceTask.waypoints.size} waypoints for ${newTaskType.name} task")
        } else {
            log("No waypoints to preserve during task switch")
        }
        persistenceBridge.persistTaskType(newTaskType)
        persistenceBridge.syncAllAndAutosave()
        updateTaskSnapshot(newTaskType)
    }

    fun initializeTask(waypoints: List<SearchWaypoint>) =
        withCurrentManager(
            racingBlock = { initializeRacingTask(waypoints) },
            aatBlock = { initializeAATTask(waypoints) }
        ).also { publishTaskMutation() }

    fun addWaypoint(searchWaypoint: SearchWaypoint) =
        withCurrentManager(
            racingBlock = { addRacingWaypoint(searchWaypoint) },
            aatBlock = { addAATWaypoint(searchWaypoint) }
        ).also { publishTaskMutation() }

    fun removeWaypoint(index: Int) =
        withCurrentManager(
            racingBlock = { removeRacingWaypoint(index) },
            aatBlock = { removeAATWaypoint(index) }
        ).also { publishTaskMutation() }

    fun clearTask() = withCurrentManager(
        racingBlock = { clearRacingTask() },
        aatBlock = { clearAATTask() }
    ).also { publishTaskMutation() }

    fun getTaskSummary(): String = withCurrentManager(racingBlock = { getRacingTaskSummary() }, aatBlock = { getAATTaskSummary() })

    fun isTaskValid(): Boolean = withCurrentManager(racingBlock = { isRacingTaskValid() }, aatBlock = { isAATTaskValid() })

    fun reorderWaypoints(fromIndex: Int, toIndex: Int) =
        withCurrentManager(
            racingBlock = { reorderRacingWaypoints(fromIndex, toIndex) },
            aatBlock = { reorderAATWaypoints(fromIndex, toIndex) }
        ).also { publishTaskMutation() }

    fun updateWaypointPointType(update: RacingWaypointTypeUpdate) {
        if (_taskType.value != TaskType.RACING) {
            log("Cannot update racing point type - current task type is ${_taskType.value}")
            return
        }
        racingTaskManager.updateRacingWaypointType(update)
        publishTaskMutation()
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
    ) = updateWaypointPointType(
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

    fun replaceWaypoint(index: Int, newWaypoint: SearchWaypoint) =
        withCurrentManager(
            racingBlock = { replaceRacingWaypoint(index, newWaypoint) },
            aatBlock = { replaceAATWaypoint(index, newWaypoint) }
        ).also { publishTaskMutation() }

    suspend fun loadSavedTasks() {
        clearAatEditMode()
        persistenceBridge.loadSavedTasks()
        updateTaskSnapshot()
    }

    fun advanceToNextLeg() {
        val before = currentActiveLeg()
        withCurrentManager(racingBlock = { advanceToNextLeg() }, aatBlock = { advanceToNextLeg() })
        val after = currentActiveLeg()
        if (after != before) {
            updateTaskSnapshot()
            legChangeHandlers.forEach { handler -> handler.invoke(after) }
        }
    }

    fun goToPreviousLeg() {
        val before = currentActiveLeg()
        withCurrentManager(racingBlock = { goToPreviousLeg() }, aatBlock = { goToPreviousLeg() })
        val after = currentActiveLeg()
        if (after != before) {
            updateTaskSnapshot()
            legChangeHandlers.forEach { handler -> handler.invoke(after) }
        }
    }
    fun setActiveLeg(index: Int) = withCurrentManager(
        racingBlock = { setRacingLeg(index) },
        aatBlock = { setAATLeg(index) }
    ).also {
        updateTaskSnapshot()
        legChangeHandlers.forEach { handler -> handler.invoke(currentActiveLeg()) }
    }

    fun calculateTaskDistanceForTaskMeters(task: Task): Double {
        if (task.waypoints.size < 2) {
            return 0.0
        }
        val delegate = currentDelegate()
        return task.waypoints
            .zipWithNext()
            .sumOf { (from, to) -> delegate.calculateSegmentDistanceMeters(from, to) }
    }

    fun calculateSimpleSegmentDistanceMeters(from: TaskWaypoint, to: TaskWaypoint): Double =
        currentDelegate().calculateSegmentDistanceMeters(from, to)

    fun calculateDistanceToCurrentWaypointMeters(currentLat: Double, currentLon: Double): Double? = when (_taskType.value) {
        TaskType.RACING -> racingTaskManager.calculateDistanceToCurrentWaypointEntryMeters(currentLat, currentLon)
        TaskType.AAT -> aatTaskManager.calculateDistanceToCurrentTargetPointMeters(currentLat, currentLon)
    }

    fun calculateOptimalStartLineCrossingPoint(startWaypoint: TaskWaypoint, nextWaypoint: TaskWaypoint): Pair<Double, Double> {
        if (_taskType.value == TaskType.RACING) {
            racingTaskManager.getCoreTask().toRacingWaypoints().firstOrNull { it.id == startWaypoint.id }?.let { wp ->
                val lineWidthMeters = wp.gateWidthMeters
                return racingTaskManager.calculateOptimalLineCrossingPoint(
                    wp.lat,
                    wp.lon,
                    nextWaypoint.lat,
                    nextWaypoint.lon,
                    lineWidthMeters
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
        return persistenceBridge.loadTask(taskName).also { loaded ->
            if (loaded) {
                clearAatEditMode()
                updateTaskSnapshot()
            }
        }
    }

    suspend fun deleteTask(taskName: String): Boolean {
        return persistenceBridge.deleteTask(taskName)
    }

    fun setTaskType(taskType: TaskType) = switchToTaskType(taskType)

    @VisibleForTesting
    fun setTaskTypeForTesting(taskType: TaskType) {
        clearAatEditMode()
        _taskType.value = taskType
        updateTaskSnapshot(taskType)
    }

    fun racingAdvanceSnapshot(): RacingAdvanceState.Snapshot = _racingAdvanceSnapshot.value

    fun setRacingAdvanceMode(mode: RacingAdvanceState.Mode) {
        racingAdvanceState.setMode(mode)
        publishRacingAdvanceSnapshot()
    }

    fun setRacingAdvanceArmed(armed: Boolean) {
        racingAdvanceState.setArmed(armed)
        publishRacingAdvanceSnapshot()
    }

    fun toggleRacingAdvanceArmed(): Boolean {
        val armed = racingAdvanceState.toggleArmed()
        publishRacingAdvanceSnapshot()
        return armed
    }

    fun shouldRacingAdvance(eventType: RacingNavigationEventType): Boolean =
        racingAdvanceState.shouldAdvance(eventType)

    fun resetRacingAdvanceToStartPhase() {
        racingAdvanceState.resetToStartPhase()
        publishRacingAdvanceSnapshot()
    }

    fun onRacingStartAccepted() {
        racingAdvanceState.onStartAdvanced()
        publishRacingAdvanceSnapshot()
    }

    fun restoreRacingAdvanceSnapshot(snapshot: RacingAdvanceState.Snapshot) {
        racingAdvanceState.restore(snapshot)
        publishRacingAdvanceSnapshot()
    }

    fun updateAATWaypointPointType(update: AATWaypointTypeUpdate) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot update AAT point type - current task type is ${_taskType.value}"); return
        }
        aatDelegate.updateWaypointPointTypeMeters(update)
        publishTaskMutation()
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
    ) = updateAATWaypointPointType(
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

    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot update AAT target point - current task type is ${_taskType.value}"); return
        }
        aatDelegate.updateTargetPoint(index, lat, lon)
        publishTaskMutation()
    }

    fun setAATTargetParam(index: Int, targetParam: Double) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot update AAT target param - current task type is ${_taskType.value}"); return
        }
        aatDelegate.updateTargetParam(index, targetParam)
        publishTaskMutation()
    }

    fun toggleAATTargetLock(index: Int) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot toggle AAT target lock - current task type is ${_taskType.value}"); return
        }
        aatDelegate.toggleTargetLock(index)
        publishTaskMutation()
    }

    fun setAATTargetLock(index: Int, locked: Boolean) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot set AAT target lock - current task type is ${_taskType.value}"); return
        }
        aatDelegate.setTargetLock(index, locked)
        publishTaskMutation()
    }

    fun applyAATTargetState(
        index: Int,
        targetParam: Double,
        targetLocked: Boolean,
        targetLat: Double?,
        targetLon: Double?
    ) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot apply AAT target state - current task type is ${_taskType.value}"); return
        }
        aatDelegate.applyTargetState(
            index = index,
            targetParam = targetParam,
            targetLocked = targetLocked,
            targetLat = targetLat,
            targetLon = targetLon
        )
        publishTaskMutation()
    }

    fun updateAATParameters(minimumTime: java.time.Duration, maximumTime: java.time.Duration) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot update AAT parameters - current task type is ${_taskType.value}"); return
        }
        aatDelegate.updateParameters(minimumTime, maximumTime)
        publishTaskMutation()
    }

    fun updateAATArea(index: Int, radiusMeters: Double) {
        if (_taskType.value != TaskType.AAT) {
            log("Cannot update AAT area - current task type is ${_taskType.value}"); return
        }
        aatDelegate.updateArea(index, radiusMeters)
        publishTaskMutation()
    }

    internal fun updateRacingStartRules(command: UpdateRacingStartRulesCommand) {
        if (_taskType.value != TaskType.RACING) {
            log("Cannot update racing start rules - current task type is ${_taskType.value}")
            return
        }
        racingTaskManager.updateRacingStartRules(command)
        publishTaskMutation()
    }

    internal fun updateRacingFinishRules(command: UpdateRacingFinishRulesCommand) {
        if (_taskType.value != TaskType.RACING) {
            log("Cannot update racing finish rules - current task type is ${_taskType.value}")
            return
        }
        racingTaskManager.updateRacingFinishRules(command)
        publishTaskMutation()
    }

    internal fun updateRacingValidationRules(command: UpdateRacingValidationRulesCommand) {
        if (_taskType.value != TaskType.RACING) {
            log("Cannot update racing validation rules - current task type is ${_taskType.value}")
            return
        }
        racingTaskManager.updateRacingValidationRules(command)
        publishTaskMutation()
    }

    fun getRacingValidationProfile(): RacingTaskStructureRules.Profile =
        racingTaskManager.getRacingValidationProfile()

    private fun clearCurrentTask() {
        currentDelegate().clearTask()
    }

    fun setProximityHandler(handler: (Boolean, Boolean) -> Unit) {
        proximityHandler = handler
    }

    fun clearProximityHandler() {
        proximityHandler = null
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

    fun checkAATAreaTap(lat: Double, lon: Double): Pair<Int, AATWaypoint>? =
        if (_taskType.value == TaskType.AAT) aatDelegate.checkAreaTap(lat, lon) else null
    fun enterAATEditMode(waypointIndex: Int) {
        if (_taskType.value != TaskType.AAT) {
            return
        }
        aatDelegate.enterEditMode(waypointIndex)
        syncAatEditModeState()
    }

    fun exitAATEditMode() {
        if (_taskType.value != TaskType.AAT) {
            _aatEditWaypointIndex.value = null
            return
        }
        aatDelegate.exitEditMode()
        _aatEditWaypointIndex.value = null
    }

    fun isInAATEditMode(): Boolean = _aatEditWaypointIndex.value != null
    fun getAATEditWaypointIndex(): Int? = _aatEditWaypointIndex.value

}
