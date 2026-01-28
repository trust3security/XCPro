package com.example.dfcards.dfcards

import kotlinx.coroutines.flow.MutableStateFlow

internal data class ProfileCardsUpdate(
    val profileId: ProfileId,
    val flightMode: FlightModeSelection,
    val cardIds: List<String>
)

internal data class ProfileTemplateUpdate(
    val profileId: ProfileId,
    val flightMode: FlightModeSelection,
    val templateId: String
)

internal data class ProfileVisibilityUpdate(
    val profileId: ProfileId,
    val flightMode: FlightModeSelection,
    val isVisible: Boolean
)

internal object FlightDataStateMapper {
    fun updateProfileCards(
        profileModeCards: MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, List<String>>>>,
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        cardIds: List<String>
    ): ProfileCardsUpdate {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        val sanitized = cardIds.distinct()
        profileModeCards.value = profileModeCards.value.toMutableMap().apply {
            val existing = this[targetProfileId]?.toMutableMap() ?: mutableMapOf()
            existing[flightMode] = sanitized
            this[targetProfileId] = existing
        }
        return ProfileCardsUpdate(targetProfileId, flightMode, sanitized)
    }

    fun updateProfileTemplate(
        profileModeTemplates: MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, String>>>,
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        templateId: String
    ): ProfileTemplateUpdate {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        profileModeTemplates.value = profileModeTemplates.value.toMutableMap().apply {
            val existing = this[targetProfileId]?.toMutableMap() ?: mutableMapOf()
            existing[flightMode] = templateId
            this[targetProfileId] = existing
        }
        return ProfileTemplateUpdate(targetProfileId, flightMode, templateId)
    }

    fun updateProfileVisibility(
        profileModeVisibilities: MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, Boolean>>>,
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        isVisible: Boolean
    ): ProfileVisibilityUpdate {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        val updated = profileModeVisibilities.value.toMutableMap()
        val profileEntries = (updated[targetProfileId]?.toMutableMap()
            ?: FlightVisibility.defaultVisibilityMap()).apply {
            this[flightMode] = isVisible
            this[FlightModeSelection.CRUISE] = true
        }
        updated[targetProfileId] = profileEntries.toMap()
        profileModeVisibilities.value = updated.toMap()
        return ProfileVisibilityUpdate(targetProfileId, flightMode, isVisible)
    }

    fun flightModeVisibilitiesFor(
        profileModeVisibilities: Map<ProfileId, Map<FlightModeSelection, Boolean>>,
        profileId: ProfileId?
    ): Map<FlightModeSelection, Boolean> {
        val defaults = FlightVisibility.defaultVisibilityMap()
        profileModeVisibilities[FlightVisibility.normalizeProfileId(profileId)]?.forEach { (mode, visible) ->
            if (mode != FlightModeSelection.CRUISE) {
                defaults[mode] = visible
            }
        }
        return defaults.toMap()
    }

    fun getProfileCards(
        profileModeCards: Map<ProfileId, Map<FlightModeSelection, List<String>>>,
        profileId: ProfileId?,
        flightMode: FlightModeSelection
    ): List<String> =
        profileModeCards[FlightVisibility.normalizeProfileId(profileId)]?.get(flightMode).orEmpty()

    fun getProfileTemplateId(
        profileModeTemplates: Map<ProfileId, Map<FlightModeSelection, String>>,
        profileId: ProfileId?,
        flightMode: FlightModeSelection
    ): String? =
        profileModeTemplates[FlightVisibility.normalizeProfileId(profileId)]?.get(flightMode)

    fun resolveTemplateIdForPersistence(
        profileModeTemplates: Map<ProfileId, Map<FlightModeSelection, String>>,
        profileId: ProfileId,
        flightMode: FlightModeSelection,
        setProfileTemplate: (ProfileId, FlightModeSelection, String) -> Unit
    ): String {
        val existing = getProfileTemplateId(profileModeTemplates, profileId, flightMode)
        if (existing != null) return existing
        val fallback = FlightCardStateMapper.fallbackTemplateIdFor(flightMode)
        setProfileTemplate(profileId, flightMode, fallback)
        return fallback
    }

    fun allTemplateCardCounts(
        profileModeCards: Map<ProfileId, Map<FlightModeSelection, List<String>>>,
        profileModeTemplates: Map<ProfileId, Map<FlightModeSelection, String>>,
        profileId: ProfileId?
    ): Map<String, Int> {
        val normalized = FlightVisibility.normalizeProfileId(profileId)
        val cardsByMode = profileModeCards[normalized].orEmpty()
        val templatesByMode = profileModeTemplates[normalized].orEmpty()
        if (cardsByMode.isEmpty() || templatesByMode.isEmpty()) return emptyMap()

        val counts = mutableMapOf<String, Int>()
        cardsByMode.forEach { (mode, cards) ->
            val templateId = templatesByMode[mode] ?: return@forEach
            counts[templateId] = cards.size
        }
        return counts
    }

    fun templateCardCounts(
        profileModeCards: Map<ProfileId, Map<FlightModeSelection, List<String>>>,
        profileModeTemplates: Map<ProfileId, Map<FlightModeSelection, String>>,
        profileId: ProfileId?,
        flightMode: FlightModeSelection
    ): Map<String, Int> {
        val normalized = FlightVisibility.normalizeProfileId(profileId)
        val templateId = profileModeTemplates[normalized]?.get(flightMode)
        val allCounts = allTemplateCardCounts(profileModeCards, profileModeTemplates, profileId)
        return if (templateId != null && allCounts.containsKey(templateId)) {
            mapOf(templateId to allCounts.getValue(templateId))
        } else {
            emptyMap()
        }
    }
}
