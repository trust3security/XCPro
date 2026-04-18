package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.trust3.xcpro.glider.GliderRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GliderConfigProfileSettingsContributor @Inject constructor(
    private val gliderRepository: GliderRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionIds.GLIDER_CONFIG)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.GLIDER_CONFIG) return null
        val profiles = profileIds.associateWith { profileId ->
            val snapshot = gliderRepository.loadProfileSnapshot(profileId)
            GliderProfileSectionSnapshot(
                selectedModelId = snapshot.selectedModelId,
                effectiveModelId = snapshot.effectiveModelId,
                isFallbackPolarActive = snapshot.isFallbackPolarActive,
                config = snapshot.config
            )
        }
        return gson.toJsonTree(GliderSectionSnapshot(profiles = profiles))
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.GLIDER_CONFIG) return
        val section = gson.fromJson(payload, GliderSectionSnapshot::class.java)
        if (section.profiles.isNotEmpty()) {
            section.profiles.forEach { (sourceProfileId, snapshot) ->
                val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                    ?: return@forEach
                gliderRepository.saveProfileSnapshot(
                    profileId = profileId,
                    selectedModelId = snapshot.selectedModelId,
                    config = snapshot.config
                )
            }
            return
        }
        val legacyConfig = section.config ?: return
        val targetProfileIds = importedProfileIdMap.values.toSet().ifEmpty {
            setOf(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)
        }
        targetProfileIds.forEach { profileId ->
            gliderRepository.saveProfileSnapshot(
                profileId = profileId,
                selectedModelId = section.selectedModelId,
                config = legacyConfig
            )
        }
    }
}
