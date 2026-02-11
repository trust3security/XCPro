package com.example.xcpro.tasks.data.persistence

import android.content.Context
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATFinishPointType
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATRadiusAuthority
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.models.getAuthorityRadius
import com.example.xcpro.tasks.aat.persistence.AATTaskFileIO
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.persistence.AATTaskPersistence
import com.example.xcpro.tasks.domain.persistence.RacingTaskPersistence
import com.example.xcpro.tasks.domain.persistence.TaskTypeSettingsRepository
import com.example.xcpro.tasks.racing.RacingTaskPersistence as LegacyRacingTaskPersistence
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TASK_TYPE_PREFS = "task_coordinator_prefs"
private const val KEY_CURRENT_TASK_TYPE = "current_task_type"
private const val KEY_MIN_TIME_SECONDS = "aatMinimumTimeSeconds"
private const val KEY_MAX_TIME_SECONDS = "aatMaximumTimeSeconds"

@Singleton
class SharedPrefsTaskTypeSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) : TaskTypeSettingsRepository {

    private val prefs = context.getSharedPreferences(TASK_TYPE_PREFS, Context.MODE_PRIVATE)
    private val _taskTypeFlow = MutableStateFlow(readTaskType(TaskType.RACING))
    override val taskTypeFlow: StateFlow<TaskType> = _taskTypeFlow.asStateFlow()

    override suspend fun loadTaskType(defaultType: TaskType): TaskType {
        val loaded = readTaskType(defaultType)
        _taskTypeFlow.value = loaded
        return loaded
    }

    override suspend fun saveTaskType(taskType: TaskType) {
        prefs.edit().putString(KEY_CURRENT_TASK_TYPE, taskType.name).apply()
        _taskTypeFlow.value = taskType
    }

    private fun readTaskType(defaultType: TaskType): TaskType {
        val stored = prefs.getString(KEY_CURRENT_TASK_TYPE, defaultType.name)
        return runCatching { TaskType.valueOf(stored ?: defaultType.name) }.getOrDefault(defaultType)
    }
}

@Singleton
class RacingTaskPersistenceAdapter @Inject constructor(
    @ApplicationContext context: Context
) : RacingTaskPersistence {

    private val delegate = LegacyRacingTaskPersistence(context)

    override suspend fun listTaskNames(): List<String> =
        delegate.getSavedRacingTasks().map { it.removeSuffix(".cup") }

    override suspend fun loadAutosavedTask(): Task? =
        delegate.loadRacingTask()?.toCoreTask()

    override suspend fun loadTask(taskName: String): Task? =
        delegate.loadRacingTaskFromFile(taskName)?.toCoreTask()

    override suspend fun saveTask(taskName: String, task: Task): Boolean {
        val racingTask = task.toSimpleRacingTask()
        if (taskName == AUTOSAVE_SLOT) {
            delegate.saveRacingTask(racingTask)
            return true
        }
        delegate.saveRacingTask(racingTask)
        return delegate.saveRacingTask(racingTask, taskName)
    }

    override suspend fun deleteTask(taskName: String): Boolean {
        if (taskName == AUTOSAVE_SLOT) {
            delegate.saveRacingTask(SimpleRacingTask(id = "", waypoints = emptyList()))
            return true
        }
        return delegate.deleteRacingTask(taskName)
    }
}

@Singleton
class AATTaskPersistenceAdapter @Inject constructor(
    @ApplicationContext context: Context
) : AATTaskPersistence {

    private val delegate = AATTaskFileIO(context)

    override suspend fun listTaskNames(): List<String> =
        delegate.getSavedTaskFiles().map { it.removeSuffix(".cup") }

    override suspend fun loadAutosavedTask(): Task? =
        delegate.loadFromPreferences()?.toCoreTask()

    override suspend fun loadTask(taskName: String): Task? =
        delegate.loadTaskFromFile(taskName)?.toCoreTask()

    override suspend fun saveTask(taskName: String, task: Task): Boolean {
        val aatTask = task.toSimpleAATTask()
        if (taskName == AUTOSAVE_SLOT) {
            delegate.saveToPreferences(aatTask)
            return true
        }
        delegate.saveToPreferences(aatTask)
        return delegate.saveTaskToFile(aatTask, taskName)
    }

    override suspend fun deleteTask(taskName: String): Boolean {
        if (taskName == AUTOSAVE_SLOT) {
            delegate.saveToPreferences(SimpleAATTask(id = "", waypoints = emptyList()))
            return true
        }
        return delegate.deleteTaskFile(taskName)
    }
}

