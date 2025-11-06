package com.example.xcpro.map

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import com.example.xcpro.tasks.BottomSheetState
import androidx.compose.ui.unit.IntSize
import com.example.xcpro.screens.overlays.getMapStyleUrl
import com.example.xcpro.map.BlueLocationOverlay
import com.example.xcpro.map.DistanceCirclesOverlay
import com.example.xcpro.map.FlightDataManager
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.RealTimeFlightData
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MapScreenState(
    private val context: Context,
    initialMapStyle: String
) {
    companion object {
        private const val PREFS_NAME = "MapPrefs"
    }

    val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Flight Mode State
    var currentMode by mutableStateOf(com.example.xcpro.FlightMode.CRUISE)
    var currentFlightMode by mutableStateOf(FlightModeSelection.CRUISE)

    // Gesture State
    var swipeDirection by mutableStateOf<String?>(null)
    var lastSwipeTime by mutableLongStateOf(0L)

    // Map State
    var mapLibreMap by mutableStateOf<MapLibreMap?>(null)
    var mapView by mutableStateOf<MapView?>(null)
    var targetZoom by mutableStateOf<Float?>(null)
    var targetLatLng by mutableStateOf<org.maplibre.android.geometry.LatLng?>(null)
    var mapStyleUrl by mutableStateOf(getMapStyleUrl(initialMapStyle))
    var lastCameraBearing by mutableStateOf(0.0)

    // UI State
    var isUIEditMode by mutableStateOf(false)
    var showCardLibrary by mutableStateOf(false)
    var safeContainerSize by mutableStateOf(IntSize.Zero)
    var showTaskBottomSheet by mutableStateOf(false)
    var taskBottomSheetInitialHeight by mutableStateOf<BottomSheetState>(BottomSheetState.HALF_EXPANDED)

    // SkySight State
    var selectedSkysightLayers by mutableStateOf(setOf<String>())

    // Flight Data Manager - centralized flight data handling
    lateinit var flightDataManager: FlightDataManager

    // Location Tracking State
    var currentUserLocation by mutableStateOf<org.maplibre.android.geometry.LatLng?>(null)
    var blueLocationOverlay by mutableStateOf<BlueLocationOverlay?>(null)
    var distanceCirclesOverlay by mutableStateOf<DistanceCirclesOverlay?>(null)
    var showDistanceCircles by mutableStateOf(false)

    // Camera Control State
    var showRecenterButton by mutableStateOf(false)
    var isTrackingLocation by mutableStateOf(true)
    var lastUserPanTime by mutableStateOf(0L)

    // Pan-and-Return State
    var showReturnButton by mutableStateOf(false)
    var savedLocation by mutableStateOf<org.maplibre.android.geometry.LatLng?>(null)
    var savedZoom by mutableStateOf<Double?>(null)
    var savedBearing by mutableStateOf<Double?>(null)

    // Initial Setup State
    var hasInitiallyCentered by mutableStateOf(false)

    // Flight Mode Functions
    fun updateFlightMode(newMode: com.example.xcpro.FlightMode) {
        android.util.Log.d("MapScreenState", "🔄 updateFlightMode called: ${currentMode.displayName} → ${newMode.displayName}")
        currentMode = newMode
        currentFlightMode = when (newMode) {
            com.example.xcpro.FlightMode.CRUISE -> FlightModeSelection.CRUISE
            com.example.xcpro.FlightMode.THERMAL -> FlightModeSelection.THERMAL
            com.example.xcpro.FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
            com.example.xcpro.FlightMode.HAWK -> FlightModeSelection.HAWK
        }
        android.util.Log.d("MapScreenState", "✅ mapState.currentMode updated to: ${currentMode.displayName}")
        // Update FlightDataManager if initialized
        if (::flightDataManager.isInitialized) {
            flightDataManager.updateFlightModeFromEnum(newMode)
            android.util.Log.d("MapScreenState", "✅ FlightDataManager also updated to: ${flightDataManager.currentFlightMode.displayName}")
        } else {
            android.util.Log.w("MapScreenState", "⚠️ FlightDataManager not initialized - skipping update")
        }
    }

    fun resetToDefaults() {
        currentMode = com.example.xcpro.FlightMode.CRUISE
        swipeDirection = null
        showTaskBottomSheet = false
        showCardLibrary = false
        isUIEditMode = false
        showReturnButton = false
        showRecenterButton = false
        isTrackingLocation = true
    }

    fun saveLocation(location: org.maplibre.android.geometry.LatLng, zoom: Double, bearing: Double) {
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

// FlightMode enum moved to main package (com.example.xcpro.FlightMode)
// enum class FlightMode {
//     CRUISE, THERMAL, FINAL_GLIDE
// }
