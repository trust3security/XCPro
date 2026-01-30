package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardPreferences
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.flow.inVm
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastControllerFactory
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.domain.toUserMessage
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.map.trail.domain.TrailProcessor
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import com.example.xcpro.qnh.CalibrateQnhUseCase
import com.example.xcpro.qnh.QnhCalibrationFailureReason
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.variometer.layout.VariometerLayoutUseCase
import com.example.xcpro.sensors.GPSData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

/**
 * Lifecycle-aware owner for map state and domain controllers (no runtime map handles).
 */
@HiltViewModel
class MapScreenViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val taskManager: TaskManagerCoordinator,
    private val taskNavigationController: TaskNavigationController,
    val cardPreferences: CardPreferences,
    private val mapStyleUseCase: MapStyleUseCase,
    private val unitsUseCase: UnitsPreferencesUseCase,
    private val waypointLoader: WaypointLoader,
    private val gliderConfigUseCase: GliderConfigUseCase,
    val varioServiceManager: VarioServiceManager,
    private val flightDataUseCase: FlightDataUseCase,
    private val windStateUseCase: WindStateUseCase,
    val igcReplayController: IgcReplayController,
    private val racingReplayLogBuilder: RacingReplayLogBuilder,
    private val orientationManagerFactory: MapOrientationManagerFactory,
    private val qnhUseCase: QnhUseCase,
    private val trailSettingsUseCase: MapTrailSettingsUseCase,
    private val calibrateQnhUseCase: CalibrateQnhUseCase,
    private val variometerLayoutUseCase: VariometerLayoutUseCase,
    private val ballastControllerFactory: BallastControllerFactory
) : ViewModel() {

    private val initialStyleName = mapStyleUseCase.initialStyle()
    private val mapStateStore: MapStateStore = MapStateStore(initialStyleName)
    val mapState: MapStateReader = mapStateStore
    val mapStateActions: MapStateActions = MapStateActionsDelegate(mapStateStore)

    private val ballastController = ballastControllerFactory.create(viewModelScope)

    val flightDataManager: FlightDataManager =
        FlightDataManager(appContext, cardPreferences, viewModelScope)
    val unifiedSensorManager = varioServiceManager.unifiedSensorManager
    val orientationManager: MapOrientationManager =
        orientationManagerFactory.create(viewModelScope)
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
    val replaySessionState: StateFlow<SessionState> = igcReplayController.session
    val showVarioDemoFab: Boolean = MapFeatureFlags.showVarioDemoFab
    val showRacingReplayFab: Boolean = MapFeatureFlags.showRacingReplayFab
    val gpsStatusFlow: StateFlow<com.example.xcpro.sensors.GpsStatus> =
        unifiedSensorManager.gpsStatusFlow
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
    val mapLocation: StateFlow<GPSData?> =
        flightDataUseCase.flightData
            .map { it?.gps }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val variometerUiState: StateFlow<VariometerUiState> = variometerLayoutUseCase.state

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val trailProcessor = TrailProcessor()
    private val _trailUpdates = MutableStateFlow<TrailUpdateResult?>(null)
    internal val trailUpdates: StateFlow<TrailUpdateResult?> = _trailUpdates.asStateFlow()
    private val _uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 1)
    val uiEffects: SharedFlow<MapUiEffect> = _uiEffects.asSharedFlow()
    private val _mapCommands = MutableSharedFlow<MapCommand>(extraBufferCapacity = 1)
    val mapCommands: SharedFlow<MapCommand> = _mapCommands.asSharedFlow()
    private val _containerReady = MutableStateFlow(false)
    private val _liveDataReady = MutableStateFlow(false)
    val cardHydrationReady: StateFlow<Boolean> =
        combine(_containerReady, _liveDataReady) { container, data -> container && data }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val observers = MapScreenObservers(
        scope = viewModelScope,
        flightDataFlow = flightDataUseCase.flightData,
        windStateFlow = windStateUseCase.windState,
        flightStateFlow = varioServiceManager.flightStateSource.flightState,
        flightDataManager = flightDataManager,
        mapStateStore = mapStateStore,
        liveDataReady = _liveDataReady,
        containerReady = _containerReady,
        uiEffects = _uiEffects,
        igcReplayController = igcReplayController,
        trailProcessor = trailProcessor,
        trailUpdates = _trailUpdates
    )

    private val replayCoordinator = MapScreenReplayCoordinator(
        taskManager = taskManager,
        taskNavigationController = taskNavigationController,
        flightDataFlow = flightDataUseCase.flightData,
        igcReplayController = igcReplayController,
        racingReplayLogBuilder = racingReplayLogBuilder,
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
        mapStateStore.setDisplaySmoothingProfile(MapFeatureFlags.defaultDisplaySmoothingProfile)
        observeTrailSettings()
        observers.start()
        replayCoordinator.start()
    }

    private val _isAATEditMode = MutableStateFlow(false)
    val isAATEditMode: StateFlow<Boolean> = _isAATEditMode.asStateFlow()
    private val unitsState = unitsUseCase.unitsFlow.inVm(
        scope = viewModelScope,
        initial = UnitsPreferences()
    )

    init {
        if (MapFeatureFlags.loadSavedTasksOnInit) {
            taskManager.loadSavedTasks()
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

    fun onVarioDemoReplayCar() {
        replayCoordinator.onVarioDemoReplayCar()
    }

    fun updateSafeContainerSize(size: MapStateStore.MapSize) {
        mapStateStore.updateSafeContainerSize(size)
    }

    fun setMapStyle(styleName: String) {
        if (mapStateStore.updateMapStyleName(styleName)) {
            emitMapCommand(MapCommand.SetStyle(styleName))
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

    private fun loadWaypoints() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWaypoints = true, waypointError = null) }
            val result = runCatching { waypointLoader.load(appContext) }
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
                    Log.e("MapScreenViewModel", "Failed to load waypoints", error)
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
        ballastController.dispose()
        super.onCleared()
    }

    private companion object {
        private const val TAG = "MapScreenViewModel"
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


