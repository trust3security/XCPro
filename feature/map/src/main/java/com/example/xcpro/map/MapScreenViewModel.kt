package com.example.xcpro.map

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardPreferences
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.convertToRealTimeFlightData
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.common.flow.inVm
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindRepository
import com.example.xcpro.weather.wind.data.WindState
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastController
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.domain.toUserMessage
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
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
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Lifecycle-aware owner for long-lived map state and controllers.
 */
@HiltViewModel
class MapScreenViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val taskManager: TaskManagerCoordinator,
    val cardPreferences: CardPreferences,
    private val mapStyleRepository: MapStyleRepository,
    private val unitsRepository: UnitsRepository,
    private val qnhPreferencesRepository: QnhPreferencesRepository,
    private val waypointLoader: WaypointLoader,
    private val gliderRepository: GliderRepository,
    private val varioServiceManager: VarioServiceManager,
    private val flightDataRepository: FlightDataRepository,
    private val windRepository: WindRepository,
    private val igcReplayController: IgcReplayController,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val ballastController = BallastController(
        repository = gliderRepository,
        scope = viewModelScope,
        dispatcher = defaultDispatcher
    )

    val mapState: MapScreenState = MapScreenState(appContext, mapStyleRepository.initialStyle())
    val flightDataManager: FlightDataManager =
        FlightDataManager(appContext, cardPreferences, viewModelScope)
    val locationManager: LocationManager = LocationManager(
        context = appContext,
        mapState = mapState,
        coroutineScope = viewModelScope,
        qnhPreferencesRepository = qnhPreferencesRepository,
        varioServiceManager = varioServiceManager
    )
    val orientationManager: MapOrientationManager =
        MapOrientationManager(appContext, viewModelScope, locationManager.unifiedSensorManager)
    val lifecycleManager: MapLifecycleManager =
        MapLifecycleManager(mapState, orientationManager, locationManager)
    val mapInitializer: MapInitializer = MapInitializer(
        context = appContext,
        mapState = mapState,
        orientationManager = orientationManager,
        taskManager = taskManager,
        unifiedSensorManager = locationManager.unifiedSensorManager
    )

    val cameraManager = MapCameraManager(mapState)
    val modalManager = MapModalManager(mapState)
    val overlayManager = MapOverlayManager(appContext, mapState, taskManager)
    val taskScreenManager = MapTaskScreenManager(mapState, taskManager)
    val ballastUiState: StateFlow<BallastUiState> = ballastController.state
    val sharedFlightDataRepository: FlightDataRepository = flightDataRepository
    val windState: StateFlow<WindState> = windRepository.windState
    val replaySessionState: StateFlow<IgcReplayController.SessionState> = igcReplayController.session
    val showReplayDebugFab: Boolean = MapFeatureFlags.showReplayDebugFab
    val gpsStatusFlow: StateFlow<com.example.xcpro.sensors.GpsStatus> =
        locationManager.unifiedSensorManager.gpsStatusFlow

    private val variometerRepository = VariometerWidgetRepository(mapState.sharedPrefs)
    private val _variometerUiState = MutableStateFlow(VariometerUiState())
    val variometerUiState: StateFlow<VariometerUiState> = _variometerUiState.asStateFlow()

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val _uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 1)
    val uiEffects: SharedFlow<MapUiEffect> = _uiEffects.asSharedFlow()
    private val _containerReady = MutableStateFlow(false)
    private val _liveDataReady = MutableStateFlow(false)
    val cardHydrationReady: StateFlow<Boolean> =
        combine(_containerReady, _liveDataReady) { container, data -> container && data }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        observeFlightDataRepository()
        observeSafeContainerSize()
        observeReplayEvents()
        observeReplaySessionDebug()
    }

    private fun observeFlightDataRepository() {
        combine(
            flightDataRepository.flightData,
            windRepository.windState
        ) { data, wind -> data to wind }
            .onEach { (data, wind) ->
                if (data != null) {
                    if (!_liveDataReady.value) {
                        _liveDataReady.value = true
                    }
                    val liveData = convertToRealTimeFlightData(data)
                        .applyWindState(wind)
                    flightDataManager.updateLiveFlightData(liveData)
                } else {
                    flightDataManager.updateLiveFlightData(null)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSafeContainerSize() {
        mapState.safeContainerSizeFlow
            .onEach { size: IntSize ->
                if (!_containerReady.value && size.width > 0 && size.height > 0) {
                    _containerReady.value = true
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeReplayEvents() {
        igcReplayController.events
            .onEach { event ->
                when (event) {
                    is IgcReplayController.ReplayEvent.Completed ->
                        _uiEffects.emit(MapUiEffect.ShowToast("Replay finished (${event.samples} samples)"))
                    is IgcReplayController.ReplayEvent.Failed -> {
                        if (event.throwable is kotlinx.coroutines.CancellationException) {
                            _uiEffects.emit(MapUiEffect.ShowToast("Replay failed: job was cancelled"))
                            return@onEach
                        }
                        Log.e("MapScreenViewModel", "Replay failed", event.throwable)
                        _uiEffects.emit(MapUiEffect.ShowToast("Replay failed: ${event.throwable.message ?: "Unknown error"}"))
                    }
                    IgcReplayController.ReplayEvent.Cancelled -> {
                        _uiEffects.emit(MapUiEffect.ShowToast("Replay failed: job was cancelled"))
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Debug helper: log session changes so we can see selection/status from the UI layer.
     */
    private fun observeReplaySessionDebug() {
        igcReplayController.session
            .onEach { session ->
                Log.i(
                    "MapScreenViewModel",
                    "REPLAY_SESSION_UI status=${session.status} selection=${session.selection} elapsed=${session.elapsedMillis} dur=${session.durationMillis}"
                )
            }
            .launchIn(viewModelScope)
    }

    private fun RealTimeFlightData.applyWindState(windState: WindState): RealTimeFlightData {
        if (!windState.isAvailable) {
            return this
        }

        val vector = windState.vector ?: return this
        val directionFrom = ((vector.directionFromDeg % 360.0) + 360.0) % 360.0

        return copy(
            windSpeed = vector.speed.toFloat(),
            windDirection = directionFrom.toFloat(),
            windQuality = windState.quality,
            windSource = windState.source.name,
            windHeadwind = windState.headwind,
            windCrosswind = windState.crosswind
        )
    }

    private val _isAATEditMode = MutableStateFlow(false)
    val isAATEditMode: StateFlow<Boolean> = _isAATEditMode.asStateFlow()
    private val unitsState = unitsRepository.unitsFlow.inVm(
        scope = viewModelScope,
        initial = UnitsPreferences()
    )

    init {
        mapState.flightDataManager = flightDataManager
        if (MapFeatureFlags.loadSavedTasksOnInit) {
            taskManager.loadSavedTasks()
        }
        _uiState.update { it.copy(isUiEditMode = mapState.isUIEditMode, isDrawerOpen = false) }
        observeUnits()
        observeGliderConfig()
        onEvent(MapUiEvent.RefreshWaypoints)
    }

    fun onEvent(event: MapUiEvent) {
        when (event) {
            MapUiEvent.RefreshWaypoints -> loadWaypoints()
            MapUiEvent.ToggleUiEditMode -> setUiEditMode(!mapState.isUIEditMode)
            is MapUiEvent.SetUiEditMode -> setUiEditMode(event.enabled)
            MapUiEvent.ToggleDrawer -> toggleDrawer()
            is MapUiEvent.SetDrawerOpen -> setDrawerOpen(event.isOpen)
        }
    }

    fun onReplayPlayPause() {
        viewModelScope.launch {
            when (replaySessionState.value.status) {
                IgcReplayController.SessionStatus.PLAYING -> igcReplayController.pause()
                IgcReplayController.SessionStatus.PAUSED -> igcReplayController.play()
                IgcReplayController.SessionStatus.IDLE -> {
                    // Ensure we have a selection before play
                    val selection = replaySessionState.value.selection
                    if (selection == null) {
                        try {
                            igcReplayController.loadAsset(DEV_REPLAY_ASSET_PATH)
                        } catch (t: Throwable) {
                            Log.e("MapScreenViewModel", "Auto replay failed", t)
                            _uiEffects.emit(MapUiEffect.ShowToast("IGC replay asset missing"))
                            return@launch
                        }
                    }
                    // When starting a new replay, force the map to recentre on the first replay point
                    mapState.hasInitiallyCentered = false
                    mapState.showReturnButton = false
                    mapState.isTrackingLocation = true
                    igcReplayController.play()
                }
            }
        }
    }

    fun onReplayStop() {
        igcReplayController.stop()
    }

    fun onReplaySpeedChanged(multiplier: Double) {
        viewModelScope.launch {
            if (!ensureReplaySelectionLoaded()) return@launch
            igcReplayController.setSpeed(multiplier)
        }
    }

    fun onReplaySeek(progress: Float) {
        viewModelScope.launch {
            if (!ensureReplaySelectionLoaded()) return@launch
            igcReplayController.seekTo(progress)
        }
    }

    fun onReplayDevAutoplay() {
        viewModelScope.launch {
            try {
                igcReplayController.loadAsset(DEV_REPLAY_ASSET_PATH)
                igcReplayController.play()
            } catch (t: Throwable) {
                Log.e("MapScreenViewModel", "Failed to start dev replay", t)
            }
        }
    }

    /**
     * Launches an IGC replay from a user-selected SAF Uri.
     * AI-NOTE: This mirrors the simple flow from IgcReplaySim—pick, load, play—while reusing
     * the existing replay pipeline (ReplaySensorSource -> FlightDataCalculator -> repository).
     */
    fun onReplayFileChosen(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            Log.i("MapScreenViewModel", "REPLAY_FILE chosen uri=$uri name=$displayName")
            _uiEffects.emit(MapUiEffect.ShowToast("Loading replay..."))
            val loadResult = runCatching {
                // AI-NOTE: Use NonCancellable so a quick scope cancellation (e.g., navigation churn)
                // doesn't misreport a successful load as a failure.
                withContext(NonCancellable) { igcReplayController.loadFile(uri, displayName) }
            }

            if (igcReplayController.session.value.selection != null) {
                Log.i("MapScreenViewModel", "REPLAY_FILE load success uri=$uri")
                mapState.hasInitiallyCentered = false
                mapState.showReturnButton = false
                mapState.isTrackingLocation = true
                Log.i("MapScreenViewModel", "REPLAY_FILE starting play uri=$uri")
                igcReplayController.play()
                return@launch
            }

            loadResult.onFailure { t ->
                if (t is CancellationException) {
                    Log.w("MapScreenViewModel", "Replay load cancelled after prepare uri=$uri", t)
                } else {
                    Log.e("MapScreenViewModel", "Replay load failed uri=$uri", t)
                    _uiEffects.emit(
                        MapUiEffect.ShowToast("Replay failed: ${t.message ?: "Unknown error"}")
                    )
                }
            }
        }
    }

    /**
     * Ensures a replay selection is loaded so slider/speed/play actions have data to act on.
     * Returns true when a selection is present (either pre-existing or after loading the dev asset).
     */
    private suspend fun ensureReplaySelectionLoaded(): Boolean {
        if (replaySessionState.value.selection != null) return true
        return try {
            igcReplayController.loadAsset(DEV_REPLAY_ASSET_PATH)
            true
        } catch (t: Throwable) {
            Log.e("MapScreenViewModel", "Replay asset missing", t)
            _uiEffects.emit(MapUiEffect.ShowToast("IGC replay asset missing"))
            false
        }
    }

    private fun setUiEditMode(enabled: Boolean) {
        if (mapState.isUIEditMode == enabled) {
            return
        }
        mapState.isUIEditMode = enabled
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
        if (_variometerUiState.value.isInitialized) {
            return
        }
        val centeredOffset = Offset(
            x = ((screenWidthPx - defaultSizePx) / 2f).coerceAtLeast(0f),
            y = ((screenHeightPx - defaultSizePx) / 2f).coerceAtLeast(0f)
        )
        val persistedLayout = variometerRepository.load(centeredOffset, defaultSizePx)
        val sanitizedSize = persistedLayout.sizePx.coerceIn(minSizePx, maxSizePx)
        val targetOffset = if (persistedLayout.hasPersistedOffset) {
            persistedLayout.offset
        } else {
            centeredOffset
        }
        val boundedOffset = clampOffset(targetOffset, sanitizedSize, screenWidthPx, screenHeightPx)
        _variometerUiState.value = VariometerUiState(
            offset = boundedOffset,
            sizePx = sanitizedSize,
            isInitialized = true
        )
        if (!persistedLayout.hasPersistedOffset) {
            variometerRepository.saveOffset(boundedOffset)
        }
        if (!persistedLayout.hasPersistedSize) {
            variometerRepository.saveSize(sanitizedSize)
        }
    }

    fun onVariometerOffsetCommitted(
        offset: Offset,
        screenWidthPx: Float,
        screenHeightPx: Float
    ) {
        if (!_variometerUiState.value.isInitialized) return
        val clamped = clampOffset(offset, _variometerUiState.value.sizePx, screenWidthPx, screenHeightPx)
        _variometerUiState.update { it.copy(offset = clamped) }
        variometerRepository.saveOffset(clamped)
    }

    fun onVariometerSizeCommitted(
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) {
        if (!_variometerUiState.value.isInitialized) return
        val clampedSize = sizePx.coerceIn(minSizePx, maxSizePx)
        val clampedOffset = clampOffset(_variometerUiState.value.offset, clampedSize, screenWidthPx, screenHeightPx)
        _variometerUiState.update { it.copy(sizePx = clampedSize, offset = clampedOffset) }
        variometerRepository.saveSize(clampedSize)
        variometerRepository.saveOffset(clampedOffset)
    }

    private fun clampOffset(
        offset: Offset,
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): Offset {
        val maxX = (screenWidthPx - sizePx).coerceAtLeast(0f)
        val maxY = (screenHeightPx - sizePx).coerceAtLeast(0f)
        return Offset(
            x = offset.x.coerceIn(0f, maxX),
            y = offset.y.coerceIn(0f, maxY)
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

    fun attachMapView(mapView: MapView) {
        mapState.mapView = mapView
    }

    fun initializeMap(map: MapLibreMap) {
        viewModelScope.launch {
            mapInitializer.initializeMap(map)
        }
    }

    override fun onCleared() {
        lifecycleManager.cleanup()
        ballastController.dispose()
        super.onCleared()
    }
    companion object {
        private const val DEV_REPLAY_ASSET_PATH = "replay/2025-11-11.igc"
    }
}
