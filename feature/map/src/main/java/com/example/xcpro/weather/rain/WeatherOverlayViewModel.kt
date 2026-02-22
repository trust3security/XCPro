package com.example.xcpro.weather.rain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WeatherOverlayViewModel @Inject constructor(
    observeWeatherOverlayStateUseCase: ObserveWeatherOverlayStateUseCase,
    private val weatherOverlayTabSettingsUseCase: WeatherOverlayTabSettingsUseCase
) : ViewModel() {
    val overlayState: StateFlow<WeatherOverlayRuntimeState> = observeWeatherOverlayStateUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherOverlayRuntimeState()
        )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            weatherOverlayTabSettingsUseCase.setEnabled(enabled)
        }
    }

    fun setOpacity(opacity: Float) {
        viewModelScope.launch {
            weatherOverlayTabSettingsUseCase.setOpacity(opacity)
        }
    }
}
