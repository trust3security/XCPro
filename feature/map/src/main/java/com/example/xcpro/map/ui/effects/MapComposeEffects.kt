package com.example.xcpro.map.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.dfcards.dfcards.toDensityScale
import com.example.dfcards.dfcards.toIntSizePx
import com.example.xcpro.toOrientationFlightDataSnapshot
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS
import com.example.xcpro.map.DISPLAY_POSE_MIN_FRAME_INTERVAL_REPLAY_NS
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapLocationPermissionRequester
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.map.toReplayLocationFrame
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
internal const val PROFILE_CARD_PREPARE_MIN_INTERVAL_MS = 500L

internal fun shouldDispatchDisplayPoseFrame(
    frameNanos: Long,
    lastDispatchNanos: Long,
    minIntervalNanos: Long
): Boolean {
    if (lastDispatchNanos <= 0L) return true
    return frameNanos - lastDispatchNanos >= minIntervalNanos
}

internal fun shouldDispatchLiveDisplayPoseFrame(
    frameNanos: Long,
    lastDispatchNanos: Long,
    minIntervalNanos: Long,
    runtimeAllowsDispatch: Boolean
): Boolean {
    if (!shouldDispatchDisplayPoseFrame(frameNanos, lastDispatchNanos, minIntervalNanos)) {
        return false
    }
    return runtimeAllowsDispatch
}

internal fun shouldRunComposeDisplayPoseLoop(useRenderFrameSync: Boolean): Boolean =
    !useRenderFrameSync

object MapComposeEffects {

    @Composable
    fun LocationAndPermissionEffects(
        locationManager: MapLocationRuntimePort,
        locationPermissionRequester: MapLocationPermissionRequester,
        currentLocationFlow: StateFlow<MapLocationUiModel?>,
        orientationFlow: StateFlow<OrientationData>,
        suppressLiveGps: Boolean = false,
        allowSensorStart: Boolean = true,
        renderLocalOwnship: Boolean = true
    ) {
        val suppressLiveGpsState = rememberUpdatedState(suppressLiveGps)
        val renderLocalOwnshipState = rememberUpdatedState(renderLocalOwnship)

        LaunchedEffect(renderLocalOwnship) {
            locationManager.setLocalOwnshipRenderEnabled(renderLocalOwnship)
        }

        LaunchedEffect(allowSensorStart, renderLocalOwnship) {
            if (allowSensorStart && renderLocalOwnship) {
                locationManager.requestLocationPermissions(locationPermissionRequester)
            }
        }

        LaunchedEffect(locationManager, currentLocationFlow) {
            currentLocationFlow.collectLatest { currentLocation ->
                if (renderLocalOwnshipState.value && !suppressLiveGpsState.value) {
                    currentLocation?.let { location ->
                        locationManager.updateLocationFromGPS(
                            location,
                            orientationFlow.value
                        )
                    }
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
        val lastCardPrepareMonoMs = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0L) }

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
            profileModeTemplates,
            activeTemplateId
        ) {
            if (safeContainerSize == IntSize.Zero) {
                return@LaunchedEffect
            }
            val nowMonoMs = TimeBridge.nowMonoMs()
            val nextAllowedMonoMs =
                lastCardPrepareMonoMs.value + PROFILE_CARD_PREPARE_MIN_INTERVAL_MS
            if (nowMonoMs < nextAllowedMonoMs) {
                delay(nextAllowedMonoMs - nowMonoMs)
            }
            lastCardPrepareMonoMs.value = TimeBridge.nowMonoMs()

            flightDataManager.updateFlightMode(currentFlightModeSelection)
            flightViewModel.prepareCardsForProfile(
                profileId = uiState.activeProfile?.id,
                flightMode = currentFlightModeSelection,
                containerSize = safeContainerSize.toIntSizePx(),
                density = density.toDensityScale()
            )
        }
    }

