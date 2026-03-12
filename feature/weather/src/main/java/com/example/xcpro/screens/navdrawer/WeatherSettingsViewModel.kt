package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_DEFAULT
import com.example.xcpro.weather.rain.WeatherRainAnimationSpeed
import com.example.xcpro.weather.rain.WeatherRainAnimationWindow
import com.example.xcpro.weather.rain.WeatherRainTransitionQuality
import com.example.xcpro.weather.rain.WeatherRadarFrameMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WeatherSettingsViewModel @Inject constructor(
    private val useCase: WeatherSettingsUseCase
) : ViewModel() {
    val animationWindows: List<WeatherRainAnimationWindow> = WeatherRainAnimationWindow.entries
    val animationSpeeds: List<WeatherRainAnimationSpeed> = WeatherRainAnimationSpeed.entries
    val transitionQualities: List<WeatherRainTransitionQuality> = WeatherRainTransitionQuality.entries
    val frameModes: List<WeatherRadarFrameMode> = WeatherRadarFrameMode.entries

    val overlayEnabled: StateFlow<Boolean> = useCase.rainOverlayEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val opacity: StateFlow<Float> = useCase.rainOpacityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WEATHER_RAIN_OPACITY_DEFAULT
        )

    val animatePastWindow: StateFlow<Boolean> = useCase.rainAnimatePastWindowFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val animationWindow: StateFlow<WeatherRainAnimationWindow> = useCase.rainAnimationWindowFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherRainAnimationWindow.TEN_MINUTES
        )

    val animationSpeed: StateFlow<WeatherRainAnimationSpeed> = useCase.rainAnimationSpeedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherRainAnimationSpeed.NORMAL
        )

    val transitionQuality: StateFlow<WeatherRainTransitionQuality> =
        useCase.rainTransitionQualityFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WeatherRainTransitionQuality.BALANCED
            )

    val frameMode: StateFlow<WeatherRadarFrameMode> = useCase.rainFrameModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherRadarFrameMode.LATEST
        )

    val manualFrameIndex: StateFlow<Int> = useCase.rainManualFrameIndexFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val smoothEnabled: StateFlow<Boolean> = useCase.rainSmoothEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    val snowEnabled: StateFlow<Boolean> = useCase.rainSnowEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setOverlayEnabled(enabled)
        }
    }

    fun setOpacity(opacity: Float) {
        viewModelScope.launch {
            useCase.setOpacity(opacity)
        }
    }

    fun setAnimatePastWindow(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setAnimatePastWindow(enabled)
        }
    }

    fun setAnimationWindow(window: WeatherRainAnimationWindow) {
        viewModelScope.launch {
            useCase.setAnimationWindow(window)
        }
    }

    fun setAnimationSpeed(speed: WeatherRainAnimationSpeed) {
        viewModelScope.launch {
            useCase.setAnimationSpeed(speed)
        }
    }

    fun setTransitionQuality(quality: WeatherRainTransitionQuality) {
        viewModelScope.launch {
            useCase.setTransitionQuality(quality)
        }
    }

    fun setFrameMode(mode: WeatherRadarFrameMode) {
        viewModelScope.launch {
            useCase.setFrameMode(mode)
        }
    }

    fun setManualFrameIndex(index: Int) {
        viewModelScope.launch {
            useCase.setManualFrameIndex(index)
        }
    }

    fun setSmoothEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setSmoothEnabled(enabled)
        }
    }

    fun setSnowEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setSnowEnabled(enabled)
        }
    }
}