internal const val AUTOSAVE_SLOT = "__autosave__"

private fun Task.toSimpleRacingTask(): SimpleRacingTask {
    val racingWaypoints = waypoints.mapIndexed { index, waypoint ->
        val role = when {
            waypoints.size == 1 -> RacingWaypointRole.START
            index == 0 -> RacingWaypointRole.START
            index == waypoints.lastIndex -> RacingWaypointRole.FINISH
            else -> RacingWaypointRole.TURNPOINT
        }
        val startType = waypoint.customPointType
            ?.let { runCatching { RacingStartPointType.valueOf(it) }.getOrNull() }
            ?: RacingStartPointType.START_LINE
        val finishType = waypoint.customPointType
            ?.let { runCatching { RacingFinishPointType.valueOf(it) }.getOrNull() }
            ?: RacingFinishPointType.FINISH_CYLINDER
        val turnType = waypoint.customPointType
            ?.let { runCatching { RacingTurnPointType.valueOf(it) }.getOrNull() }
            ?: RacingTurnPointType.TURN_POINT_CYLINDER
        val keyholeInnerRadius = (waypoint.customParameters["keyholeInnerRadius"] as? Number)?.toDouble() ?: 0.5
        val keyholeAngle = (waypoint.customParameters["keyholeAngle"] as? Number)?.toDouble() ?: 90.0
        val faiQuadrantOuterRadius = (waypoint.customParameters["faiQuadrantOuterRadius"] as? Number)?.toDouble() ?: 10.0

        RacingWaypoint.createWithStandardizedDefaults(
            id = waypoint.id,
            title = waypoint.title,
            subtitle = waypoint.subtitle,
            lat = waypoint.lat,
            lon = waypoint.lon,
            role = role,
            startPointType = startType,
            finishPointType = finishType,
            turnPointType = turnType,
            customGateWidth = waypoint.customRadius,
            keyholeInnerRadius = keyholeInnerRadius,
            keyholeAngle = keyholeAngle,
            faiQuadrantOuterRadius = faiQuadrantOuterRadius
        )
    }
    return SimpleRacingTask(
        id = id.ifBlank { UUID.randomUUID().toString() },
        waypoints = racingWaypoints
    )
}

