package com.example.dfcards.dfcards

import com.example.dfcards.CardPreferences

/**
 * Placeholder for derived metrics/formatting to keep VM slim.
 * (Currently just wraps existing CardStateRepository passthroughs.)
 */
internal class FlightDataDerivations(
    private val repository: CardStateRepository
) {
    val cardStateFlows get() = repository.cardStateFlows
    val legacyCardStates get() = repository.legacyCardStates
    val selectedCardIds get() = repository.selectedCardIds

    fun setSelected(ids: Set<String>) = repository.setSelectedCardIds(ids)
    fun setPreferences(prefs: CardPreferences) = repository.setCardPreferences(prefs)
    fun getAllStates(): List<CardState> = repository.getAllCardStates()
}
