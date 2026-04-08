package com.example.dfcards.dfcards

import com.example.dfcards.CardDefinition
import com.example.dfcards.CardPreferences
import com.example.dfcards.CardStrings
import com.example.dfcards.CardTimeFormatter
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.IntSizePx
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.core.time.Clock
import com.example.xcpro.core.time.DefaultClockProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Use-case wrapper around card state storage and derivations.
 */
class FlightCardsUseCase(
    scope: CoroutineScope,
    clock: Clock = DefaultClockProvider()
) {
    private val repository = CardStateRepository(scope, clock)
    private val derivations = FlightDataDerivations(repository)

    val cardStateFlows: Map<String, StateFlow<CardState>>
        get() = derivations.cardStateFlows
    val legacyCardStates: StateFlow<List<CardState>> = derivations.legacyCardStates
    val selectedCardIds: StateFlow<Set<String>> = derivations.selectedCardIds

    fun setPreferences(preferences: CardPreferences) {
        derivations.setPreferences(preferences)
    }

    fun setSelected(ids: Set<String>) {
        derivations.setSelected(ids)
    }

    fun getAllStates(): List<CardState> = derivations.getAllStates()

    fun updateFlightMode(mode: FlightModeSelection) {
        repository.updateFlightMode(mode)
    }

    fun setActiveProfile(profileId: ProfileId?) {
        repository.updateActiveProfile(profileId)
    }

    fun initializeCards(containerSize: IntSizePx, density: DensityScale) {
        repository.initializeCards(containerSize, density)
    }

    suspend fun loadEssentialCardsOnStartup(
        containerSize: IntSizePx,
        density: DensityScale,
        flightMode: FlightModeSelection?
    ) {
        repository.loadEssentialCardsOnStartup(containerSize, density, flightMode)
    }

    fun updateCardState(cardState: CardState) {
        repository.updateCardState(cardState)
    }

    fun toggleCardFromLibrary(
        cardDefinition: CardDefinition,
        containerSize: IntSizePx,
        density: DensityScale
    ) {
        repository.toggleCardFromLibrary(cardDefinition, containerSize, density)
    }

    suspend fun applyTemplate(
        template: FlightTemplate,
        containerSize: IntSizePx,
        density: DensityScale
    ) {
        repository.applyTemplate(template, containerSize, density)
    }

    fun updateCardsWithLiveData(liveData: RealTimeFlightData) {
        repository.updateCardsWithLiveData(liveData)
    }

    fun startIndependentClockTimer() {
        repository.startIndependentClockTimer()
    }

    fun stopIndependentClockTimer() {
        repository.stopIndependentClockTimer()
    }

    fun saveCurrentLayoutToTemplate() {
        repository.saveCurrentLayoutToTemplate()
    }

    fun clearAllCards() {
        repository.clearAllCards()
    }

    fun resumeLiveUpdates() {
        repository.resumeLiveUpdates()
    }

    fun getAllCardStates(): List<CardState> = repository.getAllCardStates()

    fun getCardState(cardId: String): CardState? = repository.getCardState(cardId)

    fun hasCard(cardId: String): Boolean = repository.hasCard(cardId)

    fun getCardCount(): Int = repository.getCardCount()

    fun updateUnitsPreferences(preferences: UnitsPreferences) {
        repository.updateUnitsPreferences(preferences)
    }

    fun updateCardStrings(strings: CardStrings) {
        repository.updateCardStrings(strings)
    }

    fun updateCardTimeFormatter(formatter: CardTimeFormatter) {
        repository.updateCardTimeFormatter(formatter)
    }

    fun ensureCardsExist(cardIds: Set<String>) {
        repository.ensureCardsExist(cardIds)
    }

    fun onCleared() {
        repository.onCleared()
    }
}
