package com.example.xcpro.screens.navdrawer

import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.example.xcpro.thermalling.ThermallingModeSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ThermallingSettingsUseCase @Inject constructor(
    private val repository: ThermallingModePreferencesRepository
) {
    val settingsFlow: Flow<ThermallingModeSettings> = repository.settingsFlow
    val enabledFlow: Flow<Boolean> = repository.enabledFlow
    val switchToThermalModeFlow: Flow<Boolean> = repository.switchToThermalModeFlow
    val zoomOnlyFallbackWhenThermalHiddenFlow: Flow<Boolean> =
        repository.zoomOnlyFallbackWhenThermalHiddenFlow
    val enterDelaySecondsFlow: Flow<Int> = repository.enterDelaySecondsFlow
    val exitDelaySecondsFlow: Flow<Int> = repository.exitDelaySecondsFlow
    val applyZoomOnEnterFlow: Flow<Boolean> = repository.applyZoomOnEnterFlow
    val applyContrastMapOnEnterFlow: Flow<Boolean> = repository.applyContrastMapOnEnterFlow
    val thermalZoomLevelFlow: Flow<Float> = repository.thermalZoomLevelFlow
    val rememberManualThermalZoomInSessionFlow: Flow<Boolean> =
        repository.rememberManualThermalZoomInSessionFlow
    val restorePreviousModeOnExitFlow: Flow<Boolean> = repository.restorePreviousModeOnExitFlow
    val restorePreviousZoomOnExitFlow: Flow<Boolean> = repository.restorePreviousZoomOnExitFlow

    suspend fun setEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
    }

    suspend fun setSwitchToThermalMode(enabled: Boolean) {
        repository.setSwitchToThermalMode(enabled)
    }

    suspend fun setZoomOnlyFallbackWhenThermalHidden(enabled: Boolean) {
        repository.setZoomOnlyFallbackWhenThermalHidden(enabled)
    }

    suspend fun setEnterDelaySeconds(seconds: Int) {
        repository.setEnterDelaySeconds(seconds)
    }

    suspend fun setExitDelaySeconds(seconds: Int) {
        repository.setExitDelaySeconds(seconds)
    }

    suspend fun setApplyZoomOnEnter(enabled: Boolean) {
        repository.setApplyZoomOnEnter(enabled)
    }

    suspend fun setApplyContrastMapOnEnter(enabled: Boolean) {
        repository.setApplyContrastMapOnEnter(enabled)
    }

    suspend fun setThermalZoomLevel(zoomLevel: Float) {
        repository.setThermalZoomLevel(zoomLevel)
    }

    suspend fun setRememberManualThermalZoomInSession(enabled: Boolean) {
        repository.setRememberManualThermalZoomInSession(enabled)
    }

    suspend fun setRestorePreviousModeOnExit(enabled: Boolean) {
        repository.setRestorePreviousModeOnExit(enabled)
    }

    suspend fun setRestorePreviousZoomOnExit(enabled: Boolean) {
        repository.setRestorePreviousZoomOnExit(enabled)
    }
}
