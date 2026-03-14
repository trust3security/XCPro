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
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapLocationPermissionRequester
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos

internal const val DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS = 25_000_000L
internal const val DISPLAY_POSE_MIN_FRAME_INTERVAL_REPLAY_NS = 16_666_667L
internal const val PROFILE_CARD_PREPARE_MIN_INTERVAL_MS = 500L

internal fun shouldDispatchDisplayPoseFrame(
    frameNanos: Long,
    lastDispatchNanos: Long,
    minIntervalNanos: Long
): Boolean {
    if (lastDispatchNanos <= 0L) return true
    return frameNanos - lastDispatchNanos >= minIntervalNanos
}

internal fun shouldRunComposeDisplayPoseLoop(useRenderFrameSync: Boolean): Boolean =
    !useRenderFrameSync

object MapComposeEffects {

    @Composable
    fun LocationAndPermissionEffects(
        locationManager: MapLocationRuntimePort,
        locationPermissionRequester: MapLocationPermissionRequester,
        currentLocation: MapLocationUiModel?,
        orientationData: OrientationData,
        suppressLiveGps: Boolean = false,
        allowSensorStart: Boolean = true
    ) {
        LaunchedEffect(allowSensorStart) {
            if (allowSensorStart) {
                locationManager.requestLocationPermissions(locationPermissionRequester)
            }
        }

        LaunchedEffect(currentLocation, suppressLiveGps) {
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
        orientationData: OrientationData,
        orientationManager: MapOrientationManager,
        suppressLiveGps: Boolean
    ) {
        val orientationState = rememberUpdatedState(orientationData)
        val suppressLiveGpsState = rememberUpdatedState(suppressLiveGps)

        LaunchedEffect(Unit) {
            flightDataManager.liveFlightDataFlow.collectLatest { liveData ->
                if (liveData != null) {
                    orientationManager.updateFromFlightData(
                        liveData.toOrientationFlightDataSnapshot()
                    )
                    // AI-NOTE: Avoid stale captures in a long-lived collector; replay map updates
                    // must see the latest orientation and replay/live toggle values.
                    if (suppressLiveGpsState.value) {
                        // Replay/IGC: use flight data for map updates when GPS is suppressed.
                        locationManager.updateLocationFromFlightData(
                            liveData,
                            orientationState.value
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun DisplayPoseEffects(
        locationManager: MapLocationRuntimePort,
        orientationData: OrientationData,
        replaySessionState: SessionState,
        useRenderFrameSync: Boolean
    ) {
        val replayState = rememberUpdatedState(replaySessionState)

        LaunchedEffect(replaySessionState.speedMultiplier) {
            locationManager.setReplaySpeedMultiplier(replaySessionState.speedMultiplier)
        }

        LaunchedEffect(orientationData) {
            locationManager.updateOrientation(orientationData)
        }

        if (!shouldRunComposeDisplayPoseLoop(useRenderFrameSync)) {
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
                if (!shouldDispatchDisplayPoseFrame(
                        frameNanos = frameNanos,
                        lastDispatchNanos = lastDispatchNanos,
                        minIntervalNanos = minFrameIntervalNanos
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
    fun MapStyleAndConfigurationEffects(
        initialMapStyle: String,
        onStyleResolved: (String) -> Unit
    ) {
        LaunchedEffect(initialMapStyle) {
            onStyleResolved(initialMapStyle)
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
        locationManager: MapLocationRuntimePort,
        locationPermissionRequester: MapLocationPermissionRequester,
        currentLocation: MapLocationUiModel?,
        orientationData: OrientationData,
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
        initialMapStyle: String,
        onMapStyleResolved: (String) -> Unit,
        replaySessionState: SessionState,
        useRenderFrameSync: Boolean,
        suppressLiveGps: Boolean = false,
        allowSensorStart: Boolean = true
    ) {
        val density = LocalDensity.current

        LocationAndPermissionEffects(
            locationManager = locationManager,
            locationPermissionRequester = locationPermissionRequester,
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
            flightDataManager = flightDataManager,
            locationManager = locationManager,
            orientationData = orientationData,
            orientationManager = orientationManager,
            suppressLiveGps = suppressLiveGps
        )

        DisplayPoseEffects(
            locationManager = locationManager,
            orientationData = orientationData,
            replaySessionState = replaySessionState,
            useRenderFrameSync = useRenderFrameSync
        )

        MapStyleAndConfigurationEffects(
            initialMapStyle = initialMapStyle,
            onStyleResolved = onMapStyleResolved
        )

        TestAndDebugEffects(orientationData = orientationData)
    }
}
