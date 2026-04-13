package com.example.xcpro.map

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.qnh.QnhCalibrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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
    gliderConfigFlow: Flow<GliderConfig>,
    qnhCalibrationState: Flow<QnhCalibrationState>,
    trailSettingsFlow: Flow<TrailSettings>,
    mapStateStore: MapStateStore
) {
    unitsState
        .onEach { preferences ->
            uiState.update { it.copy(unitsPreferences = preferences) }
            flightDataManager.updateUnitsPreferences(preferences)
        }
        .launchIn(scope)

    gliderConfigFlow
        .onEach { config -> uiState.update { it.copy(hideBallastPill = config.hideBallastPill) } }
        .launchIn(scope)

    qnhCalibrationState
        .onEach { state -> uiState.update { it.copy(qnhCalibrationState = state) } }
        .launchIn(scope)

    trailSettingsFlow
        .onEach { settings -> mapStateStore.setTrailSettings(settings) }
        .launchIn(scope)
}
