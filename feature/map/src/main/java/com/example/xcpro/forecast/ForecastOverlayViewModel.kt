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
    private val setForecastEnabledUseCase: SetForecastEnabledUseCase,
    private val selectForecastParameterUseCase: SelectForecastParameterUseCase,
    private val setForecastAutoTimeEnabledUseCase: SetForecastAutoTimeEnabledUseCase,
    private val setForecastTimeUseCase: SetForecastTimeUseCase,
    private val setForecastOpacityUseCase: SetForecastOpacityUseCase,
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
            setForecastEnabledUseCase(enabled)
        }
    }

    fun selectParameter(parameterId: ForecastParameterId) {
        viewModelScope.launch {
            selectForecastParameterUseCase(parameterId)
        }
    }

    fun selectTime(timeUtcMs: Long) {
        viewModelScope.launch {
            setForecastTimeUseCase(timeUtcMs)
        }
    }

    fun setAutoTimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            setForecastAutoTimeEnabledUseCase(enabled)
        }
    }

    fun jumpToNow() {
        viewModelScope.launch {
            setForecastAutoTimeEnabledUseCase(true)
        }
    }

    fun setOpacity(opacity: Float) {
        viewModelScope.launch {
            setForecastOpacityUseCase(opacity)
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
