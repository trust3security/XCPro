package com.example.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.common.flight.FlightMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-source-of-truth for map UI state. Avoids UI/platform types so the ViewModel
 * can own state without referencing Compose or MapLibre classes.
 */
class MapStateStore(
    initialStyleName: String
) {
    data class MapPoint(val latitude: Double, val longitude: Double)

    data class MapSize(val widthPx: Int, val heightPx: Int) {
        companion object {
            val Zero = MapSize(0, 0)
        }
    }

    private val _safeContainerSize = MutableStateFlow(MapSize.Zero)
    val safeContainerSize: StateFlow<MapSize> = _safeContainerSize.asStateFlow()

    private val _mapStyleName = MutableStateFlow(initialStyleName)
    val mapStyleName: StateFlow<String> = _mapStyleName.asStateFlow()

    private val _showRecenterButton = MutableStateFlow(false)
    val showRecenterButton: StateFlow<Boolean> = _showRecenterButton.asStateFlow()

    private val _showReturnButton = MutableStateFlow(false)
    val showReturnButton: StateFlow<Boolean> = _showReturnButton.asStateFlow()

    private val _isTrackingLocation = MutableStateFlow(true)
    val isTrackingLocation: StateFlow<Boolean> = _isTrackingLocation.asStateFlow()

    private val _lastUserPanTime = MutableStateFlow(0L)
    val lastUserPanTime: StateFlow<Long> = _lastUserPanTime.asStateFlow()

    private val _hasInitiallyCentered = MutableStateFlow(false)
    val hasInitiallyCentered: StateFlow<Boolean> = _hasInitiallyCentered.asStateFlow()

    private val _savedLocation = MutableStateFlow<MapPoint?>(null)
    val savedLocation: StateFlow<MapPoint?> = _savedLocation.asStateFlow()

    private val _savedZoom = MutableStateFlow<Double?>(null)
    val savedZoom: StateFlow<Double?> = _savedZoom.asStateFlow()

    private val _savedBearing = MutableStateFlow<Double?>(null)
    val savedBearing: StateFlow<Double?> = _savedBearing.asStateFlow()

    private val _currentMode = MutableStateFlow(FlightMode.CRUISE)
    val currentMode: StateFlow<FlightMode> = _currentMode.asStateFlow()

    private val _currentFlightMode = MutableStateFlow(FlightModeSelection.CRUISE)
    val currentFlightMode: StateFlow<FlightModeSelection> = _currentFlightMode.asStateFlow()

    private val _currentZoom = MutableStateFlow(10f)
    val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _targetLatLng = MutableStateFlow<MapPoint?>(null)
    val targetLatLng: StateFlow<MapPoint?> = _targetLatLng.asStateFlow()

    private val _targetZoom = MutableStateFlow<Float?>(null)
    val targetZoom: StateFlow<Float?> = _targetZoom.asStateFlow()

    private val _currentUserLocation = MutableStateFlow<MapPoint?>(null)
    val currentUserLocation: StateFlow<MapPoint?> = _currentUserLocation.asStateFlow()

    fun updateSafeContainerSize(size: MapSize) {
        if (size == MapSize.Zero) return
        if (_safeContainerSize.value != size) {
            _safeContainerSize.value = size
        }
    }

    fun updateMapStyleName(styleName: String): Boolean {
        if (_mapStyleName.value == styleName) return false
        _mapStyleName.value = styleName
        return true
    }

    fun setShowRecenterButton(show: Boolean) {
        if (_showRecenterButton.value != show) {
            _showRecenterButton.value = show
        }
    }

    fun setShowReturnButton(show: Boolean) {
        if (_showReturnButton.value != show) {
            _showReturnButton.value = show
        }
    }

    fun setTrackingLocation(enabled: Boolean) {
        if (_isTrackingLocation.value != enabled) {
            _isTrackingLocation.value = enabled
        }
    }

    fun updateLastUserPanTime(timestampMillis: Long) {
        if (_lastUserPanTime.value != timestampMillis) {
            _lastUserPanTime.value = timestampMillis
        }
    }

    fun setHasInitiallyCentered(centered: Boolean) {
        if (_hasInitiallyCentered.value != centered) {
            _hasInitiallyCentered.value = centered
        }
    }

    fun saveLocation(location: MapPoint?, zoom: Double?, bearing: Double?) {
        _savedLocation.value = location
        _savedZoom.value = zoom
        _savedBearing.value = bearing
    }

    fun setSavedLocation(location: MapPoint?) {
        _savedLocation.value = location
    }

    fun setSavedZoom(zoom: Double?) {
        _savedZoom.value = zoom
    }

    fun setSavedBearing(bearing: Double?) {
        _savedBearing.value = bearing
    }

    fun clearSavedLocation() {
        _savedLocation.value = null
        _savedZoom.value = null
        _savedBearing.value = null
    }

    fun setCurrentMode(mode: FlightMode) {
        if (_currentMode.value != mode) {
            _currentMode.value = mode
        }
    }

    fun setCurrentFlightMode(mode: FlightModeSelection) {
        if (_currentFlightMode.value != mode) {
            _currentFlightMode.value = mode
        }
    }

    fun updateCurrentZoom(zoom: Float) {
        if (_currentZoom.value != zoom) {
            _currentZoom.value = zoom
        }
    }

    fun setTargetLatLng(location: MapPoint?) {
        _targetLatLng.value = location
    }

    fun setTargetZoom(zoom: Float?) {
        _targetZoom.value = zoom
    }

    fun setTarget(location: MapPoint?, zoom: Float?) {
        _targetLatLng.value = location
        _targetZoom.value = zoom
    }

    fun setCurrentUserLocation(location: MapPoint?) {
        _currentUserLocation.value = location
    }
}
