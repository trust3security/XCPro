package com.example.dfcards.profiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.dfcards.CardState
import com.example.dfcards.dfcards.FlightData
import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsProfileIds
import com.example.xcpro.core.common.profiles.ProfileSettingsSectionContract
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class CardProfileSettingsContributor @Inject constructor(
    private val cardPreferences: CardPreferences
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> =
        setOf(ProfileSettingsSectionContract.CARD_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionContract.CARD_PREFERENCES) return null
        val templateCardsByProfile = normalizeProfileScopedMap(
            cardPreferences.getAllProfileTemplateCards().first()
        )
        val modeTemplatesByProfile = normalizeProfileScopedMap(
            cardPreferences.getAllProfileFlightModeTemplates().first()
        )
        return gson.toJsonTree(
            CardPreferencesSectionPayload(
                templates = cardPreferences.getAllTemplates().first().map { template ->
                    CardTemplatePayload(
                        id = template.id,
                        name = template.name,
                        description = template.description,
                        cardIds = template.cardIds.toList(),
                        isPreset = template.isPreset,
                        createdAt = template.createdAt
                    )
                },
                profileTemplateCards = profileIds.associateWith { profileId ->
                    templateCardsByProfile[profileId].orEmpty()
                },
                profileFlightModeTemplates = profileIds.associateWith { profileId ->
                    modeTemplatesByProfile[profileId].orEmpty()
                },
                profileFlightModeVisibilities = profileIds.associateWith { profileId ->
                    cardPreferences.getProfileAllFlightModeVisibilities(profileId).first().toMap()
                },
                profileCardPositions = profileIds.associateWith { profileId ->
                    FlightModeSelection.values().associate { mode ->
                        mode.name to cardPreferences.getProfileCardPositions(profileId, mode.name)
                            .first()
                            .mapValues { (_, position) ->
                                CardPositionPayload(
                                    x = position.x,
                                    y = position.y,
                                    width = position.width,
                                    height = position.height
                                )
                            }
                    }
                },
                cardsAcrossPortrait = cardPreferences.getCardsAcrossPortrait().first(),
                cardsAnchorPortrait = cardPreferences.getCardsAnchorPortrait().first().name,
                lastActiveTemplate = cardPreferences.getLastActiveTemplate().first(),
                varioSmoothingAlpha = cardPreferences.getVarioSmoothingAlpha().first()
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionContract.CARD_PREFERENCES) return
        val section = gson.fromJson(payload, CardPreferencesSectionPayload::class.java)

        val templates = section.templates.map { template ->
            FlightTemplate(
                id = template.id,
                name = template.name,
                description = template.description,
                cardIds = template.cardIds,
                icon = Icons.Default.Star,
                isPreset = template.isPreset,
                createdAt = template.createdAt
            )
        }
        cardPreferences.saveAllTemplates(templates)
        cardPreferences.setCardsAcrossPortrait(section.cardsAcrossPortrait)
        val anchor = runCatching {
            CardPreferences.CardAnchor.valueOf(section.cardsAnchorPortrait)
        }.getOrDefault(CardPreferences.CardAnchor.TOP)
        cardPreferences.setCardsAnchorPortrait(anchor)
        section.lastActiveTemplate?.let { cardPreferences.saveLastActiveTemplate(it) }
        cardPreferences.saveVarioSmoothingAlpha(section.varioSmoothingAlpha)

        section.profileTemplateCards.forEach { (sourceProfileId, templateMap) ->
            val profileId = ProfileSettingsProfileIds.resolveImportedProfileId(
                sourceProfileId = sourceProfileId,
                importedProfileIdMap = importedProfileIdMap
            ) ?: return@forEach
            templateMap.forEach { (templateId, cardIds) ->
                cardPreferences.saveProfileTemplateCards(profileId, templateId, cardIds)
            }
        }
        section.profileFlightModeTemplates.forEach { (sourceProfileId, modeMap) ->
            val profileId = ProfileSettingsProfileIds.resolveImportedProfileId(
                sourceProfileId = sourceProfileId,
                importedProfileIdMap = importedProfileIdMap
            ) ?: return@forEach
            modeMap.forEach { (mode, templateId) ->
                cardPreferences.saveProfileFlightModeTemplate(profileId, mode, templateId)
            }
        }
        section.profileFlightModeVisibilities.forEach { (sourceProfileId, visibilityMap) ->
            val profileId = ProfileSettingsProfileIds.resolveImportedProfileId(
                sourceProfileId = sourceProfileId,
                importedProfileIdMap = importedProfileIdMap
            ) ?: return@forEach
            visibilityMap.forEach { (mode, visible) ->
                cardPreferences.saveProfileFlightModeVisibility(profileId, mode, visible)
            }
        }
        section.profileCardPositions.forEach { (sourceProfileId, modeMap) ->
            val profileId = ProfileSettingsProfileIds.resolveImportedProfileId(
                sourceProfileId = sourceProfileId,
                importedProfileIdMap = importedProfileIdMap
            ) ?: return@forEach
            modeMap.forEach { (mode, positionMap) ->
                val states = positionMap.map { (cardId, position) ->
                    CardState(
                        id = cardId,
                        x = position.x,
                        y = position.y,
                        width = position.width,
                        height = position.height,
                        flightData = FlightData(
                            id = cardId,
                            label = "",
                            primaryValue = ""
                        )
                    )
                }
                cardPreferences.saveProfileCardPositions(profileId, mode, states)
            }
        }
    }

    private fun <T> normalizeProfileScopedMap(raw: Map<String, T>): Map<String, T> {
        if (raw.isEmpty()) return emptyMap()
        val normalized = linkedMapOf<String, T>()
        raw.entries.sortedBy { it.key }.forEach { (profileId, value) ->
            val resolvedId = ProfileSettingsProfileIds.normalizeOrNull(profileId) ?: return@forEach
            if (!normalized.containsKey(resolvedId)) {
                normalized[resolvedId] = value
            }
        }
        return normalized.toMap()
    }
}

private data class CardPreferencesSectionPayload(
    val templates: List<CardTemplatePayload>,
    val profileTemplateCards: Map<String, Map<String, List<String>>>,
    val profileFlightModeTemplates: Map<String, Map<String, String>>,
    val profileFlightModeVisibilities: Map<String, Map<String, Boolean>>,
    val profileCardPositions: Map<String, Map<String, Map<String, CardPositionPayload>>>,
    val cardsAcrossPortrait: Int,
    val cardsAnchorPortrait: String,
    val lastActiveTemplate: String?,
    val varioSmoothingAlpha: Float
)

private data class CardTemplatePayload(
    val id: String,
    val name: String,
    val description: String,
    val cardIds: List<String>,
    val isPreset: Boolean,
    val createdAt: Long
)

private data class CardPositionPayload(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
