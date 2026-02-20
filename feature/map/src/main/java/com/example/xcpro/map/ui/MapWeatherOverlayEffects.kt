package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.weather.rain.WeatherOverlayViewModel

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
