package com.example.dfcards.dfcards

import com.example.dfcards.CardPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Focused seam for binding persistent card preferences into the card ViewModel.
 * Keeps raw preference storage out of screen ViewModel constructors.
 */
@Singleton
class FlightCardSessionBinder @Inject constructor(
    private val cardPreferences: CardPreferences
) {
    fun bind(flightViewModel: FlightDataViewModel) {
        flightViewModel.initializeCardPreferences(cardPreferences)
        flightViewModel.startIndependentClockTimer()
    }
}
