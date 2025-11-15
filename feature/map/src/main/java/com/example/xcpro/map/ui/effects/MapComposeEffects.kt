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
import com.example.xcpro.loadConfig
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.screens.overlays.getMapStyleUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState

object MapComposeEffects {

    @Composable
    fun LocationAndPermissionEffects(
        locationManager: LocationManager,
        locationPermissionLauncher: ActivityResultLauncher<Array<String>>,
        currentLocation: GPSData?,
        orientationData: OrientationData
    ) {
        LaunchedEffect(Unit) {
            locationManager.checkAndRequestLocationPermissions(locationPermissionLauncher)
        }

        LaunchedEffect(currentLocation, orientationData.mode, orientationData.bearing) {
            currentLocation?.let { location ->
                locationManager.updateLocationFromGPS(
                    location,
                    orientationData.mode,
                    orientationData.bearing
                )
            }
        }
    }

    @Composable
    fun ProfileAndConfigurationEffects(
        uiState: ProfileUiState,
        flightDataManager: FlightDataManager,
        mapState: MapScreenState,
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

            val currentMode = mapState.currentMode
            if (!flightDataManager.isCurrentModeVisible(currentMode)) {
                val fallback = flightDataManager.getFallbackMode()
                mapState.currentMode = fallback
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
        cardsReady: Boolean
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
            snapshotFlow { flightDataManager.liveFlightData }
                .collectLatest { liveData ->
                    if (liveData != null) {
                        if (cardsReadyState.value) {
                            flightViewModel.updateCardsWithLiveData(liveData)
                        }
                        orientationManager.updateFromFlightData(liveData)
                        locationManager.updateLocationFromFlightData(
                            liveData,
                            orientationData.mode,
                            orientationData.bearing
                        )
                    }
                }
        }

        LaunchedEffect(flightDataManager.unitsPreferences) {
            flightViewModel.updateUnitsPreferences(flightDataManager.unitsPreferences)
        }

        LaunchedEffect(orientationData.mode) {
            while (isActive) {
                flightDataManager.liveFlightData?.let { liveData ->
                    if (liveData.latitude != 0.0 && liveData.longitude != 0.0) {
                        locationManager.updateLocationFromFlightData(
                            liveData,
                            orientationData.mode,
                            orientationData.bearing
                        )
                    }
                }
                delay(100)
            }
        }
    }

    @Composable
    fun MapStyleAndConfigurationEffects(
        initialMapStyle: String,
        mapState: MapScreenState,
        onMapStyleSelected: (String) -> Unit
    ) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            runCatching { loadConfig(context) }
                .mapCatching { config ->
                    config?.optJSONObject("app")?.optString("mapStyle")
                }
                .onSuccess { stored ->
                    val style = stored ?: initialMapStyle
                    mapState.mapStyleUrl = getMapStyleUrl(style)
                    onMapStyleSelected(style)
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
        mapState: MapScreenState,
        currentFlightModeSelection: FlightModeSelection,
        safeContainerSize: IntSize,
        flightViewModel: FlightDataViewModel,
        cardPreferences: CardPreferences,
        profileModeCards: Map<String, Map<FlightModeSelection, List<String>>>,
        profileModeTemplates: Map<String, Map<FlightModeSelection, String>>,
        activeTemplateId: String?,
        initialMapStyle: String,
        onMapStyleSelected: (String) -> Unit,
        cardsReady: Boolean
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
            cardsReady = cardsReady
        )

        MapStyleAndConfigurationEffects(
            initialMapStyle = initialMapStyle,
            mapState = mapState,
            onMapStyleSelected = onMapStyleSelected
        )

        TestAndDebugEffects(orientationData = orientationData)
    }
}

