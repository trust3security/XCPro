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
            parseFailed = false,
            normalizedLegacyAircraftTypes = false
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
            parsedProfiles += gson.parseUserProfileOrNull(element)
        }
        parsedProfiles
    }.getOrElse { error ->
        onParseError("Failed to parse profiles JSON", error)
        return ParseProfilesResult(
            profiles = fallback,
            bootstrapMessage = "Failed to parse stored profiles.",
            parseFailed = true,
            normalizedLegacyAircraftTypes = false
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
        parseFailed = false,
        normalizedLegacyAircraftTypes = sanitized.normalizedLegacyAircraftTypes
    )
}

internal fun sanitizeProfiles(rawProfiles: List<UserProfile?>): SanitizedProfilesResult {
    val unique = LinkedHashMap<String, UserProfile>()
    var dropped = false
    var normalizedLegacyAircraftTypes = false

    rawProfiles.forEach { profile ->
        if (profile == null) {
            dropped = true
            return@forEach
        }
        val normalizedProfile = profile.normalizedForPersistence()
        if (normalizedProfile != profile) {
            normalizedLegacyAircraftTypes = true
        }
        val validation = runCatching {
            val id = normalizedProfile.id.trim()
            val name = normalizedProfile.name.trim()
            val typeValid = normalizedProfile.aircraftType.name.isNotBlank()
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
        if (unique.put(id, normalizedProfile) != null) {
            dropped = true
        }
    }

    return SanitizedProfilesResult(
        profiles = unique.values.toList(),
        droppedInvalidEntries = dropped,
        normalizedLegacyAircraftTypes = normalizedLegacyAircraftTypes
    )
}

internal fun ensureBootstrapProfile(
    profiles: List<UserProfile>,
    clock: Clock
): DefaultProfileProvisioningResult {
    val canonicalProfiles = normalizeProfilesForPersistence(profiles)
    val normalizedProfiles = canonicalProfiles.profiles
    if (normalizedProfiles.isEmpty()) {
        return DefaultProfileProvisioningResult(
            profiles = emptyList(),
            insertedDefaultProfile = false,
            migratedLegacyDefaultAlias = false,
            normalizedLegacyAircraftTypes = canonicalProfiles.normalizedLegacyAircraftTypes
        )
    }
    if (normalizedProfiles.any { it.id == ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID }) {
        return DefaultProfileProvisioningResult(
            profiles = normalizedProfiles,
            insertedDefaultProfile = false,
            migratedLegacyDefaultAlias = false,
            normalizedLegacyAircraftTypes = canonicalProfiles.normalizedLegacyAircraftTypes
        )
    }

    val legacyDefaultIndex = normalizedProfiles.indexOfFirst {
        ProfileIdResolver.isLegacyDefaultAlias(it.id)
    }
    if (legacyDefaultIndex >= 0) {
        val migrated = normalizedProfiles.toMutableList()
        val legacyDefault = migrated[legacyDefaultIndex]
        migrated[legacyDefaultIndex] = legacyDefault.copy(
            id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID
        )
        return DefaultProfileProvisioningResult(
            profiles = dedupeProfilesById(migrated),
            insertedDefaultProfile = false,
            migratedLegacyDefaultAlias = true,
            normalizedLegacyAircraftTypes = canonicalProfiles.normalizedLegacyAircraftTypes
        )
    }

    return DefaultProfileProvisioningResult(
        profiles = listOf(buildDefaultProfile(clock)) + normalizedProfiles,
        insertedDefaultProfile = true,
        migratedLegacyDefaultAlias = false,
        normalizedLegacyAircraftTypes = canonicalProfiles.normalizedLegacyAircraftTypes
    )
}

internal fun buildDefaultProfile(
    clock: Clock,
    aircraftType: AircraftType = AircraftType.PARAGLIDER
): UserProfile =
    UserProfile(
        id = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
        name = "Default",
        aircraftType = aircraftType.canonicalForPersistence(),
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
    val migratedLegacyDefaultAlias: Boolean,
    val normalizedLegacyAircraftTypes: Boolean
)
