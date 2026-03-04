package com.example.xcpro.map

import com.example.dfcards.CardPreferences
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal fun createCardIngestionCoordinator(
    scope: CoroutineScope,
    cardHydrationReady: StateFlow<Boolean>,
    flightDataManager: FlightDataManager,
    unitsPreferencesFlow: StateFlow<UnitsPreferences>,
    cardPreferences: CardPreferences
): CardIngestionCoordinator = CardIngestionCoordinator(
    scope = scope,
    cardHydrationReady = cardHydrationReady,
    cardFlightDataFlow = flightDataManager.cardFlightDataFlow,
    consumeBufferedCardSample = { flightDataManager.consumeBufferedCardSample() },
    unitsPreferencesFlow = unitsPreferencesFlow,
    initializeCardPreferences = { flightViewModel ->
        flightViewModel.initializeCardPreferences(cardPreferences)
    },
    startIndependentClock = { flightViewModel ->
        flightViewModel.startIndependentClockTimer()
    }
)
