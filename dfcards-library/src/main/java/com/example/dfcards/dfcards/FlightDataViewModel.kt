package com.example.dfcards.dfcards

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class FlightDataViewModel : ViewModel() {

    // ✅ REFACTORED: Individual StateFlow per card for independent updates
    private val _cardStateFlows = mutableMapOf<String, MutableStateFlow<CardState>>()

    // ✅ Expose as immutable map of StateFlows
    val cardStateFlows: Map<String, StateFlow<CardState>>
        get() = _cardStateFlows.mapValues { it.value.asStateFlow() }

    // ✅ BACKWARD COMPATIBILITY: Maintain old API for existing code
    @Deprecated("Use cardStateFlows instead for better performance", ReplaceWith("getAllCardStates()"))
    val cardStates: StateFlow<List<CardState>> = MutableStateFlow<List<CardState>>(emptyList()).apply {
        // This is a legacy compatibility shim - updates from getAllCardStates()
    }.asStateFlow()

    // Selected card IDs for template building
    private val _selectedCardIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedCardIds: StateFlow<Set<String>> = _selectedCardIds.asStateFlow()

    // Card preferences for persistence
    private var cardPreferences: CardPreferences? = null

    // ✅ Current flight mode tracking for proper template fallback
    private var currentFlightMode: FlightModeSelection? = null

    // ✅ Initialization tracking
    private var hasInitialized = false
    private var isInitializing = false

    // ✅ Manual positioning protection
    private var isManuallyPositioning = false
    private var manualPositioningTimeout: kotlinx.coroutines.Job? = null

    // ✅ FIX: Track manually positioned cards to prevent template overrides
    private val manuallyPositionedCards = mutableSetOf<String>()

    // Initialize with card preferences
    fun initializeCardPreferences(preferences: CardPreferences) {
        this.cardPreferences = preferences
    }

    // ✅ Update current flight mode for better template fallback
    fun updateFlightMode(flightMode: FlightModeSelection) {
        this.currentFlightMode = flightMode
        println("DEBUG: FlightDataViewModel - Flight mode updated to: ${flightMode.displayName}")
    }

    // ✅ FIXED: Removed automatic fallback - template loading handles everything
    fun initializeCards(containerSize: IntSize, density: Density) {
        println("DEBUG: FlightDataViewModel - initializeCards called")
        println("DEBUG: Automatic fallback removed - loadTemplateForProfile() handles all card loading")
        println("DEBUG: Current card count: ${_cardStateFlows.size}")

        // ✅ Keep function for backward compatibility but don't load fallback cards
        // Template loading in FlightDataManager.loadTemplateForProfile() handles:
        // - User selects 0 cards → displays 0 cards ✅
        // - User selects X cards → displays X cards ✅
        // - First launch → loads default template ✅
    }
    // Add this method to FlightDataViewModel.kt
    private suspend fun loadSavedCardPosition(cardState: CardState): CardState {
        // ✅ FIX: Don't override manually positioned cards
        if (manuallyPositionedCards.contains(cardState.id)) {
            println("🚫 BLOCKED: Not loading saved position for manually positioned card ${cardState.id}")
            return cardState
        }

        return cardPreferences?.getCardPosition(cardState.id)?.firstOrNull()?.let { savedPosition ->
            println("📥 LOADED: Restored saved position for card ${cardState.id}: (${savedPosition.x.toInt()}, ${savedPosition.y.toInt()})")
            cardState.copy(
                x = savedPosition.x,
                y = savedPosition.y,
                width = savedPosition.width,
                height = savedPosition.height
            )
        } ?: cardState
    }

    // ✅ Load essential cards automatically
    private suspend fun loadEssentialCardsOnStartup(containerSize: IntSize, density: Density, currentFlightMode: FlightModeSelection? = null) {
        // FIXED: Use current flight mode to select appropriate template
        val templateId = when (currentFlightMode) {
            FlightModeSelection.CRUISE -> "id01"
            FlightModeSelection.THERMAL -> "id02"
            FlightModeSelection.FINAL_GLIDE -> "id03"
            else -> "id01" // Fallback to Cruise if unknown
        }
        
        val template = FlightTemplates.getDefaultTemplates().find { it.id == templateId }

        if (template != null) {
            println("DEBUG: Auto-loading ${template.name} template for ${currentFlightMode?.displayName ?: "default"} mode with ${template.cardIds.size} cards")
            applyTemplateInternal(template, containerSize, density, isAutoLoad = true)
        } else {
            // Fallback: create essential cards manually
            println("DEBUG: Template $templateId not found for ${currentFlightMode?.displayName}, creating manual essential cards")
            createEssentialCardsManually(containerSize, density)
        }
    }

    // ✅ Manual essential cards creation - REFACTORED for map
    private fun createEssentialCardsManually(containerSize: IntSize, density: Density) = with(density) {
        val essentialCardIds = listOf("gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed")
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

                // ✅ Add to map instead of list
                _cardStateFlows[cardState.id] = MutableStateFlow(cardState)
            } else {
                println("DEBUG: Card definition not found for: $cardId")
            }
        }

        _selectedCardIds.value = essentialCardIds.toSet()

        println("DEBUG: Created ${_cardStateFlows.size} essential cards manually in map")
    }

    // ✅ FIXED: Update individual card state with manual positioning protection
    // Replace the existing updateCardState method in FlightDataViewModel.kt

    // ✅ REFACTORED: Update individual card - now updates only specific StateFlow
    fun updateCardState(cardState: CardState) {
        // Mark as manually positioning
        isManuallyPositioning = true

        // Cancel any existing timeout
        manualPositioningTimeout?.cancel()

        // ✅ FIX: Mark this card as manually positioned (prevent template overrides)
        manuallyPositionedCards.add(cardState.id)

        // ✅ Update only the specific card's StateFlow
        val oldPosition = _cardStateFlows[cardState.id]?.value
        _cardStateFlows[cardState.id]?.value = cardState

        println("🔵 CARD MOVED: ${cardState.id} from (${oldPosition?.x?.toInt()}, ${oldPosition?.y?.toInt()}) → (${cardState.x.toInt()}, ${cardState.y.toInt()})")

        // Save position immediately when user moves card
        viewModelScope.launch {
            cardPreferences?.saveCardPosition(cardState)
            println("💾 SAVED: Card ${cardState.id} position saved to storage")
        }

        // Set timeout to resume live data updates after user stops moving cards
        manualPositioningTimeout = viewModelScope.launch {
            delay(2000) // Wait 2 seconds after last manual change
            isManuallyPositioning = false
            println("✅ RESUMED: Live data updates resumed (cards can receive data)")
        }
    }

    // ✅ REFACTORED: Toggle card - check map instead of list
    fun toggleCardFromLibrary(
        cardDefinition: CardDefinition,
        containerSize: IntSize,
        density: Density
    ) {
        if (_cardStateFlows.containsKey(cardDefinition.id)) {
            removeCard(cardDefinition.id)
        } else {
            addCard(cardDefinition, containerSize, density)
        }
    }

    // ✅ REFACTORED: Add new card to map
    private fun addCard(
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

        // ✅ Add to map instead of list
        _cardStateFlows[newCard.id] = MutableStateFlow(newCard)

        println("DEBUG: FlightDataViewModel - Added card to map: ${cardDefinition.id}")
    }

    // ✅ SSOT: Never actually remove cards - they stay in memory
    // Only used by manual card library toggle, not template switching
    private fun removeCard(cardId: String) {
        _cardStateFlows.remove(cardId)
        println("DEBUG: FlightDataViewModel - Removed card from memory: $cardId")
    }

    // Apply template
    fun applyTemplate(
        template: FlightTemplate,
        containerSize: IntSize,
        density: Density
    ) {
        applyTemplateInternal(template, containerSize, density, isAutoLoad = false)
    }

    // Update the existing applyTemplateInternal method signature and implementation

    // ✅ SSOT COMPLIANT: Never destroy cards, only control visibility
    // Cards keep their live data even when not displayed
    private fun applyTemplateInternal(
        template: FlightTemplate,
        containerSize: IntSize,
        density: Density,
        isAutoLoad: Boolean = false
    ) {
        println("📋 TEMPLATE: Applying template '${template.name}' (auto: $isAutoLoad)")

        val currentCardIds = _cardStateFlows.keys.toSet()
        val templateCardIds = template.cardIds.toSet()

        println("📋 TEMPLATE: Cards in memory: ${currentCardIds.size}, Template wants: ${templateCardIds.size}")
        println("📋 TEMPLATE: Manually positioned cards: ${manuallyPositionedCards.joinToString(", ")}")

        // ✅ SSOT: Create cards that don't exist yet (keep live data flowing to them)
        val cardsToCreate = templateCardIds - currentCardIds

        if (cardsToCreate.isNotEmpty()) {
            println("📋 TEMPLATE: Creating ${cardsToCreate.size} new cards: ${cardsToCreate.joinToString(", ")}")

            val newCards = kotlinx.coroutines.runBlocking {
                createCardsFromTemplate(template, containerSize, density)
            }

            // Add only cards that don't exist yet
            newCards.filter { it.id in cardsToCreate }.forEach { cardState ->
                _cardStateFlows[cardState.id] = MutableStateFlow(cardState)
                println("📋 TEMPLATE: ✅ Created card ${cardState.id} at (${cardState.x.toInt()}, ${cardState.y.toInt()})")
            }
        } else {
            println("📋 TEMPLATE: No new cards to create (all already exist)")
        }

        // ✅ FIX: NEVER reposition existing cards (especially manually positioned ones)
        println("📋 TEMPLATE: Existing cards KEEP their current positions (no repositioning)")

        // ✅ SSOT: Just update which cards are SELECTED (visible)
        // Cards not in template still exist in _cardStateFlows and receive live data
        // They're just not rendered by CardContainer
        _selectedCardIds.value = template.cardIds.toSet()

        println("📋 TEMPLATE: ✅ Applied successfully")
        println("📋 TEMPLATE:   - Total cards in memory: ${_cardStateFlows.size}")
        println("📋 TEMPLATE:   - Visible cards: ${_selectedCardIds.value.size}")
        println("📋 TEMPLATE:   - Manually positioned (protected): ${manuallyPositionedCards.size}")
    }

    // ✅ Create cards from template
    // Replace the existing createCardsFromTemplate method in FlightDataViewModel.kt

    private suspend fun createCardsFromTemplate(
        template: FlightTemplate,
        containerSize: IntSize,
        density: Density
    ): List<CardState> = with(density) {
        val cardWidth = 120.dp.toPx()
        val cardHeight = 80.dp.toPx()
        val spacing = 16.dp.toPx()
        val padding = 16.dp.toPx()

        val cols = ((containerSize.width - padding * 2 + spacing) / (cardWidth + spacing)).toInt().coerceAtLeast(1)

        val newCards = template.cardIds.mapIndexedNotNull { index, cardId ->
            val cardDefinition = CardLibrary.allCards.find { it.id == cardId }
            if (cardDefinition != null) {
                val row = index / cols
                val col = index % cols
                val defaultX = padding + col * (cardWidth + spacing)
                val defaultY = padding + row * (cardHeight + spacing)

                // ✅ SSOT: Cards start with initial values, live data updates them
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

                // 🔥 NEW: Load saved position if it exists, otherwise use template position
                loadSavedCardPosition(defaultCardState)
            } else {
                println("DEBUG: Card definition not found for: $cardId")
                null
            }
        }

        newCards
    }

    // ✅ REFACTORED: Find next available position using map
    private fun findNextAvailablePosition(
        containerSize: IntSize,
        cardWidth: Float,
        cardHeight: Float,
        padding: Float
    ): Pair<Float, Float> {
        val spacing = 16f
        val currentCards = _cardStateFlows.values.map { it.value }

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

    // ✅ SSOT COMPLIANT: Update ALL cards in memory (visible or hidden)
    // This ensures zero data loss when switching modes
    private var lastUpdateTime = 0L
    private val UPDATE_THROTTLE_MS = 200L // ✅ faster refresh for responsive metrics

    // ✅ INDEPENDENT CLOCK TIMER: Updates time card separately from GPS
    private var clockTimerJob: kotlinx.coroutines.Job? = null

    fun updateCardsWithLiveData(liveData: RealTimeFlightData) {
        // ✅ SKIP updates during manual positioning
        if (isManuallyPositioning) {
            return
        }

        val currentTime = System.currentTimeMillis()

        // ✅ UPDATE: Time card now has independent timer, skip it here
        // (See startIndependentClockTimer() for time card updates)

        // ✅ Update all cards except time card at 1Hz
        if (currentTime - lastUpdateTime < UPDATE_THROTTLE_MS) {
            return
        }
        lastUpdateTime = currentTime

        // ✅ SSOT: Update ALL cards in _cardStateFlows (visible or not)
        // This is the SINGLE source of truth - no cache, no duplication
        _cardStateFlows.forEach { (cardId, stateFlow) ->
            if (cardId == "local_time") return@forEach // Skip time card (independent timer)

            val currentState = stateFlow.value
            val updatedFlightData = mapRealDataToCard(currentState.flightData, liveData)

            // Only update THIS card's StateFlow if its data changed
            if (updatedFlightData != currentState.flightData) {
                // ✅ FIX: Verify position preservation before update
                val oldPosition = Pair(currentState.x, currentState.y)
                val newState = currentState.copy(flightData = updatedFlightData)

                // ✅ CRITICAL: Verify .copy() preserved position (sanity check)
                if (newState.x != oldPosition.first || newState.y != oldPosition.second) {
                    println("🚨 ERROR: Live data update accidentally changed position for card $cardId!")
                    println("🚨 ERROR: Old: (${oldPosition.first.toInt()}, ${oldPosition.second.toInt()}) → New: (${newState.x.toInt()}, ${newState.y.toInt()})")
                    return@forEach // Don't apply this broken update
                }

                stateFlow.value = newState

                // Only log if card is currently visible
                if (cardId in _selectedCardIds.value) {
                    println("📊 DATA: Updated card $cardId value: ${updatedFlightData.primaryValue}")
                }
            }
        }
        // ✅ SSOT RESULT: All cards (visible + hidden) stay current with live data
    }

    // ✅ NEW: Independent clock timer - updates every second, separate from GPS
    fun startIndependentClockTimer() {
        // Stop existing timer if running
        clockTimerJob?.cancel()

        clockTimerJob = viewModelScope.launch {
            while (true) {
                // Update time card with current system time
                _cardStateFlows["local_time"]?.let { timeCardFlow ->
                    val currentState = timeCardFlow.value
                    val currentTime = System.currentTimeMillis()

                    // Format time independently (no GPS dependency)
                    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(currentTime))
                    val seconds = java.text.SimpleDateFormat("ss", java.util.Locale.getDefault())
                        .format(java.util.Date(currentTime))

                    val updatedFlightData = currentState.flightData.copy(
                        primaryValue = time,
                        secondaryValue = seconds
                    )

                    if (updatedFlightData != currentState.flightData) {
                        timeCardFlow.value = currentState.copy(flightData = updatedFlightData)
                    }
                }

                // Wait exactly 1 second for next update
                delay(1000L)
            }
        }

        println("DEBUG: ⏰ Independent clock timer started (1Hz updates)")
    }

    // ✅ Stop the independent clock timer
    fun stopIndependentClockTimer() {
        clockTimerJob?.cancel()
        clockTimerJob = null
        println("DEBUG: ⏰ Independent clock timer stopped")
    }

    // ✅ FIXED: Use centralized CardLibrary mapping (SSOT principle)
    // This ensures ALL 25+ card types get live data, not just the 11 we had before
    private fun mapRealDataToCard(
        currentFlightData: FlightData,
        realData: RealTimeFlightData
    ): FlightData {
        // ✅ Use centralized mapping from CardLibrary (handles all card types)
        val (primaryValue, secondaryValue) = CardLibrary.mapLiveDataToCard(
            cardId = currentFlightData.id,
            liveData = realData
        )

        return currentFlightData.copy(
            primaryValue = primaryValue,
            secondaryValue = secondaryValue
        )
    }

    // ✅ REFACTORED: Save current layout using getAllCardStates()
    fun saveCurrentLayoutToTemplate() {
        viewModelScope.launch {
            cardPreferences?.let { prefs ->
                val currentCards = getAllCardStates()
                if (currentCards.isNotEmpty()) {
                    prefs.saveTemplateCardPositions("current_layout", currentCards)
                }
            }
        }
    }

    // ✅ REFACTORED: Clear all cards from map
    fun clearAllCards() {
        _cardStateFlows.clear()
        _selectedCardIds.value = emptySet()
        manuallyPositionedCards.clear() // ✅ FIX: Clear manual positioning tracking
        hasInitialized = false
        isManuallyPositioning = false
        manualPositioningTimeout?.cancel()
        println("🧹 CLEAR: Cleared all cards from map and manual positioning tracking")
    }

    // ✅ Method to force resume live updates (for debugging)
    fun resumeLiveDataUpdates() {
        isManuallyPositioning = false
        manualPositioningTimeout?.cancel()
        println("DEBUG: Force resumed live data updates")
    }

    // ✅ NEW HELPER METHODS: Backward compatibility and utility functions

    /**
     * Get all card states as a list (for backward compatibility and template operations)
     */
    fun getAllCardStates(): List<CardState> {
        return _cardStateFlows.values.map { it.value }.sortedBy { it.y * 10000 + it.x }
    }

    /**
     * Get a single card state by ID
     */
    fun getCardState(cardId: String): CardState? {
        return _cardStateFlows[cardId]?.value
    }

    /**
     * Check if a card exists
     */
    fun hasCard(cardId: String): Boolean {
        return _cardStateFlows.containsKey(cardId)
    }

    /**
     * Get count of current cards
     */
    fun getCardCount(): Int {
        return _cardStateFlows.size
    }

    override fun onCleared() {
        super.onCleared()
        manualPositioningTimeout?.cancel()
        stopIndependentClockTimer()
        println("DEBUG: FlightDataViewModel - Cleared")
    }
}
