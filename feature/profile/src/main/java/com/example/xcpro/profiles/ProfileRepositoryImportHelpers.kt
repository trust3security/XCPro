package com.example.xcpro.profiles

import java.util.Locale
import java.util.UUID

internal fun generateUniqueId(knownIds: MutableSet<String>, preferredId: String): String {
    if (preferredId.isNotBlank() && knownIds.add(preferredId)) {
        return preferredId
    }
    var generatedId: String
    do {
        generatedId = UUID.randomUUID().toString()
    } while (!knownIds.add(generatedId))
    return generatedId
}

internal fun resolveImportedName(
    baseName: String,
    knownNames: MutableSet<String>,
    policy: ProfileNameCollisionPolicy
): String {
    val normalizedBase = baseName.trim()
    val baseKey = normalizedBase.lowercase(Locale.ROOT)
    if (knownNames.add(baseKey)) {
        return normalizedBase
    }

    return when (policy) {
        ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX ->
            resolveImportedNameWithSuffix(normalizedBase, knownNames)
    }
}

private fun resolveImportedNameWithSuffix(
    normalizedBase: String,
    knownNames: MutableSet<String>
): String {
    var suffix = 1
    while (true) {
        val candidate = if (suffix == 1) {
            "$normalizedBase (Imported)"
        } else {
            "$normalizedBase (Imported $suffix)"
        }
        val candidateKey = candidate.lowercase(Locale.ROOT)
        if (knownNames.add(candidateKey)) {
            return candidate
        }
        suffix++
    }
}

internal fun resolveImportActiveProfile(
    profiles: List<UserProfile>,
    currentActiveId: String?,
    importedProfiles: List<UserProfile>,
    keepCurrentActive: Boolean
): UserProfile? {
    if (profiles.isEmpty()) {
        return null
    }

    if (keepCurrentActive && !currentActiveId.isNullOrBlank()) {
        return profiles.find { it.id == currentActiveId } ?: profiles.firstOrNull()
    }

    if (!keepCurrentActive) {
        val newestImportedId = importedProfiles.lastOrNull()?.id
        if (!newestImportedId.isNullOrBlank()) {
            return profiles.find { it.id == newestImportedId } ?: profiles.firstOrNull()
        }
    }

    return if (!currentActiveId.isNullOrBlank()) {
        profiles.find { it.id == currentActiveId } ?: profiles.firstOrNull()
    } else {
        profiles.firstOrNull()
    }
}

internal data class ParseProfilesResult(
    val profiles: List<UserProfile>,
    val bootstrapMessage: String?,
    val parseFailed: Boolean
)

internal data class SanitizedProfilesResult(
    val profiles: List<UserProfile>,
    val droppedInvalidEntries: Boolean
)
