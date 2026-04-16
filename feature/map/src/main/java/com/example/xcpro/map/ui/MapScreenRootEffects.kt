package com.example.xcpro.map.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.airspace.AirspaceUiState
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.MapLifecycleEffects
import com.example.xcpro.map.MapLocationFlightDataRuntimeBinder
import com.example.xcpro.map.MapOrientationFlightDataRuntimeBinder
import com.example.xcpro.map.MapOrientationFlightDataRuntimePort
import com.example.xcpro.map.MapLifecycleRuntimePort
import com.example.xcpro.map.MapLocationPermissionRequester
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.ui.effects.MapComposeEffects
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.replay.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
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
    lifecycleManager: MapLifecycleRuntimePort,
    runtimeController: MapRuntimeController,
    locationManager: MapLocationRuntimePort,
    locationPermissionRequester: MapLocationPermissionRequester,
    currentLocationFlow: StateFlow<MapLocationUiModel?>,
    orientationFlow: StateFlow<OrientationData>,
    liveFlightDataFlow: StateFlow<RealTimeFlightData?>,
    orientationFlightDataRuntimePort: MapOrientationFlightDataRuntimePort,
    profileUiState: ProfileUiState,
    currentFlightModeSelection: FlightModeSelection,
    safeContainerSize: IntSize,
    flightCardsBinding: MapScreenFlightCardsBinding,
    replaySessionState: SessionState,
    useRenderFrameSync: Boolean,
    suppressLiveGps: Boolean,
    allowSensorStart: Boolean,
    renderLocalOwnship: Boolean
) {
    val shouldForwardReplayLocationUpdateState =
        rememberUpdatedState(suppressLiveGps && renderLocalOwnship)
    val orientationFlightDataRuntimeBinder = remember(
        liveFlightDataFlow,
        orientationFlightDataRuntimePort
    ) {
        MapOrientationFlightDataRuntimeBinder(
            liveFlightDataFlow = liveFlightDataFlow,
            orientationRuntimePort = orientationFlightDataRuntimePort
        )
    }
    val locationFlightDataRuntimeBinder = remember(
        liveFlightDataFlow,
        locationManager,
        orientationFlow
    ) {
        MapLocationFlightDataRuntimeBinder(
            liveFlightDataFlow = liveFlightDataFlow,
            locationManager = locationManager,
            orientationProvider = { orientationFlow.value },
            shouldForwardReplayLocationUpdate = { shouldForwardReplayLocationUpdateState.value }
        )
    }
    LaunchedEffect(orientationFlightDataRuntimeBinder) {
        orientationFlightDataRuntimeBinder.collectLatestFlightData()
    }
    LaunchedEffect(locationFlightDataRuntimeBinder) {
        locationFlightDataRuntimeBinder.collectLatestFlightData()
    }
    MapComposeEffects.AllMapEffects(
        locationManager = locationManager,
        locationPermissionRequester = locationPermissionRequester,
        currentLocationFlow = currentLocationFlow,
        orientationFlow = orientationFlow,
        uiState = profileUiState,
        currentFlightModeSelection = currentFlightModeSelection,
        safeContainerSize = safeContainerSize,
        flightViewModel = flightCardsBinding.flightViewModel,
        profileModeCards = flightCardsBinding.profileModeCards,
        profileModeTemplates = flightCardsBinding.profileModeTemplates,
        activeTemplateId = flightCardsBinding.activeTemplateId,
        replaySessionState = replaySessionState,
        useRenderFrameSync = useRenderFrameSync,
        suppressLiveGps = suppressLiveGps,
        allowSensorStart = allowSensorStart,
        renderLocalOwnship = renderLocalOwnship
    )
    MapLifecycleEffects.LifecycleObserverEffect(lifecycleManager)
    DisposableEffect(lifecycleManager, runtimeController) {
        onDispose {
            runtimeController.clearMap()
            lifecycleManager.cleanup()
        }
    }
}
