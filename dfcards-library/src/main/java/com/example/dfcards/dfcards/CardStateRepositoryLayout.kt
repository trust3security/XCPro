package com.example.dfcards.dfcards

import com.example.dfcards.CardDefinition
import com.example.dfcards.CardLibrary
import com.example.dfcards.CardPreferences
import com.example.dfcards.CardPreferences.CardAnchor
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.FlightTemplates
import com.example.dfcards.dfcards.CardState
import com.example.dfcards.dfcards.FlightData
import com.trust3.xcpro.core.common.geometry.DensityScale
import com.trust3.xcpro.core.common.geometry.IntSizePx
import com.trust3.xcpro.core.common.geometry.dpToPx
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.ceil

private const val DEFAULT_CARD_WIDTH_DP = 120f
private const val DEFAULT_CARD_HEIGHT_DP = 80f
private const val LAYOUT_PADDING_DP = 0f
private const val LAYOUT_SPACING_DP = 0f
private const val MIN_CARD_WIDTH_DP = 24f
private const val MIN_CARD_HEIGHT_DP = 48f
private const val CARD_ASPECT_RATIO = 2f / 3f

private data class CardLayoutSpec(
    val cols: Int,
    val cardWidth: Float,
    val cardHeight: Float,
    val spacing: Float,
    val padding: Float,
    val anchor: CardAnchor
)

private data class CardLayoutPersistenceScope(
    val profileId: ProfileId,
    val flightMode: FlightModeSelection?
)

private fun CardStateRepository.rememberLayoutContext(containerSize: IntSizePx, density: DensityScale) {
    lastContainerSize = containerSize
    lastDensity = density
}

private fun CardStateRepository.captureLayoutScope(): CardLayoutPersistenceScope =
    CardLayoutPersistenceScope(
        profileId = activeProfileId,
        flightMode = currentFlightMode
    )

private fun CardStateRepository.snapshotScopedCardStates(): List<CardState> {
    val selectedIds = selectedCardIds.value
    return if (selectedIds.isEmpty()) {
        getAllCardStates()
    } else {
        selectedIds.mapNotNull { cardStateFlowsMap[it]?.value }.sortedBy { it.y * 10_000 + it.x }
    }
}

private suspend fun CardStateRepository.loadSavedPositions(
    preferences: CardPreferences,
    scopeState: CardLayoutPersistenceScope,
    cardIds: Set<String>
): Map<String, CardPreferences.CardPosition> {
    if (cardIds.isEmpty()) return emptyMap()

    val flightMode = scopeState.flightMode
    if (flightMode != null) {
        val scopedPositions = preferences.getProfileCardPositions(scopeState.profileId, flightMode.name)
            .first()
            .filterKeys { it in cardIds }
        if (scopedPositions.isNotEmpty()) {
            return scopedPositions
        }
    }

    if (scopeState.profileId != FlightVisibility.normalizeProfileId(null)) {
        return emptyMap()
    }

    return buildMap {
        cardIds.forEach { cardId ->
            val legacyPosition = preferences.getCardPosition(cardId).firstOrNull() ?: return@forEach
            put(cardId, legacyPosition)
        }
    }
}

private suspend fun CardStateRepository.persistScopedLayout(
    preferences: CardPreferences,
    scopeState: CardLayoutPersistenceScope,
    cardStates: List<CardState>
) {
    if (cardStates.isEmpty()) return

    val flightMode = scopeState.flightMode
    if (flightMode != null) {
        preferences.saveProfileCardPositions(scopeState.profileId, flightMode.name, cardStates)
        return
    }

    cardStates.forEach { preferences.saveCardPosition(it) }
}

private fun CardStateRepository.layoutSpec(containerSize: IntSizePx, density: DensityScale): CardLayoutSpec {
    val cols = cardsAcrossPortrait.coerceIn(
        CardPreferences.MIN_CARDS_ACROSS_PORTRAIT,
        CardPreferences.MAX_CARDS_ACROSS_PORTRAIT
    )
    val paddingPx = density.dpToPx(LAYOUT_PADDING_DP)
    val spacingPx = density.dpToPx(LAYOUT_SPACING_DP)
    val availableWidth = (containerSize.width - paddingPx * 2)
    val baseWidth = if (availableWidth > 0 && cols > 0) {
        (availableWidth - spacingPx * (cols - 1)) / cols
    } else {
        density.dpToPx(DEFAULT_CARD_WIDTH_DP)
    }
    val cardWidth = if (baseWidth > 0f) baseWidth else density.dpToPx(MIN_CARD_WIDTH_DP)
    val cardHeight = max(cardWidth * CARD_ASPECT_RATIO, density.dpToPx(MIN_CARD_HEIGHT_DP))
    return CardLayoutSpec(
        cols = max(cols, 1),
        cardWidth = cardWidth,
        cardHeight = cardHeight,
        spacing = spacingPx,
        padding = paddingPx,
        anchor = cardsAnchorPortrait
    )
}

