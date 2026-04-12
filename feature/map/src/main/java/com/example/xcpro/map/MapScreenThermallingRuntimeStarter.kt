package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.replay.SessionState
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.thermalling.ThermallingModeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal fun startMapScreenThermallingRuntime(
    scope: CoroutineScope,
    thermallingController: ThermallingModeRuntimeController,
    settingsFlow: Flow<ThermallingModeSettings>,
    flightData: StateFlow<CompleteFlightData?>,
    visibleModes: StateFlow<List<FlightMode>>,
    replaySessionState: StateFlow<SessionState>,
    mapStateStore: MapStateStore,
    mapStateActions: MapStateActions,
    applyRuntimeFlightMode: (FlightMode) -> Unit,
    clearRuntimeFlightModeOverride: () -> Unit,
    applyContrastMap: (Boolean) -> Unit
) {
    bindThermallingRuntimeWiring(
        scope = scope,
        controller = thermallingController,
        settingsFlow = settingsFlow,
        flightData = flightData,
        visibleModes = visibleModes,
        replaySessionState = replaySessionState,
        mapStateReader = mapStateStore,
        mapStateStore = mapStateStore,
        mapStateActions = mapStateActions,
        applyRuntimeFlightMode = applyRuntimeFlightMode,
        clearRuntimeFlightModeOverride = clearRuntimeFlightModeOverride,
        applyContrastMap = applyContrastMap
    )
}