    @Composable
    fun FlightDataAndCardEffects(
        flightDataManager: FlightDataManager,
        locationManager: MapLocationRuntimePort,
        orientationFlow: StateFlow<OrientationData>,
        orientationManager: MapOrientationManager,
        suppressLiveGps: Boolean,
        renderLocalOwnship: Boolean
    ) {
        val suppressLiveGpsState = rememberUpdatedState(suppressLiveGps)
        val renderLocalOwnshipState = rememberUpdatedState(renderLocalOwnship)

        LaunchedEffect(Unit) {
            flightDataManager.liveFlightDataFlow.collectLatest { liveData ->
                if (liveData != null) {
                    orientationManager.updateFromFlightData(
                        liveData.toOrientationFlightDataSnapshot()
                    )
                    // AI-NOTE: Avoid stale captures in a long-lived collector; replay map updates
                    // must see the latest orientation and replay/live toggle values.
                    if (renderLocalOwnshipState.value && suppressLiveGpsState.value) {
                        // Replay/IGC: use flight data for map updates when GPS is suppressed.
                        locationManager.updateLocationFromReplayFrame(
                            liveData.toReplayLocationFrame(),
                            orientationFlow.value
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun DisplayPoseEffects(
        locationManager: MapLocationRuntimePort,
        orientationFlow: StateFlow<OrientationData>,
        replaySessionState: SessionState,
        useRenderFrameSync: Boolean,
        renderLocalOwnship: Boolean
    ) {
        val replayState = rememberUpdatedState(replaySessionState)

        LaunchedEffect(replaySessionState.speedMultiplier) {
            locationManager.setReplaySpeedMultiplier(replaySessionState.speedMultiplier)
        }

        LaunchedEffect(locationManager, orientationFlow) {
            orientationFlow.collectLatest { orientationData ->
                locationManager.updateOrientation(orientationData)
            }
        }

        if (!renderLocalOwnship || !shouldRunComposeDisplayPoseLoop(useRenderFrameSync)) {
            return
        }

        LaunchedEffect(locationManager) {
            var lastDispatchNanos = 0L
            while (isActive) {
                val frameNanos = withFrameNanos { it }
                val minFrameIntervalNanos = if (replayState.value.selection != null) {
                    DISPLAY_POSE_MIN_FRAME_INTERVAL_REPLAY_NS
                } else {
                    DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS
                }
                if (!shouldDispatchLiveDisplayPoseFrame(
                        frameNanos = frameNanos,
                        lastDispatchNanos = lastDispatchNanos,
                        minIntervalNanos = minFrameIntervalNanos,
                        runtimeAllowsDispatch = locationManager.shouldDispatchLiveDisplayFrame()
                    )
                ) {
                    continue
                }
                lastDispatchNanos = frameNanos
                locationManager.onDisplayFrame()
            }
        }
    }

    @Composable
    fun AllMapEffects(
        locationManager: MapLocationRuntimePort,
        locationPermissionRequester: MapLocationPermissionRequester,
        currentLocationFlow: StateFlow<MapLocationUiModel?>,
        orientationFlow: StateFlow<OrientationData>,
        orientationManager: MapOrientationManager,
        uiState: ProfileUiState,
        flightDataManager: FlightDataManager,
        currentMode: FlightMode,
        onModeChange: (FlightMode) -> Unit,
        currentFlightModeSelection: FlightModeSelection,
        safeContainerSize: IntSize,
        flightViewModel: FlightDataViewModel,
        profileModeCards: Map<String, Map<FlightModeSelection, List<String>>>,
        profileModeTemplates: Map<String, Map<FlightModeSelection, String>>,
        activeTemplateId: String?,
        replaySessionState: SessionState,
        useRenderFrameSync: Boolean,
        suppressLiveGps: Boolean = false,
        allowSensorStart: Boolean = true,
        renderLocalOwnship: Boolean = true
    ) {
        val density = LocalDensity.current

        LocationAndPermissionEffects(
            locationManager = locationManager,
            locationPermissionRequester = locationPermissionRequester,
            currentLocationFlow = currentLocationFlow,
            orientationFlow = orientationFlow,
            suppressLiveGps = suppressLiveGps,
            allowSensorStart = allowSensorStart,
            renderLocalOwnship = renderLocalOwnship
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
            flightDataManager = flightDataManager,
            locationManager = locationManager,
            orientationFlow = orientationFlow,
            orientationManager = orientationManager,
            suppressLiveGps = suppressLiveGps,
            renderLocalOwnship = renderLocalOwnship
        )

        DisplayPoseEffects(
            locationManager = locationManager,
            orientationFlow = orientationFlow,
            replaySessionState = replaySessionState,
            useRenderFrameSync = useRenderFrameSync,
            renderLocalOwnship = renderLocalOwnship
        )
    }
}
