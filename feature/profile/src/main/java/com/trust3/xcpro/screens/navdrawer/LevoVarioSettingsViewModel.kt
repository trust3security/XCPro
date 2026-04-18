package com.trust3.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.audio.effectiveLiftStartThreshold
import com.trust3.xcpro.audio.effectiveSinkStartThreshold
import com.trust3.xcpro.audio.withEffectiveLiftStartThreshold
import com.trust3.xcpro.audio.withEffectiveSinkStartThreshold
import com.trust3.xcpro.audio.VarioAudioSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LevoVarioUiState(
    val macCready: Double = 0.0,
    val macCreadyRisk: Double = 0.0,
    val autoMcEnabled: Boolean = true,
    val teCompensationEnabled: Boolean = true,
    val showWindSpeedOnVario: Boolean = true,
    val liftStartThreshold: Float = VarioAudioSettings().effectiveLiftStartThreshold().toFloat(),
    val sinkStartThreshold: Float = VarioAudioSettings().effectiveSinkStartThreshold().toFloat(),
    val audioSettings: VarioAudioSettings = VarioAudioSettings()
)

@HiltViewModel
class LevoVarioSettingsViewModel @Inject constructor(
    private val useCase: LevoVarioSettingsUseCase
) : ViewModel() {

    private val configFlow = useCase.configFlow

    val uiState: StateFlow<LevoVarioUiState> = configFlow.map { config ->
        LevoVarioUiState(
            macCready = config.macCready,
            macCreadyRisk = config.macCreadyRisk,
            autoMcEnabled = config.autoMcEnabled,
            teCompensationEnabled = config.teCompensationEnabled,
            showWindSpeedOnVario = config.showWindSpeedOnVario,
            liftStartThreshold = config.audioSettings.liftStartThreshold.toFloat(),
            sinkStartThreshold = config.audioSettings.sinkStartThreshold.toFloat(),
            audioSettings = config.audioSettings
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LevoVarioUiState()
        )

    fun setMacCready(value: Double) {
        viewModelScope.launch {
            useCase.setMacCready(value)
        }
    }

    fun setMacCreadyRisk(value: Double) {
        viewModelScope.launch {
            useCase.setMacCreadyRisk(value)
        }
    }

    fun setAutoMcEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setAutoMcEnabled(enabled)
        }
    }

    fun setTeCompensationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setTeCompensationEnabled(enabled)
        }
    }

    fun setShowWindSpeedOnVario(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setShowWindSpeedOnVario(enabled)
        }
    }

    private fun updateAudioSettings(transform: (VarioAudioSettings) -> VarioAudioSettings) {
        viewModelScope.launch {
            useCase.updateAudioSettings(transform)
        }
    }

    fun setAudioEnabled(enabled: Boolean) = updateAudioSettings { it.copy(enabled = enabled) }

    fun setAudioVolume(volume: Float) =
        updateAudioSettings { it.copy(volume = volume.coerceIn(0f, 1f)) }

    fun setLiftStartThreshold(value: Float) =
        updateAudioSettings {
            it.withEffectiveLiftStartThreshold(value.toDouble())
        }

    fun setSinkStartThreshold(value: Float) =
        updateAudioSettings {
            it.withEffectiveSinkStartThreshold(value.toDouble())
        }
}
