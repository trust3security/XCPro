package com.example.dfcards

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.example.dfcards.dfcards.CardState
import com.example.xcpro.core.time.Clock
import com.example.xcpro.core.time.DefaultClockProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "card_preferences")

class CardPreferences(
    private val context: Context,
    private val clock: Clock = DefaultClockProvider()
) {

    companion object {
        const val MIN_CARDS_ACROSS_PORTRAIT = 3
        const val MAX_CARDS_ACROSS_PORTRAIT = 8
        const val DEFAULT_CARDS_ACROSS_PORTRAIT = MIN_CARDS_ACROSS_PORTRAIT
        val DEFAULT_ANCHOR_PORTRAIT = CardAnchor.TOP

        private const val DEFAULT_SMOOTHING_ALPHA = 0.25f
        private val VARIO_SMOOTHING_KEY = floatPreferencesKey("vario_smoothing_alpha")
        private val CARDS_ACROSS_PORTRAIT_KEY = intPreferencesKey("cards_across_portrait")
        private val CARDS_ANCHOR_PORTRAIT_KEY = stringPreferencesKey("cards_anchor_portrait")
    }

    // EXISTING: Save individual card state (keep for backward compatibility)
    suspend fun saveCardState(cardState: CardState) {
        context.dataStore.edit { preferences ->
            preferences[floatPreferencesKey("${cardState.id}_x")] = cardState.x
            preferences[floatPreferencesKey("${cardState.id}_y")] = cardState.y
            preferences[floatPreferencesKey("${cardState.id}_width")] = cardState.width
            preferences[floatPreferencesKey("${cardState.id}_height")] = cardState.height
        }
    }

    // EXISTING: Individual card position (MAKE SURE THIS IS HERE)
    fun getCardPosition(cardId: String): Flow<CardPosition?> {
        return context.dataStore.data.map { preferences ->
            val x = preferences[floatPreferencesKey("${cardId}_x")]
            val y = preferences[floatPreferencesKey("${cardId}_y")]
            val width = preferences[floatPreferencesKey("${cardId}_width")]
            val height = preferences[floatPreferencesKey("${cardId}_height")]

            if (x != null && y != null && width != null && height != null) {
                CardPosition(x, y, width, height)
            } else null
        }
    }

    // NEW: Save card positions for a specific template
    suspend fun saveTemplateCardPositions(templateId: String, cardStates: List<CardState>) {
        context.dataStore.edit { preferences ->
            // Clear existing positions for this template
            val keysToRemove = preferences.asMap().keys.filter {
                it.name.startsWith("${templateId}_")
            }
            keysToRemove.forEach { preferences.remove(it) }

            // Save new positions
            cardStates.forEach { cardState ->
                val prefix = "${templateId}_${cardState.id}"
                preferences[floatPreferencesKey("${prefix}_x")] = cardState.x
                preferences[floatPreferencesKey("${prefix}_y")] = cardState.y
                preferences[floatPreferencesKey("${prefix}_width")] = cardState.width
                preferences[floatPreferencesKey("${prefix}_height")] = cardState.height
            }

            // Save timestamp for this template layout
            preferences[stringPreferencesKey("${templateId}_last_updated")] = clock.nowWallMs().toString()
        }
    }

    // NEW: Load card positions for a specific template
    fun getTemplateCardPositions(templateId: String): Flow<Map<String, CardPosition>> {
        return context.dataStore.data.map { preferences ->
            val positions = mutableMapOf<String, CardPosition>()

            // Find all cards for this template
            val templateKeys = preferences.asMap().keys.filter {
                it.name.startsWith("${templateId}_") && it.name.endsWith("_x")
            }

            templateKeys.forEach { xKey ->
                val keyName = xKey.name
                val cardId = keyName.removePrefix("${templateId}_").removeSuffix("_x")

                val x = preferences[floatPreferencesKey("${templateId}_${cardId}_x")]
                val y = preferences[floatPreferencesKey("${templateId}_${cardId}_y")]
                val width = preferences[floatPreferencesKey("${templateId}_${cardId}_width")]
                val height = preferences[floatPreferencesKey("${templateId}_${cardId}_height")]

                if (x != null && y != null && width != null && height != null) {
                    positions[cardId] = CardPosition(x, y, width, height)
                }
            }

            positions
        }
    }

    // NEW: Check if template has saved positions
    fun hasTemplatePositions(templateId: String): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences.asMap().keys.any {
                it.name.startsWith("${templateId}_") && it.name.endsWith("_x")
            }
        }
    }

    // NEW: Get last active template
    fun getLastActiveTemplate(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[stringPreferencesKey("last_active_template")]
        }
    }

    // NEW: Save last active template
    suspend fun saveLastActiveTemplate(templateId: String) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("last_active_template")] = templateId
        }
    }

    fun getCardsAcrossPortrait(): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            (preferences[CARDS_ACROSS_PORTRAIT_KEY] ?: DEFAULT_CARDS_ACROSS_PORTRAIT)
                .coerceIn(MIN_CARDS_ACROSS_PORTRAIT, MAX_CARDS_ACROSS_PORTRAIT)
        }
    }

    suspend fun setCardsAcrossPortrait(count: Int) {
        val clamped = count.coerceIn(MIN_CARDS_ACROSS_PORTRAIT, MAX_CARDS_ACROSS_PORTRAIT)
        context.dataStore.edit { preferences ->
            preferences[CARDS_ACROSS_PORTRAIT_KEY] = clamped
        }
    }

    fun getCardsAnchorPortrait(): Flow<CardAnchor> {
        return context.dataStore.data.map { preferences ->
            when (preferences[CARDS_ANCHOR_PORTRAIT_KEY]) {
                CardAnchor.BOTTOM.name -> CardAnchor.BOTTOM
                else -> DEFAULT_ANCHOR_PORTRAIT
            }
        }
    }

    suspend fun setCardsAnchorPortrait(anchor: CardAnchor) {
        context.dataStore.edit { preferences ->
            preferences[CARDS_ANCHOR_PORTRAIT_KEY] = anchor.name
        }
    }

    //  NEW: Save profile-specific template configuration
    suspend fun saveProfileTemplateCards(profileId: String, templateId: String, cardIds: List<String>) {
        android.util.Log.d("CardPreferences", " saveProfileTemplateCards: Profile '$profileId', Template '$templateId', Cards: ${cardIds.joinToString(",")}")
        
        context.dataStore.edit { preferences ->
            // Save the card configuration for this profile+template combination
            val key = "profile_${profileId}_template_${templateId}_cards"
            preferences[stringPreferencesKey(key)] = cardIds.joinToString(",")
            android.util.Log.d("CardPreferences", " Saved profile-specific cards: $key = ${cardIds.joinToString(",")}")
        }
    }
    
    //  NEW: Get profile-specific template configuration
    fun getProfileTemplateCards(profileId: String, templateId: String): Flow<List<String>?> {
        return context.dataStore.data.map { preferences ->
            val key = "profile_${profileId}_template_${templateId}_cards"
            val cardsString = preferences[stringPreferencesKey(key)]
            val cards = cardsString?.split(",")?.filter { it.isNotBlank() }
            android.util.Log.d("CardPreferences", " Loading profile cards: $key = ${cards?.joinToString(",") ?: "NULL (using default)"}")
            cards
        }
    }
    
    // UPDATED: Save ALL templates (unified system) - WITH isPreset support
    suspend fun saveAllTemplates(templates: List<FlightTemplate>) {
        android.util.Log.d("CardPreferences", " saveAllTemplates called with ${templates.size} templates")
        templates.forEach { template ->
            android.util.Log.d("CardPreferences", "   ${template.id}: ${template.name} - ${template.cardIds.size} cards: ${template.cardIds.joinToString(",")}")
        }
        
        context.dataStore.edit { preferences ->
            // Clear existing templates
            val keysToRemove = preferences.asMap().keys.filter {
                it.name.startsWith("all_template_")
            }
            android.util.Log.d("CardPreferences", " Removing ${keysToRemove.size} old template keys")
            keysToRemove.forEach { preferences.remove(it) }

            // Save all templates
            templates.forEachIndexed { index, template ->
                preferences[stringPreferencesKey("all_template_${index}_id")] = template.id
                preferences[stringPreferencesKey("all_template_${index}_name")] = template.name
                preferences[stringPreferencesKey("all_template_${index}_desc")] = template.description
                preferences[stringPreferencesKey("all_template_${index}_cards")] = template.cardIds.joinToString(",")
                preferences[stringPreferencesKey("all_template_${index}_preset")] = template.isPreset.toString() // ADDED
            }
            preferences[stringPreferencesKey("all_template_count")] = templates.size.toString()
            android.util.Log.d("CardPreferences", " Saved ${templates.size} templates to preferences")
        }
    }

    // UPDATED: Get ALL templates (unified system) - WITH isPreset support
    fun getAllTemplates(): Flow<List<FlightTemplate>> {
        return context.dataStore.data.map { preferences ->
            val count = preferences[stringPreferencesKey("all_template_count")]?.toIntOrNull() ?: 0

            if (count == 0) {
                // First time - return defaults AND save them
                val defaults = FlightTemplates.getDefaultTemplates()
                android.util.Log.d("CardPreferences", " First time loading - initializing with ${defaults.size} default templates")
                defaults
            } else {
                // Check if we have old template IDs that need migration
                val hasOldIds = preferences.asMap().keys.any { key ->
                    key.name.startsWith("all_template_") && 
                    (key.name.contains("essential") || key.name.contains("thermal") || key.name.contains("cross_country"))
                }
                
                if (hasOldIds) {
                    // Migrate old templates but preserve user modifications
                    android.util.Log.w("CardPreferences", " Migrating old template IDs to new id01, id02, id03 system")
                    // Don't reset - just continue loading what we have
                }
                
                // Load saved templates (whether migrated or not)
                val templates = mutableListOf<FlightTemplate>()
                for (i in 0 until count) {
                    val id = preferences[stringPreferencesKey("all_template_${i}_id")]
                    val name = preferences[stringPreferencesKey("all_template_${i}_name")]
                    val desc = preferences[stringPreferencesKey("all_template_${i}_desc")]
                    val cardsString = preferences[stringPreferencesKey("all_template_${i}_cards")]
                    val isPreset = preferences[stringPreferencesKey("all_template_${i}_preset")]?.toBoolean() ?: false // ADDED

                    if (id != null && name != null && desc != null && cardsString != null) {
                        val cardIds = if (cardsString.isNotBlank()) {
                            cardsString.split(",")
                        } else emptyList()

                        templates.add(
                            FlightTemplate(
                                id = id,
                                name = name,
                                description = desc,
                                cardIds = cardIds,
                                icon = Icons.Default.Star,
                                isPreset = isPreset // ADDED
                            )
                        )
                        android.util.Log.d("CardPreferences", " Loaded template ${id}: ${name} - ${cardIds.size} cards: ${cardIds.joinToString(",")}")
                    }
                }
                android.util.Log.d("CardPreferences", " Loaded ${templates.size} templates from preferences")
                templates
            }
        }
    }

    //  UPDATED: Profile-aware flight mode  template mapping
    suspend fun saveFlightModeTemplate(flightMode: String, templateId: String) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("flight_mode_${flightMode}_template")] = templateId
        }
    }
    
    //  NEW: Profile-aware flight mode  template mapping
    suspend fun saveProfileFlightModeTemplate(profileId: String, flightMode: String, templateId: String) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("profile_${profileId}_flight_mode_${flightMode}_template")] = templateId
        }
    }

    //  UPDATED: Get saved template for flight mode (backward compatibility)
    fun getFlightModeTemplate(flightMode: String): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[stringPreferencesKey("flight_mode_${flightMode}_template")]
        }
    }
    
    //  NEW: Get saved template for profile + flight mode
    fun getProfileFlightModeTemplate(profileId: String, flightMode: String): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[stringPreferencesKey("profile_${profileId}_flight_mode_${flightMode}_template")]
        }
    }

    fun getAllProfileTemplateCards(): Flow<Map<String, Map<String, List<String>>>> {
        return context.dataStore.data.map { preferences ->
            val result = mutableMapOf<String, MutableMap<String, List<String>>>()
            preferences.asMap().forEach { (key, value) ->
                val name = key.name
                if (name.startsWith("profile_") && name.contains("_template_") && name.endsWith("_cards")) {
                    val profilePart = name.substringAfter("profile_").substringBefore("_template_")
                    val templatePart = name.substringAfter("_template_").substringBefore("_cards")
                    val cardsString = value as? String
                    val cards = cardsString
                        ?.split(",")
                        ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                        .orEmpty()
                    val templateMap = result.getOrPut(profilePart) { mutableMapOf() }
                    templateMap[templatePart] = cards
                }
            }
            result.mapValues { it.value.toMap() }
        }
    }

    fun getAllProfileFlightModeTemplates(): Flow<Map<String, Map<String, String>>> {
        return context.dataStore.data.map { preferences ->
            val result = mutableMapOf<String, MutableMap<String, String>>()
            preferences.asMap().forEach { (key, value) ->
                val name = key.name
                if (name.startsWith("profile_") && name.contains("_flight_mode_") && name.endsWith("_template")) {
                    val profilePart = name.substringAfter("profile_").substringBefore("_flight_mode_")
                    val modePart = name.substringAfter("_flight_mode_").substringBefore("_template")
                    val templateId = value as? String ?: return@forEach
                    val templateMap = result.getOrPut(profilePart) { mutableMapOf() }
                    templateMap[modePart] = templateId
                }
            }
            result.mapValues { it.value.toMap() }
        }
    }


    //  NEW: Get all flight mode template mappings
    fun getAllFlightModeTemplates(): Flow<Map<String, String>> {
        return context.dataStore.data.map { preferences ->
            val mappings = mutableMapOf<String, String>()

            // Check for each screen mode (with S prefix)
            listOf("CRUISE", "THERMAL", "FINAL_GLIDE").forEach { flightMode ->
                val templateId = preferences[stringPreferencesKey("flight_mode_template_$flightMode")]
                if (templateId != null) {
                    mappings[flightMode] = templateId
                }
            }

            mappings
        }
    }

    //  UPDATED: Save card position (backward compatibility)
    suspend fun saveCardPosition(cardState: CardState) {
        context.dataStore.edit { preferences ->
            preferences[floatPreferencesKey("${cardState.id}_x")] = cardState.x
            preferences[floatPreferencesKey("${cardState.id}_y")] = cardState.y
            preferences[floatPreferencesKey("${cardState.id}_width")] = cardState.width
            preferences[floatPreferencesKey("${cardState.id}_height")] = cardState.height
        }
    }
    
    //  NEW: Profile-aware card position saving
    suspend fun saveProfileCardPositions(profileId: String, flightMode: String, cardStates: List<CardState>) {
        context.dataStore.edit { preferences ->
            // Clear existing positions for this profile + flight mode
            val keysToRemove = preferences.asMap().keys.filter {
                it.name.startsWith("profile_${profileId}_${flightMode}_")
            }
            keysToRemove.forEach { preferences.remove(it) }

            // Save new positions
            cardStates.forEach { cardState ->
                val prefix = "profile_${profileId}_${flightMode}_${cardState.id}"
                preferences[floatPreferencesKey("${prefix}_x")] = cardState.x
                preferences[floatPreferencesKey("${prefix}_y")] = cardState.y
                preferences[floatPreferencesKey("${prefix}_width")] = cardState.width
                preferences[floatPreferencesKey("${prefix}_height")] = cardState.height
            }

            // Save timestamp for this profile + flight mode layout
            preferences[stringPreferencesKey("profile_${profileId}_${flightMode}_last_updated")] = clock.nowWallMs().toString()
        }
    }

    //  NEW: Load card positions for profile + flight mode
    fun getProfileCardPositions(profileId: String, flightMode: String): Flow<Map<String, CardPosition>> {
        return context.dataStore.data.map { preferences ->
            val positions = mutableMapOf<String, CardPosition>()

            // Find all cards for this profile + flight mode
            val profileKeys = preferences.asMap().keys.filter {
                it.name.startsWith("profile_${profileId}_${flightMode}_") && it.name.endsWith("_x")
            }

            profileKeys.forEach { xKey ->
                val keyName = xKey.name
                val cardId = keyName.removePrefix("profile_${profileId}_${flightMode}_").removeSuffix("_x")

                val x = preferences[floatPreferencesKey("profile_${profileId}_${flightMode}_${cardId}_x")]
                val y = preferences[floatPreferencesKey("profile_${profileId}_${flightMode}_${cardId}_y")]
                val width = preferences[floatPreferencesKey("profile_${profileId}_${flightMode}_${cardId}_width")]
                val height = preferences[floatPreferencesKey("profile_${profileId}_${flightMode}_${cardId}_height")]

                if (x != null && y != null && width != null && height != null) {
                    positions[cardId] = CardPosition(x, y, width, height)
                }
            }

            positions
        }
    }

    //  NEW: Check if profile has saved card positions for flight mode
    fun hasProfileCardPositions(profileId: String, flightMode: String): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences.asMap().keys.any {
                it.name.startsWith("profile_${profileId}_${flightMode}_") && it.name.endsWith("_x")
            }
        }
    }

    //  NEW: Save flight mode visibility for profile
    suspend fun saveProfileFlightModeVisibility(profileId: String, flightMode: String, isVisible: Boolean) {
        android.util.Log.d("CardPreferences", " Saving flight mode visibility: Profile '$profileId', Mode '$flightMode', Visible: $isVisible")
        context.dataStore.edit { preferences ->
            val key = "profile_${profileId}_${flightMode}_visible"
            preferences[booleanPreferencesKey(key)] = isVisible
        }
    }

    //  NEW: Get flight mode visibility for profile
    fun getProfileFlightModeVisibility(profileId: String, flightMode: String): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            val key = "profile_${profileId}_${flightMode}_visible"
            preferences[booleanPreferencesKey(key)] ?: true // Default to visible
        }
    }

    //  NEW: Get all flight mode visibilities for profile
    fun getProfileAllFlightModeVisibilities(profileId: String): Flow<Map<String, Boolean>> {
        return context.dataStore.data.map { preferences ->
            val visibilities = mutableMapOf<String, Boolean>()
            val flightModes = listOf("CRUISE", "THERMAL", "FINAL_GLIDE")
            
            flightModes.forEach { flightMode ->
                val key = "profile_${profileId}_${flightMode}_visible"
                if (flightMode == "CRUISE") {
                    // CRUISE is always visible
                    visibilities[flightMode] = true
                } else {
                    // Others can be toggled
                    visibilities[flightMode] = preferences[booleanPreferencesKey(key)] ?: true
                }
            }
            
            visibilities
        }
    }

    data class CardPosition(val x: Float, val y: Float, val width: Float, val height: Float)

    // Variometer smoothing preference ---------------------------------------------------
    suspend fun saveVarioSmoothingAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.05f, 0.95f)
        context.dataStore.edit { preferences ->
            preferences[VARIO_SMOOTHING_KEY] = clamped
        }
    }

    fun getVarioSmoothingAlpha(): Flow<Float> {
        return context.dataStore.data.map { preferences ->
            preferences[VARIO_SMOOTHING_KEY] ?: DEFAULT_SMOOTHING_ALPHA
        }
    }

    suspend fun clearProfile(profileId: String) {
        context.dataStore.edit { preferences ->
            val keysToRemove = preferences.asMap().keys.filter { key ->
                key.name.startsWith("profile_${profileId}_")
            }
            keysToRemove.forEach { preferences.remove(it) }
        }
    }

    enum class CardAnchor { TOP, BOTTOM }
}
