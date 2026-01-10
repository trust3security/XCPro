package com.example.xcpro.map.ui.effects

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.loadConfig
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.sensors.GPSData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import androidx.compose.runtime.rememberUpdatedState

object MapComposeEffects {

    @Composable
    fun LocationAndPermissionEffects(
        locationManager: LocationManager,
        locationPermissionLauncher: ActivityResultLauncher<Array<String>>,
        currentLocation: GPSData?,
        orientationData: OrientationData,
        suppressLiveGps: Boolean = false,
        allowSensorStart: Boolean = true
    ) {
        LaunchedEffect(allowSensorStart) {
            if (allowSensorStart) {
                locationManager.checkAndRequestLocationPermissions(locationPermissionLauncher)
            }
        }

        LaunchedEffect(currentLocation, suppressLiveGps, orientationData.mode, orientationData.bearing) {
            if (!suppressLiveGps) {
                currentLocation?.let { location ->
                    locationManager.updateLocationFromGPS(
                        location,
                        orientationData
                    )
                }
            }
        }
    }

    @Composable
    fun ProfileAndConfigurationEffects(
        uiState: ProfileUiState,
        flightDataManager: FlightDataManager,
        currentMode: FlightMode,
        onModeChange: (FlightMode) -> Unit,
        currentFlightModeSelection: FlightModeSelection,
        safeContainerSize: IntSize,
        flightViewModel: FlightDataViewModel,
        density: androidx.compose.ui.unit.Density,
        profileModeCards: Map<String, Map<FlightModeSelection, List<String>>>,
        profileModeTemplates: Map<String, Map<FlightModeSelection, String>>,
        activeTemplateId: String?
    ) {
        LaunchedEffect(uiState.activeProfile?.id) {
            flightDataManager.loadVisibleModes(uiState.activeProfile?.id, uiState.activeProfile?.name)

            if (!flightDataManager.isCurrentModeVisible(currentMode)) {
                val fallback = flightDataManager.getFallbackMode()
                onModeChange(fallback)
            }
        }

        LaunchedEffect(
            currentFlightModeSelection,
            uiState.activeProfile?.id,
            safeContainerSize,
            profileModeCards,
            profileModeTemplates
        ) {
            if (safeContainerSize == IntSize.Zero) {
                return@LaunchedEffect
            }

            flightDataManager.updateFlightMode(currentFlightModeSelection)
            flightViewModel.prepareCardsForProfile(
                profileId = uiState.activeProfile?.id,
                flightMode = currentFlightModeSelection,
                containerSize = safeContainerSize,
                density = density
            )
        }
    }

    @Composable
    fun FlightDataAndCardEffects(
        flightViewModel: FlightDataViewModel,
        cardPreferences: CardPreferences,
        safeContainerSize: IntSize,
        density: androidx.compose.ui.unit.Density,
        flightDataManager: FlightDataManager,
        locationManager: LocationManager,
        orientationData: OrientationData,
        orientationManager: MapOrientationManager,
        cardsReady: Boolean,
        suppressLiveGps: Boolean
    ) {
        val cardsReadyState = rememberUpdatedState(cardsReady)

        LaunchedEffect(Unit) {
            flightViewModel.initializeCardPreferences(cardPreferences)
            flightViewModel.startIndependentClockTimer()
        }

        LaunchedEffect(cardsReady) {
            if (cardsReady) {
                flightDataManager.consumeBufferedCardSample()?.let { buffered ->
                    flightViewModel.updateCardsWithLiveData(buffered)
                }
            }
        }

        LaunchedEffect(Unit) {
            flightDataManager.cardFlightDataFlow.collectLatest { displaySample ->
                if (displaySample != null && cardsReadyState.value) {
                    flightViewModel.updateCardsWithLiveData(displaySample)
                }
            }
        }

        LaunchedEffect(Unit) {
            flightDataManager.liveFlightDataFlow.collectLatest { liveData ->
                if (liveData != null) {
                    orientationManager.updateFromFlightData(liveData)
                    if (suppressLiveGps) {
                        // Replay/IGC: use flight data for map updates when GPS is suppressed.
                        locationManager.updateLocationFromFlightData(
                            liveData,
                            orientationData
                        )
                    }
                }
            }
        }

        LaunchedEffect(flightDataManager.unitsPreferences) {
            flightViewModel.updateUnitsPreferences(flightDataManager.unitsPreferences)
        }
    }

    @Composable
    fun MapStyleAndConfigurationEffects(
        initialMapStyle: String,
        onStyleResolved: (String) -> Unit
    ) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            runCatching { loadConfig(context) }
                .mapCatching { config ->
                    config?.optJSONObject("app")?.optString("mapStyle")
                }
                .onSuccess { stored ->
                    val style = stored ?: initialMapStyle
                    onStyleResolved(style)
                }
                .onFailure { _ -> }
        }
    }

    @Composable
    fun TestAndDebugEffects(
        orientationData: OrientationData
    ) {
        LaunchedEffect(orientationData.mode, orientationData.isValid) { }
    }

    @Composable
    fun AllMapEffects(
        locationManager: LocationManager,
        locationPermissionLauncher: ActivityResultLauncher<Array<String>>,
        currentLocation: GPSData?,
        orientationData: OrientationData,
        orientationManager: MapOrientationManager,
        uiState: ProfileUiState,
        flightDataManager: FlightDataManager,
        currentMode: FlightMode,
        onModeChange: (FlightMode) -> Unit,
        currentFlightModeSelection: FlightModeSelection,
        safeContainerSize: IntSize,
        flightViewModel: FlightDataViewModel,
        cardPreferences: CardPreferences,
        profileModeCards: Map<String, Map<FlightModeSelection, List<String>>>,
        profileModeTemplates: Map<String, Map<FlightModeSelection, String>>,
        activeTemplateId: String?,
        initialMapStyle: String,
        onMapStyleResolved: (String) -> Unit,
        cardsReady: Boolean,
        suppressLiveGps: Boolean = false,
        allowSensorStart: Boolean = true
    ) {
        val density = LocalDensity.current

        LocationAndPermissionEffects(
            locationManager = locationManager,
            locationPermissionLauncher = locationPermissionLauncher,
            currentLocation = currentLocation,
            orientationData = orientationData,
            suppressLiveGps = suppressLiveGps,
            allowSensorStart = allowSensorStart
        )

        ProfileAndConfigurationEffects(
            uiState = uiState,
            flightDataManager = flightDataManager,
            currentMode = currentMode,
            onModeChange = onModeChange,
            currentFlightModeSelection = currentFlightModeSelection,
            safeContainerSize = safeContainerSize,
            flightViewModel = flightViewModel,
            density = density,
            profileModeCards = profileModeCards,
            profileModeTemplates = profileModeTemplates,
            activeTemplateId = activeTemplateId
        )

        FlightDataAndCardEffects(
            flightViewModel = flightViewModel,
            cardPreferences = cardPreferences,
            safeContainerSize = safeContainerSize,
            density = density,
            flightDataManager = flightDataManager,
            locationManager = locationManager,
            orientationData = orientationData,
            orientationManager = orientationManager,
            cardsReady = cardsReady,
            suppressLiveGps = suppressLiveGps
        )

        MapStyleAndConfigurationEffects(
            initialMapStyle = initialMapStyle,
            onStyleResolved = onMapStyleResolved
        )

        TestAndDebugEffects(orientationData = orientationData)
    }
}
