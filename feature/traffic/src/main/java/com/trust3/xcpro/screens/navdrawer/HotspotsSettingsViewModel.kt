package com.trust3.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.ogn.OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT
import com.trust3.xcpro.ogn.OGN_THERMAL_RETENTION_DEFAULT_HOURS
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HotspotsSettingsViewModel @Inject constructor(
    private val useCase: HotspotsSettingsUseCase
) : ViewModel() {
    val uiState: StateFlow<HotspotsSettingsUiState> = combine(
        useCase.thermalRetentionHoursFlow,
        useCase.hotspotsDisplayPercentFlow
    ) { retentionHours, displayPercent ->
            HotspotsSettingsUiState(
                retentionHours = retentionHours,
                displayPercent = displayPercent
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HotspotsSettingsUiState(
                retentionHours = OGN_THERMAL_RETENTION_DEFAULT_HOURS,
                displayPercent = OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT
            )
        )

    fun setRetentionHours(hours: Int) {
        viewModelScope.launch {
            useCase.setThermalRetentionHours(hours)
        }
    }

    fun setDisplayPercent(percent: Int) {
        viewModelScope.launch {
            useCase.setHotspotsDisplayPercent(percent)
        }
    }
}
