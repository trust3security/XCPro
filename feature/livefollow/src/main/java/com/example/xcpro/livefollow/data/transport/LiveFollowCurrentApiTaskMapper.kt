package com.example.xcpro.livefollow.data.transport

import com.example.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal fun mapCurrentApiTaskUpsertPayload(
    sessionId: String,
    snapshot: LiveFollowTaskSnapshot
): String? {
    val taskName = snapshot.taskName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val turnpoints = JsonArray()
    snapshot.points.forEach { point ->
        if (!point.latitudeDeg.isFinite() || !point.longitudeDeg.isFinite()) {
            return@forEach
        }
        if (point.latitudeDeg !in -90.0..90.0 || point.longitudeDeg !in -180.0..180.0) {
            return@forEach
        }
        val pointName = point.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
        val pointType = point.type?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach

        val turnpoint = JsonObject().apply {
            addProperty("name", pointName)
            addProperty("type", pointType)
            addProperty("lat", point.latitudeDeg)
            addProperty("lon", point.longitudeDeg)
            point.radiusMeters
                ?.takeIf { it.isFinite() && it > 0.0 && it <= 500_000.0 }
                ?.let { addProperty("radius_m", it) }
        }
        turnpoints.add(turnpoint)
    }
    if (turnpoints.size() < 2) {
        return null
    }

    val payloadTask = JsonObject().apply {
        add("turnpoints", turnpoints)
        add("start", startFinishMetadata(turnpoints = turnpoints, index = 0))
        add("finish", startFinishMetadata(turnpoints = turnpoints, index = turnpoints.size() - 1))
    }
    return JsonObject().apply {
        addProperty("session_id", sessionId)
        addProperty("task_name", taskName)
        add("task", payloadTask)
    }.toString()
}

internal fun mapCurrentApiTaskClearPayload(
    sessionId: String
): String {
    return JsonObject().apply {
        addProperty("session_id", sessionId)
        addProperty("clear_task", true)
    }.toString()
}

private fun startFinishMetadata(
    turnpoints: JsonArray,
    index: Int
): JsonObject {
    val turnpoint = turnpoints[index].asJsonObject
    return JsonObject().apply {
        addProperty("type", turnpoint.get("type").asString)
        turnpoint.get("radius_m")?.takeUnless { it.isJsonNull }?.asDouble?.let {
            addProperty("radius_m", it)
        }
    }
}
