package com.example.dfcards.dfcards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.FlightTemplates

/**
 * Builds the active template view model from stored mappings + available templates.
 * Kept separate to lighten FlightDataViewModel.
 */
internal object FlightCardStateMapper {

    private val DEFAULT_TEMPLATE_IDS = mapOf(
        FlightModeSelection.CRUISE to "id01",
        FlightModeSelection.THERMAL to "id02",
        FlightModeSelection.FINAL_GLIDE to "id03"
    )

    fun buildActiveTemplate(
        profileId: String?,
        flightMode: FlightModeSelection,
        availableTemplates: List<FlightTemplate>,
        profileTemplates: Map<String, Map<FlightModeSelection, String>>,
        profileCards: Map<String, Map<FlightModeSelection, List<String>>>
    ): FlightTemplate? {
        val normalized = FlightVisibility.normalizeProfileId(profileId)
        val profileTemplateId = profileTemplates[normalized]?.get(flightMode)
        val cards = profileCards[normalized]?.get(flightMode)

        val baseTemplate = profileTemplateId?.let { id ->
            availableTemplates.firstOrNull { it.id == id }
        } ?: fallbackTemplateForMode(flightMode, availableTemplates)

        return when {
            cards != null -> {
                val source = baseTemplate
                FlightTemplate(
                    id = source?.id ?: profileTemplateId ?: fallbackTemplateIdFor(flightMode),
                    name = source?.name ?: "${flightMode.displayName} Custom",
                    description = source?.description ?: "Profile specific layout",
                    cardIds = cards,
                    icon = source?.icon ?: Icons.Default.Star,
                    isPreset = source?.isPreset ?: false
                )
            }
            else -> baseTemplate
        }
    }

    fun fallbackTemplateIdFor(flightMode: FlightModeSelection): String =
        DEFAULT_TEMPLATE_IDS[flightMode] ?: DEFAULT_TEMPLATE_IDS.getValue(FlightModeSelection.CRUISE)

    private fun fallbackTemplateForMode(
        flightMode: FlightModeSelection,
        templates: List<FlightTemplate>
    ): FlightTemplate? {
        val fallbackId = fallbackTemplateIdFor(flightMode)
        return templates.firstOrNull { it.id == fallbackId }
            ?: FlightTemplates.getDefaultTemplates().firstOrNull { it.id == fallbackId }
    }
}
