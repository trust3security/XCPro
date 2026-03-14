package com.example.xcpro.profiles

import com.example.xcpro.core.common.profiles.ProfileSettingsProfileIds

internal fun resolveImportedProfileId(
    sourceProfileId: String,
    importedProfileIdMap: Map<String, String>
): String? = ProfileSettingsProfileIds.resolveImportedProfileId(
    sourceProfileId = sourceProfileId,
    importedProfileIdMap = importedProfileIdMap
)
