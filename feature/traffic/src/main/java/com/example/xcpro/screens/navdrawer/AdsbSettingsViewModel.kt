package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.adsb.ADSB_MAX_DISTANCE_DEFAULT_KM
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
import com.example.xcpro.adsb.ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS
import com.example.xcpro.adsb.ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
import com.example.xcpro.adsb.OpenSkyClientCredentials
import com.example.xcpro.common.units.UnitsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
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
    val emergencyFlashEnabled: StateFlow<Boolean> = useCase.emergencyFlashEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
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
    val emergencyAudioMasterEnabled: StateFlow<Boolean> = useCase.emergencyAudioMasterEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )
    val emergencyAudioShadowMode: StateFlow<Boolean> = useCase.emergencyAudioShadowModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )
    val emergencyAudioRollbackLatched: StateFlow<Boolean> = useCase.emergencyAudioRollbackLatchedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )
    val emergencyAudioRollbackReason: StateFlow<String?> = useCase.emergencyAudioRollbackReasonFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
    val units: StateFlow<UnitsPreferences> = useCase.unitsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UnitsPreferences()
        )

    val uiState: StateFlow<AdsbSettingsUiState> = combine(
        iconSizePx,
        maxDistanceKm,
        verticalAboveMeters,
        verticalBelowMeters,
        emergencyFlashEnabled,
        emergencyAudioEnabled,
        emergencyAudioMasterEnabled,
        emergencyAudioShadowMode,
        emergencyAudioRollbackLatched,
        emergencyAudioRollbackReason,
        units
    ) { values ->
        AdsbSettingsUiState(
            iconSizePx = values[0] as Int,
            maxDistanceKm = values[1] as Int,
            verticalAboveMeters = values[2] as Double,
            verticalBelowMeters = values[3] as Double,
            emergencyFlashEnabled = values[4] as Boolean,
            emergencyAudioEnabled = values[5] as Boolean,
            emergencyAudioMasterEnabled = values[6] as Boolean,
            emergencyAudioShadowMode = values[7] as Boolean,
            emergencyAudioRollbackLatched = values[8] as Boolean,
            emergencyAudioRollbackReason = values[9] as String?,
            units = values[10] as UnitsPreferences
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdsbSettingsUiState()
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

    fun setEmergencyFlashEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setEmergencyFlashEnabled(enabled)
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

    fun setEmergencyAudioMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setEmergencyAudioMasterEnabled(enabled)
        }
    }

    fun setEmergencyAudioShadowMode(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setEmergencyAudioShadowMode(enabled)
        }
    }

    fun clearEmergencyAudioRollback() {
        viewModelScope.launch {
            useCase.clearEmergencyAudioRollback()
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
