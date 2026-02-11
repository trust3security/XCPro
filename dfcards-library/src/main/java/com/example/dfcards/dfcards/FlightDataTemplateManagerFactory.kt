package com.example.dfcards.dfcards

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightTemplate
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class FlightDataTemplateManagerFactory @Inject constructor() {
    fun create(
        cardPreferences: StateFlow<CardPreferences?>,
        availableTemplates: StateFlow<List<FlightTemplate>>
    ): FlightDataTemplateManager =
        FlightDataTemplateManager(
            cardPreferences = cardPreferences,
            availableTemplates = availableTemplates
        )
}
