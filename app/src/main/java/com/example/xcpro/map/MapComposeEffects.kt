package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.OrientationData
import com.example.xcpro.MapOrientationMode
import com.example.xcpro.profiles.ProfileUiState
import kotlinx.coroutines.isActive
import com.example.xcpro.screens.overlays.getMapStyleUrl
import com.example.xcpro.loadConfig
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.dfcards.FlightDataViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlin.random.Random

/**
 * Centralized Compose side effects for MapScreen
 * Handles complex LaunchedEffect blocks to improve testability and maintainability
 */
object MapComposeEffects {

    private const val TAG = "MapComposeEffects"

    /**
     * Location permission and GPS update effects
     */
    @Composable
    fun LocationAndPermissionEffects(
        locationManager: LocationManager,
        locationPermissionLauncher: ActivityResultLauncher<Array<String>>,
        currentLocation: GPSData?,
        orientationData: OrientationData
    ) {
        // Initialize location permissions on first load
        LaunchedEffect(Unit) {
            locationManager.checkAndRequestLocationPermissions(locationPermissionLauncher)
        }

        // Update location overlay when GPS location changes
        LaunchedEffect(currentLocation, orientationData.mode, orientationData.bearing) {
            currentLocation?.let { location ->
                // Pass magnetic heading from orientation data for drift angle calculation
                // Update even without GPS fix for continuous tracking
                locationManager.updateLocationFromGPS(location, orientationData.mode, orientationData.bearing)
            }
        }
    }

    /**
     * Profile changes and flight mode visibility effects
     */
    @Composable
    fun ProfileAndConfigurationEffects(
        uiState: ProfileUiState,
        flightDataManager: FlightDataManager,
        mapState: MapScreenState,
        currentFlightModeSelection: FlightModeSelection,
        safeContainerSize: IntSize,
        flightViewModel: FlightDataViewModel,
        density: androidx.compose.ui.unit.Density
    ) {
        // Load visible flight modes when profile changes
        LaunchedEffect(uiState.activeProfile?.id) {
            flightDataManager.loadVisibleModes(uiState.activeProfile?.id, uiState.activeProfile?.name)

            // Switch to fallback mode if current mode is not visible
            val currentMode = mapState.currentMode
            val isVisible = flightDataManager.isCurrentModeVisible(currentMode)
            val visibleModes = flightDataManager.visibleModes
            val fallbackMode = flightDataManager.getFallbackMode()

            Log.d(TAG, "🔍 VISIBILITY CHECK: currentMode=$currentMode, isVisible=$isVisible")
            Log.d(TAG, "🔍 VISIBLE MODES: $visibleModes")
            Log.d(TAG, "🔍 FALLBACK MODE: $fallbackMode")

            if (!isVisible) {
                mapState.currentMode = fallbackMode
                Log.d(TAG, "🔄 Current mode $currentMode not visible, switched to fallback mode $fallbackMode")
                Log.d(TAG, "🔄 NEW MODE STATE: ${mapState.currentMode}")
            } else {
                Log.d(TAG, "✅ Current mode $currentMode is visible - no change needed")
            }
        }

        // Load template for current profile and flight mode
        // ✅ NEW: Added templateVersion dependency to reload when cards are toggled in Flight Data screen
        LaunchedEffect(
            currentFlightModeSelection,
            flightDataManager.allTemplates,
            uiState.activeProfile,
            safeContainerSize,
            flightDataManager.templateVersion // ✅ NEW: Reactive template version tracking
        ) {
            Log.d(TAG, "🔄 Template loading LaunchedEffect triggered:")
            Log.d(TAG, "  - Flight mode: ${currentFlightModeSelection.displayName}")
            Log.d(TAG, "  - Container size: $safeContainerSize")
            Log.d(TAG, "  - Profile: ${uiState.activeProfile?.name}")
            Log.d(TAG, "  - Templates: ${flightDataManager.allTemplates.size}")
            Log.d(TAG, "  - Template version: ${flightDataManager.templateVersion}") // ✅ NEW: Log version

            flightDataManager.loadTemplateForProfile(
                currentFlightModeSelection,
                uiState.activeProfile?.id,
                uiState.activeProfile?.name,
                safeContainerSize,
                flightViewModel,
                density
            )
        }

        // Synchronize FlightDataViewModel with current flight mode
        LaunchedEffect(flightDataManager.currentFlightMode) {
            flightViewModel.updateFlightMode(flightDataManager.currentFlightMode)
        }
    }

