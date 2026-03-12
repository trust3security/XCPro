package com.example.xcpro.map.ui

import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.airspace.AirspaceUiState
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.MapLifecycleEffects
import com.example.xcpro.map.MapLifecycleManager
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.ui.effects.MapComposeEffects
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.replay.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun MapScreenBackHandler(
    drawerState: androidx.compose.material3.DrawerState,
    modalManager: MapModalManager,
    taskScreenManager: MapTaskScreenManager,
    isTaskPanelVisible: Boolean,
    navController: NavHostController,
    coroutineScope: CoroutineScope
) {
    BackHandler(enabled = drawerState.isOpen || modalManager.isAnyModalOpen() || isTaskPanelVisible) {
        when {
            drawerState.isOpen -> {
                coroutineScope.launch { drawerState.close() }
            }
            modalManager.handleBackGesture() -> Unit
            taskScreenManager.handleBackGesture() -> Unit
            else -> navController.popBackStack()
        }
    }
}

@Composable
internal fun MapAirspaceOverlayEffect(
    mapState: MapScreenState,
    airspaceState: AirspaceUiState,
    overlayManager: MapOverlayManager
) {
    LaunchedEffect(mapState.mapLibreMap, airspaceState.enabledFiles, airspaceState.classStates) {
        overlayManager.refreshAirspace(mapState.mapLibreMap)
    }
}

@Composable
internal fun MapVisibilityLifecycleEffect(mapViewModel: MapScreenViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapViewModel) {
        mapViewModel.setMapVisible(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> mapViewModel.setMapVisible(true)
                Lifecycle.Event.ON_STOP -> mapViewModel.setMapVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewModel.setMapVisible(false)
        }
    }
}

@Composable
internal fun MapScreenComposeAndLifecycleEffects(
    lifecycleManager: MapLifecycleManager,
    runtimeController: MapRuntimeController,
    locationManager: LocationManager,
    locationPermissionLauncher: ActivityResultLauncher<Array<String>>,
    currentLocation: MapLocationUiModel?,
    orientationData: OrientationData,
    orientationManager: MapOrientationManager,
    profileUiState: ProfileUiState,
    flightDataManager: FlightDataManager,
    currentMode: FlightMode,
    onModeChange: (FlightMode) -> Unit,
    currentFlightModeSelection: FlightModeSelection,
    safeContainerSize: IntSize,
    flightCardsBinding: MapScreenFlightCardsBinding,
    initialMapStyle: String,
    onMapStyleResolved: (String) -> Unit,
    replaySessionState: SessionState,
    suppressLiveGps: Boolean,
    allowSensorStart: Boolean
) {
    MapComposeEffects.AllMapEffects(
        locationManager = locationManager,
        locationPermissionLauncher = locationPermissionLauncher,
        currentLocation = currentLocation,
        orientationData = orientationData,
        orientationManager = orientationManager,
        uiState = profileUiState,
        flightDataManager = flightDataManager,
        currentMode = currentMode,
        onModeChange = onModeChange,
        currentFlightModeSelection = currentFlightModeSelection,
        safeContainerSize = safeContainerSize,
        flightViewModel = flightCardsBinding.flightViewModel,
        profileModeCards = flightCardsBinding.profileModeCards,
        profileModeTemplates = flightCardsBinding.profileModeTemplates,
        activeTemplateId = flightCardsBinding.activeTemplateId,
        initialMapStyle = initialMapStyle,
        onMapStyleResolved = onMapStyleResolved,
        replaySessionState = replaySessionState,
        suppressLiveGps = suppressLiveGps,
        allowSensorStart = allowSensorStart
    )
    MapLifecycleEffects.LifecycleObserverEffect(lifecycleManager)
    DisposableEffect(lifecycleManager, runtimeController) {
        onDispose {
            runtimeController.clearMap()
            lifecycleManager.cleanup()
        }
    }
}
