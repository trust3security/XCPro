package com.example.dfcards.dfcards

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.trust3.xcpro.core.common.geometry.DensityScale
import com.trust3.xcpro.core.common.geometry.IntSizePx
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class FlightDataProfileCoordinator(
    private val profileStore: FlightProfileStore,
    private val cardPreferences: StateFlow<CardPreferences?>,
    private val ioDispatcher: CoroutineDispatcher,
    private val setProfileTemplate: (ProfileId?, FlightModeSelection, String) -> Unit,
    private val setProfileCards: (ProfileId?, FlightModeSelection, List<String>) -> Unit,
    private val setFlightMode: (FlightModeSelection) -> Unit,
    private val setActiveProfile: (ProfileId?) -> Unit,
    private val initializeCards: (IntSizePx, DensityScale) -> Unit,
    private val applyTemplate: suspend (FlightTemplate, IntSizePx, DensityScale) -> Unit,
    private val loadEssentialCardsOnStartup: suspend (IntSizePx, DensityScale, FlightModeSelection?) -> Unit,
    private val logDebug: (String) -> Unit
) {

    suspend fun selectProfileTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        template: FlightTemplate
    ) {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        logDebug("selectProfileTemplate: profile=$targetProfileId mode=$flightMode template=${template.id}")
        val preferences = cardPreferences.value
        val storedCards = if (preferences == null) {
            null
        } else {
            withContext(ioDispatcher) {
                preferences.getProfileTemplateCards(targetProfileId, template.id).first()
            }
        }
        val cards = storedCards ?: template.cardIds
        setProfileTemplate(profileId, flightMode, template.id)
        setProfileCards(profileId, flightMode, cards)
    }

    suspend fun prepareCardsForProfile(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        containerSize: IntSizePx,
        density: DensityScale
    ) {
        logDebug(
            "prepareCardsForProfile(profile=$profileId, mode=$flightMode, size=${containerSize.width}x${containerSize.height})"
        )
        setFlightMode(flightMode)
        setActiveProfile(profileId)
        initializeCards(containerSize, density)

        val template = profileStore.buildActiveTemplate(profileId, flightMode)
        if (template != null) {
            applyTemplate(template, containerSize, density)
            setProfileCards(profileId, flightMode, template.cardIds)
        } else {
            loadEssentialCardsOnStartup(containerSize, density, flightMode)
        }
    }
}

