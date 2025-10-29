package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardPreferences
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastController
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.common.units.UnitsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val injectedTaskManager: TaskManagerCoordinator,
    private val injectedCardPreferences: CardPreferences,
    private val mapStyleRepository: MapStyleRepository,
    private val unitsRepository: UnitsRepository,
    private val qnhPreferencesRepository: QnhPreferencesRepository,
    private val waypointLoader: WaypointLoader
) : ViewModel() {

    val taskManager: TaskManagerCoordinator = injectedTaskManager

    private val _hawkDashboardPending = MutableStateFlow(false)
    private val _hawkDashboardClients = MutableStateFlow(0)

    private val gliderRepository = GliderRepository.getInstance(appContext)
    private val ballastController = BallastController(
        repository = gliderRepository,
        scope = viewModelScope,
        dispatcher = Dispatchers.Default
    )

    val mapState = MapScreenState(appContext, mapStyleRepository.initialStyle())
    val cardPreferences: CardPreferences = injectedCardPreferences
    val flightDataManager = FlightDataManager(appContext, cardPreferences, viewModelScope)
    val orientationManager = MapOrientationManager(appContext, viewModelScope)
    val locationManager = LocationManager(
        context = appContext,
        mapState = mapState,
        coroutineScope = viewModelScope,
        qnhPreferencesRepository = qnhPreferencesRepository
    ) { hasHawkDashboardClient() }
    val lifecycleManager = MapLifecycleManager(mapState, orientationManager, locationManager)
    val mapInitializer = MapInitializer(
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

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    private val _uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 1)
    val uiEffects: SharedFlow<MapUiEffect> = _uiEffects.asSharedFlow()

    private val _isAATEditMode = MutableStateFlow(false)
    val isAATEditMode: StateFlow<Boolean> = _isAATEditMode.asStateFlow()

    init {
        mapState.flightDataManager = flightDataManager
        taskManager.loadSavedTasks()
        observeUnits()
        onEvent(MapUiEvent.RefreshWaypoints)
    }

    fun onEvent(event: MapUiEvent) {
        when (event) {
            MapUiEvent.RefreshWaypoints -> loadWaypoints()
        }
    }

    private fun observeUnits() {
        viewModelScope.launch {
            unitsRepository.unitsFlow.collect { preferences ->
                _uiState.update { it.copy(unitsPreferences = preferences) }
                flightDataManager.updateUnitsPreferences(preferences)
            }
        }
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
                            waypointError = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            waypoints = emptyList(),
                            isLoadingWaypoints = false,
                            waypointError = error.message ?: "Failed to load waypoints"
                        )
                    }
                    Log.e("MapScreenViewModel", "Failed to load waypoints", error)
                    _uiEffects.tryEmit(
                        MapUiEffect.ShowToast(
                            error.message ?: "Failed to load waypoints"
                        )
                    )
                }
        }
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
