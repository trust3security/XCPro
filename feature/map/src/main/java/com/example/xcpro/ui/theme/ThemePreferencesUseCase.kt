package com.example.xcpro.ui.theme

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ThemePreferencesUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository
) {
    fun getThemeId(profileId: String): String = repository.getThemeId(profileId)

    fun observeThemeId(profileId: String): Flow<String> = repository.observeThemeId(profileId)

    fun getCustomColorsJson(profileId: String, themeId: String): String? =
        repository.getCustomColorsJson(profileId, themeId)

    fun observeCustomColorsJson(profileId: String, themeId: String): Flow<String?> =
        repository.observeCustomColorsJson(profileId, themeId)

    fun setThemeId(profileId: String, themeId: String) {
        repository.setThemeId(profileId, themeId)
    }

    fun setCustomColorsJson(profileId: String, themeId: String, json: String?) {
        repository.setCustomColorsJson(profileId, themeId, json)
    }
}
