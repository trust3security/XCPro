package com.example.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.trail.TrailSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-source-of-truth for map UI state. Avoids UI/platform types so the ViewModel
 * can own state without referencing Compose or MapLibre classes.
 */
class MapStateStore(
    initialStyleName: String
) : MapStateReader {
    private val _safeContainerSize = MutableStateFlow(MapSize.Zero)
    override val safeContainerSize: StateFlow<MapSize> = _safeContainerSize.asStateFlow()

    private val _mapStyleName = MutableStateFlow(initialStyleName)
    override val mapStyleName: StateFlow<String> = _mapStyleName.asStateFlow()

    private val _showRecenterButton = MutableStateFlow(false)
    override val showRecenterButton: StateFlow<Boolean> = _showRecenterButton.asStateFlow()

    private val _showReturnButton = MutableStateFlow(false)
    override val showReturnButton: StateFlow<Boolean> = _showReturnButton.asStateFlow()

    private val _isTrackingLocation = MutableStateFlow(true)
    override val isTrackingLocation: StateFlow<Boolean> = _isTrackingLocation.asStateFlow()

    private val _showDistanceCircles = MutableStateFlow(false)
    override val showDistanceCircles: StateFlow<Boolean> = _showDistanceCircles.asStateFlow()

    private val _lastUserPanTime = MutableStateFlow(0L)
    override val lastUserPanTime: StateFlow<Long> = _lastUserPanTime.asStateFlow()

    private val _hasInitiallyCentered = MutableStateFlow(false)
    override val hasInitiallyCentered: StateFlow<Boolean> = _hasInitiallyCentered.asStateFlow()

    private val _savedLocation = MutableStateFlow<MapPoint?>(null)
    override val savedLocation: StateFlow<MapPoint?> = _savedLocation.asStateFlow()

    private val _savedZoom = MutableStateFlow<Double?>(null)
    override val savedZoom: StateFlow<Double?> = _savedZoom.asStateFlow()

    private val _savedBearing = MutableStateFlow<Double?>(null)
    override val savedBearing: StateFlow<Double?> = _savedBearing.asStateFlow()

    private val _lastCameraSnapshot = MutableStateFlow<CameraSnapshot?>(null)
    override val lastCameraSnapshot: StateFlow<CameraSnapshot?> = _lastCameraSnapshot.asStateFlow()

    private val _currentMode = MutableStateFlow(FlightMode.CRUISE)
    override val currentMode: StateFlow<FlightMode> = _currentMode.asStateFlow()

    private val _currentFlightMode = MutableStateFlow(FlightModeSelection.CRUISE)
    override val currentFlightMode: StateFlow<FlightModeSelection> = _currentFlightMode.asStateFlow()

    private val _currentZoom = MutableStateFlow(10f)
    override val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _targetLatLng = MutableStateFlow<MapPoint?>(null)
    override val targetLatLng: StateFlow<MapPoint?> = _targetLatLng.asStateFlow()

    private val _targetZoom = MutableStateFlow<Float?>(null)
    override val targetZoom: StateFlow<Float?> = _targetZoom.asStateFlow()

    private val _currentUserLocation = MutableStateFlow<MapPoint?>(null)
    override val currentUserLocation: StateFlow<MapPoint?> = _currentUserLocation.asStateFlow()

    private val _trailSettings = MutableStateFlow(TrailSettings())
    val trailSettings: StateFlow<TrailSettings> = _trailSettings.asStateFlow()

    private val _displayPoseMode = MutableStateFlow(DisplayPoseMode.SMOOTHED)
    override val displayPoseMode: StateFlow<DisplayPoseMode> = _displayPoseMode.asStateFlow()

    private val _displaySmoothingProfile = MutableStateFlow(DisplaySmoothingProfile.SMOOTH)
    override val displaySmoothingProfile: StateFlow<DisplaySmoothingProfile> =
        _displaySmoothingProfile.asStateFlow()

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

    fun setShowDistanceCircles(show: Boolean) {
        if (_showDistanceCircles.value != show) {
            _showDistanceCircles.value = show
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

    fun updateCameraSnapshot(target: MapPoint?, zoom: Double?, bearing: Double?) {
        if (target == null || zoom == null || bearing == null) return
        if (!zoom.isFinite() || !bearing.isFinite()) return
        val snapshot = CameraSnapshot(target = target, zoom = zoom, bearing = bearing)
        if (_lastCameraSnapshot.value != snapshot) {
            _lastCameraSnapshot.value = snapshot
        }
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

    fun setTarget(location: MapPoint?, zoom: Float?) {
        _targetLatLng.value = location
        _targetZoom.value = zoom
    }

    fun setCurrentUserLocation(location: MapPoint?) {
        _currentUserLocation.value = location
    }

    fun setTrailSettings(settings: TrailSettings) {
        if (_trailSettings.value != settings) {
            _trailSettings.value = settings
        }
    }

    fun setDisplayPoseMode(mode: DisplayPoseMode) {
        if (_displayPoseMode.value != mode) {
            _displayPoseMode.value = mode
        }
    }

    fun setDisplaySmoothingProfile(profile: DisplaySmoothingProfile) {
        if (_displaySmoothingProfile.value != profile) {
            _displaySmoothingProfile.value = profile
        }
    }
}
