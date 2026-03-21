package com.example.xcpro.livefollow.data.transport

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

internal data class CurrentApiSessionStartResponse(
    val sessionId: String,
    val shareCode: String,
    val status: String,
    val writeToken: String
)

internal data class CurrentApiSessionEndResponse(
    val sessionId: String,
    val status: String,
    val endedAtWallMs: Long?
)

internal data class CurrentApiLiveReadResponse(
    val sessionId: String,
    val shareCode: String?,
    val status: String,
    val latest: CurrentApiLivePoint?,
    val positions: List<CurrentApiLivePoint>,
    val hasTask: Boolean
)

internal data class CurrentApiActivePilotsResponse(
    val items: List<CurrentApiActivePilotItem>,
    val generatedAtWallMs: Long?
)

internal data class CurrentApiActivePilotItem(
    val sessionId: String?,
    val shareCode: String,
    val status: String,
    val displayLabel: String?,
    val lastPositionWallMs: Long?,
    val latest: CurrentApiLivePoint?
)

internal data class CurrentApiLivePoint(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMslMeters: Double?,
    val groundSpeedMs: Double?,
    val headingDeg: Double?,
    val fixWallMs: Long?
)

internal fun parseCurrentApiSessionStartResponse(body: String): CurrentApiSessionStartResponse {
    val root = parseCurrentApiRootObject(body)
    return CurrentApiSessionStartResponse(
        sessionId = root.requiredString("session_id"),
        shareCode = root.requiredString("share_code"),
        status = root.requiredString("status"),
        writeToken = root.requiredString("write_token")
    )
}

internal fun parseCurrentApiSessionEndResponse(body: String): CurrentApiSessionEndResponse {
    val root = parseCurrentApiRootObject(body)
    return CurrentApiSessionEndResponse(
        sessionId = root.requiredString("session_id"),
        status = root.requiredString("status"),
        endedAtWallMs = parseCurrentApiWallMs(root.optionalString("ended_at"))
    )
}

internal fun parseCurrentApiLiveReadResponse(body: String): CurrentApiLiveReadResponse {
    val root = parseCurrentApiRootObject(body)
    val positions = mutableListOf<CurrentApiLivePoint>()
    val positionsJson = root.optionalArray("positions")
    if (positionsJson != null) {
        for (pointJson in positionsJson) {
            pointJson.asObjectOrNull()?.let { positions += parseCurrentApiLivePoint(it) }
        }
    }

    return CurrentApiLiveReadResponse(
        sessionId = root.requiredString("session"),
        shareCode = root.optionalString("share_code"),
        status = root.requiredString("status"),
        latest = root.optionalObject("latest")?.let(::parseCurrentApiLivePoint),
        positions = positions,
        hasTask = root.hasNonNull("task")
    )
}

internal fun parseCurrentApiActivePilotsResponse(body: String): CurrentApiActivePilotsResponse {
    val root = parseCurrentApiRootObject(body)
    val items = mutableListOf<CurrentApiActivePilotItem>()
    val itemsJson = root.optionalArray("items")
    if (itemsJson != null) {
        for (itemJson in itemsJson) {
            val itemObject = itemJson.asObjectOrNull() ?: continue
            runCatching { parseCurrentApiActivePilotItem(itemObject) }
                .getOrNull()
                ?.let(items::add)
        }
    }

    return CurrentApiActivePilotsResponse(
        items = items,
        generatedAtWallMs = parseCurrentApiWallMs(root.optionalString("generated_at"))
    )
}

internal fun preferredCurrentApiLivePoint(
    response: CurrentApiLiveReadResponse
): CurrentApiLivePoint? = response.latest ?: response.positions.lastOrNull()

internal fun currentApiIsoUtcFromWallMs(wallMs: Long): String = Instant.ofEpochMilli(wallMs).toString()

internal fun parseCurrentApiWallMs(rawValue: String?): Long? {
    val trimmed = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        Instant.parse(trimmed).toEpochMilli()
    }.getOrElse {
        runCatching {
            LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }
}

