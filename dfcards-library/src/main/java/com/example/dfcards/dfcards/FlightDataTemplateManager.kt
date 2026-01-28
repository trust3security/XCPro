package com.example.dfcards.dfcards

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightTemplate
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

internal class FlightDataTemplateManager(
    private val cardPreferences: StateFlow<CardPreferences?>,
    private val availableTemplates: StateFlow<List<FlightTemplate>>
) {

    suspend fun createTemplate(name: String, cardIds: List<String>) {
        val preferences = cardPreferences.value ?: return
        val newTemplate = FlightTemplate(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "Custom template",
            cardIds = cardIds,
            isPreset = false
        )
        val updated = availableTemplates.value + newTemplate
        preferences.saveAllTemplates(updated)
    }

    suspend fun updateTemplate(templateId: String, name: String, cardIds: List<String>) {
        val preferences = cardPreferences.value ?: return
        val updated = availableTemplates.value.map { template ->
            if (template.id == templateId) {
                template.copy(name = name, cardIds = cardIds, isPreset = false)
            } else {
                template
            }
        }
        preferences.saveAllTemplates(updated)
    }

    suspend fun deleteTemplate(templateId: String) {
        val preferences = cardPreferences.value ?: return
        val updated = availableTemplates.value.filterNot { it.id == templateId }
        preferences.saveAllTemplates(updated)
    }
}
