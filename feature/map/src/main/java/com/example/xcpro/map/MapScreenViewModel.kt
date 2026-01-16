package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardPreferences
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.flow.inVm
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayMode
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastController
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.domain.toUserMessage
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.qnh.CalibrateQnhUseCase
import com.example.xcpro.qnh.QnhCalibrationFailureReason
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEventType
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.sensors.GPSData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
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
    val cardPreferences: CardPreferences,
    private val mapStyleRepository: MapStyleRepository,
    private val unitsRepository: UnitsRepository,
    private val waypointLoader: WaypointLoader,
    private val gliderRepository: GliderRepository,
    val varioServiceManager: VarioServiceManager,
    private val flightDataRepository: FlightDataRepository,
    private val windRepository: WindSensorFusionRepository,
    val igcReplayController: IgcReplayController,
    private val racingReplayLogBuilder: RacingReplayLogBuilder,
    private val orientationManagerFactory: MapOrientationManagerFactory,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val qnhRepository: QnhRepository,
    private val trailSettingsUseCase: MapTrailSettingsUseCase,
    private val calibrateQnhUseCase: CalibrateQnhUseCase
) : ViewModel(), MapStateActions {

    private val initialStyleName = mapStyleRepository.initialStyle()
    private val mapStateStore: MapStateStore = MapStateStore(initialStyleName)
    val mapState: MapStateReader = mapStateStore

    private val ballastController = BallastController(
        repository = gliderRepository,
        scope = viewModelScope,
        dispatcher = defaultDispatcher
    )

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
    val sharedFlightDataRepository: FlightDataRepository = flightDataRepository
    val windState: StateFlow<WindState> = windRepository.windState
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
    // AI-NOTE: Map location is derived from FlightDataRepository (SSOT) so the ViewModel does not
    // read sensor flows directly and replay/live source gating is honored by the repository.
    val mapLocation: StateFlow<GPSData?> =
        flightDataRepository.flightData
            .map { it?.gps }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val sharedPrefs = appContext.getSharedPreferences("MapPrefs", Context.MODE_PRIVATE)
    private val variometerLayoutController = VariometerLayoutController(sharedPrefs)
    val variometerUiState: StateFlow<VariometerUiState> = variometerLayoutController.state

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
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
        flightDataRepository = flightDataRepository,
        windRepository = windRepository,
        flightStateSource = varioServiceManager.flightStateSource,
        flightDataManager = flightDataManager,
        mapStateStore = mapStateStore,
        liveDataReady = _liveDataReady,
        containerReady = _containerReady,
        uiEffects = _uiEffects,
        igcReplayController = igcReplayController
    )

    private val taskNavigationController = TaskNavigationController(taskManager)
    private val racingEventDebouncer = RacingNavigationEventDebouncer()
    private val racingReplayLogger = RacingReplayEventLogger()
    private var racingReplayActive = false
    private var racingReplayAdvanceSnapshot: RacingAdvanceState.Snapshot? = null
    private var racingReplaySpeedSnapshot: Double? = null
    private var lastRacingFix: RacingNavigationFix? = null
    private val racingFixFlow = flightDataRepository.flightData
        .mapNotNull { data -> data?.gps?.let(RacingNavigationFixAdapter::toFix) }
        .onEach { fix -> lastRacingFix = fix }

    private var demoReplaySnapshot: ReplayUiSnapshot? = null

    init {
        mapStateStore.setTrailSettings(trailSettingsUseCase.getSettings())
        setDisplaySmoothingProfile(MapFeatureFlags.defaultDisplaySmoothingProfile)
        observeTrailSettings()
        observers.start()
        observeReplayEvents()
        observeReplayDisplayPoseMode()
        observeRacingNavigationEvents()
        taskNavigationController.bind(racingFixFlow, viewModelScope)
    }

    private val _isAATEditMode = MutableStateFlow(false)
    val isAATEditMode: StateFlow<Boolean> = _isAATEditMode.asStateFlow()
    private val unitsState = unitsRepository.unitsFlow.inVm(
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
        viewModelScope.launch {
            try {
                captureDemoReplaySnapshot()
                igcReplayController.setAutoStopAfterFinish(true)
                Log.i(TAG, "VARIO_DEMO start asset=$VARIO_DEMO_ASSET_PATH")
                igcReplayController.stopAndWait(emitCancelledEvent = false)
                igcReplayController.setReplayMode(ReplayMode.REFERENCE, resetAfterSession = true)
                igcReplayController.loadAsset(VARIO_DEMO_ASSET_PATH, "Vario demo")
                setHasInitiallyCentered(false)
                setShowReturnButton(false)
                setTrackingLocation(true)
                igcReplayController.play()
                _uiEffects.emit(MapUiEffect.ShowToast("Vario demo replay started"))
            } catch (t: Throwable) {
                restoreDemoReplaySnapshot()
                Log.e(TAG, "Failed to start vario demo replay", t)
                _uiEffects.emit(MapUiEffect.ShowToast("Vario demo replay failed"))
            }
        }
    }

    fun onRacingTaskReplay() {
        viewModelScope.launch {
            racingReplayActive = true
            racingReplayLogger.reset()
            try {
                val task = currentRacingTaskOrNull()
                if (task == null) {
                    racingReplayActive = false
                    _uiEffects.emit(
                        MapUiEffect.ShowToast("Racing replay needs an active racing task with at least 2 waypoints")
                    )
                    return@launch
                }
                captureRacingReplayAdvanceSnapshot()
                captureRacingReplaySpeedSnapshot()
                taskNavigationController.resetNavigationState()
                taskManager.setActiveLeg(0)
                taskNavigationController.setAdvanceMode(RacingAdvanceState.Mode.AUTO)
                taskNavigationController.setAdvanceArmed(true)
                igcReplayController.setSpeed(RACING_REPLAY_SPEED_MULTIPLIER)
                igcReplayController.setAutoStopAfterFinish(true)
                igcReplayController.stopAndWait(emitCancelledEvent = false)
                igcReplayController.setReplayMode(ReplayMode.REFERENCE, resetAfterSession = true)
                val log = racingReplayLogBuilder.build(
                    task = task,
                    logPoints = true
                )
                igcReplayController.loadLog(log, "Racing task replay")
                setHasInitiallyCentered(false)
                setShowReturnButton(false)
                setTrackingLocation(true)
                igcReplayController.play()
                _uiEffects.emit(MapUiEffect.ShowToast("Racing task replay started"))
            } catch (t: Throwable) {
                restoreRacingReplayAdvanceSnapshot()
                restoreRacingReplaySpeedSnapshot()
                racingReplayActive = false
                Log.e(TAG, "Failed to start racing task replay", t)
                _uiEffects.emit(MapUiEffect.ShowToast("Racing task replay failed"))
            }
        }
    }

    fun onVarioDemoReplaySim() {
        viewModelScope.launch {
            try {
                captureDemoReplaySnapshot()
                igcReplayController.setAutoStopAfterFinish(true)
                Log.i(TAG, "VARIO_DEMO_SIM start asset=$VARIO_DEMO_ASSET_PATH")
                igcReplayController.stopAndWait(emitCancelledEvent = false)
                igcReplayController.setReplayMode(ReplayMode.REALTIME_SIM, resetAfterSession = true)
                igcReplayController.loadAsset(VARIO_DEMO_ASSET_PATH, "Vario demo (sim)")
                setHasInitiallyCentered(false)
                setShowReturnButton(false)
                setTrackingLocation(true)
                igcReplayController.play()
                _uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim) replay started"))
            } catch (t: Throwable) {
                restoreDemoReplaySnapshot()
                Log.e(TAG, "Failed to start vario demo replay (sim)", t)
                _uiEffects.emit(MapUiEffect.ShowToast("Vario demo (sim) replay failed"))
            }
        }
    }

    fun updateSafeContainerSize(size: MapStateStore.MapSize) {
        mapStateStore.updateSafeContainerSize(size)
    }

    override fun setShowDistanceCircles(show: Boolean) {
        mapStateStore.setShowDistanceCircles(show)
    }

    override fun toggleDistanceCircles() {
        val next = !mapStateStore.showDistanceCircles.value
        mapStateStore.setShowDistanceCircles(next)
    }

    override fun updateCurrentZoom(zoom: Float) {
        mapStateStore.updateCurrentZoom(zoom)
    }

    override fun setTarget(location: MapStateStore.MapPoint?, zoom: Float?) {
        mapStateStore.setTarget(location, zoom)
    }

    override fun setCurrentUserLocation(location: MapStateStore.MapPoint?) {
        mapStateStore.setCurrentUserLocation(location)
    }

    override fun setHasInitiallyCentered(centered: Boolean) {
        mapStateStore.setHasInitiallyCentered(centered)
    }

    override fun setTrackingLocation(enabled: Boolean) {
        mapStateStore.setTrackingLocation(enabled)
    }

    override fun setShowRecenterButton(show: Boolean) {
        mapStateStore.setShowRecenterButton(show)
    }

    override fun setShowReturnButton(show: Boolean) {
        mapStateStore.setShowReturnButton(show)
    }

    override fun updateLastUserPanTime(timestampMillis: Long) {
        mapStateStore.updateLastUserPanTime(timestampMillis)
    }

    override fun saveLocation(location: MapStateStore.MapPoint?, zoom: Double?, bearing: Double?) {
        mapStateStore.saveLocation(location, zoom, bearing)
    }

    override fun setDisplayPoseMode(mode: DisplayPoseMode) {
        mapStateStore.setDisplayPoseMode(mode)
    }

    override fun setDisplaySmoothingProfile(profile: DisplaySmoothingProfile) {
        mapStateStore.setDisplaySmoothingProfile(profile)
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
        when (event) {
            MapUiEvent.RefreshWaypoints -> loadWaypoints()
            MapUiEvent.ToggleUiEditMode -> setUiEditMode(!_uiState.value.isUiEditMode)
            is MapUiEvent.SetUiEditMode -> setUiEditMode(event.enabled)
            MapUiEvent.ToggleDrawer -> toggleDrawer()
            is MapUiEvent.SetDrawerOpen -> setDrawerOpen(event.isOpen)
        }
    }

    private fun normalizeAngleDeg(angle: Float): Float {
        var normalized = angle % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }
    private fun setUiEditMode(enabled: Boolean) {
        if (_uiState.value.isUiEditMode == enabled) {
            return
        }
        _uiState.update { it.copy(isUiEditMode = enabled) }
    }

    private fun toggleDrawer() {
        val shouldOpen = !_uiState.value.isDrawerOpen
        _uiState.update { it.copy(isDrawerOpen = shouldOpen) }
        _uiEffects.tryEmit(if (shouldOpen) MapUiEffect.OpenDrawer else MapUiEffect.CloseDrawer)
    }

    private fun setDrawerOpen(isOpen: Boolean) {
        if (_uiState.value.isDrawerOpen == isOpen) {
            return
        }
        _uiState.update { it.copy(isDrawerOpen = isOpen) }
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
        gliderRepository.config
            .onEach { config ->
                _uiState.update { it.copy(hideBallastPill = config.hideBallastPill) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeQnhCalibration() {
        qnhRepository.calibrationState
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

    private fun observeReplayEvents() {
        igcReplayController.events
            .onEach { event ->
                if (demoReplaySnapshot != null) {
                    restoreDemoReplaySnapshot()
                    when (event) {
                        is com.example.xcpro.replay.ReplayEvent.Failed -> {
                            igcReplayController.setAutoStopAfterFinish(false)
                            viewModelScope.launch {
                                igcReplayController.stopAndWait(emitCancelledEvent = false)
                            }
                        }
                        com.example.xcpro.replay.ReplayEvent.Cancelled -> {
                            igcReplayController.setAutoStopAfterFinish(false)
                        }
                        is com.example.xcpro.replay.ReplayEvent.Completed -> Unit
                    }
                }
                if (racingReplayActive) {
                    val session = replaySessionState.value
                    racingReplayLogger.flush(session)
                    racingReplayLogger.reset()
                    restoreRacingReplayAdvanceSnapshot()
                    restoreRacingReplaySpeedSnapshot()
                    racingReplayActive = false
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeReplayDisplayPoseMode() {
        replaySessionState
            .onEach { session ->
                val useRawReplay = MapFeatureFlags.useRawReplayPose &&
                    session.selection != null &&
                    session.status != SessionStatus.IDLE
                val mode = if (useRawReplay) DisplayPoseMode.RAW_REPLAY else DisplayPoseMode.SMOOTHED
                setDisplayPoseMode(mode)
            }
            .launchIn(viewModelScope)
    }

    private fun observeRacingNavigationEvents() {
        taskNavigationController.racingEvents
            .onEach { event ->
                if (racingReplayActive) {
                    racingReplayLogger.record(event)
                }
                if (BuildConfig.DEBUG) {
                    val fix = lastRacingFix
                    val fixLabel = if (fix != null) {
                        "fix=${fix.lat},${fix.lon} fixT=${fix.timestampMillis}"
                    } else {
                        "fix=unknown"
                    }
                    Log.i(
                        TAG,
                        "RACING_EVENT type=${event.type} from=${event.fromLegIndex} " +
                            "to=${event.toLegIndex} t=${event.timestampMillis} $fixLabel"
                    )
                }
                if (!racingEventDebouncer.shouldEmit(event)) return@onEach
                _uiEffects.emit(MapUiEffect.ShowToast(buildRacingEventMessage(event)))
            }
            .launchIn(viewModelScope)
    }

    private fun captureRacingReplayAdvanceSnapshot() {
        if (racingReplayAdvanceSnapshot != null) return
        racingReplayAdvanceSnapshot = taskNavigationController.snapshot()
    }

    private fun restoreRacingReplayAdvanceSnapshot() {
        val snapshot = racingReplayAdvanceSnapshot ?: return
        taskNavigationController.setAdvanceMode(snapshot.mode)
        taskNavigationController.setAdvanceArmed(snapshot.isArmed)
        racingReplayAdvanceSnapshot = null
    }

    private fun captureRacingReplaySpeedSnapshot() {
        if (racingReplaySpeedSnapshot != null) return
        racingReplaySpeedSnapshot = replaySessionState.value.speedMultiplier
    }

    private fun restoreRacingReplaySpeedSnapshot() {
        val snapshot = racingReplaySpeedSnapshot ?: return
        igcReplayController.setSpeed(snapshot)
        racingReplaySpeedSnapshot = null
    }

    private fun currentRacingTaskOrNull(): SimpleRacingTask? {
        if (taskManager.taskType != TaskType.RACING) {
            return null
        }
        val task = taskManager.getRacingTaskManager().currentRacingTask
        if (task.waypoints.size < 2) {
            return null
        }
        return task
    }

    private fun buildRacingEventMessage(event: RacingNavigationEvent): String {
        val task = taskManager.getRacingTaskManager().currentRacingTask
        val reachedIndex = event.fromLegIndex
        val waypointName = task.waypoints.getOrNull(reachedIndex)?.title
        return when (event.type) {
            RacingNavigationEventType.START ->
                if (waypointName != null) "Start crossed: $waypointName" else "Start crossed"
            RacingNavigationEventType.TURNPOINT ->
                if (waypointName != null) "Turnpoint reached: $waypointName" else "Turnpoint reached"
            RacingNavigationEventType.FINISH ->
                if (waypointName != null) "Finish reached: $waypointName" else "Finish reached"
        }
    }

    private fun captureDemoReplaySnapshot() {
        if (demoReplaySnapshot != null) return
        demoReplaySnapshot = ReplayUiSnapshot(
            isTrackingLocation = mapStateStore.isTrackingLocation.value,
            showReturnButton = mapStateStore.showReturnButton.value,
            showRecenterButton = mapStateStore.showRecenterButton.value,
            hasInitiallyCentered = mapStateStore.hasInitiallyCentered.value,
            savedLocation = mapStateStore.savedLocation.value,
            savedZoom = mapStateStore.savedZoom.value,
            savedBearing = mapStateStore.savedBearing.value
        )
    }

    private fun restoreDemoReplaySnapshot() {
        val snapshot = demoReplaySnapshot ?: return
        demoReplaySnapshot = null
        setTrackingLocation(snapshot.isTrackingLocation)
        setShowReturnButton(snapshot.showReturnButton)
        setShowRecenterButton(snapshot.showRecenterButton)
        setHasInitiallyCentered(snapshot.hasInitiallyCentered)
        saveLocation(snapshot.savedLocation, snapshot.savedZoom, snapshot.savedBearing)
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
        variometerLayoutController.ensureLayout(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            defaultSizePx = defaultSizePx,
            minSizePx = minSizePx,
            maxSizePx = maxSizePx
        )
    }

    fun onVariometerOffsetCommitted(
        offset: Offset,
        screenWidthPx: Float,
        screenHeightPx: Float
    ) {
        variometerLayoutController.onOffsetCommitted(offset, screenWidthPx, screenHeightPx)
    }

    fun onVariometerSizeCommitted(
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) {
        variometerLayoutController.onSizeCommitted(
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
            qnhRepository.setManualQnh(hpa)
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
        private const val VARIO_DEMO_ASSET_PATH = "replay/vario-demo-0-10-0-60s.igc"
        private const val RACING_REPLAY_SPEED_MULTIPLIER = 1.0
    }
}

private data class ReplayUiSnapshot(
    val isTrackingLocation: Boolean,
    val showReturnButton: Boolean,
    val showRecenterButton: Boolean,
    val hasInitiallyCentered: Boolean,
    val savedLocation: MapStateStore.MapPoint?,
    val savedZoom: Double?,
    val savedBearing: Double?
)

private fun QnhCalibrationFailureReason.toUserMessage(): String = when (this) {
    QnhCalibrationFailureReason.REPLAY_MODE -> "Auto calibration disabled in replay"
    QnhCalibrationFailureReason.ALREADY_RUNNING -> "Auto calibration already running"
    QnhCalibrationFailureReason.TIMEOUT -> "Auto calibration timed out"
    QnhCalibrationFailureReason.INVALID_QNH -> "Auto calibration produced invalid QNH"
    QnhCalibrationFailureReason.MISSING_SENSORS -> "Auto calibration needs GPS and baro"
    QnhCalibrationFailureReason.UNKNOWN -> "Auto calibration failed"
}


