package com.example.xcpro.map

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.unit.IntSize
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.screens.overlays.getMapStyleUrl
import com.example.xcpro.tasks.BottomSheetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

// NOTE: MapScreenState now holds runtime map handles and legacy flags only.
// UI state should live in MapStateStore; keep mutations out of composables.
class MapScreenState(
    private val context: Context,
    initialMapStyle: String
) {
    companion object {
        private const val PREFS_NAME = "MapPrefs"
    }

    val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Flight Mode State
    private val _currentMode = MutableStateFlow(FlightMode.CRUISE)
    var currentMode: FlightMode
        get() = _currentMode.value
        set(value) {
            if (_currentMode.value != value) _currentMode.value = value
        }
    val currentModeFlow: StateFlow<FlightMode> = _currentMode.asStateFlow()

    private val _currentFlightMode = MutableStateFlow(FlightModeSelection.CRUISE)
    var currentFlightMode: FlightModeSelection
        get() = _currentFlightMode.value
        set(value) {
            if (_currentFlightMode.value != value) _currentFlightMode.value = value
        }
    val currentFlightModeFlow: StateFlow<FlightModeSelection> = _currentFlightMode.asStateFlow()

    // Gesture State
    private val _swipeDirection = MutableStateFlow<String?>(null)
    var swipeDirection: String?
        get() = _swipeDirection.value
        set(value) {
            if (_swipeDirection.value != value) _swipeDirection.value = value
        }
    val swipeDirectionFlow: StateFlow<String?> = _swipeDirection.asStateFlow()

    private val _lastSwipeTime = MutableStateFlow(0L)
    var lastSwipeTime: Long
        get() = _lastSwipeTime.value
        set(value) {
            if (_lastSwipeTime.value != value) _lastSwipeTime.value = value
        }
    val lastSwipeTimeFlow: StateFlow<Long> = _lastSwipeTime.asStateFlow()

    // Map State
    var mapLibreMap: MapLibreMap? = null
    var mapView: MapView? = null
    var targetZoom: Float? = null
    var targetLatLng: org.maplibre.android.geometry.LatLng? = null

    // Camera zoom observable so Compose overlays react instantly to zoom gestures
    private val _currentZoom = MutableStateFlow(10f)
    var currentZoom: Float
        get() = _currentZoom.value
        set(value) {
            if (_currentZoom.value != value) _currentZoom.value = value
        }
    val currentZoomFlow: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _mapStyleUrl = MutableStateFlow(getMapStyleUrl(initialMapStyle))
    var mapStyleUrl: String
        get() = _mapStyleUrl.value
        set(value) {
            if (_mapStyleUrl.value != value) _mapStyleUrl.value = value
        }
    val mapStyleUrlFlow: StateFlow<String> = _mapStyleUrl.asStateFlow()

    private val _lastCameraBearing = MutableStateFlow(0.0)
    var lastCameraBearing: Double
        get() = _lastCameraBearing.value
        set(value) {
            if (_lastCameraBearing.value != value) _lastCameraBearing.value = value
        }
    val lastCameraBearingFlow: StateFlow<Double> = _lastCameraBearing.asStateFlow()

    // UI State
    private val _isUiEditMode = MutableStateFlow(false)
    var isUIEditMode: Boolean
        get() = _isUiEditMode.value
        set(value) {
            if (_isUiEditMode.value != value) _isUiEditMode.value = value
        }
    val isUiEditModeFlow: StateFlow<Boolean> = _isUiEditMode.asStateFlow()

    private val _showCardLibrary = MutableStateFlow(false)
    var showCardLibrary: Boolean
        get() = _showCardLibrary.value
        set(value) {
            if (_showCardLibrary.value != value) _showCardLibrary.value = value
        }
    val showCardLibraryFlow: StateFlow<Boolean> = _showCardLibrary.asStateFlow()

    private val _safeContainerSize = MutableStateFlow(IntSize.Zero)
    var safeContainerSize: IntSize
        get() = _safeContainerSize.value
        set(value) {
            if (value == IntSize.Zero) {
                return
            }
            if (_safeContainerSize.value != value) _safeContainerSize.value = value
        }
    val safeContainerSizeFlow: StateFlow<IntSize> = _safeContainerSize.asStateFlow()

    // Flight Data Manager - centralized flight data handling
    lateinit var flightDataManager: FlightDataManager

    // Location Tracking State
    private val _currentUserLocation =
        MutableStateFlow<org.maplibre.android.geometry.LatLng?>(null)
    var currentUserLocation: org.maplibre.android.geometry.LatLng?
        get() = _currentUserLocation.value
        set(value) {
            if (_currentUserLocation.value != value) _currentUserLocation.value = value
        }
    val currentUserLocationFlow: StateFlow<org.maplibre.android.geometry.LatLng?> =
        _currentUserLocation.asStateFlow()

    var blueLocationOverlay: BlueLocationOverlay? = null
    var distanceCirclesOverlay: DistanceCirclesOverlay? = null

    private val _showDistanceCircles = MutableStateFlow(false)
    var showDistanceCircles: Boolean
        get() = _showDistanceCircles.value
        set(value) {
            if (_showDistanceCircles.value != value) _showDistanceCircles.value = value
        }
    val showDistanceCirclesFlow: StateFlow<Boolean> = _showDistanceCircles.asStateFlow()

    // Camera Control State
    private val _showRecenterButton = MutableStateFlow(false)
    var showRecenterButton: Boolean
        get() = _showRecenterButton.value
        set(value) {
            if (_showRecenterButton.value != value) _showRecenterButton.value = value
        }
    val showRecenterButtonFlow: StateFlow<Boolean> = _showRecenterButton.asStateFlow()

    private val _isTrackingLocation = MutableStateFlow(true)
    var isTrackingLocation: Boolean
        get() = _isTrackingLocation.value
        set(value) {
            if (_isTrackingLocation.value != value) _isTrackingLocation.value = value
        }
    val isTrackingLocationFlow: StateFlow<Boolean> = _isTrackingLocation.asStateFlow()

    private val _lastUserPanTime = MutableStateFlow(0L)
    var lastUserPanTime: Long
        get() = _lastUserPanTime.value
        set(value) {
            if (_lastUserPanTime.value != value) _lastUserPanTime.value = value
        }
    val lastUserPanTimeFlow: StateFlow<Long> = _lastUserPanTime.asStateFlow()

    // Pan-and-Return State
    private val _showReturnButton = MutableStateFlow(false)
    var showReturnButton: Boolean
        get() = _showReturnButton.value
        set(value) {
            if (_showReturnButton.value != value) _showReturnButton.value = value
        }
    val showReturnButtonFlow: StateFlow<Boolean> = _showReturnButton.asStateFlow()

    private val _savedLocation =
        MutableStateFlow<org.maplibre.android.geometry.LatLng?>(null)
    var savedLocation: org.maplibre.android.geometry.LatLng?
        get() = _savedLocation.value
        set(value) {
            if (_savedLocation.value != value) _savedLocation.value = value
        }
    val savedLocationFlow: StateFlow<org.maplibre.android.geometry.LatLng?> =
        _savedLocation.asStateFlow()

    private val _savedZoom = MutableStateFlow<Double?>(null)
    var savedZoom: Double?
        get() = _savedZoom.value
        set(value) {
            if (_savedZoom.value != value) _savedZoom.value = value
        }
    val savedZoomFlow: StateFlow<Double?> = _savedZoom.asStateFlow()

    private val _savedBearing = MutableStateFlow<Double?>(null)
    var savedBearing: Double?
        get() = _savedBearing.value
        set(value) {
            if (_savedBearing.value != value) _savedBearing.value = value
        }
    val savedBearingFlow: StateFlow<Double?> = _savedBearing.asStateFlow()

    // Initial Setup State
    private val _hasInitiallyCentered = MutableStateFlow(false)
    var hasInitiallyCentered: Boolean
        get() = _hasInitiallyCentered.value
        set(value) {
            if (_hasInitiallyCentered.value != value) _hasInitiallyCentered.value = value
        }
    val hasInitiallyCenteredFlow: StateFlow<Boolean> = _hasInitiallyCentered.asStateFlow()

    fun updateFlightMode(newMode: FlightMode) {
        android.util.Log.d(
            "MapScreenState",
            "updateFlightMode called: ${currentMode.displayName} -> ${newMode.displayName}"
        )
        currentMode = newMode
        currentFlightMode = when (newMode) {
            FlightMode.CRUISE -> FlightModeSelection.CRUISE
            FlightMode.THERMAL -> FlightModeSelection.THERMAL
            FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
        }
        android.util.Log.d("MapScreenState", "Map state currentMode updated to: ${currentMode.displayName}")
        if (::flightDataManager.isInitialized) {
            flightDataManager.updateFlightModeFromEnum(newMode)
            android.util.Log.d(
                "MapScreenState",
                "FlightDataManager also updated to: ${flightDataManager.currentFlightMode.displayName}"
            )
        } else {
            android.util.Log.w("MapScreenState", "FlightDataManager not initialized - skipping update")
        }
    }

    fun resetToDefaults() {
        currentMode = FlightMode.CRUISE
        swipeDirection = null
        showCardLibrary = false
        isUIEditMode = false
        showReturnButton = false
        showRecenterButton = false
        isTrackingLocation = true
    }

    fun saveLocation(
        location: org.maplibre.android.geometry.LatLng,
        zoom: Double,
        bearing: Double
    ) {
        savedLocation = location
        savedZoom = zoom
        savedBearing = bearing
    }

    fun clearSavedLocation() {
        savedLocation = null
        savedZoom = null
        savedBearing = null
    }

    fun updateMapStyle(newStyle: String) {
        mapStyleUrl = getMapStyleUrl(newStyle)
    }
}
