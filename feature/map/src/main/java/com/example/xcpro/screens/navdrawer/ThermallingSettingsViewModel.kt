package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ThermallingSettingsViewModel @Inject constructor(
    private val useCase: ThermallingSettingsUseCase
) : ViewModel() {
    val uiState: StateFlow<ThermallingSettingsUiState> = useCase.settingsFlow
        .map { settings ->
            ThermallingSettingsUiState(
                enabled = settings.enabled,
                switchToThermalMode = settings.switchToThermalMode,
                zoomOnlyFallbackWhenThermalHidden = settings.zoomOnlyFallbackWhenThermalHidden,
                enterDelaySeconds = settings.enterDelaySeconds,
                exitDelaySeconds = settings.exitDelaySeconds,
                applyZoomOnEnter = settings.applyZoomOnEnter,
                thermalZoomLevel = settings.thermalZoomLevel,
                rememberManualThermalZoomInSession = settings.rememberManualThermalZoomInSession,
                restorePreviousModeOnExit = settings.restorePreviousModeOnExit,
                restorePreviousZoomOnExit = settings.restorePreviousZoomOnExit
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThermallingSettingsUiState()
        )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setEnabled(enabled)
        }
    }

    fun setSwitchToThermalMode(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setSwitchToThermalMode(enabled)
        }
    }

    fun setZoomOnlyFallbackWhenThermalHidden(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setZoomOnlyFallbackWhenThermalHidden(enabled)
        }
    }

    fun setEnterDelaySeconds(seconds: Int) {
        viewModelScope.launch {
            useCase.setEnterDelaySeconds(seconds)
        }
    }

    fun setExitDelaySeconds(seconds: Int) {
        viewModelScope.launch {
            useCase.setExitDelaySeconds(seconds)
        }
    }

    fun setApplyZoomOnEnter(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setApplyZoomOnEnter(enabled)
        }
    }

    fun setThermalZoomLevel(zoomLevel: Float) {
        viewModelScope.launch {
            useCase.setThermalZoomLevel(zoomLevel)
        }
    }

    fun setRememberManualThermalZoomInSession(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setRememberManualThermalZoomInSession(enabled)
        }
    }

    fun setRestorePreviousModeOnExit(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setRestorePreviousModeOnExit(enabled)
        }
    }

    fun setRestorePreviousZoomOnExit(enabled: Boolean) {
        viewModelScope.launch {
            useCase.setRestorePreviousZoomOnExit(enabled)
        }
    }
}
