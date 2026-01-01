package com.example.dfcards.dfcards

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Keeps profile card/template state hydrated and consistent with preferences.
 */
internal class FlightProfileStore(
    private val profileModeTemplates: MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, String>>>,
    private val profileModeCards: MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, List<String>>>>,
    private val profileModeVisibilities: MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, Boolean>>>,
    private val activeProfileId: StateFlow<ProfileId?>,
    private val availableTemplates: StateFlow<List<FlightTemplate>>,
    private val ingestProvider: () -> FlightDataIngest?
) {

    fun ensureVisibilityEntry(profileId: ProfileId) {
        if (profileModeVisibilities.value.containsKey(profileId)) return
        profileModeVisibilities.value = profileModeVisibilities.value.toMutableMap().apply {
            this[profileId] = FlightVisibility.defaultVisibilityMap().toMap()
        }
    }

    suspend fun hydrateFromPreferences(preferences: CardPreferences) {
        val ingestHelper = ingestProvider() ?: return
        val templateMappingsRaw = ingestHelper.loadProfileTemplates()
        val templateCardsRaw = ingestHelper.loadProfileTemplateCards()

        val templateMappings = mutableMapOf<ProfileId, MutableMap<FlightModeSelection, String>>()
        val cardsByMode = mutableMapOf<ProfileId, MutableMap<FlightModeSelection, List<String>>>()

        templateMappingsRaw.forEach { (profileId, modeMap) ->
            val templateDest = templateMappings.getOrPut(profileId) { mutableMapOf() }
            val cardDest = cardsByMode.getOrPut(profileId) { mutableMapOf() }
            modeMap.forEach { (modeName, templateId) ->
                val mode = modeName.toFlightModeOrNull() ?: return@forEach
                templateDest[mode] = templateId
                val cards = templateCardsRaw[profileId]?.get(templateId)
                if (cards != null) {
                    cardDest[mode] = cards
                }
            }
        }

        profileModeTemplates.value = templateMappings.mapValues { it.value.toMap() }
        profileModeCards.value = cardsByMode.mapValues { it.value.toMap() }

        val profileIds = (templateMappings.keys + cardsByMode.keys).toMutableSet()
        activeProfileId.value?.let { profileIds.add(it) }
        if (profileIds.isNotEmpty()) {
            val visibilityMappings = mutableMapOf<ProfileId, Map<FlightModeSelection, Boolean>>()
            profileIds.forEach { profileId ->
                val raw = preferences.getProfileAllFlightModeVisibilities(profileId).first()
                visibilityMappings[profileId] = FlightVisibility.buildVisibilityMap(raw)
            }
            profileModeVisibilities.value = visibilityMappings.toMap()
        } else {
            profileModeVisibilities.value = emptyMap()
        }
    }

    fun buildActiveTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection
    ): FlightTemplate? {
        val profileKey = FlightVisibility.normalizeProfileId(profileId)

        return FlightCardStateMapper.buildActiveTemplate(
            profileId = profileId,
            flightMode = flightMode,
            availableTemplates = availableTemplates.value,
            profileTemplates = profileModeTemplates.value,
            profileCards = profileModeCards.value
        )
    }

    private fun String.toFlightModeOrNull(): FlightModeSelection? =
        runCatching { FlightModeSelection.valueOf(this) }.getOrNull()
}
