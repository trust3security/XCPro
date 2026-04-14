package com.example.xcpro.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ForecastOverlayViewModel @Inject constructor(
    observeForecastOverlayStateUseCase: ObserveForecastOverlayStateUseCase,
    private val preferencesRepository: ForecastPreferencesRepository,
    private val selectForecastPrimaryParameterUseCase: SelectForecastPrimaryParameterUseCase,
    private val selectForecastWindParameterUseCase: SelectForecastWindParameterUseCase,
    private val setForecastTimeUseCase: SetForecastTimeUseCase,
    private val queryForecastValueAtPointUseCase: QueryForecastValueAtPointUseCase
) : ViewModel() {

    val overlayState: StateFlow<ForecastOverlayUiState> = observeForecastOverlayStateUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ForecastOverlayUiState()
        )

    private val _pointCallout = MutableStateFlow<ForecastPointCallout?>(null)
    val pointCallout: StateFlow<ForecastPointCallout?> = _pointCallout.asStateFlow()

    private val _queryStatus = MutableStateFlow<String?>(null)
    val queryStatus: StateFlow<String?> = _queryStatus.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOverlayEnabled(enabled)
        }
    }

    fun selectSkySightPrimaryParameter(parameterId: ForecastParameterId) {
        viewModelScope.launch {
            selectForecastPrimaryParameterUseCase(parameterId)
        }
    }

    fun setWindOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setWindOverlayEnabled(enabled)
        }
    }

    fun selectWindParameter(parameterId: ForecastParameterId) {
        viewModelScope.launch {
            selectForecastWindParameterUseCase(parameterId)
        }
    }

    fun selectTime(timeUtcMs: Long) {
        viewModelScope.launch {
            setForecastTimeUseCase(timeUtcMs)
        }
    }

    fun setAutoTimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoTimeEnabled(enabled)
        }
    }

    fun setFollowTimeOffsetMinutes(offsetMinutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setFollowTimeOffsetMinutes(offsetMinutes)
        }
    }

    fun jumpToNow() {
        viewModelScope.launch {
            preferencesRepository.setAutoTimeEnabled(true)
        }
    }

    fun setOpacity(opacity: Float) {
        viewModelScope.launch {
            preferencesRepository.setOpacity(opacity)
        }
    }

    fun setWindOverlayScale(scale: Float) {
        viewModelScope.launch {
            preferencesRepository.setWindOverlayScale(scale)
        }
    }

    fun setWindDisplayMode(mode: ForecastWindDisplayMode) {
        viewModelScope.launch {
            preferencesRepository.setWindDisplayMode(mode)
        }
    }

    fun setSkySightSatelliteOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSkySightSatelliteOverlayEnabled(enabled)
        }
    }

    fun setSkySightSatelliteImageryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSkySightSatelliteImageryEnabled(enabled)
        }
    }

    fun setSkySightSatelliteRadarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSkySightSatelliteRadarEnabled(enabled)
        }
    }

    fun setSkySightSatelliteLightningEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSkySightSatelliteLightningEnabled(enabled)
        }
    }

    fun setSkySightSatelliteAnimateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSkySightSatelliteAnimateEnabled(enabled)
        }
    }

    fun setSkySightSatelliteHistoryFrames(frameCount: Int) {
        viewModelScope.launch {
            preferencesRepository.setSkySightSatelliteHistoryFrames(frameCount)
        }
    }

    fun queryPointValue(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            when (val result = queryForecastValueAtPointUseCase(latitude, longitude)) {
                is ForecastPointQueryResult.Success -> {
                    _pointCallout.value = ForecastPointCallout(
                        latitude = result.latitude,
                        longitude = result.longitude,
                        pointValue = result.pointValue
                    )
                    _queryStatus.value = null
                }

                is ForecastPointQueryResult.Unavailable -> {
                    _queryStatus.value = result.reason
                }

                is ForecastPointQueryResult.Error -> {
                    _queryStatus.value = result.message
                }
            }
        }
    }

    fun clearPointCallout() {
        _pointCallout.value = null
    }

    fun clearQueryStatus() {
        _queryStatus.value = null
    }
}