    /**
     * Flight data cards initialization and live data updates
     * ✅ REFACTORED: No longer needs cardStates parameter - ViewModel tracks internally
     */
    @Composable
    fun FlightDataAndCardEffects(
        flightViewModel: FlightDataViewModel,
        cardPreferences: CardPreferences,
        safeContainerSize: IntSize,
        density: androidx.compose.ui.unit.Density,
        flightDataManager: FlightDataManager,
        locationManager: LocationManager,
        orientationData: OrientationData
    ) {
        // Initialize card preferences and start independent clock timer
        LaunchedEffect(Unit) {
            flightViewModel.initializeCardPreferences(cardPreferences)
            flightViewModel.startIndependentClockTimer()
            Log.d(TAG, "⏰ Started independent clock timer for time card")
        }

        // ✅ REMOVED: Redundant initializeCards() fallback
        // Template loading in ProfileAndConfigurationEffects already handles this correctly
        // - If user selects 0 cards → displays 0 cards
        // - If user selects X cards → displays X cards
        // - First launch → loadTemplateForProfile() provides fallback

        // Load all flight data templates
        LaunchedEffect(Unit) {
            flightDataManager.loadAllTemplates()
        }

        // Update cards and location with live flight data
        // Collect every emission so vertical-speed-only changes update immediately
        LaunchedEffect(Unit) {
            snapshotFlow { flightDataManager.liveFlightData }
                .filterNotNull()
                .collectLatest { liveData ->
                    flightViewModel.updateCardsWithLiveData(liveData)
                    locationManager.updateLocationFromFlightData(
                        liveData,
                        orientationData.mode,
                        orientationData.bearing
                    )
                }
        }

        LaunchedEffect(flightDataManager.unitsPreferences) {
            flightViewModel.updateUnitsPreferences(flightDataManager.unitsPreferences)
        }


        // Continuous update loop for ALL modes - ensures smooth real-time tracking
        // The icon stays at actual GPS position and only moves when pilot moves
        LaunchedEffect(orientationData.mode) {
            Log.d(TAG, "🔄 Starting continuous ${orientationData.mode} update loop")
            while (isActive) {
                    flightDataManager.liveFlightData?.let { liveData ->
                        // Update position even without perfect GPS fix for smooth tracking
                        // Just need valid coordinates (not 0,0)
                        if (liveData.latitude != 0.0 && liveData.longitude != 0.0) {
                            locationManager.updateLocationFromFlightData(
                                liveData,
                                orientationData.mode,
                                orientationData.bearing
                            )
                        }
                    }
                delay(100) // 10Hz update rate for butter-smooth tracking
            }
            Log.d(TAG, "⏹ Stopped ${orientationData.mode} update loop")
        }
    }

    /**
     * Map style loading and configuration effects
     */
    @Composable
    fun MapStyleAndConfigurationEffects(
        initialMapStyle: String,
        mapState: MapScreenState,
        onMapStyleSelected: (String) -> Unit
    ) {
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            try {
                val config = loadConfig(context)
                val savedStyle = config?.optJSONObject("app")?.optString("mapStyle") ?: initialMapStyle
                mapState.mapStyleUrl = getMapStyleUrl(savedStyle)
                onMapStyleSelected(savedStyle)
                Log.d(TAG, "🎨 Map style loaded: $savedStyle")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading map style configuration: ${e.message}")
            }
        }
    }

    /**
     * Debug and test effects (compass logging, variometer animation)
     */
    @Composable
    fun TestAndDebugEffects(
        orientationData: OrientationData
    ) {
        LaunchedEffect(orientationData.mode, orientationData.isValid) {
            Log.d(TAG, "Compass rendering: mode=${orientationData.mode}, " +
                      "bearing=${orientationData.bearing.toInt()}°, " +
                      "valid=${orientationData.isValid}, " +
                      "timestamp=${orientationData.timestamp}")
        }
    }

    /**
     * Combined effects function for easy integration
     * Calls all individual effect groups with proper dependencies
     */
    // ✅ REFACTORED: Removed cardStates parameter - no longer needed
    @Composable
    fun AllMapEffects(
        locationManager: LocationManager,
        locationPermissionLauncher: ActivityResultLauncher<Array<String>>,
        currentLocation: GPSData?,
        orientationData: OrientationData,
        uiState: ProfileUiState,
        flightDataManager: FlightDataManager,
        mapState: MapScreenState,
        currentFlightModeSelection: FlightModeSelection,
        safeContainerSize: IntSize,
        flightViewModel: FlightDataViewModel,
        cardPreferences: CardPreferences,
        initialMapStyle: String,
        onMapStyleSelected: (String) -> Unit
    ) {
        val density = LocalDensity.current

        LocationAndPermissionEffects(
            locationManager = locationManager,
            locationPermissionLauncher = locationPermissionLauncher,
            currentLocation = currentLocation,
            orientationData = orientationData
        )

        ProfileAndConfigurationEffects(
            uiState = uiState,
            flightDataManager = flightDataManager,
            mapState = mapState,
            currentFlightModeSelection = currentFlightModeSelection,
            safeContainerSize = safeContainerSize,
            flightViewModel = flightViewModel,
            density = density
        )

        // ✅ REFACTORED: No longer pass cardStates
        FlightDataAndCardEffects(
            flightViewModel = flightViewModel,
            cardPreferences = cardPreferences,
            safeContainerSize = safeContainerSize,
            density = density,
            flightDataManager = flightDataManager,
            locationManager = locationManager,
            orientationData = orientationData
        )

        MapStyleAndConfigurationEffects(
            initialMapStyle = initialMapStyle,
            mapState = mapState,
            onMapStyleSelected = onMapStyleSelected
        )

        TestAndDebugEffects(orientationData = orientationData)
    }
}

