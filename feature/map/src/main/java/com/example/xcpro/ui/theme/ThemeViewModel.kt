package com.example.xcpro.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.profiles.ProfileIdResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ThemeUiState(
    val profileId: String = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
    val themeId: String = "default",
    val customColorsJson: String? = null
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val useCase: ThemePreferencesUseCase
) : ViewModel() {

    private val profileId = MutableStateFlow(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)

    val uiState: StateFlow<ThemeUiState> = profileId
        .flatMapLatest { id ->
            useCase.observeThemeId(id)
                .flatMapLatest { themeId ->
                    useCase.observeCustomColorsJson(id, themeId)
                        .map { json ->
                            ThemeUiState(
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
            initialValue = ThemeUiState()
        )

    fun setProfileId(id: String) {
        if (profileId.value != id) {
            profileId.value = id
        }
    }
}
