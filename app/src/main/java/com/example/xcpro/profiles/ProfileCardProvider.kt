package com.example.xcpro.profiles

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcpro.FlightMode

/**
 * Provides flight data cards based on active profile and current flight mode
 */
class ProfileCardProvider(private val context: Context) {
    
    private val profileRepository = ProfileRepository.getInstance(context)
    
    /**
     * Get card IDs that should be displayed for the current profile and flight mode
     */
    suspend fun getCardsForCurrentMode(flightMode: FlightMode): List<String> {
        val activeProfile = profileRepository.activeProfile.value
        return if (activeProfile != null) {
            // Get profile-specific card configuration for this mode
            activeProfile.cardConfigurations[flightMode] 
                ?: ProfileAwareTemplates.getCardConfigurationForMode(activeProfile.aircraftType, flightMode)
        } else {
            // Fallback to generic configuration
            listOf("gps_alt", "baro_alt", "vario", "ias", "ground_speed", "agl")
        }
    }
    
    /**
     * Get available templates for the active profile
     */
    suspend fun getAvailableTemplates() = 
        profileRepository.activeProfile.value?.let { profile ->
            ProfileAwareTemplates.getTemplatesForAircraft(profile.aircraftType)
        } ?: emptyList()
    
    /**
     * Get templates filtered for a specific flight mode
     */
    suspend fun getTemplatesForMode(flightMode: FlightMode) = 
        profileRepository.activeProfile.value?.let { profile ->
            ProfileAwareTemplates.getTemplatesForFlightMode(profile.aircraftType, flightMode)
        } ?: emptyList()
    
    /**
     * Check if a card is relevant for the current profile's aircraft type
     */
    suspend fun isCardRelevant(cardId: String): Boolean {
        val activeProfile = profileRepository.activeProfile.value ?: return true
        
        return when (activeProfile.aircraftType) {
            AircraftType.PARAGLIDER -> isCardRelevantForParaglider(cardId)
            AircraftType.HANG_GLIDER -> isCardRelevantForHangGlider(cardId)
            AircraftType.SAILPLANE -> isCardRelevantForSailplane(cardId)
            AircraftType.GLIDER -> isCardRelevantForGlider(cardId)
        }
    }
    
    private fun isCardRelevantForParaglider(cardId: String): Boolean {
        val paraglidingCards = setOf(
            "gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed",
            "thermal_avg", "climb_rate", "wind_spd", "wind_dir", "track",
            "wpt_dist", "wpt_brg", "flight_time", "glide_ratio"
        )
        val irrelevantCards = setOf(
            "mc_speed", "ld_req", "final_gld", "task_spd", "ballast", "bugs"
        )
        return paraglidingCards.contains(cardId) && !irrelevantCards.contains(cardId)
    }
    
    private fun isCardRelevantForHangGlider(cardId: String): Boolean {
        val hangGlidingCards = setOf(
            "gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed",
            "thermal_avg", "climb_rate", "wind_spd", "wind_dir", "track",
            "wpt_dist", "wpt_brg", "flight_time", "ld_curr", "ld_avg", "netto"
        )
        val irrelevantCards = setOf(
            "mc_speed", "ballast", "bugs", "water_ballast"
        )
        return hangGlidingCards.contains(cardId) && !irrelevantCards.contains(cardId)
    }
    
    private fun isCardRelevantForSailplane(cardId: String): Boolean {
        // Sailplanes have access to most advanced cards
        val irrelevantCards = setOf(
            "emergency_freq" // More relevant for paragliding
        )
        return !irrelevantCards.contains(cardId)
    }
    
    private fun isCardRelevantForGlider(cardId: String): Boolean {
        return isCardRelevantForSailplane(cardId) // Same as sailplane
    }
}

/**
 * Composable helper to get ProfileCardProvider
 */
@Composable
fun rememberProfileCardProvider(): ProfileCardProvider {
    val profileViewModel: ProfileViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    return ProfileCardProvider(context)
}

/**
 * Composable to get cards for current flight mode
 */
@Composable
fun getCardsForFlightMode(
    flightMode: FlightMode,
    provider: ProfileCardProvider = rememberProfileCardProvider()
): List<String> {
    val profileViewModel: ProfileViewModel = viewModel()
    val uiState by profileViewModel.uiState.collectAsState()
    
    return uiState.activeProfile?.let { profile ->
        profile.cardConfigurations[flightMode] 
            ?: ProfileAwareTemplates.getCardConfigurationForMode(profile.aircraftType, flightMode)
    } ?: listOf("gps_alt", "baro_alt", "vario", "ias", "ground_speed", "agl")
}