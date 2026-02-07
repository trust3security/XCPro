package com.example.xcpro.screens.navdrawer

import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class LevoVarioSettingsUseCase @Inject constructor(
    private val preferencesRepository: LevoVarioPreferencesRepository
) {
    val configFlow = preferencesRepository.config
    val audioSettingsFlow = preferencesRepository.config.map { it.audioSettings }

    suspend fun setMacCready(value: Double) {
        preferencesRepository.setMacCready(value)
    }

    suspend fun setMacCreadyRisk(value: Double) {
        preferencesRepository.setMacCreadyRisk(value)
    }

    suspend fun setAutoMcEnabled(enabled: Boolean) {
        preferencesRepository.setAutoMcEnabled(enabled)
    }

    suspend fun setShowWindSpeedOnVario(enabled: Boolean) {
        preferencesRepository.setShowWindSpeedOnVario(enabled)
    }

    suspend fun setShowHawkCard(enabled: Boolean) {
        preferencesRepository.setShowHawkCard(enabled)
    }

    suspend fun updateAudioSettings(transform: (VarioAudioSettings) -> VarioAudioSettings) {
        preferencesRepository.updateAudioSettings(transform)
    }
}
