package com.example.xcpro.map

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardPreferences
import com.example.xcpro.MapOrientationManager
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
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastController
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.domain.toUserMessage
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.variometer.layout.VariometerUiState
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
import kotlinx.coroutines.flow.launchIn
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
    @ApplicationContext private val appContext: Context,
    val taskManager: TaskManagerCoordinator,
    val cardPreferences: CardPreferences,
    private val mapStyleRepository: MapStyleRepository,
    private val unitsRepository: UnitsRepository,
    val qnhPreferencesRepository: QnhPreferencesRepository,
    private val waypointLoader: WaypointLoader,
    private val gliderRepository: GliderRepository,
    val varioServiceManager: VarioServiceManager,
    private val flightDataRepository: FlightDataRepository,
    private val windRepository: WindSensorFusionRepository,
    val igcReplayController: IgcReplayController,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
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
        MapOrientationManager(appContext, viewModelScope, unifiedSensorManager)
    val ballastUiState: StateFlow<BallastUiState> = ballastController.state
    val sharedFlightDataRepository: FlightDataRepository = flightDataRepository
    val windState: StateFlow<WindState> = windRepository.windState
    val replaySessionState: StateFlow<SessionState> = igcReplayController.session
    val showReplayDebugFab: Boolean = MapFeatureFlags.showReplayDebugFab
    val showVarioDemoFab: Boolean = MapFeatureFlags.showVarioDemoFab
    val gpsStatusFlow: StateFlow<com.example.xcpro.sensors.GpsStatus> =
        unifiedSensorManager.gpsStatusFlow

    private val sharedPrefs = appContext.getSharedPreferences("MapPrefs", Context.MODE_PRIVATE)
    private val variometerLayoutController = VariometerLayoutController(sharedPrefs)
    val variometerUiState: StateFlow<VariometerUiState> = variometerLayoutController.state

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val _uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 1)
    val uiEffects: SharedFlow<MapUiEffect> = _uiEffects.asSharedFlow()
    private val replayCoordinator = MapReplayCoordinator(
        sessionState = replaySessionState,
        igcReplayController = igcReplayController,
        stateActions = this,
        uiEffects = _uiEffects,
        scope = viewModelScope
    )
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
        flightDataManager = flightDataManager,
        mapStateStore = mapStateStore,
        liveDataReady = _liveDataReady,
        containerReady = _containerReady,
        uiEffects = _uiEffects,
        igcReplayController = igcReplayController
    )

    init {
        observers.start()
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
        onEvent(MapUiEvent.RefreshWaypoints)
    }

    fun emitMapCommand(command: MapCommand) {
        _mapCommands.tryEmit(command)
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

    fun onReplayPlayPause() = replayCoordinator.onReplayPlayPause()

    fun onReplayStop() = replayCoordinator.onReplayStop()

    fun onReplaySpeedChanged(multiplier: Double) = replayCoordinator.onReplaySpeedChanged(multiplier)

    fun onReplaySeek(progress: Float) = replayCoordinator.onReplaySeek(progress)

    fun onReplayDevAutoplay() = replayCoordinator.onReplayDevAutoplay()

    fun onVarioDemoReplay() = replayCoordinator.onVarioDemoReplay()

    fun onReplayFileChosen(uri: Uri, displayName: String?) =
        replayCoordinator.onReplayFileChosen(uri, displayName)
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

    override fun onCleared() {
        ballastController.dispose()
        super.onCleared()
    }
}


