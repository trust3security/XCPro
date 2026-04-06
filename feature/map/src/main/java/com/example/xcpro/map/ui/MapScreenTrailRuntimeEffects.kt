package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.example.xcpro.map.DisplayClock
import com.example.xcpro.map.DisplayPoseSnapshot
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.domain.TrailTimeBase
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import org.maplibre.android.geometry.LatLng

internal data class TrailDisplayPoseSeed(
    val displayLocation: LatLng?,
    val displayTimeMillis: Long?
)

internal fun resolveTrailDisplayPoseSeed(
    isReplay: Boolean,
    snapshot: DisplayPoseSnapshot?
): TrailDisplayPoseSeed = if (isReplay && snapshot?.timeBase == DisplayClock.TimeBase.REPLAY) {
    TrailDisplayPoseSeed(
        displayLocation = snapshot.location,
        displayTimeMillis = snapshot.timestampMs
    )
} else {
    TrailDisplayPoseSeed(
        displayLocation = null,
        displayTimeMillis = null
    )
}

internal fun shouldListenForDisplayPose(
    renderLocalOwnship: Boolean,
    trailEnabled: Boolean
): Boolean = renderLocalOwnship && trailEnabled

internal fun resolveDisplayPoseTrailTimeBase(
    snapshot: DisplayPoseSnapshot?
): TrailTimeBase? = when (snapshot?.timeBase) {
    DisplayClock.TimeBase.MONOTONIC -> TrailTimeBase.LIVE_MONOTONIC
    DisplayClock.TimeBase.WALL -> TrailTimeBase.LIVE_WALL
    DisplayClock.TimeBase.REPLAY -> TrailTimeBase.REPLAY_IGC
    null -> null
}

internal fun forwardDisplayPoseSnapshot(
    snailTrailManager: SnailTrailManager,
    snapshot: DisplayPoseSnapshot?
) {
    snailTrailManager.updateDisplayPose(
        displayLocation = snapshot?.location,
        displayTimeMillis = snapshot?.timestampMs,
        displayTimeBase = resolveDisplayPoseTrailTimeBase(snapshot),
        frameId = snapshot?.frameId
    )
}

@Composable
internal fun MapScreenTrailRuntimeEffects(
    snailTrailManager: SnailTrailManager,
    locationManager: MapLocationRuntimePort,
    trailUpdateResult: TrailUpdateResult?,
    trailSettings: TrailSettings,
    currentZoom: Float,
    renderLocalOwnship: Boolean
) {
    LaunchedEffect(
        trailUpdateResult,
        trailSettings
    ) {
        val isReplay = trailUpdateResult?.renderState?.isReplay == true
        val displayPoseSeed = resolveTrailDisplayPoseSeed(
            isReplay = isReplay,
            snapshot = if (isReplay) locationManager.getDisplayPoseSnapshot() else null
        )
        snailTrailManager.updateFromTrailUpdate(
            update = trailUpdateResult,
            settings = trailSettings,
            currentZoom = currentZoom,
            displayLocation = displayPoseSeed.displayLocation,
            displayTimeMillis = displayPoseSeed.displayTimeMillis
        )
    }

    LaunchedEffect(currentZoom) {
        snailTrailManager.onZoomChanged(currentZoom)
    }

    DisposableEffect(locationManager, snailTrailManager, renderLocalOwnship, trailSettings.length) {
        if (!shouldListenForDisplayPose(renderLocalOwnship, trailSettings.length != TrailLength.OFF)) {
            locationManager.setDisplayPoseFrameListener(null)
            onDispose { locationManager.setDisplayPoseFrameListener(null) }
        } else {
            val listener: (DisplayPoseSnapshot) -> Unit = { snapshot ->
                forwardDisplayPoseSnapshot(
                    snailTrailManager = snailTrailManager,
                    snapshot = snapshot
                )
            }
            locationManager.setDisplayPoseFrameListener(listener)
            onDispose { locationManager.setDisplayPoseFrameListener(null) }
        }
    }
}
