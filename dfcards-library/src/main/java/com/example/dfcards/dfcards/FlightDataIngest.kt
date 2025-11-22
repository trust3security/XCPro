package com.example.dfcards.dfcards

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightTemplate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Persistence + ingestion helpers kept out of the ViewModel to reduce surface.
 */
internal class FlightDataIngest(
    private val preferences: CardPreferences,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun loadProfileTemplates(): Map<String, Map<String, String>> =
        withContext(ioDispatcher) { preferences.getAllProfileFlightModeTemplates().first() }

    suspend fun loadProfileTemplateCards(): Map<String, Map<String, List<String>>> =
        withContext(ioDispatcher) { preferences.getAllProfileTemplateCards().first() }

    suspend fun loadProfileVisibilities(profileId: String): Map<String, Boolean> =
        withContext(ioDispatcher) { preferences.getProfileAllFlightModeVisibilities(profileId).first() }

    fun observeTemplateCatalog(): StateFlow<List<FlightTemplate>> =
        preferences.getAllTemplates() as StateFlow<List<FlightTemplate>>
}
