package com.example.xcpro.map.ui

import com.example.xcpro.livefollow.watch.LiveFollowMapRenderState
import com.example.xcpro.map.model.MapLocationUiModel

internal data class MapLocalOwnshipRenderState(
    val renderLocalOwnship: Boolean,
    val currentLocation: MapLocationUiModel?,
    val showRecenterButton: Boolean,
    val showReturnButton: Boolean
)

internal fun shouldRenderLocalOwnship(
    allowFlightSensorStart: Boolean,
    watchMapRenderState: LiveFollowMapRenderState
): Boolean {
    return allowFlightSensorStart && !watchMapRenderState.isVisible
}

internal fun resolveMapLocalOwnshipRenderState(
    renderLocalOwnship: Boolean,
    currentLocation: MapLocationUiModel?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean
): MapLocalOwnshipRenderState {
    return MapLocalOwnshipRenderState(
        renderLocalOwnship = renderLocalOwnship,
        currentLocation = currentLocation.takeIf { renderLocalOwnship },
        showRecenterButton = renderLocalOwnship && showRecenterButton,
        showReturnButton = renderLocalOwnship && showReturnButton
    )
}