internal fun parseCurrentApiErrorMessage(
    responseBody: String,
    httpCode: Int
): String {
    if (responseBody.isBlank()) return "HTTP $httpCode"

    val jsonMessage = runCatching { parseCurrentApiRootObject(responseBody) }.getOrNull()?.let { root ->
        readJsonDetail(root.get("detail"))
    }
    return jsonMessage?.takeIf { it.isNotBlank() }
        ?: responseBody.trim().takeIf { it.isNotEmpty() }
        ?: "HTTP $httpCode"
}

private fun parseCurrentApiLivePoint(root: JsonObject): CurrentApiLivePoint {
    return CurrentApiLivePoint(
        latitudeDeg = root.requiredDouble("lat"),
        longitudeDeg = root.requiredDouble("lon"),
        altitudeMslMeters = root.optionalFiniteDouble("alt"),
        groundSpeedMs = root.optionalFiniteDouble("speed"),
        headingDeg = root.optionalFiniteDouble("heading"),
        fixWallMs = parseCurrentApiWallMs(root.optionalString("timestamp"))
    )
}

private fun parseCurrentApiActivePilotItem(root: JsonObject): CurrentApiActivePilotItem {
    return CurrentApiActivePilotItem(
        sessionId = root.optionalStringAny("session", "session_id"),
        shareCode = root.requiredString("share_code"),
        status = root.requiredString("status"),
        displayLabel = root.optionalString("display_label"),
        lastPositionWallMs = parseCurrentApiWallMs(root.optionalString("last_position_at")),
        latest = root.optionalObject("latest")?.let(::parseCurrentApiLivePoint)
    )
}

private fun JsonObject.requiredString(key: String): String {
    return optionalString(key)
        ?: throw IllegalArgumentException("Missing $key")
}

private fun JsonObject.requiredDouble(key: String): Double {
    return optionalFiniteDouble(key) ?: throw IllegalArgumentException("Missing $key")
}

private fun JsonObject.optionalString(key: String): String? {
    return get(key)
        ?.takeUnless(JsonElement::isJsonNull)
        ?.asPrimitiveOrNull()
        ?.asString
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.optionalStringAny(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull(::optionalString)
}

private fun JsonObject.optionalFiniteDouble(key: String): Double? {
    val primitive = get(key)
        ?.takeUnless(JsonElement::isJsonNull)
        ?.asPrimitiveOrNull()
        ?.takeIf(JsonPrimitive::isNumber)
        ?: return null
    return primitive.asDouble.takeIf { it.isFinite() }
}

private fun JsonObject.optionalObject(key: String): JsonObject? =
    get(key)?.takeUnless(JsonElement::isJsonNull)?.asObjectOrNull()

private fun JsonObject.optionalArray(key: String): JsonArray? =
    get(key)?.takeUnless(JsonElement::isJsonNull)?.asArrayOrNull()

private fun JsonObject.hasNonNull(key: String): Boolean =
    get(key)?.isJsonNull == false

private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { isJsonObject }?.asJsonObject

private fun JsonElement.asArrayOrNull(): JsonArray? = takeIf { isJsonArray }?.asJsonArray

private fun JsonElement.asPrimitiveOrNull(): JsonPrimitive? =
    takeIf { it.isJsonPrimitive }?.asJsonPrimitive

private fun parseCurrentApiRootObject(body: String): JsonObject {
    val root = runCatching { JsonParser.parseString(body) }.getOrElse { cause ->
        throw IllegalArgumentException("Invalid LiveFollow payload", cause)
    }
    return root.asObjectOrNull() ?: throw IllegalArgumentException("Invalid LiveFollow payload")
}

private fun readJsonDetail(detail: JsonElement?): String? {
    return when (detail) {
        null -> null
        is JsonArray -> detail.toString().takeIf { it.isNotEmpty() }
        is JsonObject -> detail.toString().takeIf { it.isNotEmpty() }
        else -> detail.asPrimitiveOrNull()?.asString?.trim()?.takeIf { it.isNotEmpty() }
            ?: detail.toString().takeIf { it.isNotEmpty() }
    }
}
