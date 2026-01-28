package com.example.dfcards.dfcards

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal object FlightDataFlowBuilder {
    fun activeCardIds(
        profileModeCards: StateFlow<Map<ProfileId, Map<FlightModeSelection, List<String>>>>,
        activeProfileId: StateFlow<ProfileId?>,
        currentFlightMode: StateFlow<FlightModeSelection>,
        scope: CoroutineScope
    ): StateFlow<List<String>> =
        combine(profileModeCards, activeProfileId, currentFlightMode) { cardsMap, profileId, mode ->
            cardsMap[FlightVisibility.normalizeProfileId(profileId)]?.get(mode).orEmpty()
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun activeCards(
        activeCardIds: StateFlow<List<String>>,
        allStates: StateFlow<List<CardState>>,
        scope: CoroutineScope
    ): StateFlow<List<CardState>> =
        combine(activeCardIds, allStates) { desiredIds, allStatesList ->
            if (desiredIds.isEmpty()) {
                emptyList()
            } else {
                val stateMap = allStatesList.associateBy { it.id }
                desiredIds.mapNotNull { stateMap[it] }
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun activeTemplateId(
        profileModeTemplates: StateFlow<Map<ProfileId, Map<FlightModeSelection, String>>>,
        activeProfileId: StateFlow<ProfileId?>,
        currentFlightMode: StateFlow<FlightModeSelection>,
        scope: CoroutineScope
    ): StateFlow<String?> =
        combine(profileModeTemplates, activeProfileId, currentFlightMode) { templateMap, profileId, mode ->
            templateMap[FlightVisibility.normalizeProfileId(profileId)]?.get(mode)
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
}
