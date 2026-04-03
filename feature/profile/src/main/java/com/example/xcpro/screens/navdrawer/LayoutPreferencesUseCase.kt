package com.example.xcpro.screens.navdrawer

import com.example.dfcards.CardPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LayoutPreferencesUseCase @Inject constructor(
    private val cardPreferences: CardPreferences
) {
    // AI-NOTE: These flows back the General -> Layouts DF-card screen only and
    // intentionally stay on the global CardPreferences seam for this phase.
    val cardsAcrossPortrait: Flow<Int> = cardPreferences.getCardsAcrossPortrait()
    val cardsAnchorPortrait: Flow<CardPreferences.CardAnchor> = cardPreferences.getCardsAnchorPortrait()

    suspend fun setCardsAcrossPortrait(count: Int) {
        cardPreferences.setCardsAcrossPortrait(count)
    }

    suspend fun setCardsAnchorPortrait(anchor: CardPreferences.CardAnchor) {
        cardPreferences.setCardsAnchorPortrait(anchor)
    }
}
