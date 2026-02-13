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
import com.example.xcpro.qnh.CalibrateQnhUseCase
import com.example.xcpro.replay.ReplayDisplayPose
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.variometer.layout.VariometerLayoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Lifecycle-aware owner for map state and domain controllers (no runtime map handles).
 */
@HiltViewModel
class MapScreenViewModel @Inject constructor(
    private val mapStyleUseCase: MapStyleUseCase,
    private val unitsUseCase: UnitsPreferencesUseCase,
    private val mapWaypointsUseCase: MapWaypointsUseCase,
    val mapAirspaceUseCase: AirspaceUseCase,
    val mapWaypointFilesUseCase: WaypointFilesUseCase,
    private val gliderConfigUseCase: GliderConfigUseCase,
    private val sensorsUseCase: MapSensorsUseCase,
    private val flightDataUseCase: FlightDataUseCase,
    private val mapUiControllersUseCase: MapUiControllersUseCase,
    private val windStateUseCase: WindStateUseCase,
    private val mapReplayUseCase: MapReplayUseCase,
    private val mapTasksUseCase: MapTasksUseCase,
    private val mapFeatureFlagsUseCase: MapFeatureFlagsUseCase,
    private val mapCardPreferencesUseCase: MapCardPreferencesUseCase,
    private val qnhUseCase: QnhUseCase,
    private val trailSettingsUseCase: MapTrailSettingsUseCase,
    private val calibrateQnhUseCase: CalibrateQnhUseCase,
    private val variometerLayoutUseCase: VariometerLayoutUseCase,
    private val mapVarioPreferencesUseCase: MapVarioPreferencesUseCase,
    private val hawkVarioUseCase: HawkVarioUseCase,
    private val ognTrafficUseCase: OgnTrafficUseCase,
    private val adsbTrafficUseCase: AdsbTrafficUseCase,
    private val adsbMetadataEnrichmentUseCase: AdsbMetadataEnrichmentUseCase
) : ViewModel() {

    private val initialStyleName = mapStyleUseCase.initialStyle()
    private val mapStateStore: MapStateStore = MapStateStore(initialStyleName)
    val mapState: MapStateReader = mapStateStore
    val mapStateActions: MapStateActions = MapStateActionsDelegate(mapStateStore)
    private val featureFlags = mapFeatureFlagsUseCase.featureFlags
    val mapFeatureFlags = featureFlags
    val cardPreferences = mapCardPreferencesUseCase.cardPreferences

    private val uiControllers = mapUiControllersUseCase.create(viewModelScope)
    private val ballastController = uiControllers.ballastController
    val flightDataManager: FlightDataManager = uiControllers.flightDataManager
    val orientationManager: MapOrientationManager = uiControllers.orientationManager
    val windArrowState: StateFlow<WindArrowUiState> =
        combine(
            flightDataManager.windIndicatorStateFlow,
            orientationManager.orientationFlow
        ) { wind, orientation ->
            val baseDirection = wind.directionFromDeg ?: 0f
            val relativeDirection = normalizeAngleDeg(baseDirection - orientation.bearing.toFloat())
            WindArrowUiState(
                directionScreenDeg = relativeDirection,
                isValid = wind.isValid
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = WindArrowUiState()
        )
    val ballastUiState: StateFlow<BallastUiState> = ballastController.state
    val windState: StateFlow<WindState> = windStateUseCase.windState
    val showWindSpeedOnVario: StateFlow<Boolean> =
        mapVarioPreferencesUseCase.showWindSpeedOnVario.eagerState(initial = true)
    val showHawkCard: StateFlow<Boolean> =
        mapVarioPreferencesUseCase.showHawkCard.eagerState(initial = false)
    val hawkVarioUiState: StateFlow<HawkVarioUiState> =
        hawkVarioUseCase.hawkVarioUiState.eagerState(initial = HawkVarioUiState())
    val replaySessionState: StateFlow<SessionState> = mapReplayUseCase.replaySession
    val showVarioDemoFab: Boolean = featureFlags.showVarioDemoFab
    val showRacingReplayFab: Boolean = featureFlags.showRacingReplayFab
    val gpsStatusFlow: StateFlow<GpsStatusUiModel> =
        sensorsUseCase.gpsStatusFlow
            .map { it.toUiModel() }
            .eagerState(initial = GpsStatusUiModel.Searching)
    val suppressLiveGps: StateFlow<Boolean> =
        replaySessionState
            .map { it.selection != null }
            .eagerState(initial = replaySessionState.value.selection != null)
    val allowSensorStart: StateFlow<Boolean> =
        replaySessionState
            .map { it.selection == null || it.status == SessionStatus.IDLE }
            .eagerState(
                initial = replaySessionState.value.selection == null ||
                    replaySessionState.value.status == SessionStatus.IDLE
            )
    // AI-NOTE: Map location is derived from FlightDataUseCase (SSOT) so the ViewModel does not
    // read sensor flows directly and replay/live source gating is honored by the repository.
    val mapLocation: StateFlow<MapLocationUiModel?> =
        flightDataUseCase.flightData
            .map { it?.gps?.toUiModel() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val ognTargets: StateFlow<List<OgnTrafficTarget>> = ognTrafficUseCase.targets
    val ognSnapshot: StateFlow<OgnTrafficSnapshot> = ognTrafficUseCase.snapshot
    val ognOverlayEnabled: StateFlow<Boolean> =
        ognTrafficUseCase.overlayEnabled.eagerState(initial = false)
    val ognIconSizePx: StateFlow<Int> =
        ognTrafficUseCase.iconSizePx.eagerState(initial = OGN_ICON_SIZE_DEFAULT_PX)
    private val rawAdsbTargets: StateFlow<List<AdsbTrafficUiModel>> = adsbTrafficUseCase.targets
    private val enrichedAdsbTargets: StateFlow<List<AdsbTrafficUiModel>> =
        adsbMetadataEnrichmentUseCase.targetsWithMetadata(rawAdsbTargets).eagerState(initial = emptyList())
    val adsbTargets: StateFlow<List<AdsbTrafficUiModel>> =
        combine(rawAdsbTargets, enrichedAdsbTargets) { rawTargets, enrichedTargets ->
            if (rawTargets.isEmpty()) {
                return@combine emptyList()
            }
            if (enrichedTargets.isEmpty()) {
                return@combine rawTargets
            }

            val metadataById = HashMap<String, AdsbTrafficUiModel>(enrichedTargets.size)
            for (target in enrichedTargets) {
                metadataById[target.id.raw] = target
            }
            rawTargets.map { target ->
                val enriched = metadataById[target.id.raw] ?: return@map target
                if (
                    target.metadataTypecode == enriched.metadataTypecode &&
                    target.metadataIcaoAircraftType == enriched.metadataIcaoAircraftType
                ) {
                    target
                } else {
                    target.copy(
                        metadataTypecode = enriched.metadataTypecode,
                        metadataIcaoAircraftType = enriched.metadataIcaoAircraftType
                    )
                }
            }
        }.eagerState(initial = emptyList())
    val adsbSnapshot: StateFlow<AdsbTrafficSnapshot> = adsbTrafficUseCase.snapshot
    val adsbOverlayEnabled: StateFlow<Boolean> =
        adsbTrafficUseCase.overlayEnabled.eagerState(initial = false)
    val adsbIconSizePx: StateFlow<Int> =
        adsbTrafficUseCase.iconSizePx.eagerState(initial = ADSB_ICON_SIZE_DEFAULT_PX)

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
    val selectedAdsbTarget: StateFlow<AdsbSelectedTargetDetails?> =
        adsbMetadataEnrichmentUseCase.selectedTargetDetails(
            selectedIcao24 = _selectedAdsbId,
            adsbTargets = rawAdsbTargets
        ).eagerState(initial = null)
    private val _containerReady = MutableStateFlow(false)
    private val _liveDataReady = MutableStateFlow(false)
    private val _isMapVisible = MutableStateFlow(false)
    private val _isAATEditMode = MutableStateFlow(false)
    val cardHydrationReady: StateFlow<Boolean> =
        combine(_containerReady, _liveDataReady) { container, data -> container && data }
            .eagerState(initial = false)

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
        featureFlags = featureFlags,
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
        rawAdsbTargets = rawAdsbTargets,
        selectedAdsbId = _selectedAdsbId,
        ognTrafficUseCase = ognTrafficUseCase,
        adsbTrafficUseCase = adsbTrafficUseCase
    )

    init {
        mapStateStore.setTrailSettings(trailSettingsUseCase.getSettings())
        mapStateStore.setDisplaySmoothingProfile(featureFlags.defaultDisplaySmoothingProfile)
        viewModelScope.launch {
            adsbTrafficUseCase.bootstrapMetadataSync()
        }
        observeTrailSettings()
        trafficCoordinator.bind()
        flightDataUiAdapter.start()
        replayCoordinator.start()
    }

    val isAATEditMode: StateFlow<Boolean> = _isAATEditMode.asStateFlow()
    val taskType: StateFlow<TaskType> = mapTasksUseCase.taskTypeFlow
    val mapSensorsRuntimeUseCase: MapSensorsUseCase = sensorsUseCase
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
        if (featureFlags.loadSavedTasksOnInit) {
            viewModelScope.launch {
                mapTasksUseCase.loadSavedTasks()
            }
        }
        observeUnits()
        observeGliderConfig()
        observeQnhCalibration()
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
        mapStateStore.setCurrentFlightMode(
            when (newMode) {
            FlightMode.CRUISE -> com.example.dfcards.FlightModeSelection.CRUISE
            FlightMode.THERMAL -> com.example.dfcards.FlightModeSelection.THERMAL
            FlightMode.FINAL_GLIDE -> com.example.dfcards.FlightModeSelection.FINAL_GLIDE
            }
        )
        flightDataManager.updateFlightModeFromEnum(newMode)
        sensorsUseCase.setFlightMode(newMode)
    }

    fun onEvent(event: MapUiEvent) = uiEventHandler.onEvent(event)

    private fun <T> Flow<T>.eagerState(initial: T): StateFlow<T> =
        stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = initial)

    private fun normalizeAngleDeg(angle: Float): Float = ((angle % 360f) + 360f) % 360f

    private fun observeUnits() = unitsState
        .onEach { preferences ->
            _uiState.update { it.copy(unitsPreferences = preferences) }
            flightDataManager.updateUnitsPreferences(preferences)
        }
        .launchIn(viewModelScope)

    private fun observeGliderConfig() = gliderConfigUseCase.config
        .onEach { config -> _uiState.update { it.copy(hideBallastPill = config.hideBallastPill) } }
        .launchIn(viewModelScope)

    private fun observeQnhCalibration() = qnhUseCase.calibrationState
        .onEach { state -> _uiState.update { it.copy(qnhCalibrationState = state) } }
        .launchIn(viewModelScope)

    private fun observeTrailSettings() = trailSettingsUseCase.settingsFlow
        .onEach { settings -> mapStateStore.setTrailSettings(settings) }
        .launchIn(viewModelScope)

    fun setMapVisible(isVisible: Boolean) = trafficCoordinator.setMapVisible(isVisible)
    fun onToggleOgnTraffic() = trafficCoordinator.onToggleOgnTraffic()
    fun onToggleAdsbTraffic() = trafficCoordinator.onToggleAdsbTraffic()
    fun onAdsbTargetSelected(id: Icao24) = trafficCoordinator.onAdsbTargetSelected(id)
    fun dismissSelectedAdsbTarget() = trafficCoordinator.dismissSelectedAdsbTarget()

    private fun loadWaypoints() = waypointQnhCoordinator.loadWaypoints()

    fun ensureVariometerLayout(
        screenWidthPx: Float,
        screenHeightPx: Float,
        defaultSizePx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) = variometerLayoutUseCase.ensureLayout(
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        defaultSizePx = defaultSizePx,
        minSizePx = minSizePx,
        maxSizePx = maxSizePx
    )

    fun onVariometerOffsetCommitted(
        offset: OffsetPx,
        screenWidthPx: Float,
        screenHeightPx: Float
    ) = variometerLayoutUseCase.onOffsetCommitted(offset, screenWidthPx, screenHeightPx)

    fun onVariometerSizeCommitted(
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) = variometerLayoutUseCase.onSizeCommitted(
        sizePx = sizePx,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        minSizePx = minSizePx,
        maxSizePx = maxSizePx
    )

    fun setAATEditMode(enabled: Boolean) {
        _isAATEditMode.value = enabled
    }

    fun createTaskGestureHandler(callbacks: TaskGestureCallbacks): TaskGestureHandler =
        mapTasksUseCase.createGestureHandler(callbacks)

    fun getInterpolatedReplayHeadingDeg(nowMs: Long): Double? =
        mapReplayUseCase.getInterpolatedReplayHeadingDeg(nowMs)

    fun getInterpolatedReplayPose(nowMs: Long): ReplayDisplayPose? =
        mapReplayUseCase.getInterpolatedReplayPose(nowMs)

    fun enterAATEditMode(waypointIndex: Int) {
        _isAATEditMode.value = true
        mapTasksUseCase.enterAATEditMode(waypointIndex)
    }

    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) =
        mapTasksUseCase.updateAATTargetPoint(index, lat, lon)

    fun exitAATEditMode() {
        _isAATEditMode.value = false
        mapTasksUseCase.exitAATEditMode()
    }

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

