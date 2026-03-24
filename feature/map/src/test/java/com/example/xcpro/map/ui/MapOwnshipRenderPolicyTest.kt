package com.example.xcpro.map.ui

import com.example.xcpro.livefollow.watch.LiveFollowMapRenderState
import com.example.xcpro.map.model.MapLocationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOwnshipRenderPolicyTest {

    @Test
    fun friendsFlyingRoute_hidesLocalOwnshipEvenWithoutActiveWatchSession() {
        val renderLocalOwnship = shouldRenderLocalOwnship(
            allowFlightSensorStart = false,
            watchMapRenderState = LiveFollowMapRenderState(isVisible = false)
        )

        val resolvedState = resolveMapLocalOwnshipRenderState(
            renderLocalOwnship = renderLocalOwnship,
            currentLocation = sampleLocation(),
            showRecenterButton = true,
            showReturnButton = true
        )

        assertFalse(renderLocalOwnship)
        assertNull(resolvedState.currentLocation)
        assertFalse(resolvedState.showRecenterButton)
        assertFalse(resolvedState.showReturnButton)
    }

    @Test
    fun activeWatchSession_hidesLocalOwnshipWhileFlyingSensorsRemainAllowed() {
        assertFalse(
            shouldRenderLocalOwnship(
                allowFlightSensorStart = true,
                watchMapRenderState = LiveFollowMapRenderState(isVisible = true)
            )
        )
    }

    @Test
    fun flyingMode_preservesLocalOwnshipInputsWhenNotWatching() {
        val renderLocalOwnship = shouldRenderLocalOwnship(
            allowFlightSensorStart = true,
            watchMapRenderState = LiveFollowMapRenderState(isVisible = false)
        )

        val resolvedState = resolveMapLocalOwnshipRenderState(
            renderLocalOwnship = renderLocalOwnship,
            currentLocation = sampleLocation(),
            showRecenterButton = true,
            showReturnButton = false
        )

        assertTrue(renderLocalOwnship)
        assertEquals(sampleLocation(), resolvedState.currentLocation)
        assertTrue(resolvedState.showRecenterButton)
        assertFalse(resolvedState.showReturnButton)
    }

    private fun sampleLocation(): MapLocationUiModel {
        return MapLocationUiModel(
            latitude = -33.9,
            longitude = 151.2,
            speedMs = 18.0,
            bearingDeg = 180.0,
            accuracyMeters = 4.0,
            timestampMs = 1_000L
        )
    }
}
