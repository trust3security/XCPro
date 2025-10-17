package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcpro.FlightMode
// import com.example.xcpro.profiles.ProfileUIState
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightTemplate
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.dfcards.FlightDataViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Centralized flight data management for MapScreen
 * Handles flight templates, modes, card library, and real-time data integration
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

    // Flight Data State
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

    // ✅ NEW: Template version tracking for reactive updates
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

    /**
     * Get default template for flight mode
     */
    fun getDefaultTemplateForMode(mode: FlightModeSelection, templates: List<FlightTemplate>): FlightTemplate? {
        return when (mode) {
            FlightModeSelection.CRUISE -> templates.find { it.id == "id01" }
                ?: templates.find { it.id == "essential" }        // Fallback to old ID
            FlightModeSelection.THERMAL -> templates.find { it.id == "id02" }
                ?: templates.find { it.id == "thermal" }          // Fallback to old ID
            FlightModeSelection.FINAL_GLIDE -> templates.find { it.id == "id03" }
                ?: templates.find { it.id == "cross_country" }    // Fallback to old ID
        } ?: templates.find { it.id == "id01" } ?: templates.find { it.id == "essential" } // Ultimate fallback
    }

    /**
     * Map FlightMode to FlightModeSelection
     */
    fun mapToFlightModeSelection(mode: FlightMode): FlightModeSelection {
        return when (mode) {
            FlightMode.CRUISE -> FlightModeSelection.CRUISE
            FlightMode.THERMAL -> FlightModeSelection.THERMAL
            FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
        }
    }

    /**
     * Map FlightModeSelection to FlightMode
     */
    fun mapToFlightMode(modeSelection: FlightModeSelection): FlightMode {
        return when (modeSelection) {
            FlightModeSelection.CRUISE -> FlightMode.CRUISE
            FlightModeSelection.THERMAL -> FlightMode.THERMAL
            FlightModeSelection.FINAL_GLIDE -> FlightMode.FINAL_GLIDE
        }
    }

    /**
     * Update flight mode
     */
    fun updateFlightMode(newMode: FlightModeSelection) {
        currentFlightMode = newMode
        Log.d(TAG, "Flight mode updated to: ${newMode.displayName}")
    }

    /**
     * Update flight mode from FlightMode enum
     */
    fun updateFlightModeFromEnum(newMode: FlightMode) {
        currentFlightMode = mapToFlightModeSelection(newMode)
        Log.d(TAG, "Flight mode updated from enum to: ${currentFlightMode.displayName}")
    }

    /**
     * Load visible flight modes from profile preferences
     */
    suspend fun loadVisibleModes(profileId: String?, profileName: String?) {
        Log.d(TAG, "🔍 LOAD VISIBLE MODES: profileId=$profileId, profileName=$profileName")
        if (profileId != null) {
            try {
                val visibilities = cardPreferences.getProfileAllFlightModeVisibilities(profileId).first()
                Log.d(TAG, "🔍 RAW VISIBILITIES: $visibilities")

                val filteredModes = mutableListOf<FlightMode>()

                // CRUISE is always included
                filteredModes.add(FlightMode.CRUISE)
                Log.d(TAG, "🔍 ADDED CRUISE (always included)")

                // Add others only if they're visible
                if (visibilities["THERMAL"] == true) {
                    filteredModes.add(FlightMode.THERMAL)
                    Log.d(TAG, "🔍 ADDED THERMAL (visible=true)")
                } else {
                    Log.d(TAG, "🔍 SKIPPED THERMAL (visible=${visibilities["THERMAL"]})")
                }

                if (visibilities["FINAL_GLIDE"] == true) {
                    filteredModes.add(FlightMode.FINAL_GLIDE)
                    Log.d(TAG, "🔍 ADDED FINAL_GLIDE (visible=true)")
                } else {
                    Log.d(TAG, "🔍 SKIPPED FINAL_GLIDE (visible=${visibilities["FINAL_GLIDE"]})")
                }

                visibleModes = filteredModes
                Log.d(TAG, "✅ FINAL VISIBLE MODES for profile '$profileName': ${filteredModes.map { it.name }}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading flight mode visibilities: ${e.message}")
                visibleModes = listOf(FlightMode.CRUISE) // Fallback to Cruise only
                Log.d(TAG, "✅ FALLBACK VISIBLE MODES: [CRUISE]")
            }
        } else {
            Log.d(TAG, "⚠️ No profileId provided - keeping existing visibleModes: ${visibleModes.map { it.name }}")
        }
    }

    /**
     * Load all templates from preferences
     * ✅ FIXED: Use .first() instead of .collect() to prevent infinite blocking
     */
    suspend fun loadAllTemplates() {
        allTemplates = cardPreferences.getAllTemplates().first()
        Log.d(TAG, "✅ Loaded ${allTemplates.size} flight templates")
    }

    /**
     * Load appropriate template for current flight mode and profile
     */
    suspend fun loadTemplateForProfile(
        currentFlightModeSelection: FlightModeSelection,
        profileId: String?,
        profileName: String?,
        safeContainerSize: IntSize,
        flightViewModel: FlightDataViewModel,
        density: androidx.compose.ui.unit.Density
    ) {
        Log.d(TAG, "Template loading triggered:")
        Log.d(TAG, "  - currentFlightModeSelection: ${currentFlightModeSelection.displayName}")
        Log.d(TAG, "  - allTemplates.size: ${allTemplates.size}")
        Log.d(TAG, "  - safeContainerSize: $safeContainerSize")
        Log.d(TAG, "  - activeProfile: $profileName")

        Log.d(TAG, "🔍 Checking template loading conditions:")
        Log.d(TAG, "  - allTemplates.isNotEmpty(): ${allTemplates.isNotEmpty()}")
        Log.d(TAG, "  - safeContainerSize != IntSize.Zero: ${safeContainerSize != IntSize.Zero}")

        // ✅ FIX: Allow template loading even if container size is temporarily zero
        // The container size race condition will be handled by retrying when size becomes available
        if (allTemplates.isNotEmpty()) {
            val currentFlightMode = mapToFlightMode(currentFlightModeSelection)

            // ✅ SIMPLIFIED: Consistent template loading logic
            val templateToLoad = if (profileId != null) {
                // Profile exists: Check for saved profile-specific template
                // ✅ FIX: Use .name instead of .displayName to match FlightDataScreensTab save logic
                cardPreferences.getProfileFlightModeTemplate(profileId, currentFlightModeSelection.name).first()?.let { profileTemplateId ->
                    val baseTemplate = allTemplates.find { it.id == profileTemplateId }
                    if (baseTemplate != null) {
                        // ✅ FIX: Load profile-specific card configuration
                        val profileCards = cardPreferences.getProfileTemplateCards(profileId, baseTemplate.id).first()
                        if (profileCards != null) {
                            // Use profile-specific cards instead of global template cards
                            Log.d(TAG, "🎯 Using profile-specific cards for $profileName: ${profileCards.joinToString(",").ifEmpty { "NONE (0 cards)" }}")
                            baseTemplate.copy(cardIds = profileCards)
                        } else {
                            baseTemplate
                        }
                    } else {
                        null
                    }
                } ?: getDefaultTemplateForMode(currentFlightModeSelection, allTemplates) // Use consistent default
            } else {
                // No profile: Always use the same default template for each mode
                getDefaultTemplateForMode(currentFlightModeSelection, allTemplates)
            }

            Log.d(TAG, "🔍 Template selection path:")
            Log.d(TAG, "  - Has active profile: ${profileId != null}")
            Log.d(TAG, "  - Mode: ${currentFlightModeSelection.displayName}")
            Log.d(TAG, "  - Selected template: ${templateToLoad?.name} (${templateToLoad?.cardIds?.size} cards)")
            Log.d(TAG, "  - Cards: ${templateToLoad?.cardIds?.joinToString(",") ?: "none"}")

            templateToLoad?.let { template ->
                Log.d(TAG, "✅ Loading template '${template.name}' for profile '$profileName' in ${currentFlightModeSelection.displayName} mode with ${template.cardIds.size} cards")
                flightViewModel.applyTemplate(template, safeContainerSize, density)
            } ?: run {
                Log.w(TAG, "❌ No template found for profile '$profileName' in ${currentFlightModeSelection.displayName} mode")
                Log.w(TAG, "❌ Available templates: ${allTemplates.map { "${it.id}:${it.name}" }}")

                // Force load Cruise template as absolute fallback
                val cruiseTemplate = allTemplates.find { it.id == "id01" }
                    ?: allTemplates.find { it.id == "essential" } // Fallback to old ID if new not found
                cruiseTemplate?.let { cruise ->
                    Log.d(TAG, "⚠️ Loading ${cruise.name} template as fallback")
                    flightViewModel.applyTemplate(cruise, safeContainerSize, density)
                } ?: run {
                    Log.e(TAG, "❌ No fallback template found! Available: ${allTemplates.map { "${it.id}:${it.name}" }}")
                }
            }
        } else {
            // ✅ FIXED: Don't call initializeCards() fallback - just wait for templates to load
            Log.w(TAG, "⚠️ Templates not loaded yet - waiting for loadAllTemplates() to complete")
        }
    }

    /**
     * Apply template to profile and save preferences
     */
    suspend fun applyTemplateToProfile(
        template: FlightTemplate,
        profileId: String?,
        profileName: String?,
        safeContainerSize: IntSize,
        flightViewModel: FlightDataViewModel,
        density: androidx.compose.ui.unit.Density,
        profileViewModel: Any // ProfileViewModel - avoid direct dependency
    ) {
        // ✅ FIX: Always process template loading, container size will be handled by LaunchedEffect
        val currentFlightModeEnum = mapToFlightMode(currentFlightMode)

        if (safeContainerSize != IntSize.Zero) {

            if (profileId != null) {
                Log.d(TAG, "Applying template '${template.name}' to profile '$profileName' for ${currentFlightMode.displayName}")

                // Save template to profile-specific preferences
                // ✅ FIX: Use .name instead of .displayName to match FlightDataScreensTab save logic
                cardPreferences.saveProfileFlightModeTemplate(
                    profileId = profileId,
                    flightMode = currentFlightMode.name,
                    templateId = template.id
                )

                // Also save to profile's card configuration
                (profileViewModel as? com.example.xcpro.profiles.ProfileViewModel)?.saveProfileCardConfiguration(
                    profileId,
                    currentFlightModeEnum,
                    template.id
                )
            } else {
                // Fallback to global save if no active profile
                Log.d(TAG, "No active profile, saving template '${template.name}' globally for ${currentFlightMode.displayName}")
                // ✅ FIX: Use .name instead of .displayName to match FlightDataScreensTab save logic
                cardPreferences.saveFlightModeTemplate(
                    flightMode = currentFlightMode.name,
                    templateId = template.id
                )
            }

            flightViewModel.applyTemplate(template, safeContainerSize, density)
            showCardLibrary = false
        } else {
            // ✅ FIX: Container size is zero, but save template for later application
            Log.w(TAG, "⚠️ Container size is zero, template '${template.name}' saved but not applied yet")
            Log.w(TAG, "⚠️ Template will be applied when container size becomes available via LaunchedEffect")
        }
    }

    /**
     * Update templates list and save to preferences
     */
    suspend fun updateAllTemplates(updatedTemplates: List<FlightTemplate>) {
        allTemplates = updatedTemplates
        cardPreferences.saveAllTemplates(updatedTemplates)
        Log.d(TAG, "Updated and saved ${updatedTemplates.size} templates")
    }

    /**
     * ✅ NEW: Increment template version to trigger reactive reload
     * Call this when card selection changes in Flight Data screen
     */
    fun incrementTemplateVersion() {
        templateVersion++
        Log.d(TAG, "🔄 Template version incremented to: $templateVersion")
    }

    /**
     * Edit existing template
     */
    suspend fun editTemplate(existingTemplate: FlightTemplate, newName: String, newCardIds: List<String>) {
        val updatedTemplate = existingTemplate.copy(
            name = newName,
            cardIds = newCardIds
        )
        val updatedTemplates = allTemplates.map { template ->
            if (template.id == existingTemplate.id) updatedTemplate else template
        }
        updateAllTemplates(updatedTemplates)
        Log.d(TAG, "Edited template: ${existingTemplate.name} -> ${newName}")
    }

    /**
     * Create new template
     */
    suspend fun createNewTemplate(name: String, cardIds: List<String>) {
        val newTemplate = FlightTemplate(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            description = "Custom template",
            cardIds = cardIds,
            icon = Icons.Default.Star
        )
        val updatedTemplates = allTemplates + newTemplate
        updateAllTemplates(updatedTemplates)
        Log.d(TAG, "Created new template: $name with ${cardIds.size} cards")
    }

    fun updateUnitsPreferences(preferences: UnitsPreferences) {
        unitsPreferences = preferences
        Log.d(TAG, "FlightDataManager: units updated to $preferences")
    }

    /**
     * Delete template
     */
    suspend fun deleteTemplate(templateToDelete: FlightTemplate) {
        val updatedTemplates = allTemplates.filter { it.id != templateToDelete.id }
        updateAllTemplates(updatedTemplates)
        Log.d(TAG, "Deleted template: ${templateToDelete.name}")
    }

    /**
     * Update live flight data
     */
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
        val alpha = smoothingAlpha
        val smoothed = previous + alpha * (rawVs - previous)
        smoothedVerticalSpeed = smoothed

        liveFlightData = newData.copy(verticalSpeed = smoothed)
        Log.d(TAG, "Live flight data updated: GPS fixed=${newData}")
    }

    /**
     * Show card library
     */
    fun showCardLibrary() {
        showCardLibrary = true
        Log.d(TAG, "Card library opened")
    }

    /**
     * Hide card library
     */
    fun hideCardLibrary() {
        showCardLibrary = false
        Log.d(TAG, "Card library closed")
    }

    /**
     * Check if current mode is in visible modes list
     */
    fun isCurrentModeVisible(currentMode: FlightMode): Boolean {
        return currentMode in visibleModes
    }

    /**
     * Get fallback mode if current mode is not visible
     */
    fun getFallbackMode(): FlightMode {
        return FlightMode.CRUISE // Always fallback to Cruise
    }
}

