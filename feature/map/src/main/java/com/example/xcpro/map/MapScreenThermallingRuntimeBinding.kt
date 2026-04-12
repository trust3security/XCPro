package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.sensors.CompleteFlightData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import com.example.xcpro.replay.SessionState
import com.example.xcpro.thermalling.ThermallingModeSettings

internal fun bindThermallingRuntimeWiring(
    scope: CoroutineScope,
    controller: ThermallingModeRuntimeController,
    settingsFlow: Flow<ThermallingModeSettings>,
    flightData: StateFlow<CompleteFlightData?>,
    visibleModes: StateFlow<List<FlightMode>>,
    replaySessionState: StateFlow<SessionState>,
    mapStateReader: MapStateReader,
    mapStateStore: MapStateStore,
    mapStateActions: MapStateActions,
    applyRuntimeFlightMode: (FlightMode) -> Unit,
    clearRuntimeFlightModeOverride: () -> Unit,
    applyContrastMap: (Boolean) -> Unit
) {
    val thermalModeVisible = visibleModes
        .map { modes -> FlightMode.THERMAL in modes }
        .eagerState(scope = scope, initial = false)
    val settings = settingsFlow.eagerState(scope = scope, initial = ThermallingModeSettings())
    val replayActive = replaySessionState
        .map { session -> session.hasSelection }
        .eagerState(scope = scope, initial = replaySessionState.value.hasSelection)

    ThermallingModeRuntimeWiring(
        scope = scope,
        controller = controller,
        settings = settings,
        flightData = flightData,
        thermalModeVisible = thermalModeVisible,
        replayActive = replayActive,
        currentMode = mapStateReader.currentMode,
        currentZoom = mapStateReader.currentZoom,
        currentBaseStyle = mapStateStore.baseMapStyleName,
        applyRuntimeFlightMode = applyRuntimeFlightMode,
        clearRuntimeFlightModeOverride = clearRuntimeFlightModeOverride,
        applyZoom = { zoom ->
            val target = mapStateReader.currentUserLocation.value
                ?: mapStateReader.lastCameraSnapshot.value?.target
                ?: mapStateReader.targetLatLng.value
            if (target != null) {
                mapStateActions.setTarget(target, zoom)
            }
        },
        applyContrastMap = applyContrastMap
    ).bind()
}
