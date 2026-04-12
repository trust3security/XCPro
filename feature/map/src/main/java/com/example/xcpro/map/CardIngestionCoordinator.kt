package com.example.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CardIngestionCoordinator(
    private val scope: CoroutineScope,
    private val cardHydrationReady: StateFlow<Boolean>,
    private val cardFlightDataFlow: StateFlow<RealTimeFlightData?>,
    private val consumeBufferedCardSample: () -> RealTimeFlightData?,
    private val unitsPreferencesFlow: StateFlow<UnitsPreferences>,
    private val initializeCardPreferences: (FlightDataViewModel) -> Unit,
    private val startIndependentClock: (FlightDataViewModel) -> Unit,
    private val onProfileModeVisibilitiesChanged:
        (String?, Map<String, Map<FlightModeSelection, Boolean>>) -> Unit = { _, _ -> }
) {
    @Volatile
    private var cardsReady: Boolean = false
    private var boundViewModel: FlightDataViewModel? = null
    private var hydrationJob: Job? = null
    private var cardsJob: Job? = null
    private var unitsJob: Job? = null
    private var profileVisibilityJob: Job? = null

    @Synchronized
    fun bindCards(flightViewModel: FlightDataViewModel) {
        if (boundViewModel === flightViewModel) return
        boundViewModel = flightViewModel
        cancelJobs()

        initializeCardPreferences(flightViewModel)
        startIndependentClock(flightViewModel)

        cardsReady = cardHydrationReady.value

        hydrationJob = scope.launch {
            cardHydrationReady.collectLatest { ready ->
                    cardsReady = ready
                    if (ready) {
                        consumeBufferedCardSample()?.let { buffered ->
                            flightViewModel.updateCardsWithLiveData(buffered)
                        }
                    }
                }
        }

        cardsJob = scope.launch {
            cardFlightDataFlow.collectLatest { displaySample ->
                if (displaySample != null && cardsReady) {
                    flightViewModel.updateCardsWithLiveData(displaySample)
                }
            }
        }

        unitsJob = scope.launch {
            unitsPreferencesFlow.collectLatest { preferences ->
                flightViewModel.updateUnitsPreferences(preferences)
            }
        }

        profileVisibilityJob = scope.launch {
            combine(
                flightViewModel.activeProfileId,
                flightViewModel.profileModeVisibilities
            ) { activeProfileId, visibilities ->
                activeProfileId to visibilities
            }.collectLatest { (activeProfileId, visibilities) ->
                onProfileModeVisibilitiesChanged(activeProfileId, visibilities)
            }
        }
    }

    @Synchronized
    fun stop() {
        boundViewModel = null
        cancelJobs()
    }

    @Synchronized
    private fun cancelJobs() {
        hydrationJob?.cancel()
        cardsJob?.cancel()
        unitsJob?.cancel()
        profileVisibilityJob?.cancel()
        hydrationJob = null
        cardsJob = null
        unitsJob = null
        profileVisibilityJob = null
    }
}
