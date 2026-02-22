package com.example.xcpro.weather.rain

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class WeatherOverlayTabSettingsUseCase @Inject constructor(
    private val preferencesRepository: WeatherOverlayPreferencesRepository
) {
    val enabledFlow: Flow<Boolean> = preferencesRepository.enabledFlow
    val opacityFlow: Flow<Float> = preferencesRepository.opacityFlow

    suspend fun setEnabled(enabled: Boolean) {
        preferencesRepository.setEnabled(enabled)
    }

    suspend fun setOpacity(opacity: Float) {
        preferencesRepository.setOpacity(opacity)
    }
}

