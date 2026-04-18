package com.trust3.xcpro.screens.navdrawer.lookandfeel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.profiles.ProfileIdResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class LookAndFeelUiState(
    val profileId: String = ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID,
    val statusBarStyleId: String = "transparent",
    val cardStyleId: String = "standard",
    val colorThemeId: String = "default"
)

@HiltViewModel
class LookAndFeelViewModel @Inject constructor(
    private val useCase: LookAndFeelUseCase
) : ViewModel() {

    private val profileId = MutableStateFlow(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID)

    val uiState: StateFlow<LookAndFeelUiState> = profileId
        .flatMapLatest { id ->
            combine(
                useCase.observeStatusBarStyleId(id),
                useCase.observeCardStyleId(id),
                useCase.observeColorThemeId(id)
            ) { statusBarId, cardStyleId, colorThemeId ->
                LookAndFeelUiState(
                    profileId = id,
                    statusBarStyleId = statusBarId,
                    cardStyleId = cardStyleId,
                    colorThemeId = colorThemeId
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LookAndFeelUiState()
        )

    fun setProfileId(id: String) {
        if (profileId.value != id) {
            profileId.value = id
        }
    }

    fun setStatusBarStyleId(styleId: String) {
        useCase.setStatusBarStyleId(profileId.value, styleId)
    }

    fun setCardStyleId(styleId: String) {
        useCase.setCardStyleId(profileId.value, styleId)
    }

    fun setColorThemeId(themeId: String) {
        useCase.setColorThemeId(profileId.value, themeId)
    }
}
