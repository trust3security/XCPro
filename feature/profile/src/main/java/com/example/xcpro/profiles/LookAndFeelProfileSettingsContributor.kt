package com.example.xcpro.profiles

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LookAndFeelProfileSettingsContributor @Inject constructor(
    private val lookAndFeelPreferences: LookAndFeelPreferences
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> =
        setOf(ProfileSettingsSectionIds.LOOK_AND_FEEL_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.LOOK_AND_FEEL_PREFERENCES) return null
        val statusBarStyleByProfile = profileIds.associateWith { profileId ->
            lookAndFeelPreferences.getStatusBarStyleId(profileId)
        }
        val cardStyleByProfile = profileIds.associateWith { profileId ->
            lookAndFeelPreferences.getCardStyleId(profileId)
        }
        return gson.toJsonTree(
            LookAndFeelSectionSnapshot(
                statusBarStyleByProfile = statusBarStyleByProfile,
                cardStyleByProfile = cardStyleByProfile
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.LOOK_AND_FEEL_PREFERENCES) return
        val section = gson.fromJson(payload, LookAndFeelSectionSnapshot::class.java)
        section.statusBarStyleByProfile.forEach { (sourceProfileId, styleId) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            lookAndFeelPreferences.setStatusBarStyleId(profileId, styleId)
        }
        section.cardStyleByProfile.forEach { (sourceProfileId, styleId) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            lookAndFeelPreferences.setCardStyleId(profileId, styleId)
        }
    }
}
