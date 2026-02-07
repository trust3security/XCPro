package com.example.dfcards

import com.example.xcpro.common.units.UnitsPreferences

object CardLibrary {
    val allCards: List<CardDefinition> = allCardDefinitions
    private val defaultTimeFormatter = SystemCardTimeFormatter()
    private val defaultStrings = DefaultCardStrings()

    fun getCardsByCategory(category: CardCategory, hiddenCardIds: Set<String> = emptySet()): List<CardDefinition> {
        val cards = cardsByCategory[category].orEmpty()
        return if (hiddenCardIds.isEmpty()) cards else cards.filterNot { it.id in hiddenCardIds }
    }

    fun searchCards(query: String, hiddenCardIds: Set<String> = emptySet()): List<CardDefinition> {
        val results = allCards.filter { definition ->
            definition.title.contains(query, ignoreCase = true) ||
                definition.description.contains(query, ignoreCase = true)
        }
        return if (hiddenCardIds.isEmpty()) results else results.filterNot { it.id in hiddenCardIds }
    }

    fun mapLiveDataToCard(
        cardId: String,
        liveData: RealTimeFlightData?,
        units: UnitsPreferences = UnitsPreferences(),
        strings: CardStrings = defaultStrings,
        timeFormatter: CardTimeFormatter = defaultTimeFormatter
    ): Pair<String, String?> =
        CardDataFormatter.mapLiveDataToCard(CardId.fromRaw(cardId), liveData, units, strings, timeFormatter)
}
