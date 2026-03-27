package com.example.dfcards

import com.example.xcpro.common.units.UnitsPreferences

object CardLibrary {
    val allCards: List<CardDefinition> = allCardDefinitions
    private val defaultTimeFormatter = SystemCardTimeFormatter()
    private val defaultStrings = DefaultCardStrings()
    // AI-NOTE: Keep unsupported placeholder cards in the known-definition registry so
    // persisted layouts can still resolve them, but do not offer them in production selection
    // until an authoritative runtime seam exists.
    private val productionHiddenPlaceholderCardIds: Set<String> = setOf(
        CardId.WPT_DIST,
        CardId.WPT_BRG,
        CardId.WPT_ETA,
        CardId.TASK_SPD,
        CardId.TASK_DIST,
        CardId.START_ALT
    )

    fun getCardsByCategory(category: CardCategory, hiddenCardIds: Set<String> = emptySet()): List<CardDefinition> {
        val cards = cardsByCategory[category].orEmpty()
        return cards.filterForProductionSelection(hiddenCardIds)
    }

    fun searchCards(query: String, hiddenCardIds: Set<String> = emptySet()): List<CardDefinition> {
        val results = allCards.filter { definition ->
            definition.title.contains(query, ignoreCase = true) ||
                definition.description.contains(query, ignoreCase = true)
        }
        return results.filterForProductionSelection(hiddenCardIds)
    }

    fun mapLiveDataToCard(
        cardId: String,
        liveData: RealTimeFlightData?,
        units: UnitsPreferences = UnitsPreferences(),
        strings: CardStrings = defaultStrings,
        timeFormatter: CardTimeFormatter = defaultTimeFormatter
    ): Pair<String, String?> =
        CardDataFormatter.mapLiveDataToCard(CardId.fromRaw(cardId), liveData, units, strings, timeFormatter)

    private fun List<CardDefinition>.filterForProductionSelection(hiddenCardIds: Set<String>): List<CardDefinition> {
        val hiddenIds = hiddenCardIds + productionHiddenPlaceholderCardIds
        return if (hiddenIds.isEmpty()) this else filterNot { it.id in hiddenIds }
    }
}
