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
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.domain.toUserMessage
import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.hawk.HawkVarioUseCase
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.qnh.CalibrateQnhUseCase
import com.example.xcpro.qnh.QnhCalibrationFailureReason
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.FlowPreview
import java.util.Locale

/**
 * Lifecycle-aware owner for map state and domain controllers (no runtime map handles).
 */
@HiltViewModel
class MapScreenViewModel @Inject constructor(
    private val mapStyleUseCase: MapStyleUseCase,
    private val unitsUseCase: UnitsPreferencesUseCase,
    private val mapWaypointsUseCase: MapWaypointsUseCase,
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
    private val adsbTrafficUseCase: AdsbTrafficUseCase
) : ViewModel() {

    private val initialStyleName = mapStyleUseCase.initialStyle()
    private val mapStateStore: MapStateStore = MapStateStore(initialStyleName)
    val mapState: MapStateReader = mapStateStore
    val mapStateActions: MapStateActions = MapStateActionsDelegate(mapStateStore)
    private val featureFlags = mapFeatureFlagsUseCase.featureFlags
    val mapFeatureFlags = featureFlags
    val taskManager = mapTasksUseCase.taskManager
    private val taskNavigationController = mapTasksUseCase.taskNavigationController
    val varioServiceManager = sensorsUseCase.serviceManager
    val cardPreferences = mapCardPreferencesUseCase.cardPreferences
    val igcReplayController = mapReplayUseCase.controller
    private val racingReplayLogBuilder = mapReplayUseCase.racingReplayLogBuilder

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
        mapVarioPreferencesUseCase.showWindSpeedOnVario
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = true
            )
    val showHawkCard: StateFlow<Boolean> =
        mapVarioPreferencesUseCase.showHawkCard
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false
            )
    val hawkVarioUiState: StateFlow<HawkVarioUiState> =
        hawkVarioUseCase.hawkVarioUiState
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = HawkVarioUiState()
            )
    val replaySessionState: StateFlow<SessionState> = igcReplayController.session
    val showVarioDemoFab: Boolean = featureFlags.showVarioDemoFab
    val showRacingReplayFab: Boolean = featureFlags.showRacingReplayFab
    val gpsStatusFlow: StateFlow<GpsStatusUiModel> =
        sensorsUseCase.gpsStatusFlow
            .map { it.toUiModel() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = GpsStatusUiModel.Searching
            )
    val suppressLiveGps: StateFlow<Boolean> =
        replaySessionState
            .map { it.selection != null }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = replaySessionState.value.selection != null
            )
    val allowSensorStart: StateFlow<Boolean> =
        replaySessionState
            .map { it.selection == null || it.status == SessionStatus.IDLE }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = replaySessionState.value.selection == null ||
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
        ognTrafficUseCase.overlayEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false
            )
    val adsbTargets: StateFlow<List<AdsbTrafficUiModel>> = adsbTrafficUseCase.targets
    val adsbSnapshot: StateFlow<AdsbTrafficSnapshot> = adsbTrafficUseCase.snapshot
    val adsbOverlayEnabled: StateFlow<Boolean> =
        adsbTrafficUseCase.overlayEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false
            )

    val variometerUiState: StateFlow<VariometerUiState> = variometerLayoutUseCase.state

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val _trailUpdates = MutableStateFlow<TrailUpdateResult?>(null)
    internal val trailUpdates: StateFlow<TrailUpdateResult?> = _trailUpdates.asStateFlow()
    private val _uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 1)
    val uiEffects: SharedFlow<MapUiEffect> = _uiEffects.asSharedFlow()
    private val _mapCommands = MutableSharedFlow<MapCommand>(extraBufferCapacity = 1)
    val mapCommands: SharedFlow<MapCommand> = _mapCommands.asSharedFlow()
    private val _selectedAdsbTarget = MutableStateFlow<AdsbTrafficUiModel?>(null)
    val selectedAdsbTarget: StateFlow<AdsbTrafficUiModel?> = _selectedAdsbTarget.asStateFlow()
    private val _containerReady = MutableStateFlow(false)
    private val _liveDataReady = MutableStateFlow(false)
    private val _isMapVisible = MutableStateFlow(false)
    val cardHydrationReady: StateFlow<Boolean> =
        combine(_containerReady, _liveDataReady) { container, data -> container && data }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val flightDataUiAdapter = FlightDataUiAdapter(
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
        igcReplayController = igcReplayController,
        trailUpdates = _trailUpdates
    )

    private val replayCoordinator = MapScreenReplayCoordinator(
        taskManager = taskManager,
        taskNavigationController = taskNavigationController,
        flightDataFlow = flightDataUseCase.flightData,
        igcReplayController = igcReplayController,
        racingReplayLogBuilder = racingReplayLogBuilder,
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

    init {
        mapStateStore.setTrailSettings(trailSettingsUseCase.getSettings())
        mapStateStore.setDisplaySmoothingProfile(featureFlags.defaultDisplaySmoothingProfile)
        observeTrailSettings()
        observeOgnTraffic()
        observeAdsbTraffic()
        flightDataUiAdapter.start()
        replayCoordinator.start()
    }

    private val _isAATEditMode = MutableStateFlow(false)
    val isAATEditMode: StateFlow<Boolean> = _isAATEditMode.asStateFlow()
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
            mapTasksUseCase.loadSavedTasks()
        }
        observeUnits()
        observeGliderConfig()
        observeQnhCalibration()
        onEvent(MapUiEvent.RefreshWaypoints)
    }

    fun emitMapCommand(command: MapCommand) {
        _mapCommands.tryEmit(command)
    }

    fun onVarioDemoReplay() {
        replayCoordinator.onVarioDemoReplay()
    }

    fun onRacingTaskReplay() {
        replayCoordinator.onRacingTaskReplay()
    }

    fun onVarioDemoReplaySim() {
        replayCoordinator.onVarioDemoReplaySim()
    }

    fun onVarioDemoReplaySimLive() {
        replayCoordinator.onVarioDemoReplaySimLive()
    }

    fun onVarioDemoReplaySim3() {
        replayCoordinator.onVarioDemoReplaySim3()
    }

    fun updateSafeContainerSize(size: MapStateStore.MapSize) {
        mapStateStore.updateSafeContainerSize(size)
    }

    fun setMapStyle(styleName: String) {
        if (mapStateStore.updateMapStyleName(styleName)) {
            emitMapCommand(MapCommand.SetStyle(styleName))
        }
    }

    fun persistMapStyle(styleName: String) {
        viewModelScope.launch {
            mapStyleUseCase.saveStyle(styleName)
        }
    }

    fun setFlightMode(newMode: FlightMode) {
        mapStateStore.setCurrentMode(newMode)
        val selection = when (newMode) {
            FlightMode.CRUISE -> com.example.dfcards.FlightModeSelection.CRUISE
            FlightMode.THERMAL -> com.example.dfcards.FlightModeSelection.THERMAL
            FlightMode.FINAL_GLIDE -> com.example.dfcards.FlightModeSelection.FINAL_GLIDE
        }
        mapStateStore.setCurrentFlightMode(selection)
        flightDataManager.updateFlightModeFromEnum(newMode)
        sensorsUseCase.setFlightMode(newMode)
    }

    fun onEvent(event: MapUiEvent) {
        uiEventHandler.onEvent(event)
    }

    private fun normalizeAngleDeg(angle: Float): Float {
        var normalized = angle % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }
    private fun observeUnits() {
        unitsState
            .onEach { preferences ->
                _uiState.update { it.copy(unitsPreferences = preferences) }
                flightDataManager.updateUnitsPreferences(preferences)
            }
            .launchIn(viewModelScope)
    }

    private fun observeGliderConfig() {
        gliderConfigUseCase.config
            .onEach { config ->
                _uiState.update { it.copy(hideBallastPill = config.hideBallastPill) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeQnhCalibration() {
        qnhUseCase.calibrationState
            .onEach { state ->
                _uiState.update { it.copy(qnhCalibrationState = state) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeTrailSettings() {
        trailSettingsUseCase.settingsFlow
            .onEach { settings -> mapStateStore.setTrailSettings(settings) }
            .launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class)
    private fun observeOgnTraffic() {
        combine(
            allowSensorStart,
            _isMapVisible,
            ognOverlayEnabled
        ) { sensorAllowed, mapVisible, overlayEnabled ->
            sensorAllowed && mapVisible && overlayEnabled
        }
            .onEach { shouldStream ->
                ognTrafficUseCase.setStreamingEnabled(shouldStream)
            }
            .launchIn(viewModelScope)

        mapState.lastCameraSnapshot
            .filterNotNull()
            .debounce(1_500L)
            .onEach { cameraSnapshot ->
                ognTrafficUseCase.updateCenter(
                    latitude = cameraSnapshot.target.latitude,
                    longitude = cameraSnapshot.target.longitude
                )
            }
            .launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class)
    private fun observeAdsbTraffic() {
        combine(
            allowSensorStart,
            _isMapVisible,
            adsbOverlayEnabled
        ) { sensorAllowed, mapVisible, overlayEnabled ->
            sensorAllowed && mapVisible && overlayEnabled
        }
            .onEach { shouldStream ->
                adsbTrafficUseCase.setStreamingEnabled(shouldStream)
            }
            .launchIn(viewModelScope)

        mapLocation
            .filterNotNull()
            .debounce(1_500L)
            .onEach { location ->
                adsbTrafficUseCase.updateCenter(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }
            .launchIn(viewModelScope)
    }

    fun setMapVisible(isVisible: Boolean) {
        if (_isMapVisible.value == isVisible) return
        _isMapVisible.value = isVisible
    }

    fun onToggleOgnTraffic() {
        viewModelScope.launch {
            ognTrafficUseCase.setOverlayEnabled(!ognOverlayEnabled.value)
        }
    }

    fun onToggleAdsbTraffic() {
        viewModelScope.launch {
            val next = !adsbOverlayEnabled.value
            adsbTrafficUseCase.setOverlayEnabled(next)
            if (!next) {
                _selectedAdsbTarget.value = null
            }
        }
    }

    fun onAdsbTargetSelected(target: AdsbTrafficUiModel) {
        _selectedAdsbTarget.value = target
    }

    fun dismissSelectedAdsbTarget() {
        _selectedAdsbTarget.value = null
    }

    private fun loadWaypoints() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWaypoints = true, waypointError = null) }
            val result = runCatching { mapWaypointsUseCase.loadWaypoints() }
            result
                .onSuccess { waypoints ->
                    _uiState.update {
                        it.copy(
                            waypoints = waypoints,
                            isLoadingWaypoints = false,
                            waypointError = if (waypoints.isEmpty()) MapWaypointError.Empty else null
                        )
                    }
                }
                .onFailure { error ->
                    val failure = MapWaypointError.LoadFailed(error)
                    _uiState.update {
                        it.copy(
                            waypoints = emptyList(),
                            isLoadingWaypoints = false,
                            waypointError = failure
                        )
                    }
                    _uiEffects.tryEmit(
                        MapUiEffect.ShowToast(
                            failure.toUserMessage("Failed to load waypoints")
                        )
                    )
                }
        }
    }

    fun ensureVariometerLayout(
        screenWidthPx: Float,
        screenHeightPx: Float,
        defaultSizePx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) {
        variometerLayoutUseCase.ensureLayout(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            defaultSizePx = defaultSizePx,
            minSizePx = minSizePx,
            maxSizePx = maxSizePx
        )
    }

    fun onVariometerOffsetCommitted(
        offset: OffsetPx,
        screenWidthPx: Float,
        screenHeightPx: Float
    ) {
        variometerLayoutUseCase.onOffsetCommitted(offset, screenWidthPx, screenHeightPx)
    }

    fun onVariometerSizeCommitted(
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) {
        variometerLayoutUseCase.onSizeCommitted(
            sizePx = sizePx,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            minSizePx = minSizePx,
            maxSizePx = maxSizePx
        )
    }
    fun setAATEditMode(enabled: Boolean) {
        _isAATEditMode.value = enabled
    }

    fun exitAATEditMode() {
        _isAATEditMode.value = false
    }

    fun submitBallastCommand(command: BallastCommand) {
        ballastController.submit(command)
    }

    fun onAutoCalibrateQnh() {
        viewModelScope.launch {
            when (val result = calibrateQnhUseCase.execute()) {
                is com.example.xcpro.qnh.QnhCalibrationResult.Success -> {
                    val label = String.format(Locale.US, "%.1f", result.value.hpa)
                    _uiEffects.emit(MapUiEffect.ShowToast("QNH updated to $label hPa"))
                }
                is com.example.xcpro.qnh.QnhCalibrationResult.Failure -> {
                    _uiEffects.emit(MapUiEffect.ShowToast(result.reason.toUserMessage()))
                }
            }
        }
    }

    fun onSetManualQnh(hpa: Double) {
        viewModelScope.launch {
            qnhUseCase.setManualQnh(hpa)
            val label = String.format(Locale.US, "%.1f", hpa)
            _uiEffects.emit(MapUiEffect.ShowToast("QNH set to $label hPa"))
        }
    }

    override fun onCleared() {
        ognTrafficUseCase.stop()
        adsbTrafficUseCase.stop()
        ballastController.dispose()
        super.onCleared()
    }
}

private fun QnhCalibrationFailureReason.toUserMessage(): String = when (this) {
    QnhCalibrationFailureReason.REPLAY_MODE -> "Auto calibration disabled in replay"
    QnhCalibrationFailureReason.ALREADY_RUNNING -> "Auto calibration already running"
    QnhCalibrationFailureReason.TIMEOUT -> "Auto calibration timed out"
    QnhCalibrationFailureReason.INVALID_QNH -> "Auto calibration produced invalid QNH"
    QnhCalibrationFailureReason.MISSING_SENSORS -> "Auto calibration needs GPS and baro"
    QnhCalibrationFailureReason.UNKNOWN -> "Auto calibration failed"
}

private fun com.example.xcpro.sensors.GpsStatus.toUiModel(): GpsStatusUiModel = when (this) {
    com.example.xcpro.sensors.GpsStatus.NoPermission -> GpsStatusUiModel.NoPermission
    com.example.xcpro.sensors.GpsStatus.Disabled -> GpsStatusUiModel.Disabled
    com.example.xcpro.sensors.GpsStatus.Searching -> GpsStatusUiModel.Searching
    is com.example.xcpro.sensors.GpsStatus.LostFix -> GpsStatusUiModel.LostFix(ageMs = ageMs)
    is com.example.xcpro.sensors.GpsStatus.Ok -> GpsStatusUiModel.Ok(
        ageMs = ageMs,
        accuracyMeters = accuracyMeters
    )
}

private fun com.example.xcpro.sensors.GPSData.toUiModel(): MapLocationUiModel =
    MapLocationUiModel(
        latitude = position.latitude,
        longitude = position.longitude,
        speedMs = speed.value,
        bearingDeg = bearing,
        accuracyMeters = accuracy.toDouble(),
        bearingAccuracyDeg = bearingAccuracyDeg,
        speedAccuracyMs = speedAccuracyMs,
        timestampMs = timestamp,
        monotonicTimestampMs = monotonicTimestampMillis
    )


