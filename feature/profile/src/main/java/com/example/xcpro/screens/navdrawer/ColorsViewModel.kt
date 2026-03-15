package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.profiles.ProfileIdResolver
import com.example.xcpro.ui.theme.ThemePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ColorsUiState(
    val profileId: String = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
    val themeId: String = "default",
    val customColorsJson: String? = null
)

@HiltViewModel
class ColorsViewModel @Inject constructor(
    private val useCase: ThemePreferencesUseCase
) : ViewModel() {

    private val profileId = MutableStateFlow(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)

    val uiState: StateFlow<ColorsUiState> = profileId
        .flatMapLatest { id ->
            useCase.observeThemeId(id)
                .flatMapLatest { themeId ->
                    useCase.observeCustomColorsJson(id, themeId)
                        .map { json ->
                            ColorsUiState(
                                profileId = id,
                                themeId = themeId,
                                customColorsJson = json
                            )
                        }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ColorsUiState()
        )

    fun setProfileId(id: String) {
        if (profileId.value != id) {
            profileId.value = id
        }
    }

    fun setThemeId(themeId: String) {
        useCase.setThemeId(profileId.value, themeId)
    }

    fun setCustomColorsJson(themeId: String, json: String?) {
        useCase.setCustomColorsJson(profileId.value, themeId, json)
    }
}
