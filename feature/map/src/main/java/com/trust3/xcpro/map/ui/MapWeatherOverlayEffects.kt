package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.map.MapOverlayManager
import com.trust3.xcpro.weather.rain.WeatherOverlayViewModel

@Composable
internal fun MapWeatherOverlayEffects(
    overlayManager: MapOverlayManager
) {
    val rainViewModel: WeatherOverlayViewModel = hiltViewModel()
    val rainOverlayState = rainViewModel.overlayState.collectAsStateWithLifecycle().value

    LaunchedEffect(
        rainOverlayState.enabled,
        rainOverlayState.opacity,
        rainOverlayState.selectedFrame,
        rainOverlayState.transitionDurationMs,
        rainOverlayState.metadataStatus,
        rainOverlayState.metadataStale
    ) {
        overlayManager.setWeatherRainOverlay(
            enabled = rainOverlayState.enabled,
            frameSelection = rainOverlayState.selectedFrame,
            opacity = rainOverlayState.opacity,
            transitionDurationMs = rainOverlayState.transitionDurationMs,
            statusCode = rainOverlayState.metadataStatus,
            stale = rainOverlayState.metadataStale
        )
    }
}
