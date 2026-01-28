package com.example.xcpro.screens.navdrawer

import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class LevoVarioSettingsUseCase @Inject constructor(
    private val preferencesRepository: LevoVarioPreferencesRepository,
    private val sensorFusionRepository: SensorFusionRepository
) {
    val configFlow = preferencesRepository.config
    val audioSettingsFlow: StateFlow<VarioAudioSettings> = sensorFusionRepository.audioSettings

    suspend fun setMacCready(value: Double) {
        preferencesRepository.setMacCready(value)
    }

    suspend fun setMacCreadyRisk(value: Double) {
        preferencesRepository.setMacCreadyRisk(value)
    }

    fun updateAudioSettings(transform: (VarioAudioSettings) -> VarioAudioSettings) {
        val current = audioSettingsFlow.value
        val updated = transform(current)
        sensorFusionRepository.updateAudioSettings(updated)
    }
}
