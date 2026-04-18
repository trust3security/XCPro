package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.common.profiles.ProfileSettingsProfileIds

object ProfileIdResolver {
    const val CANONICAL_DEFAULT_PROFILE_ID: String =
        ProfileSettingsProfileIds.CANONICAL_DEFAULT_PROFILE_ID

    fun canonicalOrDefault(profileId: String?): String = normalizeOrNull(profileId)
        ?: CANONICAL_DEFAULT_PROFILE_ID

    fun normalizeOrNull(profileId: String?): String? = ProfileSettingsProfileIds.normalizeOrNull(profileId)

    fun isCanonicalDefault(profileId: String?): Boolean =
        ProfileSettingsProfileIds.isCanonicalDefault(profileId)

    fun isLegacyDefaultAlias(profileId: String?): Boolean =
        ProfileSettingsProfileIds.isLegacyDefaultAlias(profileId)
}
