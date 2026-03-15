package com.example.dfcards

internal val cardCatalogSections: List<Pair<CardCategory, List<CardDefinition>>> = listOf(
    CardCategory.ESSENTIAL to essentialCards,
    CardCategory.VARIO to varioCards,
    CardCategory.NAVIGATION to navigationCards,
    CardCategory.PERFORMANCE to performanceCards,
    CardCategory.TIME_WEATHER to timeWeatherCards,
    CardCategory.COMPETITION to competitionCards,
    CardCategory.ADVANCED to advancedCards
)

internal val allCardDefinitions: List<CardDefinition> =
    cardCatalogSections.flatMap { it.second }

internal val cardsByCategory: Map<CardCategory, List<CardDefinition>> =
    cardCatalogSections.toMap()
