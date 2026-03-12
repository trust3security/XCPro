package com.example.xcpro.tasks

import com.example.xcpro.tasks.aat.models.AATRadiusAuthority
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.PersistedOzParams
import com.example.xcpro.tasks.core.TargetStateCustomParams
import com.example.xcpro.tasks.core.TaskWaypointParamKeys
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.model.GeoPoint
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
import com.google.gson.Gson

/**
 * Canonical JSON snapshot for QR/export.
 * AI-NOTE: CUP cannot carry OZ/targets; this serializer is the SSOT for full-fidelity round-trips.
 */
object TaskPersistSerializer {
    private val gson = Gson()

    data class PersistedTask(
        val taskId: String? = null,
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
        val customRadius: Double? = null,
        val customRadiusMeters: Double? = null,
        val customPointType: String? = null,
        val customParameters: Map<String, Any?> = emptyMap(),
        val targetParam: Double? = null,
        val targetLocked: Boolean? = null,
        val targetLat: Double? = null,
        val targetLon: Double? = null
    )

    fun serialize(task: Task, taskType: TaskType, targets: List<TaskTargetSnapshot>): String {
        val targetsByIndex = targets.associateBy { it.index }
        val targetsById = targets.associateBy { it.id }
        val wpDtos = task.waypoints.mapIndexed { idx, wp ->
            val customOzType = (wp.customParameters[TaskWaypointParamKeys.OZ_TYPE] as? String)?.takeIf { it.isNotBlank() }
            val customOzParams = extractOzParams(wp.customParameters[TaskWaypointParamKeys.OZ_PARAMS])
            val ozType = customOzType ?: defaultOzType(taskType, wp.role)
            val ozParams = if (customOzParams.isNotEmpty()) customOzParams else defaultOzParams(taskType, wp.role)

            val target = targetsByIndex[idx] ?: targetsById[wp.id] ?: targets.getOrNull(idx)
            val waypointHasTargetState = hasExplicitTargetState(wp.customParameters)
            val waypointTargetState = TargetStateCustomParams.from(wp.customParameters)

            PersistedWaypoint(
                id = wp.id,
                title = wp.title,
                subtitle = wp.subtitle,
                lat = wp.lat,
                lon = wp.lon,
                role = wp.role,
                ozType = ozType,
                ozParams = ozParams,
                customRadius = wp.resolvedCustomRadiusMeters()?.div(1000.0),
                customRadiusMeters = wp.resolvedCustomRadiusMeters(),
                customPointType = wp.customPointType,
                customParameters = sanitizeCustomParameters(wp.customParameters),
                targetParam = target?.targetParam ?: if (waypointHasTargetState) waypointTargetState.targetParam else null,
                targetLocked = target?.isLocked ?: if (waypointHasTargetState) waypointTargetState.targetLocked else null,
                targetLat = target?.target?.lat ?: if (waypointHasTargetState) waypointTargetState.targetLat else null,
                targetLon = target?.target?.lon ?: if (waypointHasTargetState) waypointTargetState.targetLon else null
            )
        }
        return gson.toJson(PersistedTask(taskId = task.id, taskType = taskType, waypoints = wpDtos))
    }

    fun deserialize(json: String): PersistedTask =
        gson.fromJson(json, PersistedTask::class.java)

