package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.weather.rain.WeatherOverlayPreferencesRepository
import com.trust3.xcpro.weather.rain.WeatherRainAnimationSpeed
import com.trust3.xcpro.weather.rain.WeatherRainAnimationWindow
import com.trust3.xcpro.weather.rain.WeatherRainTransitionQuality
import com.trust3.xcpro.weather.rain.WeatherRadarFrameMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WeatherSettingsUseCase @Inject constructor(
    private val rainPreferencesRepository: WeatherOverlayPreferencesRepository
) {
    val rainOverlayEnabledFlow: Flow<Boolean> = rainPreferencesRepository.enabledFlow
    val rainOpacityFlow: Flow<Float> = rainPreferencesRepository.opacityFlow
    val rainAnimatePastWindowFlow: Flow<Boolean> = rainPreferencesRepository.preferencesFlow
        .map { preferences -> preferences.animatePastWindow }
    val rainAnimationWindowFlow: Flow<WeatherRainAnimationWindow> = rainPreferencesRepository
        .preferencesFlow
        .map { preferences -> preferences.animationWindow }
    val rainAnimationSpeedFlow: Flow<WeatherRainAnimationSpeed> = rainPreferencesRepository
        .preferencesFlow
        .map { preferences -> preferences.animationSpeed }
    val rainTransitionQualityFlow: Flow<WeatherRainTransitionQuality> = rainPreferencesRepository
        .preferencesFlow
        .map { preferences -> preferences.transitionQuality }
    val rainFrameModeFlow: Flow<WeatherRadarFrameMode> = rainPreferencesRepository
        .preferencesFlow
        .map { preferences -> preferences.frameMode }
    val rainManualFrameIndexFlow: Flow<Int> = rainPreferencesRepository
        .preferencesFlow
        .map { preferences -> preferences.manualFrameIndex }
    val rainSmoothEnabledFlow: Flow<Boolean> = rainPreferencesRepository
        .preferencesFlow
        .map { preferences -> preferences.renderOptions.smooth }
    val rainSnowEnabledFlow: Flow<Boolean> = rainPreferencesRepository
        .preferencesFlow
        .map { preferences -> preferences.renderOptions.snow }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        rainPreferencesRepository.setEnabled(enabled)
    }

    suspend fun setOpacity(opacity: Float) {
        rainPreferencesRepository.setOpacity(opacity)
    }

    suspend fun setAnimatePastWindow(enabled: Boolean) {
        rainPreferencesRepository.setAnimatePastWindow(enabled)
    }

    suspend fun setAnimationWindow(window: WeatherRainAnimationWindow) {
        rainPreferencesRepository.setAnimationWindow(window)
    }

    suspend fun setAnimationSpeed(speed: WeatherRainAnimationSpeed) {
        rainPreferencesRepository.setAnimationSpeed(speed)
    }

    suspend fun setTransitionQuality(quality: WeatherRainTransitionQuality) {
        rainPreferencesRepository.setTransitionQuality(quality)
    }

    suspend fun setFrameMode(mode: WeatherRadarFrameMode) {
        rainPreferencesRepository.setFrameMode(mode)
    }

    suspend fun setManualFrameIndex(index: Int) {
        rainPreferencesRepository.setManualFrameIndex(index)
    }

    suspend fun setSmoothEnabled(enabled: Boolean) {
        rainPreferencesRepository.setSmoothEnabled(enabled)
    }

    suspend fun setSnowEnabled(enabled: Boolean) {
        rainPreferencesRepository.setSnowEnabled(enabled)
    }
}
