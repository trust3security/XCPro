package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.PersistedOzParams
import com.example.xcpro.tasks.core.TargetStateCustomParams
import com.example.xcpro.tasks.core.TaskWaypointParamKeys
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
import com.google.gson.Gson

/**
 * Lossier-than-domain but faithful enough JSON snapshot for QR/export.
 * AI-NOTE: CUP cannot carry OZ/targets; this serializer is the SSOT for full-fidelity round-trips.
 */
object TaskPersistSerializer {
    private val gson = Gson()

    data class PersistedTask(
        val taskType: TaskType,
        val waypoints: List<PersistedWaypoint>
    )

    data class PersistedWaypoint(
        val id: String,
        val title: String,
        val subtitle: String,
        val lat: Double,
        val lon: Double,
        val role: WaypointRole,
        val ozType: String?,
        val ozParams: Map<String, Double?> = emptyMap(),
        val targetParam: Double? = null,
        val targetLocked: Boolean? = null,
        val targetLat: Double? = null,
        val targetLon: Double? = null
    )

    fun serialize(task: Task, taskType: TaskType, targets: List<TaskTargetSnapshot>): String {
        val wpDtos = task.waypoints.mapIndexed { idx, wp ->
            val target = targets.getOrNull(idx)
            PersistedWaypoint(
                id = wp.id,
                title = wp.title,
                subtitle = wp.subtitle,
                lat = wp.lat,
                lon = wp.lon,
                role = wp.role,
                ozType = defaultOzType(taskType, wp.role),
                ozParams = defaultOzParams(taskType, wp.role),
                targetParam = target?.targetParam,
                targetLocked = target?.isLocked,
                targetLat = target?.target?.lat,
                targetLon = target?.target?.lon
            )
        }
        return gson.toJson(PersistedTask(taskType, wpDtos))
    }

    fun deserialize(json: String): PersistedTask =
        gson.fromJson(json, PersistedTask::class.java)

    /**
     * Convert persisted payload back into a core Task plus target overlays.
     */
    fun toTask(persisted: PersistedTask): Pair<Task, List<TaskTargetSnapshot>> {
        val task = Task(
            id = "imported",
            waypoints = persisted.waypoints.mapIndexed { index, p ->
                TaskWaypoint(
                    id = p.id.ifBlank { "wp_$index" },
                    title = p.title,
                    subtitle = p.subtitle,
                    lat = p.lat,
                    lon = p.lon,
                    role = p.role,
                    customParameters = mutableMapOf<String, Any>().apply {
                        TargetStateCustomParams(
                            targetParam = p.targetParam ?: 0.5,
                            targetLocked = p.targetLocked ?: false,
                            targetLat = p.targetLat ?: p.lat,
                            targetLon = p.targetLon ?: p.lon
                        ).applyTo(this)
                        this[TaskWaypointParamKeys.OZ_TYPE] = p.ozType ?: ""
                        this[TaskWaypointParamKeys.OZ_PARAMS] = p.ozParams as Any
                    }
                )
            }
        )

        val targets = persisted.waypoints.mapIndexed { idx, p ->
            TaskTargetSnapshot(
                index = idx,
                id = p.id,
                name = p.title,
                allowsTarget = true,
                targetParam = p.targetParam ?: 0.5,
                isLocked = p.targetLocked ?: false,
                target = if (p.targetLat != null && p.targetLon != null)
                    com.example.xcpro.tasks.domain.model.GeoPoint(p.targetLat, p.targetLon) else null
            )
        }
        return task to targets
    }

    private fun defaultOzType(taskType: TaskType, role: WaypointRole): String = when (role) {
        WaypointRole.START -> "LINE"
        WaypointRole.FINISH -> "CYLINDER"
        WaypointRole.TURNPOINT, WaypointRole.OPTIONAL ->
            if (taskType == TaskType.AAT) "SEGMENT" else "CYLINDER"
    }

    private fun defaultOzParams(taskType: TaskType, role: WaypointRole): Map<String, Double?> = when (role) {
        WaypointRole.START -> PersistedOzParams(
            lengthMeters = 1000.0,
            widthMeters = 200.0
        ).toMap()
        WaypointRole.FINISH -> PersistedOzParams(
            radiusMeters = 3000.0
        ).toMap()
        WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> {
            if (taskType == TaskType.AAT) PersistedOzParams(
                radiusMeters = 5000.0,
                outerRadiusMeters = 5000.0,
                innerRadiusMeters = 0.0,
                angleDeg = 90.0
            ).toMap()
            else PersistedOzParams(
                radiusMeters = 500.0
            ).toMap()
        }
    }
}
