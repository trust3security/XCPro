package com.example.dfcards.dfcards

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.CardDefinition
import com.example.dfcards.dfcards.CardState
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.flow.StateFlow

class FlightDataViewModel : ViewModel() {

    private val repository = CardStateRepository(viewModelScope)

    val cardStateFlows: Map<String, StateFlow<CardState>>
        get() = repository.cardStateFlows

    @Deprecated(
        message = "Use cardStateFlows instead for better performance",
        replaceWith = ReplaceWith("cardStateFlows")
    )
    val cardStates: StateFlow<List<CardState>> = repository.legacyCardStates

    val selectedCardIds: StateFlow<Set<String>> = repository.selectedCardIds

    fun initializeCardPreferences(preferences: CardPreferences) {
        repository.setCardPreferences(preferences)
    }

    fun updateFlightMode(flightMode: FlightModeSelection) {
        repository.updateFlightMode(flightMode)
        println("DEBUG: FlightDataViewModel - Flight mode updated to: ${flightMode.displayName}")
    }

    fun initializeCards(containerSize: IntSize, density: Density) {
        repository.initializeCards(containerSize, density)
    }

    suspend fun loadEssentialCardsOnStartup(
        containerSize: IntSize,
        density: Density,
        flightMode: FlightModeSelection? = null
    ) {
        repository.loadEssentialCardsOnStartup(containerSize, density, flightMode)
    }

    fun updateCardState(cardState: CardState) {
        repository.updateCardState(cardState)
    }

    fun toggleCardFromLibrary(
        cardDefinition: CardDefinition,
        containerSize: IntSize,
        density: Density
    ) {
        repository.toggleCardFromLibrary(cardDefinition, containerSize, density)
    }

    fun applyTemplate(
        template: FlightTemplate,
        containerSize: IntSize,
        density: Density
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

    fun resumeLiveDataUpdates() {
        repository.resumeLiveUpdates()
    }

    fun getAllCardStates(): List<CardState> = repository.getAllCardStates()

    fun getCardState(cardId: String): CardState? = repository.getCardState(cardId)

    fun hasCard(cardId: String): Boolean = repository.hasCard(cardId)

    fun getCardCount(): Int = repository.getCardCount()

    fun updateUnitsPreferences(preferences: UnitsPreferences) {
        repository.updateUnitsPreferences(preferences)
    }

    override fun onCleared() {
        repository.onCleared()
        super.onCleared()
    }
}
