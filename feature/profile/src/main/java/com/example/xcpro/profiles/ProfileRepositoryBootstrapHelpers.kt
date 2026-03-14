package com.example.xcpro.profiles

import com.example.xcpro.core.time.Clock
import com.google.gson.Gson
import com.google.gson.JsonParser

internal fun parseProfiles(
    json: String?,
    fallback: List<UserProfile>,
    gson: Gson,
    onParseError: (String, Throwable) -> Unit
): ParseProfilesResult {
    if (json.isNullOrBlank()) {
        return ParseProfilesResult(
            profiles = emptyList(),
            bootstrapMessage = null,
            parseFailed = false
        )
    }

    val parsed = runCatching {
        val rootElement = JsonParser.parseString(json)
        if (!rootElement.isJsonArray) {
            error("Profile payload must be a JSON array")
        }
        val parsedProfiles = mutableListOf<UserProfile?>()
        rootElement.asJsonArray.forEach { element ->
            if (element == null || element.isJsonNull) {
                parsedProfiles += null
                return@forEach
            }
            val parsedProfile = runCatching {
                gson.fromJson(element, UserProfile::class.java)
            }.getOrNull()
            parsedProfiles += parsedProfile
        }
        parsedProfiles
    }.getOrElse { error ->
        onParseError("Failed to parse profiles JSON", error)
        return ParseProfilesResult(
            profiles = fallback,
            bootstrapMessage = "Failed to parse stored profiles.",
            parseFailed = true
        )
    }

    val sanitized = sanitizeProfiles(parsed)
    val message = if (sanitized.droppedInvalidEntries) {
        "Some stored profiles were invalid and were ignored."
    } else {
        null
    }
    return ParseProfilesResult(
        profiles = sanitized.profiles,
        bootstrapMessage = message,
        parseFailed = false
    )
}

internal fun sanitizeProfiles(rawProfiles: List<UserProfile?>): SanitizedProfilesResult {
    val unique = LinkedHashMap<String, UserProfile>()
    var dropped = false

    rawProfiles.forEach { profile ->
        if (profile == null) {
            dropped = true
            return@forEach
        }
        val validation = runCatching {
            val id = profile.id.trim()
            val name = profile.name.trim()
            val typeValid = profile.aircraftType.name.isNotBlank()
            Triple(id, name, typeValid)
        }.getOrNull()

        if (validation == null) {
            dropped = true
            return@forEach
        }
        val (id, name, typeValid) = validation
        if (id.isBlank() || name.isBlank() || !typeValid) {
            dropped = true
            return@forEach
        }
        if (unique.put(id, profile) != null) {
            dropped = true
        }
    }

    return SanitizedProfilesResult(
        profiles = unique.values.toList(),
        droppedInvalidEntries = dropped
    )
}

internal fun ensureBootstrapProfile(
    profiles: List<UserProfile>,
    clock: Clock
): DefaultProfileProvisioningResult {
    if (profiles.isEmpty()) {
        return DefaultProfileProvisioningResult(
            profiles = listOf(buildDefaultProfile(clock)),
            insertedDefaultProfile = true,
            migratedLegacyDefaultAlias = false
        )
    }
    if (profiles.any { it.id == ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID }) {
        return DefaultProfileProvisioningResult(
            profiles = profiles,
            insertedDefaultProfile = false,
            migratedLegacyDefaultAlias = false
        )
    }

    val legacyDefaultIndex = profiles.indexOfFirst { ProfileIdResolver.isLegacyDefaultAlias(it.id) }
    if (legacyDefaultIndex >= 0) {
        val migrated = profiles.toMutableList()
        val legacyDefault = migrated[legacyDefaultIndex]
        migrated[legacyDefaultIndex] = legacyDefault.copy(
            id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID
        )
        return DefaultProfileProvisioningResult(
            profiles = dedupeProfilesById(migrated),
            insertedDefaultProfile = false,
            migratedLegacyDefaultAlias = true
        )
    }

    return DefaultProfileProvisioningResult(
        profiles = listOf(buildDefaultProfile(clock)) + profiles,
        insertedDefaultProfile = true,
        migratedLegacyDefaultAlias = false
    )
}

internal fun buildDefaultProfile(clock: Clock): UserProfile =
    UserProfile(
        id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
        name = "Default",
        aircraftType = AircraftType.PARAGLIDER,
        createdAt = clock.nowWallMs(),
        lastUsed = clock.nowWallMs()
    )

internal fun dedupeProfilesById(profiles: List<UserProfile>): List<UserProfile> {
    val deduped = LinkedHashMap<String, UserProfile>()
    profiles.forEach { profile ->
        if (!deduped.containsKey(profile.id)) {
            deduped[profile.id] = profile
        }
    }
    return deduped.values.toList()
}

internal fun fallbackActiveProfile(profiles: List<UserProfile>): UserProfile? =
    profiles.firstOrNull { !ProfileIdResolver.isCanonicalDefault(it.id) } ?: profiles.firstOrNull()

internal fun resolveActiveProfile(id: String?, profiles: List<UserProfile>): UserProfile? {
    val normalizedId = ProfileIdResolver.normalizeOrNull(id)
    return when {
        profiles.isEmpty() -> null
        normalizedId == null -> fallbackActiveProfile(profiles)
        else -> profiles.find { profile ->
            ProfileIdResolver.normalizeOrNull(profile.id) == normalizedId
        } ?: fallbackActiveProfile(profiles)
    }
}

internal fun mergeMessages(primary: String?, secondary: String): String =
    if (primary.isNullOrBlank()) secondary else "$primary $secondary"

internal data class DefaultProfileProvisioningResult(
    val profiles: List<UserProfile>,
    val insertedDefaultProfile: Boolean,
    val migratedLegacyDefaultAlias: Boolean
)
