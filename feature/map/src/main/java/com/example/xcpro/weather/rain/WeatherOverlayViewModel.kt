package com.example.xcpro.weather.rain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WeatherOverlayViewModel @Inject constructor(
    observeWeatherOverlayStateUseCase: ObserveWeatherOverlayStateUseCase
) : ViewModel() {
    val overlayState: StateFlow<WeatherOverlayRuntimeState> = observeWeatherOverlayStateUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeatherOverlayRuntimeState()
        )
}
