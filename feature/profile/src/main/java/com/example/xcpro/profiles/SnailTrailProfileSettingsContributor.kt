package com.example.xcpro.profiles

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.map.trail.MapTrailPreferences
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.TrailType
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnailTrailProfileSettingsContributor @Inject constructor(
    private val mapTrailPreferences: MapTrailPreferences
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> =
        setOf(ProfileSettingsSectionIds.SNAIL_TRAIL_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.SNAIL_TRAIL_PREFERENCES) return null
        val settingsByProfile = profileIds.associateWith { profileId ->
            val settings = mapTrailPreferences.readProfileSettings(profileId)
            SnailTrailProfileSectionSnapshot(
                length = settings.length.name,
                type = settings.type.name,
                windDriftEnabled = settings.windDriftEnabled,
                scalingEnabled = settings.scalingEnabled
            )
        }
        return gson.toJsonTree(SnailTrailSectionSnapshot(settingsByProfile = settingsByProfile))
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.SNAIL_TRAIL_PREFERENCES) return
        val section = gson.fromJson(payload, SnailTrailSectionSnapshot::class.java)
        val defaults = TrailSettings()
        section.settingsByProfile.forEach { (sourceProfileId, snapshot) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            val length = runCatching { TrailLength.valueOf(snapshot.length) }
                .getOrDefault(defaults.length)
            val type = runCatching { TrailType.valueOf(snapshot.type) }
                .getOrDefault(defaults.type)
            mapTrailPreferences.writeProfileSettings(
                profileId = profileId,
                settings = TrailSettings(
                    length = length,
                    type = type,
                    windDriftEnabled = snapshot.windDriftEnabled,
                    scalingEnabled = snapshot.scalingEnabled
                )
            )
        }
    }
}
