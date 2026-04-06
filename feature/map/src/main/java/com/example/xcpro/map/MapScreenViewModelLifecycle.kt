package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.ballast.BallastController
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.replay.SessionState
import com.example.xcpro.weglide.ui.WeGlideUploadPromptUiState
import kotlinx.coroutines.CoroutineScope
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
    gliderConfigUseCase: GliderConfigUseCase,
    qnhUseCase: QnhUseCase,
    trailSettingsUseCase: MapTrailSettingsUseCase,
    mapStateStore: MapStateStore,
    trafficCoordinator: MapScreenTrafficCoordinator,
    thermallingModeUseCase: ThermallingModeRuntimeUseCase,
    flightDataUseCase: FlightDataUseCase,
    replaySessionState: StateFlow<SessionState>,
    mapStateActions: MapStateActions,
    applyFlightMode: (FlightMode) -> Unit,
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
        gliderConfigUseCase = gliderConfigUseCase,
        qnhUseCase = qnhUseCase,
        trailSettingsUseCase = trailSettingsUseCase,
        mapStateStore = mapStateStore
    )
    trafficCoordinator.bind()
    startMapScreenThermallingRuntime(
        scope = scope,
        thermallingController = thermallingModeUseCase,
        settingsFlow = thermallingModeUseCase.settingsFlow,
        flightData = flightDataUseCase.flightData,
        visibleModes = flightDataManager.visibleModesFlow,
        replaySessionState = replaySessionState,
        mapStateStore = mapStateStore,
        mapStateActions = mapStateActions,
        applyFlightMode = applyFlightMode,
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
    thermallingModeUseCase: ThermallingModeRuntimeUseCase,
    ballastController: BallastController
) {
    ognTrafficFacade.stop()
    adsbTrafficFacade.stop()
    thermallingModeUseCase.reset()
    ballastController.dispose()
}
