package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.replay.SessionState
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.weather.wind.model.WindState
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
    liveDataReady = liveDataReady,
    containerReady = containerReady,
    uiEffects = uiEffects,
    trailUpdates = trailUpdates
)

internal fun createReplayCoordinatorForViewModel(
    mapReplayUseCase: MapReplayUseCase,
    flightDataFlow: StateFlow<CompleteFlightData?>,
    featureFlags: com.example.xcpro.map.config.MapFeatureFlags,
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
    selectedOgnId: MutableStateFlow<String?>,
    ognTargetEnabled: StateFlow<Boolean>,
    ognTargetAircraftKey: StateFlow<String?>,
    ognSuppressedTargetIds: StateFlow<Set<String>>,
    showSciaEnabled: StateFlow<Boolean>,
    showThermalsEnabled: StateFlow<Boolean>,
    thermalHotspots: StateFlow<List<OgnThermalHotspot>>,
    selectedThermalId: MutableStateFlow<String?>,
    rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>>,
    selectedAdsbId: MutableStateFlow<Icao24?>,
    ognTrafficUseCase: OgnTrafficUseCase,
    adsbTrafficUseCase: AdsbTrafficUseCase,
    uiEffects: MutableSharedFlow<MapUiEffect>
): MapScreenTrafficCoordinator = MapScreenTrafficCoordinator(
    scope = scope,
    allowSensorStart = allowSensorStart,
    isMapVisible = isMapVisible,
    ognOverlayEnabled = ognOverlayEnabled,
    adsbOverlayEnabled = adsbOverlayEnabled,
    mapState = mapState,
    mapLocation = mapLocation,
    isFlying = isFlying,
    ownshipAltitudeMeters = ownshipAltitudeMeters,
    ownshipIsCircling = ownshipIsCircling,
    circlingFeatureEnabled = circlingFeatureEnabled,
    adsbMaxDistanceKm = adsbFilterStates.maxDistanceKm,
    adsbVerticalAboveMeters = adsbFilterStates.verticalAboveMeters,
    adsbVerticalBelowMeters = adsbFilterStates.verticalBelowMeters,
    rawOgnTargets = rawOgnTargets,
    selectedOgnId = selectedOgnId,
    ognTargetEnabled = ognTargetEnabled,
    ognTargetAircraftKey = ognTargetAircraftKey,
    ognSuppressedTargetIds = ognSuppressedTargetIds,
    showSciaEnabled = showSciaEnabled,
    showThermalsEnabled = showThermalsEnabled,
    thermalHotspots = thermalHotspots,
    selectedThermalId = selectedThermalId,
    rawAdsbTargets = rawAdsbTargets,
    selectedAdsbId = selectedAdsbId,
    ognTrafficUseCase = ognTrafficUseCase,
    adsbTrafficUseCase = adsbTrafficUseCase,
    emitUiEffect = { effect -> uiEffects.emit(effect) }
)