    /**
     * Convert persisted payload back into a core Task plus target overlays.
     */
    fun toTask(persisted: PersistedTask): Pair<Task, List<TaskTargetSnapshot>> {
        val waypoints = mutableListOf<TaskWaypoint>()
        val targets = mutableListOf<TaskTargetSnapshot>()

        persisted.waypoints.forEachIndexed { idx, p ->
            val waypointId = p.id.ifBlank { "wp_$idx" }
            val customParameters = restoreCustomParameters(p.customParameters).toMutableMap()
            val ozType = p.ozType?.takeIf { it.isNotBlank() } ?: defaultOzType(persisted.taskType, p.role)
            val ozParams = if (p.ozParams.isNotEmpty()) p.ozParams else defaultOzParams(persisted.taskType, p.role)
            customParameters[TaskWaypointParamKeys.OZ_TYPE] = ozType
            customParameters[TaskWaypointParamKeys.OZ_PARAMS] = compactOzParams(ozParams)

            val targetFallback = TargetStateCustomParams.from(
                source = customParameters,
                fallbackTargetParam = p.targetParam ?: 0.5,
                fallbackTargetLocked = p.targetLocked ?: false
            )
            val resolvedTargetParam = p.targetParam ?: targetFallback.targetParam
            val resolvedTargetLocked = p.targetLocked ?: targetFallback.targetLocked
            val resolvedTargetLat = p.targetLat ?: targetFallback.targetLat ?: p.lat
            val resolvedTargetLon = p.targetLon ?: targetFallback.targetLon ?: p.lon

            TargetStateCustomParams(
                targetParam = resolvedTargetParam,
                targetLocked = resolvedTargetLocked,
                targetLat = resolvedTargetLat,
                targetLon = resolvedTargetLon
            ).applyTo(customParameters)

            val resolvedCustomRadiusMeters = p.customRadiusMeters?.takeIf { it > 0.0 }
                ?: p.customRadius?.takeIf { it > 0.0 }?.times(1000.0)

            waypoints += TaskWaypoint(
                id = waypointId,
                title = p.title,
                subtitle = p.subtitle,
                lat = p.lat,
                lon = p.lon,
                role = p.role,
                customRadius = null,
                customRadiusMeters = resolvedCustomRadiusMeters,
                customPointType = p.customPointType,
                customParameters = customParameters
            )

            val allowsTarget = persisted.taskType == TaskType.AAT &&
                (p.role == WaypointRole.TURNPOINT || p.role == WaypointRole.OPTIONAL)
            val targetPoint = if (resolvedTargetLat.isFinite() && resolvedTargetLon.isFinite()) {
                GeoPoint(resolvedTargetLat, resolvedTargetLon)
            } else {
                null
            }

            targets += TaskTargetSnapshot(
                index = idx,
                id = waypointId,
                name = p.title,
                allowsTarget = allowsTarget,
                targetParam = resolvedTargetParam,
                isLocked = resolvedTargetLocked,
                target = targetPoint
            )
        }

        val task = Task(
            id = persisted.taskId?.ifBlank { "imported" } ?: "imported",
            waypoints = waypoints
        )

        return task to targets
    }

    private fun hasExplicitTargetState(parameters: Map<String, Any>): Boolean {
        return parameters.containsKey(TaskWaypointParamKeys.TARGET_PARAM) ||
            parameters.containsKey(TaskWaypointParamKeys.TARGET_LOCKED) ||
            parameters.containsKey(TaskWaypointParamKeys.TARGET_LAT) ||
            parameters.containsKey(TaskWaypointParamKeys.TARGET_LON)
    }

    private fun extractOzParams(value: Any?): Map<String, Double?> {
        val map = value as? Map<*, *> ?: return emptyMap()
        return map.entries
            .mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                val number = (entry.value as? Number)?.toDouble()
                key to number
            }
            .toMap()
    }

    private fun compactOzParams(source: Map<String, Double?>): Map<String, Double> {
        return source.mapNotNull { (key, value) ->
            value?.let { key to it }
        }.toMap()
    }

    private fun sanitizeCustomParameters(source: Map<String, Any>): Map<String, Any?> {
        return source.mapValues { (_, value) -> sanitizeJsonValue(value) }
    }

    private fun sanitizeJsonValue(value: Any?): Any? = when (value) {
        null -> null
        is String, is Number, is Boolean -> value
        is Map<*, *> -> value.entries
            .mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                key to sanitizeJsonValue(entry.value)
            }
            .toMap()
        is List<*> -> value.map { sanitizeJsonValue(it) }
        else -> value.toString()
    }

    private fun restoreCustomParameters(source: Map<String, Any?>): Map<String, Any> {
        return source.entries.mapNotNull { (key, value) ->
            restoreJsonValue(value)?.let { key to it }
        }.toMap()
    }

    private fun restoreJsonValue(value: Any?): Any? = when (value) {
        null -> null
        is String, is Number, is Boolean -> value
        is Map<*, *> -> value.entries
            .mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                restoreJsonValue(entry.value)?.let { key to it }
            }
            .toMap()
        is List<*> -> value.mapNotNull { restoreJsonValue(it) }
        else -> null
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
            radiusMeters = if (taskType == TaskType.AAT) {
                AATRadiusAuthority.getRadiusMetersForRole(AATWaypointRole.FINISH)
            } else {
                3000.0
            }
        ).toMap()
        WaypointRole.TURNPOINT, WaypointRole.OPTIONAL -> {
            if (taskType == TaskType.AAT) {
                val defaultRadiusMeters = AATRadiusAuthority.getRadiusMetersForRole(AATWaypointRole.TURNPOINT)
                PersistedOzParams(
                    radiusMeters = defaultRadiusMeters,
                    outerRadiusMeters = defaultRadiusMeters,
                    innerRadiusMeters = 0.0,
                    angleDeg = 90.0
                ).toMap()
            } else PersistedOzParams(
                radiusMeters = 500.0
            ).toMap()
        }
    }
}
