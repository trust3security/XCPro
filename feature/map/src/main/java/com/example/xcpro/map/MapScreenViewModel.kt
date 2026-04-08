package com.example.xcpro.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.flow.inVm
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.hawk.HawkVarioUseCase
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.replay.SyntheticThermalReplayMode
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.qnh.CalibrateQnhUseCase
import com.example.xcpro.replay.ReplayDisplayPose
import com.example.xcpro.replay.SessionState
import com.example.xcpro.tasks.TaskFlightSurfaceUiState
import com.example.xcpro.tasks.TaskFlightSurfaceUseCase
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weglide.ui.WeGlideUploadPromptUiState
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
    private val profileSessionDependencies: MapScreenProfileSessionDependencies,
    private val mapWaypointsUseCase: MapWaypointsUseCase, private val mapAirspaceUseCase: AirspaceUseCase,
    private val mapWaypointFilesUseCase: WaypointFilesUseCase,
    private val sensorsUseCase: MapSensorsUseCase, private val flightDataUseCase: FlightDataUseCase,
    private val mapUiControllersUseCase: MapUiControllersUseCase, private val windStateUseCase: WindStateUseCase,
    private val mapReplayUseCase: MapReplayUseCase, private val mapTasksUseCase: MapTasksUseCase,
    private val taskFlightSurfaceUseCase: TaskFlightSurfaceUseCase,
    private val mapFeatureFlagsUseCase: MapFeatureFlagsUseCase,
    private val mapCardPreferencesUseCase: MapCardPreferencesUseCase,
    private val calibrateQnhUseCase: CalibrateQnhUseCase,
    private val mapVarioPreferencesUseCase: MapVarioPreferencesUseCase, private val hawkVarioUseCase: HawkVarioUseCase,
    private val ognTrafficFacade: OgnTrafficFacade, private val adsbTrafficFacade: AdsbTrafficFacade,
    private val adsbMetadataEnrichmentUseCase: AdsbMetadataEnrichmentUseCase,
    private val thermallingModeUseCase: ThermallingModeRuntimeUseCase,
    private val weGlidePromptBridge: MapScreenWeGlidePromptBridge
) : ViewModel() {
    private val initialStyleName = profileSessionDependencies.mapStyleUseCase.initialStyle()
    private val mapStateStore: MapStateStore = MapStateStore(initialStyleName)
    val mapState: MapStateReader = mapStateStore
    val mapStateActions: MapStateActions = MapStateActionsDelegate(mapStateStore)
    val baseMapStyleName: StateFlow<String> = mapStateStore.baseMapStyleName
    val forecastSatelliteOverrideEnabled: StateFlow<Boolean> = mapStateStore.forecastSatelliteOverrideEnabled
    val thermallingContrastOverrideEnabled: StateFlow<Boolean> = mapStateStore.thermallingContrastOverrideEnabled
    private val featureFlags = mapFeatureFlagsUseCase.featureFlags
    val cardPreferences = mapCardPreferencesUseCase.cardPreferences
    private val uiControllers = mapUiControllersUseCase.create(viewModelScope)
    internal val runtimeDependencies: MapScreenRuntimeDependencies = MapScreenRuntimeDependencies(
        flightDataManager = uiControllers.flightDataManager, orientationManager = uiControllers.orientationManager,
        sensorsUseCase = sensorsUseCase, tasksUseCase = mapTasksUseCase, airspaceUseCase = mapAirspaceUseCase,
        waypointFilesUseCase = mapWaypointFilesUseCase, featureFlags = featureFlags
    )
    private val ballastController = uiControllers.ballastController
    private val flightDataManager: FlightDataManager = runtimeDependencies.flightDataManager; private val orientationManager: MapOrientationManager = runtimeDependencies.orientationManager
    val windArrowState: StateFlow<WindArrowUiState> = createWindArrowState(scope = viewModelScope, flightDataManager = flightDataManager, orientationManager = orientationManager)
    val ballastUiState: StateFlow<BallastUiState> = ballastController.state
    val windState: StateFlow<WindState> = windStateUseCase.windState
    val showWindSpeedOnVario: StateFlow<Boolean> = mapVarioPreferencesUseCase.showWindSpeedOnVario.eagerState(scope = viewModelScope, initial = true)
    val showHawkCard: StateFlow<Boolean> = mapVarioPreferencesUseCase.showHawkCard.eagerState(scope = viewModelScope, initial = false)
    val hawkVarioUiState: StateFlow<HawkVarioUiState> = hawkVarioUseCase.hawkVarioUiState.eagerState(scope = viewModelScope, initial = HawkVarioUiState())
    val replaySessionState: StateFlow<SessionState> = mapReplayUseCase.replaySession
    val taskFlightSurfaceUiState: StateFlow<TaskFlightSurfaceUiState> = taskFlightSurfaceUseCase.uiState.eagerState(scope = viewModelScope, initial = TaskFlightSurfaceUiState())
    val showVarioDemoFab: Boolean = featureFlags.showVarioDemoFab
    val showRacingReplayFab: Boolean = featureFlags.showRacingReplayFab
    val gpsStatusFlow: StateFlow<GpsStatusUiModel> = createGpsStatusUiState(viewModelScope, sensorsUseCase)
    private val replaySensorGates: MapReplaySensorGateStates = createReplaySensorGateStates(viewModelScope, replaySessionState)
    val suppressLiveGps: StateFlow<Boolean> = replaySensorGates.suppressLiveGps
    val allowSensorStart: StateFlow<Boolean> = replaySensorGates.allowSensorStart
    val mapLocation: StateFlow<MapLocationUiModel?> = createMapLocationState(viewModelScope, flightDataUseCase)
    private val isFlying: StateFlow<Boolean> = sensorsUseCase.flightStateFlow.map { state -> state.isFlying }
        .eagerState(scope = viewModelScope, initial = false)
    val ownshipAltitudeMeters: StateFlow<Double?> = createOwnshipAltitudeState(viewModelScope, flightDataUseCase)
    val overlayOwnshipAltitudeMeters: StateFlow<Double?> = createOverlayOwnshipAltitudeState(viewModelScope, flightDataUseCase)
    private val ownshipIsCircling: StateFlow<Boolean> = createOwnshipCirclingState(viewModelScope, flightDataUseCase)
    private val circlingFeatureEnabledForAdsb: StateFlow<Boolean> = createCirclingFeatureEnabledState(viewModelScope, thermallingModeUseCase)
    val ognTargets: StateFlow<List<OgnTrafficTarget>> = ognTrafficFacade.targets
    val ognSnapshot: StateFlow<OgnTrafficSnapshot> = ognTrafficFacade.snapshot
    val ognOverlayEnabled: StateFlow<Boolean> = ognTrafficFacade.overlayEnabled.eagerState(scope = viewModelScope, initial = false)
    val ognIconSizePx: StateFlow<Int> = ognTrafficFacade.iconSizePx.eagerState(scope = viewModelScope, initial = OGN_ICON_SIZE_DEFAULT_PX)
    val ognDisplayUpdateMode: StateFlow<OgnDisplayUpdateMode> = ognTrafficFacade.displayUpdateMode.eagerState(scope = viewModelScope, initial = OgnDisplayUpdateMode.DEFAULT)
    val showOgnSciaEnabled: StateFlow<Boolean> = ognTrafficFacade.showSciaEnabled.eagerState(scope = viewModelScope, initial = false)
    val ognTargetEnabled: StateFlow<Boolean> = ognTrafficFacade.targetEnabled.eagerState(scope = viewModelScope, initial = false)
    val ognTargetAircraftKey: StateFlow<String?> = ognTrafficFacade.targetAircraftKey.eagerState(scope = viewModelScope, initial = null)
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
    val variometerUiState: StateFlow<VariometerUiState> =
        profileSessionDependencies.variometerLayoutUseCase.state
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val _weGlideUploadPrompt = MutableStateFlow<WeGlideUploadPromptUiState?>(null)
    val weGlideUploadPrompt: StateFlow<WeGlideUploadPromptUiState?> = _weGlideUploadPrompt.asStateFlow()
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
    val selectedAdsbId: StateFlow<Icao24?> = trafficSelectionState.selectedAdsbId
    val selectedAdsbTarget: StateFlow<AdsbSelectedTargetDetails?> = trafficSelectionState.selectedAdsbTarget
    val selectedOgnTarget: StateFlow<OgnTrafficTarget?> = trafficSelectionState.selectedOgnTarget
    val selectedOgnTargetKey: StateFlow<String?> = selectedOgnTarget
        .map { target -> target?.canonicalKey }
        .eagerState(scope = viewModelScope, initial = null)
    val selectedOgnThermal: StateFlow<OgnThermalHotspot?> = trafficSelectionState.selectedOgnThermal
    val selectedOgnThermalDetailsVisible: StateFlow<Boolean> =
        trafficSelectionState.selectedThermalDetailsVisible
    val selectedOgnThermalContext: StateFlow<SelectedOgnThermalContext?> =
        ognTrafficFacade.selectedThermalContext(trafficSelectionState.selectedThermalId)
            .eagerState(scope = viewModelScope, initial = null)
    private val _containerReady = MutableStateFlow(false)
    private val _liveDataReady = MutableStateFlow(false)
    private val _isMapVisible = MutableStateFlow(false)
    private val adsbFilterStates: AdsbFilterStateFlows = createAdsbFilterStateFlows(viewModelScope, adsbTrafficFacade)
    val cardHydrationReady: StateFlow<Boolean> = createCardHydrationReadyState(viewModelScope, _containerReady, _liveDataReady)
    private val syntheticReplayMode = MutableStateFlow(SyntheticThermalReplayMode.NONE)
    private val flightDataUiAdapter = createFlightDataUiAdapterForViewModel(
        mapReplayUseCase = mapReplayUseCase,
        scope = viewModelScope,
        flightDataFlow = flightDataUseCase.flightData,
        windStateFlow = windStateUseCase.windState,
        flightStateFlow = sensorsUseCase.flightStateFlow,
        hawkVarioUiStateFlow = hawkVarioUiState,
        flightDataManager = flightDataManager,
        mapStateStore = mapStateStore,
        trailSettingsFlow = mapStateStore.trailSettings,
        syntheticReplayMode = syntheticReplayMode,
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
        syntheticReplayMode = syntheticReplayMode,
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
        qnhUseCase = profileSessionDependencies.qnhUseCase,
        calibrateQnhUseCase = calibrateQnhUseCase
    )
    private val profileSessionCoordinator = MapScreenProfileSessionCoordinator(
        scope = viewModelScope,
        dependencies = profileSessionDependencies,
        mapStateStore = mapStateStore,
        emitMapCommand = ::emitMapCommand
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
        selectionPort = trafficSelectionState,
        ognTargetEnabled = ognTargetEnabled,
        ognTargetAircraftKey = ognTargetAircraftKey,
        ognSuppressedTargetIds = ognTrafficFacade.suppressedTargetIds,
        showSciaEnabled = showOgnSciaEnabled,
        showThermalsEnabled = showOgnThermalsEnabled,
        thermalHotspots = ognThermalHotspots,
        rawAdsbTargets = rawAdsbTargets,
        ognTrafficFacade = ognTrafficFacade,
        adsbTrafficFacade = adsbTrafficFacade,
        uiEffects = _uiEffects
    )
    private val taskShellCoordinator = MapScreenTaskShellCoordinator(
        scope = viewModelScope,
        mapTasksUseCase = mapTasksUseCase
    )
    val isAATEditMode: StateFlow<Boolean> = taskShellCoordinator.isAATEditMode
    val taskType: StateFlow<TaskType> = taskShellCoordinator.taskType
    private val unitsState = profileSessionDependencies.unitsUseCase.unitsFlow
        .inVm(scope = viewModelScope, initial = UnitsPreferences())
    val unitsPreferencesFlow: StateFlow<UnitsPreferences> = unitsState
    val trailSettings: StateFlow<TrailSettings> = mapStateStore.trailSettings
    val ognAltitudeUnit: StateFlow<AltitudeUnit> = unitsState.map { it.altitude }.eagerState(scope = viewModelScope, initial = unitsState.value.altitude)
    val cardIngestionCoordinator: CardIngestionCoordinator by lazy { createCardIngestionCoordinator(scope = viewModelScope, cardHydrationReady = cardHydrationReady, flightDataManager = flightDataManager, unitsPreferencesFlow = unitsPreferencesFlow, cardPreferences = cardPreferences) }
    val flightDataMgmtPort: FlightDataMgmtPort by lazy { createFlightDataMgmtPort(flightDataManager) { cardIngestionCoordinator.bindCards(it) } }
    init {
        mapStateStore.setTrailSettings(profileSessionDependencies.trailSettingsUseCase.getSettings())
        mapStateStore.setDisplaySmoothingProfile(featureFlags.defaultDisplaySmoothingProfile)
        startMapScreenViewModelLifecycle(
            scope = viewModelScope,
            unitsState = unitsState,
            uiState = _uiState,
            flightDataManager = flightDataManager,
            gliderConfigUseCase = profileSessionDependencies.gliderConfigUseCase,
            qnhUseCase = profileSessionDependencies.qnhUseCase,
            trailSettingsUseCase = profileSessionDependencies.trailSettingsUseCase,
            mapStateStore = mapStateStore,
            trafficCoordinator = trafficCoordinator,
            thermallingModeUseCase = thermallingModeUseCase,
            flightDataUseCase = flightDataUseCase,
            replaySessionState = replaySessionState,
            mapStateActions = mapStateActions,
            applyFlightMode = ::setFlightMode,
            applyContrastMap = ::setThermallingContrastOverrideEnabled,
            flightDataUiAdapter = flightDataUiAdapter,
            replayCoordinator = replayCoordinator,
            weGlidePromptBridge = weGlidePromptBridge,
            onPromptChanged = { promptUiState -> _weGlideUploadPrompt.value = promptUiState },
            adsbTrafficFacade = adsbTrafficFacade,
            featureFlags = featureFlags,
            mapTasksUseCase = mapTasksUseCase,
            refreshWaypoints = ::loadWaypoints
        )
    }
    fun emitMapCommand(command: MapCommand) = _mapCommands.tryEmit(command)
    fun onVarioDemoReplay() = replayCoordinator.onVarioDemoReplay()
    fun onRacingTaskReplay() = replayCoordinator.onRacingTaskReplay()
    fun onVarioDemoReplaySim() = replayCoordinator.onVarioDemoReplaySim()
    fun onVarioDemoReplaySimLive() = replayCoordinator.onVarioDemoReplaySimLive()
    fun onVarioDemoReplaySim3() = replayCoordinator.onVarioDemoReplaySim3()
    fun onSyntheticThermalReplay() = replayCoordinator.onSyntheticThermalReplay()
    fun onSyntheticThermalReplayWindNoisy() = replayCoordinator.onSyntheticThermalReplayWindNoisy()
    fun updateSafeContainerSize(size: MapSize) = mapStateStore.updateSafeContainerSize(size)
    fun setMapStyle(styleName: String) = emitEffectiveStyleCommandIfChanged(mapStateStore.setBaseMapStyle(styleName).effectiveStyleChanged)
    fun persistMapStyle(styleName: String) = profileSessionCoordinator.persistMapStyle(styleName)
    fun setForecastSatelliteOverrideEnabled(enabled: Boolean) = emitEffectiveStyleCommandIfChanged(mapStateStore.setForecastSatelliteOverrideEnabled(enabled))
    fun setThermallingContrastOverrideEnabled(enabled: Boolean) = emitEffectiveStyleCommandIfChanged(mapStateStore.setThermallingContrastOverrideEnabled(enabled))
    fun setFlightMode(newMode: FlightMode) { mapStateStore.setCurrentMode(newMode); flightDataManager.updateFlightModeFromEnum(newMode); sensorsUseCase.setFlightMode(newMode) }
    fun onEvent(event: MapUiEvent) = uiEventHandler.onEvent(event)
    fun setMapVisible(isVisible: Boolean) {
        trafficCoordinator.setMapVisible(isVisible)
        weGlidePromptBridge.onMapVisibilityChanged(isVisible)
    }
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
    fun setActiveProfileId(profileId: String) = profileSessionCoordinator.setActiveProfileId(profileId)
    fun ensureVariometerLayout(profileId: String, screenWidthPx: Float, screenHeightPx: Float, defaultSizePx: Float, minSizePx: Float, maxSizePx: Float) =
        profileSessionCoordinator.ensureVariometerLayout(profileId, screenWidthPx, screenHeightPx, defaultSizePx, minSizePx, maxSizePx)
    fun ensureVariometerLayout(screenWidthPx: Float, screenHeightPx: Float, defaultSizePx: Float, minSizePx: Float, maxSizePx: Float) =
        profileSessionCoordinator.ensureVariometerLayout(screenWidthPx, screenHeightPx, defaultSizePx, minSizePx, maxSizePx)
    fun onVariometerOffsetCommitted(offset: OffsetPx, screenWidthPx: Float, screenHeightPx: Float) =
        profileSessionDependencies.variometerLayoutUseCase.onOffsetCommitted(offset, screenWidthPx, screenHeightPx)
    fun onVariometerSizeCommitted(sizePx: Float, screenWidthPx: Float, screenHeightPx: Float, minSizePx: Float, maxSizePx: Float) =
        profileSessionDependencies.variometerLayoutUseCase.onSizeCommitted(sizePx, screenWidthPx, screenHeightPx, minSizePx, maxSizePx)
    fun createTaskGestureHandler(callbacks: TaskGestureCallbacks): TaskGestureHandler =
        taskShellCoordinator.createTaskGestureHandler(callbacks)
    fun getInterpolatedReplayHeadingDeg(nowMs: Long): Double? =
        mapReplayUseCase.getInterpolatedReplayHeadingDeg(nowMs)
    fun getInterpolatedReplayPose(nowMs: Long): ReplayDisplayPose? =
        mapReplayUseCase.getInterpolatedReplayPose(nowMs)
    fun enterAATEditMode(waypointIndex: Int) = taskShellCoordinator.enterAATEditMode(waypointIndex)
    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) =
        taskShellCoordinator.updateAATTargetPoint(index, lat, lon)
    fun exitAATEditMode() = taskShellCoordinator.exitAATEditMode()
    fun submitBallastCommand(command: BallastCommand) = ballastController.submit(command)
    fun onAutoCalibrateQnh() = waypointQnhCoordinator.onAutoCalibrateQnh()
    fun onSetManualQnh(hpa: Double) = waypointQnhCoordinator.onSetManualQnh(hpa)
    fun onConfirmWeGlideUploadPrompt() { viewModelScope.launch { weGlidePromptBridge.confirmCurrentPrompt(_uiEffects::emit) } }
    fun onDismissWeGlideUploadPrompt() = weGlidePromptBridge.dismissCurrentPrompt()
    override fun onCleared() {
        stopMapScreenViewModelLifecycle(
            ognTrafficFacade = ognTrafficFacade,
            adsbTrafficFacade = adsbTrafficFacade,
            thermallingModeUseCase = thermallingModeUseCase,
            ballastController = ballastController
        )
        super.onCleared()
    }

    private fun emitEffectiveStyleCommandIfChanged(styleChanged: Boolean) {
        if (styleChanged) emitMapCommand(MapCommand.SetStyle(mapStateStore.mapStyleName.value))
    }
}
