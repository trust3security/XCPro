package com.example.xcpro.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.xcpro.FlightMode

class ProfileCardProvider(
    private val profileViewModel: ProfileViewModel
) {

    private val currentState: ProfileUiState
        get() = profileViewModel.uiState.value

    fun getCardsForCurrentMode(flightMode: FlightMode): List<String> {
        val activeProfile = currentState.activeProfile
        return if (activeProfile != null) {
            activeProfile.cardConfigurations[flightMode]
                ?: ProfileAwareTemplates.getCardConfigurationForMode(activeProfile.aircraftType, flightMode)
        } else {
            DEFAULT_CARDS
        }
    }

    fun getAvailableTemplates(): List<String> {
        val profile = currentState.activeProfile ?: return emptyList()
        return ProfileAwareTemplates.getTemplatesForAircraft(profile.aircraftType).map { it.id }
    }

    fun getTemplatesForMode(flightMode: FlightMode): List<String> {
        val profile = currentState.activeProfile ?: return emptyList()
        return ProfileAwareTemplates.getTemplatesForFlightMode(profile.aircraftType, flightMode).map { it.id }
    }

    fun isCardRelevant(cardId: String): Boolean {
        val profile = currentState.activeProfile ?: return true
        return when (profile.aircraftType) {
            AircraftType.PARAGLIDER -> PARAGLIDER_RELEVANT.contains(cardId) && !PARAGLIDER_EXCLUDED.contains(cardId)
            AircraftType.HANG_GLIDER -> HANG_GLIDER_RELEVANT.contains(cardId) && !HANG_GLIDER_EXCLUDED.contains(cardId)
            AircraftType.SAILPLANE -> !SAILPLANE_EXCLUDED.contains(cardId)
            AircraftType.GLIDER -> !SAILPLANE_EXCLUDED.contains(cardId)
        }
    }

    companion object {
        private val DEFAULT_CARDS = listOf("gps_alt", "baro_alt", "vario", "ias", "ground_speed", "agl")

        private val PARAGLIDER_RELEVANT = setOf(
            "gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed",
            "thermal_avg", "climb_rate", "wind_spd", "wind_dir", "track",
            "wpt_dist", "wpt_brg", "flight_time", "glide_ratio"
        )
        private val PARAGLIDER_EXCLUDED = setOf(
            "mc_speed", "ld_req", "final_gld", "task_spd", "ballast", "bugs"
        )

        private val HANG_GLIDER_RELEVANT = setOf(
            "gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed",
            "thermal_avg", "climb_rate", "wind_spd", "wind_dir", "track",
            "wpt_dist", "wpt_brg", "flight_time", "ld_curr", "ld_avg", "netto"
        )
        private val HANG_GLIDER_EXCLUDED = setOf(
            "mc_speed", "ballast", "bugs", "water_ballast"
        )

        private val SAILPLANE_EXCLUDED = setOf("emergency_freq")
    }
}

@Composable
fun rememberProfileCardProvider(): ProfileCardProvider {
    val profileViewModel: ProfileViewModel = hiltViewModel()
    return remember(profileViewModel) { ProfileCardProvider(profileViewModel) }
}

@Composable
fun getCardsForFlightMode(
    flightMode: FlightMode,
    provider: ProfileCardProvider = rememberProfileCardProvider()
): List<String> = provider.getCardsForCurrentMode(flightMode)
