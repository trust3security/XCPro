package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.MapCameraEffects
import com.example.xcpro.map.MapCameraRuntimePort
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun MapScreenCameraRuntimeEffects(
    cameraManager: MapCameraRuntimePort,
    hotPathBindings: MapScreenHotPathBindings,
    replaySession: SessionState
) {
    val orientationData by hotPathBindings.orientationFlow.collectAsStateWithLifecycle()
    MapCameraEffects.AllCameraEffects(
        cameraManager = cameraManager,
        bearing = orientationData.bearing,
        orientationMode = orientationData.mode,
        bearingSource = orientationData.bearingSource,
        replayPlaying = replaySession.status == SessionStatus.PLAYING
    )
}

@Composable
internal fun MapTrafficOverlayRuntimeEffects(
    overlayManager: MapOverlayManager,
    traffic: MapTrafficUiBinding,
    currentLocation: StateFlow<MapLocationUiModel?>,
    unitsPreferences: UnitsPreferences
) {
    val locationForUi by currentLocation.collectAsStateWithLifecycle()
    val trafficOverlayPort = remember(overlayManager) {
        createTrafficOverlayRenderPort(overlayManager)
    }
    val trafficOverlayRenderState = rememberTrafficOverlayRenderState(
        traffic = traffic,
        locationForUi = locationForUi,
        unitsPreferences = unitsPreferences
    )
    MapTrafficOverlayEffects(
        port = trafficOverlayPort,
        renderState = trafficOverlayRenderState
    )
}
