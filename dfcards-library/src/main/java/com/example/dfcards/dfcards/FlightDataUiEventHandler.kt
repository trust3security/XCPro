package com.example.dfcards.dfcards

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal object FlightDataUiEventHandler {
    fun syncSelectedIdsWithRepository(
        profileModeCards: StateFlow<Map<ProfileId, Map<FlightModeSelection, List<String>>>>,
        activeProfileId: StateFlow<ProfileId?>,
        currentFlightMode: StateFlow<FlightModeSelection>,
        cardsUseCase: FlightCardsUseCase,
        setProfileCards: (ProfileId, FlightModeSelection, List<String>) -> Unit
    ) {
        val profileId = FlightVisibility.normalizeProfileId(activeProfileId.value)
        val mode = currentFlightMode.value
        val desiredIds = profileModeCards.value[profileId]?.get(mode)
        if (desiredIds != null) {
            cardsUseCase.setSelected(desiredIds.toSet())
        } else {
            val states = cardsUseCase.getAllStates()
            val ids = states.map { it.id }
            if (ids.isNotEmpty()) {
                setProfileCards(profileId, mode, ids)
            }
        }
    }

    fun persistActiveCards(
        activeProfileId: StateFlow<ProfileId?>,
        currentFlightMode: StateFlow<FlightModeSelection>,
        cardsUseCase: FlightCardsUseCase,
        setProfileCards: (ProfileId, FlightModeSelection, List<String>) -> Unit
    ) {
        val profileId = FlightVisibility.normalizeProfileId(activeProfileId.value)
        val mode = currentFlightMode.value
        val ids = cardsUseCase.selectedCardIds.value.toList()
        setProfileCards(profileId, mode, ids)
    }

    fun persistTemplateSelection(
        activeProfileId: StateFlow<ProfileId?>,
        currentFlightMode: StateFlow<FlightModeSelection>,
        setProfileTemplate: (ProfileId, FlightModeSelection, String) -> Unit,
        templateId: String
    ) {
        val profileId = FlightVisibility.normalizeProfileId(activeProfileId.value)
        val mode = currentFlightMode.value
        setProfileTemplate(profileId, mode, templateId)
    }

    fun persistProfileCards(
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher,
        cardPreferences: StateFlow<CardPreferences?>,
        profileId: ProfileId,
        templateId: String,
        cardIds: List<String>
    ) {
        val preferences = cardPreferences.value ?: return
        scope.launch(ioDispatcher) {
            preferences.saveProfileTemplateCards(profileId, templateId, cardIds)
        }
    }

    fun persistProfileTemplate(
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher,
        cardPreferences: StateFlow<CardPreferences?>,
        profileId: ProfileId,
        flightMode: FlightModeSelection,
        templateId: String
    ) {
        val preferences = cardPreferences.value ?: return
        scope.launch(ioDispatcher) {
            preferences.saveProfileFlightModeTemplate(profileId, flightMode.name, templateId)
        }
    }

    fun clearProfile(
        profileId: ProfileId,
        profileModeCards: MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, List<String>>>>,
        profileModeTemplates: MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, String>>>,
        activeProfileId: MutableStateFlow<ProfileId?>,
        cardsUseCase: FlightCardsUseCase,
        cardPreferences: StateFlow<CardPreferences?>,
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher
    ) {
        profileModeCards.value = profileModeCards.value.toMutableMap().apply {
            remove(profileId)
        }
        profileModeTemplates.value = profileModeTemplates.value.toMutableMap().apply {
            remove(profileId)
        }
        if (activeProfileId.value == profileId) {
            activeProfileId.value = null
            cardsUseCase.setSelected(emptySet())
        }
        scope.launch(ioDispatcher) {
            cardPreferences.value?.clearProfile(profileId)
        }
    }
}
