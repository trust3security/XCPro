package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val macCready: Double = 0.0,
    val macCreadyRisk: Double = 0.0,
    val audioSettings: VarioAudioSettings = VarioAudioSettings()
)

@HiltViewModel
class LevoVarioSettingsViewModel @Inject constructor(
    private val preferencesRepository: LevoVarioPreferencesRepository,
    private val varioServiceManager: VarioServiceManager
) : ViewModel() {

    private val configFlow = preferencesRepository.config
    private val audioFlow = varioServiceManager.sensorFusionRepository.audioSettings

    val uiState: StateFlow<LevoVarioUiState> = combine(configFlow, audioFlow) { config, audio ->
        LevoVarioUiState(
            macCready = config.macCready,
            macCreadyRisk = config.macCreadyRisk,
            audioSettings = audio
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LevoVarioUiState()
        )

    fun setMacCready(value: Double) {
        viewModelScope.launch {
            preferencesRepository.setMacCready(value)
        }
    }

    fun setMacCreadyRisk(value: Double) {
        viewModelScope.launch {
            preferencesRepository.setMacCreadyRisk(value)
        }
    }

    private fun updateAudioSettings(transform: (VarioAudioSettings) -> VarioAudioSettings) {
        val current = uiState.value.audioSettings
        val updated = transform(current)
        varioServiceManager.sensorFusionRepository.updateAudioSettings(updated)
    }

    fun setAudioEnabled(enabled: Boolean) = updateAudioSettings { it.copy(enabled = enabled) }

    fun setAudioVolume(volume: Float) =
        updateAudioSettings { it.copy(volume = volume.coerceIn(0f, 1f)) }

    fun setLiftThreshold(value: Float) =
        updateAudioSettings { it.copy(liftThreshold = value.toDouble()) }

    fun setDeadbandMin(value: Float) =
        updateAudioSettings {
            val clamped = value.coerceAtMost((it.deadbandMax - 0.05).toFloat())
            it.copy(deadbandMin = clamped.toDouble())
        }

    fun setDeadbandMax(value: Float) =
        updateAudioSettings {
            val clamped = value.coerceAtLeast((it.deadbandMin + 0.05).toFloat())
            it.copy(deadbandMax = clamped.toDouble())
        }

    fun setSinkThreshold(value: Float) =
        updateAudioSettings { it.copy(sinkSilenceThreshold = value.toDouble()) }
}
