package com.trust3.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.example.dfcards.dfcards.FlightCardSessionBinder
import com.trust3.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal fun createCardIngestionCoordinator(
    scope: CoroutineScope,
    cardHydrationReady: StateFlow<Boolean>,
    flightDataManager: FlightDataManager,
    unitsPreferencesFlow: StateFlow<UnitsPreferences>,
    flightCardSessionBinder: FlightCardSessionBinder,
    onProfileModeVisibilitiesChanged:
        (String?, Map<String, Map<FlightModeSelection, Boolean>>) -> Unit
): CardIngestionCoordinator = CardIngestionCoordinator(
    scope = scope,
    cardHydrationReady = cardHydrationReady,
    cardFlightDataFlow = flightDataManager.cardFlightDataFlow,
    consumeBufferedCardSample = { flightDataManager.consumeBufferedCardSample() },
    unitsPreferencesFlow = unitsPreferencesFlow,
    bindFlightCardSession = flightCardSessionBinder::bind,
    onProfileModeVisibilitiesChanged = onProfileModeVisibilitiesChanged
)
