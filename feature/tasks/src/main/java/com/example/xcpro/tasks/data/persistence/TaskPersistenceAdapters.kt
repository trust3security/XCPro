package com.example.xcpro.tasks.data.persistence

import android.content.Context
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.RacingWaypointCustomParams
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TASK_TYPE_PREFS = "task_coordinator_prefs"
private const val KEY_CURRENT_TASK_TYPE = "current_task_type"
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

    private val storage = AATCanonicalTaskStorage.create(context)

    override suspend fun listTaskNames(): List<String> =
        storage.listTaskNames()

    override suspend fun loadAutosavedTask(): Task? =
        storage.loadAutosavedTask()

    override suspend fun loadTask(taskName: String): Task? =
        storage.loadTask(taskName)

    override suspend fun saveTask(taskName: String, task: Task): Boolean {
        if (taskName == AUTOSAVE_SLOT) {
            storage.saveAutosavedTask(task)
            return true
        }
        return storage.saveTask(taskName, task)
    }

    override suspend fun deleteTask(taskName: String): Boolean {
        if (taskName == AUTOSAVE_SLOT) {
            storage.clearAutosavedTask()
            return true
        }
        return storage.deleteTask(taskName)
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
        val racingParams = RacingWaypointCustomParams.from(waypoint.customParameters)

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
            customGateWidthMeters = waypoint.resolvedCustomRadiusMeters(),
            keyholeInnerRadiusMeters = racingParams.keyholeInnerRadiusMeters,
            keyholeAngle = racingParams.keyholeAngle,
            faiQuadrantOuterRadiusMeters = racingParams.faiQuadrantOuterRadiusMeters
        )
    }
    return SimpleRacingTask(
        id = deterministicFallbackId(prefix = "racing"),
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
                customRadius = null,
                customRadiusMeters = waypoint.gateWidthMeters,
                customPointType = when (waypoint.role) {
                    RacingWaypointRole.START -> waypoint.startPointType.name
                    RacingWaypointRole.TURNPOINT -> waypoint.turnPointType.name
                    RacingWaypointRole.FINISH -> waypoint.finishPointType.name
                },
                customParameters = RacingWaypointCustomParams(
                    keyholeInnerRadiusMeters = waypoint.keyholeInnerRadiusMeters,
                    keyholeAngle = waypoint.keyholeAngle,
                    faiQuadrantOuterRadiusMeters = waypoint.faiQuadrantOuterRadiusMeters
                ).toMap()
            )
        }
    )
}

internal fun Task.deterministicFallbackId(prefix: String): String {
    if (id.isNotBlank()) {
        return id
    }
    val fingerprint = buildString {
        waypoints.forEachIndexed { index, waypoint ->
            append(index)
            append('|')
            append(waypoint.id.trim())
            append('|')
            append(waypoint.title.trim())
            append('|')
            append(String.format(Locale.US, "%.6f", waypoint.lat))
            append('|')
            append(String.format(Locale.US, "%.6f", waypoint.lon))
            append('|')
            append(waypoint.role.name)
            append(';')
        }
    }
    val suffix = fingerprint.hashCode().toUInt().toString(16).padStart(8, '0')
    return "${prefix}_$suffix"
}
