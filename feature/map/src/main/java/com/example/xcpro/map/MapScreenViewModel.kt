package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardPreferences
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _hawkDashboardPending = MutableStateFlow(false)
    private val _hawkDashboardClients = MutableStateFlow(0)

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
        hawkDashboardActive = { hasHawkDashboardClient() },
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

    private val variometerRepository = VariometerWidgetRepository(mapState.sharedPrefs)
    private val _variometerUiState = MutableStateFlow(VariometerUiState())
    val variometerUiState: StateFlow<VariometerUiState> = _variometerUiState.asStateFlow()

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val _uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 1)
    val uiEffects: SharedFlow<MapUiEffect> = _uiEffects.asSharedFlow()

    init {
        observeFlightDataRepository()
    }

    private fun observeFlightDataRepository() {
        flightDataRepository.flightData
            .onEach { data ->
                if (data != null) {
                    val liveData = convertToRealTimeFlightData(data)
                    flightDataManager.updateLiveFlightData(liveData)
                } else {
                    flightDataManager.updateLiveFlightData(null)
                }
            }
            .launchIn(viewModelScope)
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

    fun prepareHawkDashboardClient() {
        _hawkDashboardPending.value = true
    }

    fun finalizeHawkDashboardClient(): Boolean {
        val pending = _hawkDashboardPending.value
        _hawkDashboardPending.value = false
        if (pending) {
            incrementHawkClients()
            return true
        }
        return false
    }

    fun cancelHawkDashboardPreparation() {
        _hawkDashboardPending.value = false
    }

    fun registerHawkDashboardClient() {
        incrementHawkClients()
    }

    fun unregisterHawkDashboardClient() {
        _hawkDashboardClients.update { current ->
            val next = (current - 1).coerceAtLeast(0)
            if (next == 0) {
                locationManager.stopLocationTracking(force = true)
            }
            next
        }
    }

    fun hasHawkDashboardClient(): Boolean =
        _hawkDashboardPending.value || _hawkDashboardClients.value > 0

    private fun incrementHawkClients() {
        _hawkDashboardClients.update { it + 1 }
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
}
