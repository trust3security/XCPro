package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.trust3.xcpro.map.MapStyleRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapStyleProfileSettingsContributor @Inject constructor(
    private val mapStyleRepository: MapStyleRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionIds.MAP_STYLE_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.MAP_STYLE_PREFERENCES) return null
        val stylesByProfile = profileIds.associateWith { profileId ->
            mapStyleRepository.readProfileStyle(profileId)
        }
        return gson.toJsonTree(MapStyleSectionSnapshot(stylesByProfile = stylesByProfile))
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.MAP_STYLE_PREFERENCES) return
        val section = gson.fromJson(payload, MapStyleSectionSnapshot::class.java)
        section.stylesByProfile.forEach { (sourceProfileId, styleId) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            mapStyleRepository.writeProfileStyle(profileId, styleId)
        }
    }
}
