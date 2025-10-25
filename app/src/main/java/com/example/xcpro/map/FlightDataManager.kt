package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Bridge between the map-layer UI and the flight-card SSOT ViewModel. It keeps UI-facing,
 * short-lived state (live vario data, smoothing, visibility) while delegating template/card
 * ownership to [FlightDataViewModel].
 */
class FlightDataManager(
    private val context: Context,
    private val cardPreferences: CardPreferences,
    private val coroutineScope: CoroutineScope
) {

    companion object {
        private const val TAG = "FlightDataManager"
        private const val DEFAULT_SMOOTHING_ALPHA = 0.25f
    }

    var allTemplates by mutableStateOf<List<FlightTemplate>>(emptyList())
        private set

    var liveFlightData by mutableStateOf<RealTimeFlightData?>(null)
        private set
    var smoothedVerticalSpeed by mutableStateOf<Double?>(null)
        private set
    var rawVerticalSpeed by mutableStateOf<Double?>(null)
        private set

    private var smoothingAlpha: Double = DEFAULT_SMOOTHING_ALPHA.toDouble()

    var currentFlightMode by mutableStateOf(FlightModeSelection.CRUISE)
        private set

    var showCardLibrary by mutableStateOf(false)
        private set

    var visibleModes by mutableStateOf(listOf(FlightMode.CRUISE))
        private set

    /**
     * Legacy trigger used by Compose side-effects. Retained for now; no longer required once the
     * UI observes [FlightDataViewModel] directly.
     */
    var templateVersion by mutableStateOf(0)
        private set

    var unitsPreferences by mutableStateOf(UnitsPreferences())
        private set

    init {
        coroutineScope.launch {
            cardPreferences.getVarioSmoothingAlpha().collect { alpha ->
                smoothingAlpha = alpha.toDouble().coerceIn(0.05, 0.95)
                smoothedVerticalSpeed = null
            }
        }
    }

    fun mapToFlightModeSelection(mode: FlightMode): FlightModeSelection =
        when (mode) {
            FlightMode.CRUISE -> FlightModeSelection.CRUISE
            FlightMode.THERMAL -> FlightModeSelection.THERMAL
            FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
            FlightMode.HAWK -> FlightModeSelection.HAWK
        }

    fun mapToFlightMode(modeSelection: FlightModeSelection): FlightMode =
        when (modeSelection) {
            FlightModeSelection.CRUISE -> FlightMode.CRUISE
            FlightModeSelection.THERMAL -> FlightMode.THERMAL
            FlightModeSelection.FINAL_GLIDE -> FlightMode.FINAL_GLIDE
            FlightModeSelection.HAWK -> FlightMode.HAWK
        }

    fun updateFlightMode(newMode: FlightModeSelection) {
        currentFlightMode = newMode
        Log.d(TAG, "Flight mode updated to: ${newMode.displayName}")
    }

    fun updateFlightModeFromEnum(newMode: FlightMode) {
        currentFlightMode = mapToFlightModeSelection(newMode)
        Log.d(TAG, "Flight mode updated from enum to: ${currentFlightMode.displayName}")
    }

    suspend fun loadVisibleModes(profileId: String?, profileName: String?) {
        if (profileId == null) {
            Log.d(TAG, "No profileId provided - keeping existing visible modes")
            return
        }

        val visibilities = cardPreferences.getProfileAllFlightModeVisibilities(profileId).first()
        val filtered = mutableListOf<FlightMode>()
        filtered.add(FlightMode.CRUISE)
        if (visibilities["THERMAL"] != false) filtered.add(FlightMode.THERMAL)
        if (visibilities["FINAL_GLIDE"] != false) filtered.add(FlightMode.FINAL_GLIDE)
        if (visibilities["HAWK"] != false) filtered.add(FlightMode.HAWK)
        visibleModes = filtered
        Log.d(TAG, "Visible modes for profile '$profileName': ${filtered.map { it.name }}")
    }

    suspend fun loadAllTemplates() {
        allTemplates = cardPreferences.getAllTemplates().first()
        Log.d(TAG, "Loaded ${allTemplates.size} flight templates")
    }

    suspend fun loadTemplateForProfile(
        currentFlightModeSelection: FlightModeSelection,
        profileId: String?,
        profileName: String?,
        safeContainerSize: IntSize,
        flightViewModel: FlightDataViewModel,
        density: androidx.compose.ui.unit.Density
    ) {
        flightViewModel.initializeCardPreferences(cardPreferences)
        flightViewModel.setFlightMode(currentFlightModeSelection)
        flightViewModel.setActiveProfile(profileId)
        currentFlightMode = currentFlightModeSelection

        if (safeContainerSize == IntSize.Zero) {
            Log.w(TAG, "Safe container size is zero - delaying template application")
            return
        }

        flightViewModel.initializeCards(safeContainerSize, density)

        val template = resolveTemplateForProfile(currentFlightModeSelection, profileId, profileName)
        if (template == null) {
            Log.e(TAG, "No template available for ${currentFlightModeSelection.displayName}")
            return
        }

        Log.d(TAG, "Applying template '${template.name}' (${template.cardIds.size} cards)")
        flightViewModel.applyTemplate(template, safeContainerSize, density)
        profileId?.let {
            flightViewModel.setProfileTemplate(it, currentFlightModeSelection, template.id)
            flightViewModel.setProfileCards(it, currentFlightModeSelection, template.cardIds)
        }
    }

    suspend fun updateAllTemplates(updatedTemplates: List<FlightTemplate>) {
        cardPreferences.saveAllTemplates(updatedTemplates)
        allTemplates = updatedTemplates
        templateVersion++
        Log.d(TAG, "Updated templates (${updatedTemplates.size})")
    }

    fun incrementTemplateVersion() {
        templateVersion++
    }

    suspend fun editTemplate(existingTemplate: FlightTemplate, newName: String, newCardIds: List<String>) {
        val updated = existingTemplate.copy(name = newName, cardIds = newCardIds)
        allTemplates = allTemplates.map { if (it.id == existingTemplate.id) updated else it }
        updateAllTemplates(allTemplates)
    }

    suspend fun createNewTemplate(name: String, cardIds: List<String>) {
        val newTemplate = FlightTemplate(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            description = "Custom template",
            cardIds = cardIds,
            icon = Icons.Default.Star,
            isPreset = false
        )
        updateAllTemplates(allTemplates + newTemplate)
    }

    fun updateUnitsPreferences(preferences: UnitsPreferences) {
        unitsPreferences = preferences
        Log.d(TAG, "Units updated to $preferences")
    }

    suspend fun deleteTemplate(templateToDelete: FlightTemplate) {
        updateAllTemplates(allTemplates.filterNot { it.id == templateToDelete.id })
    }

    fun updateLiveFlightData(newData: RealTimeFlightData?) {
        if (newData == null) {
            liveFlightData = null
            smoothedVerticalSpeed = null
            rawVerticalSpeed = null
            return
        }

        val rawVs = newData.verticalSpeed
        rawVerticalSpeed = rawVs
        val previous = smoothedVerticalSpeed ?: rawVs
        val smoothed = previous + smoothingAlpha * (rawVs - previous)
        smoothedVerticalSpeed = smoothed
        liveFlightData = newData.copy(verticalSpeed = smoothed)
    }

    fun showCardLibrary() {
        showCardLibrary = true
    }

    fun hideCardLibrary() {
        showCardLibrary = false
    }

    fun isCurrentModeVisible(currentMode: FlightMode): Boolean =
        currentMode in visibleModes

    fun getFallbackMode(): FlightMode = FlightMode.CRUISE

    private suspend fun resolveTemplateForProfile(
        mode: FlightModeSelection,
        profileId: String?,
        profileName: String?
    ): FlightTemplate? {
        val templates = allTemplates
        val baseTemplate = if (profileId != null) {
            val preferredId = cardPreferences
                .getProfileFlightModeTemplate(profileId, mode.name)
                .firstOrNull()

            val templateFromPrefs = preferredId?.let { preferred ->
                templates.find { it.id == preferred }
            }

            val customCards = if (preferredId != null) {
                cardPreferences.getProfileTemplateCards(profileId, preferredId).firstOrNull()
            } else {
                null
            }

            when {
                templateFromPrefs != null && !customCards.isNullOrEmpty() -> {
                    Log.d(TAG, "Using profile-specific cards for $profileName: ${customCards.joinToString(",")}")
                    templateFromPrefs.copy(cardIds = customCards)
                }
                templateFromPrefs != null -> templateFromPrefs
                else -> defaultTemplateFor(mode, templates)
            }
        } else {
            defaultTemplateFor(mode, templates)
        }

        return baseTemplate ?: defaultTemplateFor(mode, templates)
    }

    private fun defaultTemplateFor(
        mode: FlightModeSelection,
        templates: List<FlightTemplate>
    ): FlightTemplate? {
        val defaultId = when (mode) {
            FlightModeSelection.CRUISE -> listOf("id01", "essential")
            FlightModeSelection.THERMAL -> listOf("id02", "thermal")
            FlightModeSelection.FINAL_GLIDE -> listOf("id03", "cross_country")
            FlightModeSelection.HAWK -> listOf("hawk", "vario", "id01")
        }

        return defaultId.firstNotNullOfOrNull { candidate ->
            templates.find { it.id == candidate }
        }
    }
}
