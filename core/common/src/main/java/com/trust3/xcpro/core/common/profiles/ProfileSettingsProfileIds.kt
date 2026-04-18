package com.trust3.xcpro.core.common.profiles

import java.util.Locale

object ProfileSettingsProfileIds {
    const val CANONICAL_DEFAULT_PROFILE_ID: String = "default-profile"

    private const val LEGACY_DEFAULT_ALIAS: String = "default"
    private const val LEGACY_DFCARDS_ALIAS: String = "__default_profile__"

    fun canonicalOrDefault(profileId: String?): String = normalizeOrNull(profileId)
        ?: CANONICAL_DEFAULT_PROFILE_ID

    fun normalizeOrNull(profileId: String?): String? {
        val trimmed = profileId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (isDefaultAlias(trimmed)) {
            CANONICAL_DEFAULT_PROFILE_ID
        } else {
            trimmed
        }
    }

    fun isCanonicalDefault(profileId: String?): Boolean =
        normalizeOrNull(profileId) == CANONICAL_DEFAULT_PROFILE_ID

    fun isLegacyDefaultAlias(profileId: String?): Boolean {
        val trimmed = profileId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val normalized = trimmed.lowercase(Locale.ROOT)
        return normalized == LEGACY_DEFAULT_ALIAS || normalized == LEGACY_DFCARDS_ALIAS
    }

    fun resolveImportedProfileId(
        sourceProfileId: String,
        importedProfileIdMap: Map<String, String>
    ): String? {
        val canonicalSource = canonicalOrDefault(sourceProfileId)
        return importedProfileIdMap[sourceProfileId]
            ?: importedProfileIdMap[canonicalSource]
    }

    private fun isDefaultAlias(profileId: String): Boolean {
        val normalized = profileId.lowercase(Locale.ROOT)
        return normalized == CANONICAL_DEFAULT_PROFILE_ID ||
            normalized == LEGACY_DEFAULT_ALIAS ||
            normalized == LEGACY_DFCARDS_ALIAS
    }
}
