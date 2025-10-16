package com.example.xcpro.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardPreferences
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.getGlobalTaskManagerCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Lifecycle-aware owner for long-lived map state and controllers.
 */
class MapScreenViewModel(
    application: Application,
    initialMapStyle: String
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _taskManager: TaskManagerCoordinator =
        getGlobalTaskManagerCoordinator(appContext)
            ?: TaskManagerCoordinator(appContext)

    val taskManager: TaskManagerCoordinator = _taskManager

    val mapState = MapScreenState(appContext, initialMapStyle)
    val cardPreferences = CardPreferences(appContext)
    val flightDataManager = FlightDataManager(appContext, cardPreferences, viewModelScope)
    val orientationManager = MapOrientationManager(appContext, viewModelScope)
    val locationManager = LocationManager(appContext, mapState, viewModelScope)
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

    private val _isAATEditMode = MutableStateFlow(false)
    val isAATEditMode: StateFlow<Boolean> = _isAATEditMode.asStateFlow()

    init {
        mapState.flightDataManager = flightDataManager
        taskManager.loadSavedTasks()
    }

    fun setAATEditMode(enabled: Boolean) {
        _isAATEditMode.value = enabled
    }

    fun exitAATEditMode() {
        _isAATEditMode.value = false
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
        super.onCleared()
    }

    companion object {
        fun provideFactory(
            application: Application,
            initialMapStyle: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MapScreenViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return MapScreenViewModel(application, initialMapStyle) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}





