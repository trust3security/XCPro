package com.trust3.xcpro.map

import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.map.ballast.BallastController
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.trail.TrailSettings
import com.trust3.xcpro.map.trail.MapTrailSettingsUseCase
import com.trust3.xcpro.qnh.QnhCalibrationState
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.thermalling.ThermallingModeSettings
import com.trust3.xcpro.weglide.ui.WeGlideUploadPromptUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Stateless startup/teardown wiring for the map screen root owner.
 * Keeps lifecycle orchestration out of the main ViewModel body without
 * introducing another long-lived mutable owner.
 */
internal fun startMapScreenViewModelLifecycle(
    scope: CoroutineScope,
    unitsState: StateFlow<UnitsPreferences>,
    uiState: MutableStateFlow<MapUiState>,
    flightDataManager: FlightDataManager,
    gliderConfigFlow: Flow<GliderConfig>,
    qnhCalibrationState: Flow<QnhCalibrationState>,
    trailSettingsFlow: Flow<TrailSettings>,
    mapStateStore: MapStateStore,
    trafficCoordinator: MapScreenTrafficCoordinator,
    thermallingController: ThermallingModeRuntimeController,
    thermallingSettingsFlow: Flow<ThermallingModeSettings>,
    flightData: StateFlow<CompleteFlightData?>,
    replaySessionState: StateFlow<SessionState>,
    mapStateActions: MapStateActions,
    visibleModes: StateFlow<List<FlightMode>>,
    applyRuntimeFlightMode: (FlightMode) -> Unit,
    clearRuntimeFlightModeOverride: () -> Unit,
    applyContrastMap: (Boolean) -> Unit,
    flightDataUiAdapter: FlightDataUiAdapter,
    replayCoordinator: MapScreenReplayCoordinator,
    weGlidePromptBridge: MapScreenWeGlidePromptBridge,
    onPromptChanged: (WeGlideUploadPromptUiState?) -> Unit,
    adsbTrafficFacade: AdsbTrafficFacade,
    featureFlags: MapFeatureFlags,
    mapTasksUseCase: MapTasksUseCase,
    refreshWaypoints: () -> Unit
) {
    bindMapStateObservers(
        scope = scope,
        unitsState = unitsState,
        uiState = uiState,
        flightDataManager = flightDataManager,
        gliderConfigFlow = gliderConfigFlow,
        qnhCalibrationState = qnhCalibrationState,
        trailSettingsFlow = trailSettingsFlow,
        mapStateStore = mapStateStore
    )
    trafficCoordinator.bind()
    startMapScreenThermallingRuntime(
        scope = scope,
        thermallingController = thermallingController,
        settingsFlow = thermallingSettingsFlow,
        flightData = flightData,
        visibleModes = visibleModes,
        replaySessionState = replaySessionState,
        mapStateStore = mapStateStore,
        mapStateActions = mapStateActions,
        applyRuntimeFlightMode = applyRuntimeFlightMode,
        clearRuntimeFlightModeOverride = clearRuntimeFlightModeOverride,
        applyContrastMap = applyContrastMap
    )
    flightDataUiAdapter.start()
    replayCoordinator.start()
    weGlidePromptBridge.bind(scope, onPromptChanged)
    scope.launch { adsbTrafficFacade.bootstrapMetadataSync() }
    if (featureFlags.loadSavedTasksOnInit) {
        scope.launch { mapTasksUseCase.loadSavedTasks() }
    }
    refreshWaypoints()
}

internal fun stopMapScreenViewModelLifecycle(
    ognTrafficFacade: OgnTrafficFacade,
    adsbTrafficFacade: AdsbTrafficFacade,
    thermallingController: ThermallingModeRuntimeController,
    ballastController: BallastController,
    clearRuntimeFlightModeOverride: () -> Unit
) {
    ognTrafficFacade.stop()
    adsbTrafficFacade.stop()
    thermallingController.reset()
    clearRuntimeFlightModeOverride()
    ballastController.dispose()
}