private fun yOriginForRow(
    row: Int,
    totalRows: Int,
    containerSize: IntSizePx,
    layout: CardLayoutSpec
): Float {
    val rowsHeight = totalRows * layout.cardHeight + (totalRows - 1) * layout.spacing
    return when (layout.anchor) {
        CardAnchor.TOP -> layout.padding + row * (layout.cardHeight + layout.spacing)
        CardAnchor.BOTTOM -> (containerSize.height - layout.padding - rowsHeight) + row * (layout.cardHeight + layout.spacing)
    }
}

internal fun CardStateRepository.maybeRelayoutExistingCards() {
    val container = lastContainerSize ?: return
    val density = lastDensity ?: return
    if (cardStateFlowsMap.isEmpty()) return
    val layoutScope = captureLayoutScope()

    val layout = layoutSpec(container, density)
    val orderedCards = getAllCardStates()
    val totalRows = ceil(orderedCards.size / layout.cols.toFloat()).toInt().coerceAtLeast(1)

    orderedCards.forEachIndexed { index, card ->
        val row = index / layout.cols
        val col = index % layout.cols
        val x = layout.padding + col * (layout.cardWidth + layout.spacing)
        val y = yOriginForRow(row, totalRows, container, layout)
        val updated = card.copy(
            x = x,
            y = y,
            width = layout.cardWidth,
            height = layout.cardHeight
        )
        cardStateFlowsMap[card.id]?.value = updated
    }

    markLegacyStateDirty()
    val persistedStates = snapshotScopedCardStates()

    scope.launch {
        cardPreferences?.let { prefs ->
            persistScopedLayout(
                preferences = prefs,
                scopeState = layoutScope,
                cardStates = persistedStates
            )
        }
    }
}

internal fun CardStateRepository.initializeCards(containerSize: IntSizePx, density: DensityScale) {
    rememberLayoutContext(containerSize, density)
    if (hasInitialized || isInitializing) return
    hasInitialized = true
    restorePersistedPositions()
}

internal suspend fun CardStateRepository.loadEssentialCardsOnStartup(
    containerSize: IntSizePx,
    density: DensityScale,
    currentFlightMode: FlightModeSelection?
) {
    rememberLayoutContext(containerSize, density)
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
    containerSize: IntSizePx,
    density: DensityScale
) {
    rememberLayoutContext(containerSize, density)
    val layout = layoutSpec(containerSize, density)
    val totalRows = ceil(essentialCardIds.size / layout.cols.toFloat()).toInt().coerceAtLeast(1)

    essentialCardIds.forEachIndexed { index, cardId ->
        val cardDefinition = CardLibrary.allCards.find { it.id == cardId }
        if (cardDefinition != null) {
            val row = index / layout.cols
            val col = index % layout.cols
            val x = layout.padding + col * (layout.cardWidth + layout.spacing)
            val y = yOriginForRow(row, totalRows, containerSize, layout)

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
                width = layout.cardWidth,
                height = layout.cardHeight,
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
    val layoutScope = captureLayoutScope()

    cardStateFlowsMap[cardState.id]?.value = cardState
    val persistedStates = snapshotScopedCardStates()

    scope.launch {
        cardPreferences?.let { prefs ->
            // AI-NOTE: Persist the profile/mode snapshot captured at drag time so a fast
            // profile switch cannot write the moved card into the wrong profile scope.
            persistScopedLayout(
                preferences = prefs,
                scopeState = layoutScope,
                cardStates = persistedStates
            )
        }
    }

    manualPositioningTimeout = scope.launch {
        delay(2000)
        isManuallyPositioning = false
        manuallyPositionedCards.clear()
    }
}

internal fun CardStateRepository.toggleCardFromLibrary(
    cardDefinition: CardDefinition,
    containerSize: IntSizePx,
    density: DensityScale
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
    containerSize: IntSizePx,
    density: DensityScale
) {
    rememberLayoutContext(containerSize, density)
    val layout = layoutSpec(containerSize, density)
    val position = findNextAvailablePosition(containerSize, layout)

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
        width = layout.cardWidth,
        height = layout.cardHeight,
        flightData = flightData
    )

    cardStateFlowsMap[newCard.id] = MutableStateFlow(newCard)
    maybeRelayoutExistingCards()
}

internal fun CardStateRepository.ensureCardsExist(cardIds: Set<String>) {
    val container = lastContainerSize ?: return
    val density = lastDensity ?: return
    val layout = layoutSpec(container, density)
    val missing = cardIds.filterNot { cardStateFlowsMap.containsKey(it) }
    if (missing.isEmpty()) return

    missing.forEach { cardId ->
        val cardDefinition = CardLibrary.allCards.find { it.id == cardId } ?: return@forEach
        val position = findNextAvailablePosition(container, layout)

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
            width = layout.cardWidth,
            height = layout.cardHeight,
            flightData = flightData
        )
        cardStateFlowsMap[newCard.id] = MutableStateFlow(newCard)
    }

    markLegacyStateDirty()
}

