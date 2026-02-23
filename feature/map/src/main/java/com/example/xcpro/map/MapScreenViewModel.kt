package com.example.xcpro.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.flow.inVm
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.metadata.domain.AdsbMetadataEnrichmentUseCase
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
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnGliderTrailSegment
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
import kotlinx.coroutines.launch
@HiltViewModel
class MapScreenViewModel @Inject constructor(
    private val mapStyleUseCase: MapStyleUseCase, private val unitsUseCase: UnitsPreferencesUseCase,
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
    private val ognTrafficUseCase: OgnTrafficUseCase, private val adsbTrafficUseCase: AdsbTrafficUseCase,
    private val adsbMetadataEnrichmentUseCase: AdsbMetadataEnrichmentUseCase
) : ViewModel() {
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
    private val replaySensorGates: MapReplaySensorGateStates =
        createReplaySensorGateStates(viewModelScope, replaySessionState)
    val suppressLiveGps: StateFlow<Boolean> = replaySensorGates.suppressLiveGps
    val allowSensorStart: StateFlow<Boolean> = replaySensorGates.allowSensorStart
    val mapLocation: StateFlow<MapLocationUiModel?> = createMapLocationState(viewModelScope, flightDataUseCase)
    private val ownshipAltitudeMeters: StateFlow<Double?> =
        createOwnshipAltitudeState(viewModelScope, flightDataUseCase)
    val ognTargets: StateFlow<List<OgnTrafficTarget>> = ognTrafficUseCase.targets
    val ognSnapshot: StateFlow<OgnTrafficSnapshot> = ognTrafficUseCase.snapshot
    val ognOverlayEnabled: StateFlow<Boolean> = ognTrafficUseCase.overlayEnabled
        .eagerState(scope = viewModelScope, initial = false)
    val ognIconSizePx: StateFlow<Int> = ognTrafficUseCase.iconSizePx
        .eagerState(scope = viewModelScope, initial = OGN_ICON_SIZE_DEFAULT_PX)
    val ognThermalHotspots: StateFlow<List<OgnThermalHotspot>> = ognTrafficUseCase.thermalHotspots
    val showOgnThermalsEnabled: StateFlow<Boolean> = ognTrafficUseCase.showThermalsEnabled
        .eagerState(scope = viewModelScope, initial = false)
    val ognGliderTrailSegments: StateFlow<List<OgnGliderTrailSegment>> = ognTrafficUseCase.gliderTrailSegments
    private val rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>> = adsbTrafficUseCase.targets
    private val enrichedAdsbTargets: StateFlow<List<AdsbTrafficUiModel>> =
        adsbMetadataEnrichmentUseCase.targetsWithMetadata(rawAdsbTargets)
            .eagerState(scope = viewModelScope, initial = emptyList())
    val adsbTargets: StateFlow<List<AdsbTrafficUiModel>> =
        createMergedAdsbTargetsState(
            scope = viewModelScope,
            rawAdsbTargets = rawAdsbTargets,
            enrichedAdsbTargets = enrichedAdsbTargets
        )
    val adsbSnapshot: StateFlow<AdsbTrafficSnapshot> = adsbTrafficUseCase.snapshot
    val adsbOverlayEnabled: StateFlow<Boolean> = adsbTrafficUseCase.overlayEnabled
        .eagerState(scope = viewModelScope, initial = false)
    val adsbIconSizePx: StateFlow<Int> = adsbTrafficUseCase.iconSizePx
        .eagerState(scope = viewModelScope, initial = ADSB_ICON_SIZE_DEFAULT_PX)
    val variometerUiState: StateFlow<VariometerUiState> = variometerLayoutUseCase.state
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val _trailUpdates = MutableStateFlow<TrailUpdateResult?>(null)
    internal val trailUpdates: StateFlow<TrailUpdateResult?> = _trailUpdates.asStateFlow()
    private val _uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 1)
    val uiEffects: SharedFlow<MapUiEffect> = _uiEffects.asSharedFlow()
    private val _mapCommands = MutableSharedFlow<MapCommand>(extraBufferCapacity = 1)
    val mapCommands: SharedFlow<MapCommand> = _mapCommands.asSharedFlow()
    private val _selectedAdsbId = MutableStateFlow<Icao24?>(null)
    val selectedAdsbId: StateFlow<Icao24?> = _selectedAdsbId.asStateFlow()
    val selectedAdsbTarget: StateFlow<AdsbSelectedTargetDetails?> = adsbMetadataEnrichmentUseCase
        .selectedTargetDetails(selectedIcao24 = _selectedAdsbId, adsbTargets = rawAdsbTargets)
        .eagerState(scope = viewModelScope, initial = null)
    private val _selectedOgnId = MutableStateFlow<String?>(null)
    val selectedOgnTarget: StateFlow<OgnTrafficTarget?> = createSelectedOgnTargetState(
        scope = viewModelScope,
        selectedOgnId = _selectedOgnId,
        ognTargets = ognTargets
    )
    private val _selectedOgnThermalId = MutableStateFlow<String?>(null)
    val selectedOgnThermal: StateFlow<OgnThermalHotspot?> = createSelectedOgnThermalState(
        scope = viewModelScope,
        selectedThermalId = _selectedOgnThermalId,
        thermalHotspots = ognThermalHotspots
    )
    private val _containerReady = MutableStateFlow(false)
    private val _liveDataReady = MutableStateFlow(false)
    private val _isMapVisible = MutableStateFlow(false)
    private val _isAATEditMode = MutableStateFlow(false)
    val cardHydrationReady: StateFlow<Boolean> =
        createCardHydrationReadyState(viewModelScope, _containerReady, _liveDataReady)
    private val flightDataUiAdapter = mapReplayUseCase.createFlightDataUiAdapter(
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
    private val replayCoordinator = mapReplayUseCase.createReplayCoordinator(
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
    private val trafficCoordinator = MapScreenTrafficCoordinator(
        scope = viewModelScope,
        allowSensorStart = allowSensorStart,
        isMapVisible = _isMapVisible,
        ognOverlayEnabled = ognOverlayEnabled,
        adsbOverlayEnabled = adsbOverlayEnabled,
        mapState = mapState,
        mapLocation = mapLocation,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        adsbMaxDistanceKm = adsbTrafficUseCase.maxDistanceKm.eagerState(
            scope = viewModelScope,
            initial = com.example.xcpro.adsb.ADSB_MAX_DISTANCE_DEFAULT_KM
        ),
        adsbVerticalAboveMeters = adsbTrafficUseCase.verticalAboveMeters.eagerState(
            scope = viewModelScope,
            initial = com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
        ),
        adsbVerticalBelowMeters = adsbTrafficUseCase.verticalBelowMeters.eagerState(
            scope = viewModelScope,
            initial = com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
        ),
        rawOgnTargets = ognTargets,
        selectedOgnId = _selectedOgnId,
        showThermalsEnabled = showOgnThermalsEnabled,
        thermalHotspots = ognThermalHotspots,
        selectedThermalId = _selectedOgnThermalId,
        rawAdsbTargets = rawAdsbTargets,
        selectedAdsbId = _selectedAdsbId,
        ognTrafficUseCase = ognTrafficUseCase,
        adsbTrafficUseCase = adsbTrafficUseCase
    )
    val isAATEditMode: StateFlow<Boolean> = _isAATEditMode.asStateFlow()
    val taskType: StateFlow<TaskType> = mapTasksUseCase.taskTypeFlow
    private val unitsState = unitsUseCase.unitsFlow.inVm(
        scope = viewModelScope,
        initial = UnitsPreferences()
    )
    val unitsPreferencesFlow: StateFlow<UnitsPreferences> = unitsState
    val cardIngestionCoordinator: CardIngestionCoordinator by lazy {
        CardIngestionCoordinator(
            scope = viewModelScope,
            cardHydrationReady = cardHydrationReady,
            cardFlightDataFlow = flightDataManager.cardFlightDataFlow,
            consumeBufferedCardSample = { flightDataManager.consumeBufferedCardSample() },
            unitsPreferencesFlow = unitsPreferencesFlow,
            initializeCardPreferences = { flightViewModel ->
                flightViewModel.initializeCardPreferences(cardPreferences)
            },
            startIndependentClock = { flightViewModel ->
                flightViewModel.startIndependentClockTimer()
            }
        )
    }
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
        flightDataUiAdapter.start()
        replayCoordinator.start()
        viewModelScope.launch {
            adsbTrafficUseCase.bootstrapMetadataSync()
        }
        if (featureFlags.loadSavedTasksOnInit) {
            viewModelScope.launch {
                mapTasksUseCase.loadSavedTasks()
            }
        }
        onEvent(MapUiEvent.RefreshWaypoints)
    }
    fun emitMapCommand(command: MapCommand) = _mapCommands.tryEmit(command)
    fun onVarioDemoReplay() = replayCoordinator.onVarioDemoReplay()
    fun onRacingTaskReplay() = replayCoordinator.onRacingTaskReplay()
    fun onVarioDemoReplaySim() = replayCoordinator.onVarioDemoReplaySim()
    fun onVarioDemoReplaySimLive() = replayCoordinator.onVarioDemoReplaySimLive()
    fun onVarioDemoReplaySim3() = replayCoordinator.onVarioDemoReplaySim3()
    fun updateSafeContainerSize(size: MapStateStore.MapSize) = mapStateStore.updateSafeContainerSize(size)
    fun setMapStyle(styleName: String) {
        if (mapStateStore.updateMapStyleName(styleName)) emitMapCommand(MapCommand.SetStyle(styleName))
    }
    fun persistMapStyle(styleName: String) = viewModelScope.launch { mapStyleUseCase.saveStyle(styleName) }
    fun setFlightMode(newMode: FlightMode) {
        mapStateStore.setCurrentMode(newMode)
        mapStateStore.setCurrentFlightMode(newMode.toCardFlightModeSelection())
        flightDataManager.updateFlightModeFromEnum(newMode)
        sensorsUseCase.setFlightMode(newMode)
    }
    fun onEvent(event: MapUiEvent) = uiEventHandler.onEvent(event)
    fun setMapVisible(isVisible: Boolean) = trafficCoordinator.setMapVisible(isVisible)
    fun onToggleOgnTraffic() = trafficCoordinator.onToggleOgnTraffic()
    fun onToggleOgnThermals() = trafficCoordinator.onToggleOgnThermals()
    fun onToggleAdsbTraffic() = trafficCoordinator.onToggleAdsbTraffic()
    fun onOgnTargetSelected(id: String) = trafficCoordinator.onOgnTargetSelected(id)
    fun onOgnThermalSelected(id: String) = trafficCoordinator.onOgnThermalSelected(id)
    fun onAdsbTargetSelected(id: Icao24) = trafficCoordinator.onAdsbTargetSelected(id)
    fun dismissSelectedOgnTarget() = trafficCoordinator.dismissSelectedOgnTarget()
    fun dismissSelectedOgnThermal() = trafficCoordinator.dismissSelectedOgnThermal()
    fun dismissSelectedAdsbTarget() = trafficCoordinator.dismissSelectedAdsbTarget()
    private fun loadWaypoints() = waypointQnhCoordinator.loadWaypoints()
    fun ensureVariometerLayout(
        screenWidthPx: Float,
        screenHeightPx: Float,
        defaultSizePx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) = variometerLayoutUseCase.ensureLayout(screenWidthPx, screenHeightPx, defaultSizePx, minSizePx, maxSizePx)
    fun onVariometerOffsetCommitted(offset: OffsetPx, screenWidthPx: Float, screenHeightPx: Float) =
        variometerLayoutUseCase.onOffsetCommitted(offset, screenWidthPx, screenHeightPx)
    fun onVariometerSizeCommitted(
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) = variometerLayoutUseCase.onSizeCommitted(sizePx, screenWidthPx, screenHeightPx, minSizePx, maxSizePx)
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
    override fun onCleared() {
        ognTrafficUseCase.stop()
        adsbTrafficUseCase.stop()
        ballastController.dispose()
        super.onCleared()
    }
}
