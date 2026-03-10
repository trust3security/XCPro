package com.example.xcpro.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.flow.inVm
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.hawk.HawkVarioUseCase
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.qnh.CalibrateQnhUseCase
import com.example.xcpro.replay.ReplayDisplayPose
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.variometer.layout.VariometerLayoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
@HiltViewModel
class MapScreenViewModel @Inject constructor(
    private val mapStyleUseCase: MapStyleUseCase, private val unitsUseCase: UnitsPreferencesUseCase,
    private val orientationSettingsUseCase: MapOrientationSettingsUseCase,
    private val mapWaypointsUseCase: MapWaypointsUseCase, private val mapAirspaceUseCase: AirspaceUseCase,
    private val mapWaypointFilesUseCase: WaypointFilesUseCase, private val gliderConfigUseCase: GliderConfigUseCase,
    private val sensorsUseCase: MapSensorsUseCase, private val flightDataUseCase: FlightDataUseCase,
    private val mapUiControllersUseCase: MapUiControllersUseCase, private val windStateUseCase: WindStateUseCase,
    private val mapReplayUseCase: MapReplayUseCase, private val mapTasksUseCase: MapTasksUseCase,
    private val mapFeatureFlagsUseCase: MapFeatureFlagsUseCase,
    private val mapCardPreferencesUseCase: MapCardPreferencesUseCase, private val qnhUseCase: QnhUseCase,
    private val trailSettingsUseCase: MapTrailSettingsUseCase, private val calibrateQnhUseCase: CalibrateQnhUseCase,
    private val variometerLayoutUseCase: VariometerLayoutUseCase,
    private val mapVarioPreferencesUseCase: MapVarioPreferencesUseCase, private val hawkVarioUseCase: HawkVarioUseCase,
    private val ognTrafficFacade: OgnTrafficFacade, private val adsbTrafficFacade: AdsbTrafficFacade,
    private val adsbMetadataEnrichmentUseCase: AdsbMetadataEnrichmentUseCase,
    private val thermallingModeUseCase: ThermallingModeRuntimeUseCase
) : ViewModel() {
    private var activeProfileId: String = DEFAULT_PROFILE_ID
    private val initialStyleName = mapStyleUseCase.initialStyle()
    private val mapStateStore: MapStateStore = MapStateStore(initialStyleName)
    val mapState: MapStateReader = mapStateStore
    val mapStateActions: MapStateActions = MapStateActionsDelegate(mapStateStore)
    private val featureFlags = mapFeatureFlagsUseCase.featureFlags
    val cardPreferences = mapCardPreferencesUseCase.cardPreferences
    private val uiControllers = mapUiControllersUseCase.create(viewModelScope)
    val runtimeDependencies: MapScreenRuntimeDependencies = MapScreenRuntimeDependencies(
        flightDataManager = uiControllers.flightDataManager,
        orientationManager = uiControllers.orientationManager,
        sensorsUseCase = sensorsUseCase,
        tasksUseCase = mapTasksUseCase,
        airspaceUseCase = mapAirspaceUseCase,
        waypointFilesUseCase = mapWaypointFilesUseCase,
        featureFlags = featureFlags
    )
    private val ballastController = uiControllers.ballastController
    private val flightDataManager: FlightDataManager = runtimeDependencies.flightDataManager; private val orientationManager: MapOrientationManager = runtimeDependencies.orientationManager
    val windArrowState: StateFlow<WindArrowUiState> =
        createWindArrowState(
            scope = viewModelScope,
            flightDataManager = flightDataManager,
            orientationManager = orientationManager
        )
    val ballastUiState: StateFlow<BallastUiState> = ballastController.state
    val windState: StateFlow<WindState> = windStateUseCase.windState
    val showWindSpeedOnVario: StateFlow<Boolean> = mapVarioPreferencesUseCase.showWindSpeedOnVario
        .eagerState(scope = viewModelScope, initial = true)
    val showHawkCard: StateFlow<Boolean> = mapVarioPreferencesUseCase.showHawkCard
        .eagerState(scope = viewModelScope, initial = false)
    val hawkVarioUiState: StateFlow<HawkVarioUiState> = hawkVarioUseCase.hawkVarioUiState
        .eagerState(scope = viewModelScope, initial = HawkVarioUiState())
    val replaySessionState: StateFlow<SessionState> = mapReplayUseCase.replaySession
    val showVarioDemoFab: Boolean = featureFlags.showVarioDemoFab
    val showRacingReplayFab: Boolean = featureFlags.showRacingReplayFab
    val gpsStatusFlow: StateFlow<GpsStatusUiModel> = createGpsStatusUiState(viewModelScope, sensorsUseCase)
    private val replaySensorGates: MapReplaySensorGateStates = createReplaySensorGateStates(viewModelScope, replaySessionState)
    val suppressLiveGps: StateFlow<Boolean> = replaySensorGates.suppressLiveGps
    val allowSensorStart: StateFlow<Boolean> = replaySensorGates.allowSensorStart
    val mapLocation: StateFlow<MapLocationUiModel?> = createMapLocationState(viewModelScope, flightDataUseCase)
    private val isFlying: StateFlow<Boolean> = sensorsUseCase.flightStateFlow
        .map { state -> state.isFlying }
        .eagerState(scope = viewModelScope, initial = false)
    val ownshipAltitudeMeters: StateFlow<Double?> = createOwnshipAltitudeState(viewModelScope, flightDataUseCase)
    private val ownshipIsCircling: StateFlow<Boolean> = createOwnshipCirclingState(viewModelScope, flightDataUseCase)
    private val circlingFeatureEnabledForAdsb: StateFlow<Boolean> = createCirclingFeatureEnabledState(viewModelScope, thermallingModeUseCase)
    val ognTargets: StateFlow<List<OgnTrafficTarget>> = ognTrafficFacade.targets
    val ognSnapshot: StateFlow<OgnTrafficSnapshot> = ognTrafficFacade.snapshot
    val ognOverlayEnabled: StateFlow<Boolean> = ognTrafficFacade.overlayEnabled
        .eagerState(scope = viewModelScope, initial = false)
    val ognIconSizePx: StateFlow<Int> = ognTrafficFacade.iconSizePx
        .eagerState(scope = viewModelScope, initial = OGN_ICON_SIZE_DEFAULT_PX)
    val ognDisplayUpdateMode: StateFlow<OgnDisplayUpdateMode> = ognTrafficFacade.displayUpdateMode
        .eagerState(scope = viewModelScope, initial = OgnDisplayUpdateMode.DEFAULT)
    val showOgnSciaEnabled: StateFlow<Boolean> = ognTrafficFacade.showSciaEnabled
        .eagerState(scope = viewModelScope, initial = false)
    val ognTargetEnabled: StateFlow<Boolean> = ognTrafficFacade.targetEnabled
        .eagerState(scope = viewModelScope, initial = false)
    val ognTargetAircraftKey: StateFlow<String?> = ognTrafficFacade.targetAircraftKey
        .eagerState(scope = viewModelScope, initial = null)
    val ognResolvedTarget: StateFlow<OgnTrafficTarget?> =
        createSelectedOgnTargetState(scope = viewModelScope, selectedOgnId = ognTargetAircraftKey, ognTargets = ognTargets)
    val ognThermalHotspots: StateFlow<List<OgnThermalHotspot>> = ognTrafficFacade.thermalHotspots
    val showOgnThermalsEnabled: StateFlow<Boolean> = ognTrafficFacade.showThermalsEnabled
        .eagerState(scope = viewModelScope, initial = false)
    val ognGliderTrailSegments: StateFlow<List<OgnGliderTrailSegment>> = ognTrafficFacade.gliderTrailSegments.eagerState(scope = viewModelScope, initial = emptyList())
    private val rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>> = adsbTrafficFacade.targets
    private val enrichedAdsbTargets: StateFlow<List<AdsbTrafficUiModel>> =
        adsbMetadataEnrichmentUseCase.targetsWithMetadata(rawAdsbTargets)
            .eagerState(scope = viewModelScope, initial = emptyList())
    val adsbTargets: StateFlow<List<AdsbTrafficUiModel>> =
        createMergedAdsbTargetsState(
            scope = viewModelScope,
            rawAdsbTargets = rawAdsbTargets,
            enrichedAdsbTargets = enrichedAdsbTargets
        )
    val adsbSnapshot: StateFlow<AdsbTrafficSnapshot> = adsbTrafficFacade.snapshot
    val adsbOverlayEnabled: StateFlow<Boolean> = adsbTrafficFacade.overlayEnabled
        .eagerState(scope = viewModelScope, initial = false)
    val adsbIconSizePx: StateFlow<Int> = adsbTrafficFacade.iconSizePx
        .eagerState(scope = viewModelScope, initial = ADSB_ICON_SIZE_DEFAULT_PX)
    val adsbEmergencyFlashEnabled: StateFlow<Boolean> = adsbTrafficFacade.emergencyFlashEnabled
        .eagerState(scope = viewModelScope, initial = ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT)
    val adsbDefaultMediumUnknownIconEnabled: StateFlow<Boolean> = adsbTrafficFacade.defaultMediumUnknownIconEnabled
        .eagerState(scope = viewModelScope, initial = ADSB_DEFAULT_MEDIUM_UNKNOWN_ICON_ENABLED_DEFAULT)
    val variometerUiState: StateFlow<VariometerUiState> = variometerLayoutUseCase.state
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val _trailUpdates = MutableStateFlow<TrailUpdateResult?>(null)
    internal val trailUpdates: StateFlow<TrailUpdateResult?> = _trailUpdates.asStateFlow()
    private val _uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 1)
    val uiEffects: SharedFlow<MapUiEffect> = _uiEffects.asSharedFlow()
    private val _mapCommands = MutableSharedFlow<MapCommand>(extraBufferCapacity = 1)
    val mapCommands: SharedFlow<MapCommand> = _mapCommands.asSharedFlow()
    private val trafficSelectionState: MapTrafficSelectionState = createTrafficSelectionState(
        scope = viewModelScope,
        adsbMetadataEnrichmentUseCase = adsbMetadataEnrichmentUseCase,
        rawAdsbTargets = rawAdsbTargets,
        ognTargets = ognTargets,
        thermalHotspots = ognThermalHotspots
    )
    val selectedAdsbId: StateFlow<Icao24?> = trafficSelectionState.selectedAdsbId.asStateFlow()
    val selectedAdsbTarget: StateFlow<AdsbSelectedTargetDetails?> = trafficSelectionState.selectedAdsbTarget
    val selectedOgnTarget: StateFlow<OgnTrafficTarget?> = trafficSelectionState.selectedOgnTarget
    val selectedOgnThermal: StateFlow<OgnThermalHotspot?> = trafficSelectionState.selectedOgnThermal
    private val _containerReady = MutableStateFlow(false)
    private val _liveDataReady = MutableStateFlow(false)
    private val _isMapVisible = MutableStateFlow(false)
    private val _isAATEditMode = MutableStateFlow(false)
    private val adsbFilterStates: AdsbFilterStateFlows = createAdsbFilterStateFlows(viewModelScope, adsbTrafficFacade)
    val cardHydrationReady: StateFlow<Boolean> = createCardHydrationReadyState(viewModelScope, _containerReady, _liveDataReady)
    private val flightDataUiAdapter = createFlightDataUiAdapterForViewModel(
        mapReplayUseCase = mapReplayUseCase,
        scope = viewModelScope,
        flightDataFlow = flightDataUseCase.flightData,
        windStateFlow = windStateUseCase.windState,
        flightStateFlow = sensorsUseCase.flightStateFlow,
        hawkVarioUiStateFlow = hawkVarioUiState,
        flightDataManager = flightDataManager,
        mapStateStore = mapStateStore,
        liveDataReady = _liveDataReady,
        containerReady = _containerReady,
        uiEffects = _uiEffects,
        trailUpdates = _trailUpdates
    )
    private val replayCoordinator = createReplayCoordinatorForViewModel(
        mapReplayUseCase = mapReplayUseCase,
        flightDataFlow = flightDataUseCase.flightData,
        featureFlags = runtimeDependencies.featureFlags,
        mapStateStore = mapStateStore,
        mapStateActions = mapStateActions,
        uiEffects = _uiEffects,
        replaySessionState = replaySessionState,
        scope = viewModelScope
    )
    private val uiEventHandler = MapScreenUiEventHandler(
        uiState = _uiState,
        uiEffects = _uiEffects,
        onRefreshWaypoints = ::loadWaypoints
    )
    private val waypointQnhCoordinator = MapScreenWaypointQnhCoordinator(
        scope = viewModelScope,
        uiState = _uiState,
        uiEffects = _uiEffects,
        mapWaypointsUseCase = mapWaypointsUseCase,
        qnhUseCase = qnhUseCase,
        calibrateQnhUseCase = calibrateQnhUseCase
    )
    private val trafficCoordinator = createTrafficCoordinatorForViewModel(
        scope = viewModelScope,
        allowSensorStart = allowSensorStart,
        isMapVisible = _isMapVisible,
        ognOverlayEnabled = ognOverlayEnabled,
        adsbOverlayEnabled = adsbOverlayEnabled,
        mapState = mapState,
        mapLocation = mapLocation,
        isFlying = isFlying,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        ownshipIsCircling = ownshipIsCircling,
        circlingFeatureEnabled = circlingFeatureEnabledForAdsb,
        adsbFilterStates = adsbFilterStates,
        rawOgnTargets = ognTargets,
        selectedOgnId = trafficSelectionState.selectedOgnId,
        ognTargetEnabled = ognTargetEnabled,
        ognTargetAircraftKey = ognTargetAircraftKey,
        ognSuppressedTargetIds = ognTrafficFacade.suppressedTargetIds,
        showSciaEnabled = showOgnSciaEnabled,
        showThermalsEnabled = showOgnThermalsEnabled,
        thermalHotspots = ognThermalHotspots,
        selectedThermalId = trafficSelectionState.selectedOgnThermalId,
        rawAdsbTargets = rawAdsbTargets,
        selectedAdsbId = trafficSelectionState.selectedAdsbId,
        ognTrafficFacade = ognTrafficFacade,
        adsbTrafficFacade = adsbTrafficFacade,
        uiEffects = _uiEffects
    )
    val isAATEditMode: StateFlow<Boolean> = _isAATEditMode.asStateFlow()
    val taskType: StateFlow<TaskType> = mapTasksUseCase.taskTypeFlow
    private val unitsState = unitsUseCase.unitsFlow.inVm(scope = viewModelScope, initial = UnitsPreferences())
    val unitsPreferencesFlow: StateFlow<UnitsPreferences> = unitsState
    val ognAltitudeUnit: StateFlow<AltitudeUnit> = unitsState.map { it.altitude }.eagerState(scope = viewModelScope, initial = unitsState.value.altitude)
    val cardIngestionCoordinator: CardIngestionCoordinator by lazy { createCardIngestionCoordinator(scope = viewModelScope, cardHydrationReady = cardHydrationReady, flightDataManager = flightDataManager, unitsPreferencesFlow = unitsPreferencesFlow, cardPreferences = cardPreferences) }
    init {
        mapStateStore.setTrailSettings(trailSettingsUseCase.getSettings())
        mapStateStore.setDisplaySmoothingProfile(featureFlags.defaultDisplaySmoothingProfile)
        bindMapStateObservers(
            scope = viewModelScope,
            unitsState = unitsState,
            uiState = _uiState,
            flightDataManager = flightDataManager,
            gliderConfigUseCase = gliderConfigUseCase,
            qnhUseCase = qnhUseCase,
            trailSettingsUseCase = trailSettingsUseCase,
            mapStateStore = mapStateStore
        )
        trafficCoordinator.bind()
        bindThermallingRuntimeWiring(viewModelScope, thermallingModeUseCase, thermallingModeUseCase.settingsFlow, flightDataUseCase.flightData, flightDataManager.visibleModesFlow, mapStateStore, mapStateActions, ::setFlightMode)
        flightDataUiAdapter.start()
        replayCoordinator.start()
        viewModelScope.launch { adsbTrafficFacade.bootstrapMetadataSync() }
        if (featureFlags.loadSavedTasksOnInit) viewModelScope.launch { mapTasksUseCase.loadSavedTasks() }
        onEvent(MapUiEvent.RefreshWaypoints)
    }
    fun emitMapCommand(command: MapCommand) = _mapCommands.tryEmit(command)
    fun onVarioDemoReplay() = replayCoordinator.onVarioDemoReplay()
    fun onRacingTaskReplay() = replayCoordinator.onRacingTaskReplay()
    fun onVarioDemoReplaySim() = replayCoordinator.onVarioDemoReplaySim()
    fun onVarioDemoReplaySimLive() = replayCoordinator.onVarioDemoReplaySimLive()
    fun onVarioDemoReplaySim3() = replayCoordinator.onVarioDemoReplaySim3()
    fun updateSafeContainerSize(size: MapStateStore.MapSize) = mapStateStore.updateSafeContainerSize(size)
    fun setMapStyle(styleName: String) { if (mapStateStore.updateMapStyleName(styleName)) emitMapCommand(MapCommand.SetStyle(styleName)) }
    fun persistMapStyle(styleName: String) = viewModelScope.launch { mapStyleUseCase.saveStyle(styleName) }
    fun setFlightMode(newMode: FlightMode) { mapStateStore.setCurrentMode(newMode); mapStateStore.setCurrentFlightMode(newMode.toCardFlightModeSelection()); flightDataManager.updateFlightModeFromEnum(newMode); sensorsUseCase.setFlightMode(newMode) }
    fun onEvent(event: MapUiEvent) = uiEventHandler.onEvent(event)
    fun setMapVisible(isVisible: Boolean) = trafficCoordinator.setMapVisible(isVisible)
    fun onToggleOgnTraffic() = trafficCoordinator.onToggleOgnTraffic()
    fun onToggleOgnScia() = trafficCoordinator.onToggleOgnScia()
    fun onToggleOgnThermals() = trafficCoordinator.onToggleOgnThermals()
    fun onSetOgnTarget(aircraftKey: String, enabled: Boolean) =
        trafficCoordinator.onSetOgnTarget(aircraftKey = aircraftKey, enabled = enabled)
    fun onToggleAdsbTraffic() = trafficCoordinator.onToggleAdsbTraffic()
    fun onOgnTargetSelected(id: String) = trafficCoordinator.onOgnTargetSelected(id)
    fun onOgnThermalSelected(id: String) = trafficCoordinator.onOgnThermalSelected(id)
    fun onAdsbTargetSelected(id: Icao24) = trafficCoordinator.onAdsbTargetSelected(id)
    fun dismissSelectedOgnTarget() = trafficCoordinator.dismissSelectedOgnTarget()
    fun dismissSelectedOgnThermal() = trafficCoordinator.dismissSelectedOgnThermal()
    fun dismissSelectedAdsbTarget() = trafficCoordinator.dismissSelectedAdsbTarget()
    private fun loadWaypoints() = waypointQnhCoordinator.loadWaypoints()
    fun setActiveProfileId(profileId: String) {
        val resolved = profileId.trim().ifBlank { DEFAULT_PROFILE_ID }
        if (activeProfileId == resolved) return
        activeProfileId = resolved
        unitsUseCase.setActiveProfileId(resolved)
        orientationSettingsUseCase.setActiveProfileId(resolved)
        gliderConfigUseCase.setActiveProfileId(resolved)
        variometerLayoutUseCase.setActiveProfileId(resolved)
    }
    fun ensureVariometerLayout(profileId: String, screenWidthPx: Float, screenHeightPx: Float, defaultSizePx: Float, minSizePx: Float, maxSizePx: Float) {
        val resolved = profileId.trim().ifBlank { DEFAULT_PROFILE_ID }
        if (activeProfileId != resolved) setActiveProfileId(resolved)
        variometerLayoutUseCase.ensureLayout(screenWidthPx, screenHeightPx, defaultSizePx, minSizePx, maxSizePx)
    }
    fun ensureVariometerLayout(screenWidthPx: Float, screenHeightPx: Float, defaultSizePx: Float, minSizePx: Float, maxSizePx: Float) =
        variometerLayoutUseCase.ensureLayout(screenWidthPx, screenHeightPx, defaultSizePx, minSizePx, maxSizePx)
    fun onVariometerOffsetCommitted(offset: OffsetPx, screenWidthPx: Float, screenHeightPx: Float) =
        variometerLayoutUseCase.onOffsetCommitted(offset, screenWidthPx, screenHeightPx)
    fun onVariometerSizeCommitted(sizePx: Float, screenWidthPx: Float, screenHeightPx: Float, minSizePx: Float, maxSizePx: Float) =
        variometerLayoutUseCase.onSizeCommitted(sizePx, screenWidthPx, screenHeightPx, minSizePx, maxSizePx)
    fun setAATEditMode(enabled: Boolean) { _isAATEditMode.value = enabled }
    fun createTaskGestureHandler(callbacks: TaskGestureCallbacks): TaskGestureHandler =
        mapTasksUseCase.createGestureHandler(callbacks)
    fun getInterpolatedReplayHeadingDeg(nowMs: Long): Double? =
        mapReplayUseCase.getInterpolatedReplayHeadingDeg(nowMs)
    fun getInterpolatedReplayPose(nowMs: Long): ReplayDisplayPose? =
        mapReplayUseCase.getInterpolatedReplayPose(nowMs)
    fun enterAATEditMode(waypointIndex: Int) { _isAATEditMode.value = true; mapTasksUseCase.enterAATEditMode(waypointIndex) }
    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) =
        mapTasksUseCase.updateAATTargetPoint(index, lat, lon)
    fun exitAATEditMode() { _isAATEditMode.value = false; mapTasksUseCase.exitAATEditMode() }
    fun submitBallastCommand(command: BallastCommand) = ballastController.submit(command)
    fun onAutoCalibrateQnh() = waypointQnhCoordinator.onAutoCalibrateQnh()
    fun onSetManualQnh(hpa: Double) = waypointQnhCoordinator.onSetManualQnh(hpa)
    override fun onCleared() { ognTrafficFacade.stop(); adsbTrafficFacade.stop(); thermallingModeUseCase.reset(); ballastController.dispose(); super.onCleared() }
    private companion object { private const val DEFAULT_PROFILE_ID = "default-profile" }
}
