package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LayoutUiState(
    val cardsAcrossPortrait: Int = CardPreferences.DEFAULT_CARDS_ACROSS_PORTRAIT,
    val anchorPortrait: CardPreferences.CardAnchor = CardPreferences.DEFAULT_ANCHOR_PORTRAIT
)

@HiltViewModel
// AI-NOTE: Keep this VM limited to the canonical DF-card layout preferences.
// Do not expand it into a map-widget or profile-scoped layout state owner.
class LayoutViewModel @Inject constructor(
    private val useCase: LayoutPreferencesUseCase
) : ViewModel() {

    val uiState: StateFlow<LayoutUiState> = combine(
        useCase.cardsAcrossPortrait,
        useCase.cardsAnchorPortrait
    ) { cardsAcross, anchor ->
        LayoutUiState(
            cardsAcrossPortrait = cardsAcross,
            anchorPortrait = anchor
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LayoutUiState()
    )

    fun setCardsAcrossPortrait(count: Int) {
        viewModelScope.launch { useCase.setCardsAcrossPortrait(count) }
    }

    fun setAnchorPortrait(anchor: CardPreferences.CardAnchor) {
        viewModelScope.launch { useCase.setCardsAnchorPortrait(anchor) }
    }
}
