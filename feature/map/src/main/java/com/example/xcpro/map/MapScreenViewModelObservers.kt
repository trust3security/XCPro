package com.example.xcpro.map

import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

internal fun bindMapStateObservers(
    scope: CoroutineScope,
    unitsState: StateFlow<UnitsPreferences>,
    uiState: MutableStateFlow<MapUiState>,
    flightDataManager: FlightDataManager,
    gliderConfigUseCase: GliderConfigUseCase,
    qnhUseCase: QnhUseCase,
    trailSettingsUseCase: MapTrailSettingsUseCase,
    mapStateStore: MapStateStore
) {
    unitsState
        .onEach { preferences ->
            uiState.update { it.copy(unitsPreferences = preferences) }
            flightDataManager.updateUnitsPreferences(preferences)
        }
        .launchIn(scope)

    gliderConfigUseCase.config
        .onEach { config -> uiState.update { it.copy(hideBallastPill = config.hideBallastPill) } }
        .launchIn(scope)

    qnhUseCase.calibrationState
        .onEach { state -> uiState.update { it.copy(qnhCalibrationState = state) } }
        .launchIn(scope)

    trailSettingsUseCase.settingsFlow
        .onEach { settings -> mapStateStore.setTrailSettings(settings) }
        .launchIn(scope)
}