private fun SimpleRacingTask.toCoreTask(): Task {
    return Task(
        id = id.ifBlank { "racing-task" },
        waypoints = waypoints.map { waypoint ->
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
                    RacingWaypointRole.TURNPOINT -> waypoint.turnPointType.name
                    RacingWaypointRole.FINISH -> waypoint.finishPointType.name
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

private fun Task.toSimpleAATTask(): SimpleAATTask {
    val minimumTime = extractDurationFromTask(KEY_MIN_TIME_SECONDS, Duration.ofHours(3))
    val maximumTime = extractDurationFromTask(KEY_MAX_TIME_SECONDS, Duration.ofHours(6))
    val aatWaypoints = waypoints.mapIndexed { index, waypoint ->
        val role = when {
            waypoints.size == 1 -> AATWaypointRole.START
            index == 0 -> AATWaypointRole.START
            index == waypoints.lastIndex -> AATWaypointRole.FINISH
            else -> AATWaypointRole.TURNPOINT
        }
        val startType = waypoint.customPointType
            ?.let { runCatching { AATStartPointType.valueOf(it) }.getOrNull() }
            ?: AATStartPointType.AAT_START_LINE
        val finishType = waypoint.customPointType
            ?.let { runCatching { AATFinishPointType.valueOf(it) }.getOrNull() }
            ?: AATFinishPointType.AAT_FINISH_CYLINDER
        val turnType = waypoint.customPointType
            ?.let { runCatching { AATTurnPointType.valueOf(it) }.getOrNull() }
            ?: AATTurnPointType.AAT_CYLINDER

        val radiusMeters = (waypoint.customParameters["radiusMeters"] as? Number)?.toDouble()
            ?: ((waypoint.customRadius ?: AATRadiusAuthority.getRadiusForRole(role)) * 1000.0)
        val outerRadiusMeters = (waypoint.customParameters["outerRadiusMeters"] as? Number)?.toDouble()
            ?: radiusMeters
        val innerRadiusMeters = (waypoint.customParameters["innerRadiusMeters"] as? Number)?.toDouble() ?: 0.0
        val startAngleDegrees = (waypoint.customParameters["startAngleDegrees"] as? Number)?.toDouble() ?: 0.0
        val endAngleDegrees = (waypoint.customParameters["endAngleDegrees"] as? Number)?.toDouble() ?: 90.0
        val lineWidthMeters = (waypoint.customParameters["lineWidthMeters"] as? Number)?.toDouble() ?: radiusMeters
        val targetLat = (waypoint.customParameters["targetLat"] as? Number)?.toDouble() ?: waypoint.lat
        val targetLon = (waypoint.customParameters["targetLon"] as? Number)?.toDouble() ?: waypoint.lon
        val isTargetPointCustomized = (waypoint.customParameters["isTargetPointCustomized"] as? Boolean)
            ?: (targetLat != waypoint.lat || targetLon != waypoint.lon)

        val areaShape = when (role) {
            AATWaypointRole.START -> if (startType == AATStartPointType.AAT_START_LINE) AATAreaShape.LINE else AATAreaShape.CIRCLE
            AATWaypointRole.FINISH -> if (finishType == AATFinishPointType.AAT_FINISH_LINE) AATAreaShape.LINE else AATAreaShape.CIRCLE
            AATWaypointRole.TURNPOINT -> if (turnType == AATTurnPointType.AAT_CYLINDER) AATAreaShape.CIRCLE else AATAreaShape.SECTOR
        }

        AATWaypoint(
            id = waypoint.id,
            title = waypoint.title,
            subtitle = waypoint.subtitle,
            lat = waypoint.lat,
            lon = waypoint.lon,
            role = role,
            assignedArea = AATAssignedArea(
                shape = areaShape,
                radiusMeters = radiusMeters,
                innerRadiusMeters = innerRadiusMeters,
                outerRadiusMeters = outerRadiusMeters,
                startAngleDegrees = startAngleDegrees,
                endAngleDegrees = endAngleDegrees,
                lineWidthMeters = lineWidthMeters
            ),
            startPointType = startType,
            finishPointType = finishType,
            turnPointType = turnType,
            targetPoint = AATLatLng(targetLat, targetLon),
            isTargetPointCustomized = isTargetPointCustomized
        )
    }
    return SimpleAATTask(
        id = id.ifBlank { UUID.randomUUID().toString() },
        waypoints = aatWaypoints,
        minimumTime = minimumTime,
        maximumTime = maximumTime
    )
}

private fun SimpleAATTask.toCoreTask(): Task {
    return Task(
        id = id.ifBlank { "aat-task" },
        waypoints = waypoints.map { waypoint ->
            val customParameters = mutableMapOf<String, Any>(
                "radiusMeters" to waypoint.assignedArea.radiusMeters,
                "outerRadiusMeters" to waypoint.assignedArea.outerRadiusMeters,
                "innerRadiusMeters" to waypoint.assignedArea.innerRadiusMeters,
                "startAngleDegrees" to waypoint.assignedArea.startAngleDegrees,
                "endAngleDegrees" to waypoint.assignedArea.endAngleDegrees,
                "lineWidthMeters" to waypoint.assignedArea.lineWidthMeters,
                "targetLat" to waypoint.targetPoint.latitude,
                "targetLon" to waypoint.targetPoint.longitude,
                "isTargetPointCustomized" to waypoint.isTargetPointCustomized,
                KEY_MIN_TIME_SECONDS to minimumTime.seconds.toDouble()
            )
            if (maximumTime != null) {
                customParameters[KEY_MAX_TIME_SECONDS] = maximumTime.seconds.toDouble()
            }
            TaskWaypoint(
                id = waypoint.id,
                title = waypoint.title,
                subtitle = waypoint.subtitle,
                lat = waypoint.lat,
                lon = waypoint.lon,
                role = when (waypoint.role) {
                    AATWaypointRole.START -> WaypointRole.START
                    AATWaypointRole.TURNPOINT -> WaypointRole.TURNPOINT
                    AATWaypointRole.FINISH -> WaypointRole.FINISH
                },
                customRadius = waypoint.getAuthorityRadius(),
                customPointType = when (waypoint.role) {
                    AATWaypointRole.START -> waypoint.startPointType.name
                    AATWaypointRole.TURNPOINT -> waypoint.turnPointType.name
                    AATWaypointRole.FINISH -> waypoint.finishPointType.name
                },
                customParameters = customParameters
            )
        }
    )
}

private fun Task.extractDurationFromTask(key: String, fallback: Duration): Duration {
    val first = waypoints.firstOrNull() ?: return fallback
    val seconds = (first.customParameters[key] as? Number)?.toLong() ?: return fallback
    if (seconds <= 0) return fallback
    return Duration.ofSeconds(seconds)
}
