package com.example.dfcards

import com.example.xcpro.common.units.UnitsPreferences

object CardLibrary {
    val allCards: List<CardDefinition> = allCardDefinitions

    fun getCardsByCategory(category: CardCategory): List<CardDefinition> =
        cardsByCategory[category].orEmpty()

    fun searchCards(query: String): List<CardDefinition> =
        allCards.filter { definition ->
            definition.title.contains(query, ignoreCase = true) ||
                definition.description.contains(query, ignoreCase = true)
        }

    fun mapLiveDataToCard(
        cardId: String,
        liveData: RealTimeFlightData?,
        units: UnitsPreferences = UnitsPreferences()
    ): Pair<String, String?> = CardDataFormatter.mapLiveDataToCard(cardId, liveData, units)
}
