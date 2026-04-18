package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.common.profiles.ProfileSettingsProfileIds

internal fun resolveImportedProfileId(
    sourceProfileId: String,
    importedProfileIdMap: Map<String, String>
): String? = ProfileSettingsProfileIds.resolveImportedProfileId(
    sourceProfileId = sourceProfileId,
    importedProfileIdMap = importedProfileIdMap
)
