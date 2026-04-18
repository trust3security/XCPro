package com.trust3.xcpro.weather.rain

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WeatherOverlayTabSettingsUseCase @Inject constructor(
    private val preferencesRepository: WeatherOverlayPreferencesRepository
) {
    val enabledFlow: Flow<Boolean> = preferencesRepository.enabledFlow
    val opacityFlow: Flow<Float> = preferencesRepository.opacityFlow
    val animatePastWindowFlow: Flow<Boolean> = preferencesRepository.preferencesFlow
        .map { preferences -> preferences.animatePastWindow }

    suspend fun setEnabled(enabled: Boolean) {
        preferencesRepository.setEnabled(enabled)
    }

    suspend fun setOpacity(opacity: Float) {
        preferencesRepository.setOpacity(opacity)
    }

    suspend fun setAnimatePastWindow(enabled: Boolean) {
        preferencesRepository.setAnimatePastWindow(enabled)
    }
}
