package com.example.dfcards.dfcards

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.dfcards.CardDefinition
import com.example.dfcards.CardLibrary
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.FlightTemplates
import com.example.dfcards.dfcards.CardState
import com.example.dfcards.dfcards.FlightData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal fun CardStateRepository.initializeCards(containerSize: IntSize, density: Density) {
    if (hasInitialized || isInitializing) return
    hasInitialized = true
    restorePersistedPositions()
}

internal suspend fun CardStateRepository.loadEssentialCardsOnStartup(
    containerSize: IntSize,
    density: Density,
    currentFlightMode: FlightModeSelection?
) {
    val templateId = when (currentFlightMode) {
        FlightModeSelection.CRUISE -> "id01"
        FlightModeSelection.THERMAL -> "id02"
        FlightModeSelection.FINAL_GLIDE -> "id03"
        else -> "id01"
    }

    val template = FlightTemplates.getDefaultTemplates().find { it.id == templateId }

    if (template != null) {
        applyTemplateInternal(template, containerSize, density, isAutoLoad = true)
    } else {
        createEssentialCardsManually(containerSize, density)
    }
}

private fun CardStateRepository.createEssentialCardsManually(
    containerSize: IntSize,
    density: Density
) = with(density) {
    val cardWidth = 120.dp.toPx()
    val cardHeight = 80.dp.toPx()
    val spacing = 16.dp.toPx()
    val padding = 16.dp.toPx()

    val cols = ((containerSize.width - padding * 2 + spacing) / (cardWidth + spacing)).toInt().coerceAtLeast(1)

    essentialCardIds.forEachIndexed { index, cardId ->
        val cardDefinition = CardLibrary.allCards.find { it.id == cardId }
        if (cardDefinition != null) {
            val row = index / cols
            val col = index % cols
            val x = padding + col * (cardWidth + spacing)
            val y = padding + row * (cardHeight + spacing)

            val flightData = FlightData(
                id = cardDefinition.id,
                label = cardDefinition.title,
                primaryValue = "--",
                secondaryValue = "INIT",
                labelFontSize = cardDefinition.labelFontSize,
                primaryFontSize = cardDefinition.primaryFontSize,
                secondaryFontSize = cardDefinition.secondaryFontSize
            )

            val cardState = CardState(
                id = cardDefinition.id,
                x = x,
                y = y,
                width = cardWidth,
                height = cardHeight,
                flightData = flightData
            )

    cardStateFlowsMap[cardState.id] = MutableStateFlow(cardState)
    restorePersistedPositions(setOf(cardState.id))
}
    }

    setSelectedCardIds(essentialCardIds.toSet())
    markLegacyStateDirty()
    lastRealTimeData?.let { updateCardsWithLiveData(it, forceVisible = true) }
}

internal fun CardStateRepository.updateCardState(cardState: CardState) {
    isManuallyPositioning = true
    manualPositioningTimeout?.cancel()
    manuallyPositionedCards.add(cardState.id)

    val oldPosition = cardStateFlowsMap[cardState.id]?.value
    cardStateFlowsMap[cardState.id]?.value = cardState

    scope.launch {
        cardPreferences?.saveCardPosition(cardState)
    }

    manualPositioningTimeout = scope.launch {
        delay(2000)
        isManuallyPositioning = false
    }
}

internal fun CardStateRepository.toggleCardFromLibrary(
    cardDefinition: CardDefinition,
    containerSize: IntSize,
    density: Density
) {
    if (cardStateFlowsMap.containsKey(cardDefinition.id)) {
        removeCard(cardDefinition.id)
    } else {
        addCard(cardDefinition, containerSize, density)
    }
    markLegacyStateDirty()
}

private fun CardStateRepository.addCard(
    cardDefinition: CardDefinition,
    containerSize: IntSize,
    density: Density
) = with(density) {
    val cardWidth = 120.dp.toPx()
    val cardHeight = 80.dp.toPx()
    val padding = 16.dp.toPx()

    val position = findNextAvailablePosition(containerSize, cardWidth, cardHeight, padding)

    val flightData = FlightData(
        id = cardDefinition.id,
        label = cardDefinition.title,
        primaryValue = "--",
        secondaryValue = "NEW",
        labelFontSize = cardDefinition.labelFontSize,
        primaryFontSize = cardDefinition.primaryFontSize,
        secondaryFontSize = cardDefinition.secondaryFontSize
    )

    val newCard = CardState(
        id = cardDefinition.id,
        x = position.first,
        y = position.second,
        width = cardWidth,
        height = cardHeight,
        flightData = flightData
    )

    cardStateFlowsMap[newCard.id] = MutableStateFlow(newCard)
}

private fun CardStateRepository.removeCard(cardId: String) {
    cardStateFlowsMap.remove(cardId)
}

internal fun CardStateRepository.restorePersistedPositions(cardIds: Set<String>? = null) {
    val preferences = cardPreferences ?: return
    val targetIds = cardIds ?: cardStateFlowsMap.keys.toSet()
    if (targetIds.isEmpty()) return
    scope.launch {
        targetIds.forEach { cardId ->
            val saved = preferences.getCardPosition(cardId).firstOrNull() ?: return@forEach
            cardStateFlowsMap[cardId]?.let { stateFlow ->
                stateFlow.value = stateFlow.value.copy(
                    x = saved.x,
                    y = saved.y,
                    width = saved.width,
                    height = saved.height
                )
            }
        }
    }
}

