package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.map.MapCameraEffects
import com.trust3.xcpro.map.MapCameraRuntimePort
import com.trust3.xcpro.map.MapOverlayManager
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.replay.SessionStatus

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
    inputs: MapTrafficOverlayRuntimeInputs,
    renderLocalOwnship: Boolean
) {
    val trafficOverlayPort = remember(overlayManager) {
        createTrafficOverlayRenderPort(overlayManager)
    }
    val collectorTelemetrySink = remember(overlayManager) {
        createTrafficOverlayCollectorTelemetrySink(overlayManager)
    }
    BindMapTrafficOverlayRuntime(
        port = trafficOverlayPort,
        telemetrySink = collectorTelemetrySink,
        inputs = inputs,
        renderLocalOwnship = renderLocalOwnship
    )
}
