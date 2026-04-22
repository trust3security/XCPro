package com.trust3.xcpro.map

import com.trust3.xcpro.hawk.HawkVarioUiState
import com.trust3.xcpro.map.model.MapLocationUiModel
import com.trust3.xcpro.map.trail.TrailSettings
import com.trust3.xcpro.map.trail.domain.TrailUpdateResult
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.weather.wind.model.WindState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal fun createFlightDataUiAdapterForViewModel(
    mapReplayUseCase: MapReplayUseCase,
    scope: CoroutineScope,
    flightDataFlow: StateFlow<CompleteFlightData?>,
    windStateFlow: StateFlow<WindState>,
    flightStateFlow: StateFlow<FlyingState>,
    hawkVarioUiStateFlow: StateFlow<HawkVarioUiState>,
    flightDataManager: FlightDataManager,
    mapStateStore: MapStateStore,
    trailSettingsFlow: StateFlow<TrailSettings>,
    liveDataReady: MutableStateFlow<Boolean>,
    containerReady: MutableStateFlow<Boolean>,
    uiEffects: MutableSharedFlow<MapUiEffect>,
    trailUpdates: MutableStateFlow<TrailUpdateResult?>
): FlightDataUiAdapter = mapReplayUseCase.createFlightDataUiAdapter(
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
    trailUpdates = trailUpdates
)

internal fun createReplayCoordinatorForViewModel(
    mapReplayUseCase: MapReplayUseCase,
    flightDataFlow: StateFlow<CompleteFlightData?>,
    featureFlags: com.trust3.xcpro.map.config.MapFeatureFlags,
    mapStateStore: MapStateStore,
    mapStateActions: MapStateActions,
    uiEffects: MutableSharedFlow<MapUiEffect>,
    replaySessionState: StateFlow<SessionState>,
    scope: CoroutineScope
): MapScreenReplayCoordinator = mapReplayUseCase.createReplayCoordinator(
    flightDataFlow = flightDataFlow,
    featureFlags = featureFlags,
    mapStateStore = mapStateStore,
    mapStateActions = mapStateActions,
    uiEffects = uiEffects,
    replaySessionState = replaySessionState,
    scope = scope
)

internal fun createTrafficCoordinatorForViewModel(
    scope: CoroutineScope,
    allowSensorStart: StateFlow<Boolean>,
    isMapVisible: MutableStateFlow<Boolean>,
    ognOverlayEnabled: StateFlow<Boolean>,
    adsbOverlayEnabled: StateFlow<Boolean>,
    mapState: MapStateReader,
    mapLocation: StateFlow<MapLocationUiModel?>,
    isFlying: StateFlow<Boolean>,
    ownshipAltitudeMeters: StateFlow<Double?>,
    ownshipIsCircling: StateFlow<Boolean>,
    circlingFeatureEnabled: StateFlow<Boolean>,
    adsbFilterStates: AdsbFilterStateFlows,
    rawOgnTargets: StateFlow<List<OgnTrafficTarget>>,
    selectionPort: TrafficSelectionPort,
    ognTargetEnabled: StateFlow<Boolean>,
    ognTargetAircraftKey: StateFlow<String?>,
    ognSuppressedTargetIds: StateFlow<Set<String>>,
    showSciaEnabled: StateFlow<Boolean>,
    showThermalsEnabled: StateFlow<Boolean>,
    thermalHotspots: StateFlow<List<OgnThermalHotspot>>,
    rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>,
    ognTrafficFacade: OgnTrafficFacade,
    adsbTrafficFacade: AdsbTrafficFacade,
    uiEffects: MutableSharedFlow<MapUiEffect>
): MapScreenTrafficCoordinator = MapScreenTrafficCoordinator(
    scope = scope,
    streamingGate = createTrafficStreamingGatePort(
        allowSensorStart = allowSensorStart,
        isMapVisible = isMapVisible
    ),
    ognOverlayEnabled = ognOverlayEnabled,
    adsbOverlayEnabled = adsbOverlayEnabled,
    viewportPort = createTrafficViewportPort(mapState),
    ownshipPort = createTrafficOwnshipPort(
        scope = scope,
        mapLocation = mapLocation,
        isFlying = isFlying,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        ownshipIsCircling = ownshipIsCircling,
        circlingFeatureEnabled = circlingFeatureEnabled
    ),
    adsbFilterPort = createAdsbTrafficFilterPort(adsbFilterStates),
    rawOgnTargets = rawOgnTargets,
    selectionPort = selectionPort,
    ognTargetEnabled = ognTargetEnabled,
    ognTargetAircraftKey = ognTargetAircraftKey,
    ognSuppressedTargetIds = ognSuppressedTargetIds,
    showSciaEnabled = showSciaEnabled,
    showThermalsEnabled = showThermalsEnabled,
    thermalHotspots = thermalHotspots,
    rawAdsbTargets = rawAdsbTargets,
    ognTrafficFacade = ognTrafficFacade,
    adsbTrafficFacade = adsbTrafficFacade,
    userMessagePort = createTrafficUserMessagePort(uiEffects)
)