internal fun CardStateRepository.applyTemplate(
    template: FlightTemplate,
    containerSize: IntSize,
    density: Density
) {
    applyTemplateInternal(template, containerSize, density, isAutoLoad = false)
}

private fun CardStateRepository.applyTemplateInternal(
    template: FlightTemplate,
    containerSize: IntSize,
    density: Density,
    isAutoLoad: Boolean
) {
    val currentCardIds = cardStateFlowsMap.keys.toSet()
    val templateCardIds = template.cardIds.toSet()

    val cardsToCreate = templateCardIds - currentCardIds

    if (cardsToCreate.isNotEmpty()) {
        val newCards = runBlocking {
            createCardsFromTemplate(template, containerSize, density)
        }

        newCards.filter { it.id in cardsToCreate }.forEach { cardState ->
            cardStateFlowsMap[cardState.id] = MutableStateFlow(cardState)
        }
    }

    setSelectedCardIds(template.cardIds.toSet())
    markLegacyStateDirty()
    lastRealTimeData?.let { updateCardsWithLiveData(it, forceVisible = true) }
}

private suspend fun CardStateRepository.createCardsFromTemplate(
    template: FlightTemplate,
    containerSize: IntSize,
    density: Density
): List<CardState> = with(density) {
    val cardWidth = 120.dp.toPx()
    val cardHeight = 80.dp.toPx()
    val spacing = 16.dp.toPx()
    val padding = 16.dp.toPx()

    val cols = ((containerSize.width - padding * 2 + spacing) / (cardWidth + spacing)).toInt().coerceAtLeast(1)

    template.cardIds.mapIndexedNotNull { index, cardId ->
        val cardDefinition = CardLibrary.allCards.find { it.id == cardId }
        if (cardDefinition != null) {
            val row = index / cols
            val col = index % cols
            val defaultX = padding + col * (cardWidth + spacing)
            val defaultY = padding + row * (cardHeight + spacing)

            val flightData = FlightData(
                id = cardDefinition.id,
                label = cardDefinition.title,
                primaryValue = "--",
                secondaryValue = "INIT",
                labelFontSize = cardDefinition.labelFontSize,
                primaryFontSize = cardDefinition.primaryFontSize,
                secondaryFontSize = cardDefinition.secondaryFontSize
            )

            val defaultCardState = CardState(
                id = cardDefinition.id,
                x = defaultX,
                y = defaultY,
                width = cardWidth,
                height = cardHeight,
                flightData = flightData
            )

            loadSavedCardPosition(defaultCardState)
        } else {
            null
        }
    }
}

private suspend fun CardStateRepository.loadSavedCardPosition(cardState: CardState): CardState {
    if (manuallyPositionedCards.contains(cardState.id)) {
        return cardState
    }

    return cardPreferences?.getCardPosition(cardState.id)?.firstOrNull()?.let { savedPosition ->
        cardState.copy(
            x = savedPosition.x,
            y = savedPosition.y,
            width = savedPosition.width,
            height = savedPosition.height
        )
    } ?: cardState
}

private fun CardStateRepository.findNextAvailablePosition(
    containerSize: IntSize,
    cardWidth: Float,
    cardHeight: Float,
    padding: Float
): Pair<Float, Float> {
    val spacing = 16f
    val currentCards = cardStateFlowsMap.values.map { it.value }

    val cols = ((containerSize.width - padding * 2 + spacing) / (cardWidth + spacing)).toInt().coerceAtLeast(1)
    var row = 0
    var col = 0

    while (true) {
        val x = padding + col * (cardWidth + spacing)
        val y = padding + row * (cardHeight + spacing)

        val isOccupied = currentCards.any { card ->
            val cardRight = card.x + card.width
            val cardBottom = card.y + card.height
            val testRight = x + cardWidth
            val testBottom = y + cardHeight

            !(x >= cardRight || testRight <= card.x || y >= cardBottom || testBottom <= card.y)
        }

        if (!isOccupied && x + cardWidth <= containerSize.width && y + cardHeight <= containerSize.height) {
            return Pair(x, y)
        }

        col++
        if (col >= cols) {
            col = 0
            row++
        }

        if (row > 20) {
            return Pair(padding, padding)
        }
    }
}

internal fun CardStateRepository.saveCurrentLayoutToTemplate() {
    scope.launch {
        cardPreferences?.let { prefs ->
            val currentCards = getAllCardStates()
            if (currentCards.isNotEmpty()) {
                prefs.saveTemplateCardPositions("current_layout", currentCards)
            }
        }
    }
}

internal fun CardStateRepository.getAllCardStates(): List<CardState> =
    cardStateFlowsMap.values.map { it.value }.sortedBy { it.y * 10000 + it.x }

internal fun CardStateRepository.getCardState(cardId: String): CardState? =
    cardStateFlowsMap[cardId]?.value

internal fun CardStateRepository.hasCard(cardId: String): Boolean =
    cardStateFlowsMap.containsKey(cardId)

internal fun CardStateRepository.getCardCount(): Int = cardStateFlowsMap.size
