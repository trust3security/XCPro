package com.example.xcpro.profiles

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.ui.theme.AppColorTheme
import com.example.xcpro.ui.theme.ThemePreferencesRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeProfileSettingsContributor @Inject constructor(
    private val themePreferencesRepository: ThemePreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionIds.THEME_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.THEME_PREFERENCES) return null
        val themeIdsByProfile = linkedMapOf<String, String>()
        val customColorsByProfileAndTheme = linkedMapOf<String, Map<String, String>>()
        profileIds.forEach { profileId ->
            val selectedThemeId = themePreferencesRepository.getThemeId(profileId)
            themeIdsByProfile[profileId] = selectedThemeId

            val candidateThemeIds = linkedSetOf<String>().apply {
                add(selectedThemeId)
                AppColorTheme.entries.forEach { theme -> add(theme.id) }
            }
            val customColorsByTheme = linkedMapOf<String, String>()
            candidateThemeIds.forEach { themeId ->
                val customJson = themePreferencesRepository.getCustomColorsJson(profileId, themeId)
                if (!customJson.isNullOrBlank()) {
                    customColorsByTheme[themeId] = customJson
                }
            }
            customColorsByProfileAndTheme[profileId] = customColorsByTheme.toMap()
        }
        return gson.toJsonTree(
            ThemeSectionSnapshot(
                themeIdByProfile = themeIdsByProfile.toMap(),
                customColorsByProfileAndTheme = customColorsByProfileAndTheme.toMap()
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.THEME_PREFERENCES) return
        val section = gson.fromJson(payload, ThemeSectionSnapshot::class.java)
        section.themeIdByProfile.forEach { (sourceProfileId, themeId) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            themePreferencesRepository.setThemeId(profileId, themeId)
        }
        section.customColorsByProfileAndTheme.forEach { (sourceProfileId, themeMap) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            themeMap.forEach { (themeId, json) ->
                themePreferencesRepository.setCustomColorsJson(profileId, themeId, json)
            }
        }
    }
}
