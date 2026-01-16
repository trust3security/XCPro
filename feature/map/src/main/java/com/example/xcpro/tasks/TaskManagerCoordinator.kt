package com.example.xcpro.tasks

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.racing.RacingTaskManager
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.maps.MapLibreMap

/**
 * Routes calls to the Racing or AAT task managers; contains no task-specific math.
 */
class TaskManagerCoordinator(val context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("task_coordinator_prefs", Context.MODE_PRIVATE)
    private val racingTaskManager = RacingTaskManager(context)
    private val aatTaskManager = AATTaskManager(context)
    private val persistenceStore = TaskCoordinatorPersistence(
        prefs,
        loadRacingTask = { racingTaskManager.loadRacingTask() != null },
        loadAATTask = { aatTaskManager.loadAATTask() != null },
        log = ::log
    )

    private var mapInstanceRef: WeakReference<MapLibreMap?> = WeakReference(null)
    private var racingDelegate = createRacingDelegate()
    private var aatDelegate = createAATDelegate()
    private var proximityHandler: ((Boolean, Boolean) -> Unit)? = null
    private val legChangeHandlers = LinkedHashSet<(Int) -> Unit>()

    private val _taskType = MutableStateFlow(TaskType.RACING)
    val taskType: TaskType get() = _taskType.value
    val taskTypeFlow: StateFlow<TaskType> = _taskType.asStateFlow()

    private inline fun <T> withCurrentManager(
        racingBlock: RacingTaskManager.() -> T,
        aatBlock: AATTaskManager.() -> T
    ): T = if (_taskType.value == TaskType.RACING) racingTaskManager.racingBlock() else aatTaskManager.aatBlock()

    private inline fun <T> withManager(
        type: TaskType,
        racingBlock: RacingTaskManager.() -> T,
        aatBlock: AATTaskManager.() -> T
    ): T = if (type == TaskType.RACING) racingTaskManager.racingBlock() else aatTaskManager.aatBlock()

    private fun log(message: String) = println("TaskManagerCoordinator: $message")

    private fun currentDelegate(): TaskTypeCoordinatorDelegate = if (_taskType.value == TaskType.RACING) racingDelegate else aatDelegate

    private fun createRacingDelegate() = RacingCoordinatorDelegate(
        taskManager = racingTaskManager,
        mapProvider = { mapInstanceRef.get() },
        log = ::log
    )

    private fun createAATDelegate() = AATCoordinatorDelegate(
        taskManager = aatTaskManager,
        mapProvider = { mapInstanceRef.get() },
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

        clearCurrentTask(mapInstanceRef.get())
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
        persistenceStore.saveTaskType(newTaskType)
    }

    fun initializeTask(waypoints: List<SearchWaypoint>) =
        withCurrentManager(racingBlock = { initializeRacingTask(waypoints) }, aatBlock = { initializeAATTask(waypoints) })

    fun addWaypoint(searchWaypoint: SearchWaypoint) =
        withCurrentManager(racingBlock = { addRacingWaypoint(searchWaypoint) }, aatBlock = { addAATWaypoint(searchWaypoint) })

    fun removeWaypoint(index: Int) =
        withCurrentManager(racingBlock = { removeRacingWaypoint(index) }, aatBlock = { removeAATWaypoint(index) })

    fun plotOnMap(map: MapLibreMap?) = currentDelegate().plotOnMap(map)

    fun clearTask() = withCurrentManager(racingBlock = { clearRacingTask() }, aatBlock = { clearAATTask() })

    fun getTaskSummary(): String = withCurrentManager(racingBlock = { getRacingTaskSummary() }, aatBlock = { getAATTaskSummary() })

    fun isTaskValid(): Boolean = withCurrentManager(racingBlock = { isRacingTaskValid() }, aatBlock = { isAATTaskValid() })

    fun loadTaskType() {
        _taskType.value = persistenceStore.loadTaskType(_taskType.value)
    }

    fun getRacingTaskManager(): RacingTaskManager = racingTaskManager
    fun getAATTaskManager(): AATTaskManager = aatTaskManager

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

    fun getTaskSpecificWaypoint(index: Int): Any? = when (_taskType.value) {
        TaskType.RACING -> racingTaskManager.currentRacingTask.waypoints.getOrNull(index)
        TaskType.AAT -> aatTaskManager.currentAATTask.waypoints.getOrNull(index)
    }

    fun loadSavedTasks() {
        loadTaskType(); val result = persistenceStore.loadSavedTasks()
        log("Finished loading saved tasks (racing=${result.racingLoaded}, aat=${result.aatLoaded})")
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
        racingBlock = { setRacingLeg(index, mapInstanceRef.get()) },
        aatBlock = { setAATLeg(index, mapInstanceRef.get()) }
    ).also { legChangeHandlers.forEach { handler -> handler.invoke(currentLeg) } }

    fun getActiveLeg(): Int = currentLeg

    fun calculateTaskDistanceForTask(task: Task): Double = currentDelegate().calculateDistance()

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

    @Deprecated("Use calculateSimpleSegmentDistance for clarity", ReplaceWith("calculateSimpleSegmentDistance"))
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double = when (_taskType.value) {
        TaskType.RACING -> racingTaskManager.calculateSegmentDistance(lat1, lon1, lat2, lon2)
        TaskType.AAT -> aatTaskManager.calculateSegmentDistance(lat1, lon1, lat2, lon2)
    }

    fun getSavedTasks(context: Context): List<String> = when (_taskType.value) {
        TaskType.RACING -> racingTaskManager.getSavedRacingTasks()
        TaskType.AAT -> aatTaskManager.getSavedAATTasks(context)
    }

    fun saveTask(context: Context, taskName: String): Boolean = when (_taskType.value) {
        TaskType.RACING -> racingTaskManager.saveRacingTask(taskName)
        TaskType.AAT -> aatTaskManager.saveAATTask(context, taskName)
    }

    fun loadTask(context: Context, taskName: String): Boolean = when (_taskType.value) {
        TaskType.RACING -> racingTaskManager.loadRacingTaskFromFile(taskName)
        TaskType.AAT -> aatTaskManager.loadAATTaskFromFile(context, taskName)
    }

    fun deleteTask(context: Context, taskName: String): Boolean = when (_taskType.value) {
        TaskType.RACING -> racingTaskManager.deleteRacingTask(taskName)
        TaskType.AAT -> aatTaskManager.deleteAATTask(context, taskName)
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

    private fun clearCurrentTask(map: MapLibreMap?) {
        currentDelegate().clearFromMap(map)
        currentDelegate().clearTask()
    }

    fun setMapInstance(map: MapLibreMap?) { mapInstanceRef = WeakReference(map) }
    fun getMapInstance(): MapLibreMap? = mapInstanceRef.get()

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
    fun checkAATTargetPointHit(screenX: Float, screenY: Float): Int? = if (_taskType.value == TaskType.AAT) aatDelegate.checkTargetPointHit(screenX, screenY) else null
}
