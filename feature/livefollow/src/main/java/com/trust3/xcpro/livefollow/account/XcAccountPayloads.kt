package com.trust3.xcpro.livefollow.account

import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

data class XcAccountMePayload(
    val profile: XcPilotProfile,
    val privacy: XcPrivacySettings
)

internal fun parseCurrentApiXcGoogleExchangeResponse(body: String): XcAccountSession {
    val root = parseXcAccountRootObject(body)
    val authMethod = root.optionalString("auth_method")
        ?.let(XcAccountAuthMethod::fromStorageValue)
        ?: XcAccountAuthMethod.GOOGLE
    return XcAccountSession(
        accessToken = root.requiredString("access_token"),
        authMethod = authMethod
    )
}

internal fun parseCurrentApiXcMeResponse(body: String): XcAccountMePayload {
    val root = parseXcAccountRootObject(body)
    return XcAccountMePayload(
        profile = parseCurrentApiXcProfileResponse(root),
        privacy = root.optionalObject("privacy")?.let(::parseCurrentApiXcPrivacyResponse)
            ?: throw IllegalArgumentException("Missing privacy")
    )
}

internal fun parseCurrentApiXcProfileResponse(body: String): XcPilotProfile {
    return parseCurrentApiXcProfileResponse(parseXcAccountRootObject(body))
}

internal fun parseCurrentApiXcPrivacyResponse(body: String): XcPrivacySettings {
    return parseCurrentApiXcPrivacyResponse(parseXcAccountRootObject(body))
}

internal fun parseCurrentApiXcApiError(
    responseBody: String,
    httpCode: Int
): XcAccountApiError {
    if (responseBody.isBlank()) {
        return XcAccountApiError(
            message = "HTTP $httpCode",
            httpCode = httpCode
        )
    }
    val root = runCatching { parseXcAccountRootObject(responseBody) }.getOrNull()
    val code = root?.optionalString("code")
    val detail = root?.get("detail")?.toDetailString()
    return XcAccountApiError(
        message = detail?.takeIf { it.isNotBlank() }
            ?: responseBody.trim().ifEmpty { "HTTP $httpCode" },
        code = code,
        httpCode = httpCode
    )
}

internal fun parseCurrentApiXcProfileResponse(root: JsonObject): XcPilotProfile {
    return XcPilotProfile(
        userId = root.requiredString("user_id"),
        handle = root.optionalString("handle"),
        displayName = root.optionalString("display_name"),
        compNumber = root.optionalString("comp_number")
    )
}

internal fun parseCurrentApiXcPrivacyResponse(root: JsonObject): XcPrivacySettings {
    return XcPrivacySettings(
        discoverability = XcDiscoverability.fromWireValue(root.requiredString("discoverability")),
        followPolicy = XcFollowPolicy.fromWireValue(root.requiredString("follow_policy")),
        defaultLiveVisibility = XcDefaultLiveVisibility.fromWireValue(
            root.requiredString("default_live_visibility")
        ),
        connectionListVisibility = XcConnectionListVisibility.fromWireValue(
            root.requiredString("connection_list_visibility")
        )
    )
}

internal fun parseXcAccountRootObject(body: String): JsonObject {
    val parsed = runCatching { JsonParser.parseString(body) }.getOrElse { cause ->
        throw IllegalArgumentException("Invalid XCPro account payload", cause)
    }
    return parsed.takeIf { it.isJsonObject }?.asJsonObject
        ?: throw IllegalArgumentException("Invalid XCPro account payload")
}

internal fun JsonObject.requiredString(key: String): String {
    return optionalString(key)
        ?: throw IllegalArgumentException("Missing $key")
}

internal fun JsonObject.optionalString(key: String): String? {
    return get(key)
        ?.takeUnless(JsonElement::isJsonNull)
        ?.asPrimitiveOrNull()
        ?.asString
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

internal fun JsonObject.optionalObject(key: String): JsonObject? {
    return get(key)?.takeUnless(JsonElement::isJsonNull)?.takeIf { it.isJsonObject }?.asJsonObject
}

internal fun JsonObject.requiredArray(key: String): JsonArray {
    return get(key)
        ?.takeUnless(JsonElement::isJsonNull)
        ?.takeIf(JsonElement::isJsonArray)
        ?.asJsonArray
        ?: throw IllegalArgumentException("Missing $key")
}

private fun JsonElement.asPrimitiveOrNull(): JsonPrimitive? {
    return takeIf { it.isJsonPrimitive }?.asJsonPrimitive
}

internal fun JsonElement.toDetailString(): String? {
    return when {
        isJsonNull -> null
        isJsonPrimitive -> asJsonPrimitive.asString?.trim()?.takeIf { it.isNotEmpty() }
        else -> toString().trim().takeIf { it.isNotEmpty() }
    }
}
