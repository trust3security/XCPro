package com.example.xcpro.screens.navdrawer.lookandfeel

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LookAndFeelUseCase @Inject constructor(
    private val repository: LookAndFeelPreferences
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

    fun getColorThemeId(profileId: String): String = repository.getColorThemeId(profileId)

    fun observeColorThemeId(profileId: String): Flow<String> =
        repository.observeColorThemeId(profileId)

    fun setColorThemeId(profileId: String, themeId: String) {
        repository.setColorThemeId(profileId, themeId)
    }
}
