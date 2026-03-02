package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.adsb.ADSB_MAX_DISTANCE_DEFAULT_KM
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
import com.example.xcpro.adsb.ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS
import com.example.xcpro.adsb.OpenSkyClientCredentials
import com.example.xcpro.common.units.UnitsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AdsbSettingsViewModel @Inject constructor(
    private val useCase: AdsbSettingsUseCase
) : ViewModel() {
    val iconSizePx: StateFlow<Int> = useCase.iconSizePxFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ADSB_ICON_SIZE_DEFAULT_PX
        )
    val maxDistanceKm: StateFlow<Int> = useCase.maxDistanceKmFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ADSB_MAX_DISTANCE_DEFAULT_KM
        )
    val verticalAboveMeters: StateFlow<Double> = useCase.verticalAboveMetersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
        )
    val verticalBelowMeters: StateFlow<Double> = useCase.verticalBelowMetersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
        )
    val emergencyAudioEnabled: StateFlow<Boolean> = useCase.emergencyAudioEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )
    val emergencyAudioCooldownMs: StateFlow<Long> = useCase.emergencyAudioCooldownMsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS
        )
    val units: StateFlow<UnitsPreferences> = useCase.unitsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UnitsPreferences()
        )

    fun setIconSizePx(iconSizePx: Int) {
        viewModelScope.launch {
            useCase.setIconSizePx(iconSizePx)
        }
    }

    fun setMaxDistanceKm(maxDistanceKm: Int) {
        viewModelScope.launch {
            useCase.setMaxDistanceKm(maxDistanceKm)
        }
    }

    fun setVerticalAboveMeters(aboveMeters: Double) {
        viewModelScope.launch {
            useCase.setVerticalAboveMeters(aboveMeters)
        }
    }

    fun setVerticalBelowMeters(belowMeters: Double) {
        viewModelScope.launch {
            useCase.setVerticalBelowMeters(belowMeters)
        }
    }

    fun setEmergencyAudioEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setEmergencyAudioEnabled(enabled)
        }
    }

    fun setEmergencyAudioCooldownMs(cooldownMs: Long) {
        viewModelScope.launch {
            useCase.setEmergencyAudioCooldownMs(cooldownMs)
        }
    }

    fun loadOpenSkyCredentials(): OpenSkyClientCredentials? =
        useCase.loadOpenSkyCredentials()

    fun saveOpenSkyCredentials(clientId: String, clientSecret: String) {
        useCase.saveOpenSkyCredentials(clientId = clientId, clientSecret = clientSecret)
    }

    fun clearOpenSkyCredentials() {
        useCase.clearOpenSkyCredentials()
    }
}
