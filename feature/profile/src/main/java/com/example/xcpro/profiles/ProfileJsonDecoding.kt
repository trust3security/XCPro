package com.example.xcpro.profiles

import com.google.gson.Gson
import com.google.gson.JsonElement

private data class DecodedUserProfilePayload(
    val id: String? = null,
    val name: String? = null,
    val aircraftType: AircraftType? = null,
    val aircraftModel: String? = null,
    val description: String? = null,
    val preferences: ProfilePreferences? = null,
    val isActive: Boolean? = null,
    val createdAt: Long? = null,
    val lastUsed: Long? = null,
    val polar: ProfilePolarSettings? = null
)

internal fun Gson.parseUserProfileOrNull(element: JsonElement?): UserProfile? {
    if (element == null || element.isJsonNull || !element.isJsonObject) {
        return null
    }
    val payload = runCatching {
        fromJson(element, DecodedUserProfilePayload::class.java)
    }.getOrNull() ?: return null

    return UserProfile(
        id = payload.id ?: "",
        name = payload.name ?: "",
        aircraftType = payload.aircraftType ?: return null,
        aircraftModel = payload.aircraftModel?.trim()?.takeIf { it.isNotEmpty() },
        description = payload.description?.trim()?.takeIf { it.isNotEmpty() },
        preferences = payload.preferences ?: ProfilePreferences(),
        isActive = payload.isActive ?: false,
        createdAt = payload.createdAt ?: 0L,
        lastUsed = payload.lastUsed ?: 0L,
        polar = payload.polar ?: ProfilePolarSettings()
    )
}

internal fun Gson.parseUserProfilesArray(element: JsonElement?): List<UserProfile?> {
    if (element == null || element.isJsonNull || !element.isJsonArray) {
        return emptyList()
    }
    return element.asJsonArray.map(::parseUserProfileOrNull)
}
