package com.example.xcpro.screens.navdrawer.lookandfeel

import com.example.xcpro.ui.theme.ThemePreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LookAndFeelUseCase @Inject constructor(
    private val repository: LookAndFeelPreferences,
    private val themePreferencesRepository: ThemePreferencesRepository
) {
    fun getStatusBarStyleId(profileId: String): String = repository.getStatusBarStyleId(profileId)

    fun observeStatusBarStyleId(profileId: String): Flow<String> =
        repository.observeStatusBarStyleId(profileId)

    fun setStatusBarStyleId(profileId: String, styleId: String) {
        repository.setStatusBarStyleId(profileId, styleId)
    }

    fun getCardStyleId(profileId: String): String = repository.getCardStyleId(profileId)

    fun observeCardStyleId(profileId: String): Flow<String> =
        repository.observeCardStyleId(profileId)

    fun setCardStyleId(profileId: String, styleId: String) {
        repository.setCardStyleId(profileId, styleId)
    }

    fun getColorThemeId(profileId: String): String = themePreferencesRepository.getThemeId(profileId)

    fun observeColorThemeId(profileId: String): Flow<String> =
        themePreferencesRepository.observeThemeId(profileId)

    fun setColorThemeId(profileId: String, themeId: String) {
        themePreferencesRepository.setThemeId(profileId, themeId)
    }
}
