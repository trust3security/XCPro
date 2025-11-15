package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.audio.VarioAudioProfile
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.vario.VarioServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LevoVarioUiState(
    val imuAssistEnabled: Boolean = true,
    val audioSettings: VarioAudioSettings = VarioAudioSettings()
)

@HiltViewModel
class LevoVarioSettingsViewModel @Inject constructor(
    private val preferencesRepository: LevoVarioPreferencesRepository,
    private val varioServiceManager: VarioServiceManager
) : ViewModel() {

    private val imuFlow = preferencesRepository.config
        .map { config -> config.imuAssistEnabled }

    private val audioFlow = varioServiceManager.flightDataCalculator.audioEngine.settings

    val uiState: StateFlow<LevoVarioUiState> = combine(imuFlow, audioFlow) { imu, audio ->
        LevoVarioUiState(imuAssistEnabled = imu, audioSettings = audio)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LevoVarioUiState()
        )

    fun setImuAssistEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setImuAssistEnabled(enabled)
        }
    }

    private fun updateAudioSettings(transform: (VarioAudioSettings) -> VarioAudioSettings) {
        val current = uiState.value.audioSettings
        val updated = transform(current)
        varioServiceManager.flightDataCalculator.audioEngine.updateSettings(updated)
    }

    fun setAudioEnabled(enabled: Boolean) = updateAudioSettings { it.copy(enabled = enabled) }

    fun setAudioVolume(volume: Float) =
        updateAudioSettings { it.copy(volume = volume.coerceIn(0f, 1f)) }

    fun setAudioProfile(profile: VarioAudioProfile) =
        updateAudioSettings { it.copy(profile = profile) }

    fun setLiftThreshold(value: Float) =
        updateAudioSettings { it.copy(liftThreshold = value.toDouble()) }

    fun setDeadband(value: Float) =
        updateAudioSettings { it.copy(deadbandRange = value.toDouble()) }

    fun setSinkThreshold(value: Float) =
        updateAudioSettings { it.copy(sinkSilenceThreshold = value.toDouble()) }

    fun playTestTone(frequency: Double) {
        viewModelScope.launch {
            varioServiceManager.flightDataCalculator.audioEngine.playTestTone(frequency, 1000)
        }
    }

    fun playTestPattern(value: Double) {
        viewModelScope.launch {
            varioServiceManager.flightDataCalculator.audioEngine.playTestPattern(value, 3000)
        }
    }
}
