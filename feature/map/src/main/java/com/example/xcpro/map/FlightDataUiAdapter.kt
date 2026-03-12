package com.example.xcpro.map

import com.example.xcpro.glide.FinalGlideUseCase
import com.example.xcpro.glide.GlideTargetSnapshot
import com.example.xcpro.map.trail.domain.TrailProcessor
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.hawk.HawkVarioUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * UI adapter for flight data: bridges SSOT flows into UI-facing state.
 * Keeps conversion and trail processing outside MapScreenViewModel.
 */
internal class FlightDataUiAdapter(
    scope: CoroutineScope,
    flightDataFlow: StateFlow<CompleteFlightData?>,
    windStateFlow: StateFlow<WindState>,
    flightStateFlow: StateFlow<FlyingState>,
    hawkVarioUiStateFlow: StateFlow<HawkVarioUiState>,
    flightDataManager: FlightDataManager,
    mapStateStore: MapStateReader,
    trailSettingsFlow: StateFlow<TrailSettings>,
    liveDataReady: MutableStateFlow<Boolean>,
    containerReady: MutableStateFlow<Boolean>,
    uiEffects: MutableSharedFlow<MapUiEffect>,
    igcReplayController: IgcReplayController,
    glideTargetFlow: Flow<GlideTargetSnapshot>,
    finalGlideUseCase: FinalGlideUseCase,
    trailUpdates: MutableStateFlow<TrailUpdateResult?>
) {
    private val observers = MapScreenObservers(
        scope = scope,
        flightDataFlow = flightDataFlow,
        windStateFlow = windStateFlow,
        flightStateFlow = flightStateFlow,
        hawkVarioUiStateFlow = hawkVarioUiStateFlow,
        flightDataManager = flightDataManager,
        mapStateStore = mapStateStore,
        trailSettingsFlow = trailSettingsFlow,
        liveDataReady = liveDataReady,
        containerReady = containerReady,
        uiEffects = uiEffects,
        igcReplayController = igcReplayController,
        glideTargetFlow = glideTargetFlow,
        finalGlideUseCase = finalGlideUseCase,
        trailProcessor = TrailProcessor(),
        trailUpdates = trailUpdates
    )

    fun start() {
        observers.start()
    }
}
