package com.example.dfcards

import com.example.xcpro.common.units.UnitsPreferences

object CardLibrary {
    val allCards: List<CardDefinition> = allCardDefinitions
    private val defaultTimeFormatter = SystemCardTimeFormatter()
    private val defaultStrings = DefaultCardStrings()

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
        units: UnitsPreferences = UnitsPreferences(),
        strings: CardStrings = defaultStrings,
        timeFormatter: CardTimeFormatter = defaultTimeFormatter
    ): Pair<String, String?> =
        CardDataFormatter.mapLiveDataToCard(CardId.fromRaw(cardId), liveData, units, strings, timeFormatter)
}
