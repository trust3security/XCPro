package com.trust3.xcpro.map

import com.trust3.xcpro.glide.GlideSolution
import com.trust3.xcpro.currentld.PilotCurrentLdSnapshot
import com.trust3.xcpro.map.trail.domain.TrailProcessor
import com.trust3.xcpro.map.trail.domain.TrailUpdateResult
import com.trust3.xcpro.map.trail.TrailSettings
import com.trust3.xcpro.navigation.WaypointNavigationSnapshot
import com.trust3.xcpro.replay.IgcReplayController
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.taskperformance.TaskPerformanceSnapshot
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.hawk.HawkVarioUiState
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
    glideSolutionFlow: Flow<GlideSolution>,
    waypointNavigationFlow: Flow<WaypointNavigationSnapshot>,
    pilotCurrentLdFlow: Flow<PilotCurrentLdSnapshot>,
    taskPerformanceFlow: Flow<TaskPerformanceSnapshot>,
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
        glideSolutionFlow = glideSolutionFlow,
        waypointNavigationFlow = waypointNavigationFlow,
        pilotCurrentLdFlow = pilotCurrentLdFlow,
        taskPerformanceFlow = taskPerformanceFlow,
        trailProcessor = TrailProcessor(),
        trailUpdates = trailUpdates
    )

    fun start() {
        observers.start()
    }
}