private fun CardStateRepository.removeCard(cardId: String) {
    cardStateFlowsMap.remove(cardId)
}

internal fun CardStateRepository.restorePersistedPositions(cardIds: Set<String>? = null) {
    val preferences = cardPreferences ?: return
    val targetIds = cardIds ?: cardStateFlowsMap.keys.toSet()
    if (targetIds.isEmpty()) return
    val layoutScope = captureLayoutScope()
    scope.launch {
        val savedPositions = loadSavedPositions(
            preferences = preferences,
            scopeState = layoutScope,
            cardIds = targetIds
        )
        targetIds.forEach { cardId ->
            val saved = savedPositions[cardId] ?: return@forEach
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

internal suspend fun CardStateRepository.applyTemplate(
    template: FlightTemplate,
    containerSize: IntSizePx,
    density: DensityScale
) {
    applyTemplateInternal(template, containerSize, density, isAutoLoad = false)
}

private suspend fun CardStateRepository.applyTemplateInternal(
    template: FlightTemplate,
    containerSize: IntSizePx,
    density: DensityScale,
    isAutoLoad: Boolean
) {
    rememberLayoutContext(containerSize, density)
    val templateCards = createCardsFromTemplate(template, containerSize, density)

    templateCards.forEach { cardState ->
        val existing = cardStateFlowsMap[cardState.id]
        if (existing != null) {
            existing.value = existing.value.copy(
                x = cardState.x,
                y = cardState.y,
                width = cardState.width,
                height = cardState.height
            )
        } else {
            cardStateFlowsMap[cardState.id] = MutableStateFlow(cardState)
        }
    }

    setSelectedCardIds(template.cardIds.toSet())
    markLegacyStateDirty()
    lastRealTimeData?.let { updateCardsWithLiveData(it, forceVisible = true) }
}

private suspend fun CardStateRepository.createCardsFromTemplate(
    template: FlightTemplate,
    containerSize: IntSizePx,
    density: DensityScale
): List<CardState> {
    rememberLayoutContext(containerSize, density)
    val layout = layoutSpec(containerSize, density)
    val totalRows = ceil(template.cardIds.size / layout.cols.toFloat()).toInt().coerceAtLeast(1)

    return template.cardIds.mapIndexedNotNull { index, cardId ->
        val cardDefinition = CardLibrary.allCards.find { it.id == cardId }
        if (cardDefinition != null) {
            val row = index / layout.cols
            val col = index % layout.cols
            val defaultX = layout.padding + col * (layout.cardWidth + layout.spacing)
            val defaultY = yOriginForRow(row, totalRows, containerSize, layout)

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
                width = layout.cardWidth,
                height = layout.cardHeight,
                flightData = flightData
            )

            loadSavedCardPosition(defaultCardState)
        } else {
            null
        }
    }
}

private suspend fun CardStateRepository.loadSavedCardPosition(cardState: CardState): CardState {
    if (isManuallyPositioning && manuallyPositionedCards.contains(cardState.id)) {
        return cardStateFlowsMap[cardState.id]?.value ?: cardState
    }

    val preferences = cardPreferences ?: return cardState
    val savedPosition = loadSavedPositions(
        preferences = preferences,
        scopeState = captureLayoutScope(),
        cardIds = setOf(cardState.id)
    )[cardState.id]

    return savedPosition?.let {
        cardState.copy(
            x = it.x,
            y = it.y,
            width = it.width,
            height = it.height
        )
    } ?: cardState
}

private fun CardStateRepository.findNextAvailablePosition(
    containerSize: IntSizePx,
    layout: CardLayoutSpec
): Pair<Float, Float> {
    val currentCards = cardStateFlowsMap.values.map { it.value }

    val cols = layout.cols
    val cardWidth = layout.cardWidth
    val cardHeight = layout.cardHeight
    val padding = layout.padding
    val spacing = layout.spacing
    var row = 0
    var col = 0

    while (true) {
        val x = padding + col * (cardWidth + spacing)
        val virtualCount = currentCards.size + 1
        val totalRows = max(1, ((virtualCount + cols - 1) / cols))
        val y = yOriginForRow(row, totalRows, containerSize, layout)

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

        if (row > 200) {
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

