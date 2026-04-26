package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.trust3.xcpro.map.DisplayClock
import com.trust3.xcpro.map.DisplayPoseSnapshot
import com.trust3.xcpro.map.MapLocationRuntimePort
import com.trust3.xcpro.map.trail.SnailTrailManager
import com.trust3.xcpro.map.trail.TrailLength
import com.trust3.xcpro.map.trail.TrailSettings
import com.trust3.xcpro.map.trail.domain.TrailTimeBase
import com.trust3.xcpro.map.trail.domain.TrailUpdateResult
import org.maplibre.android.geometry.LatLng

internal data class TrailDisplayPoseSeed(
    val displayLocation: LatLng?,
    val displayTimeMillis: Long?,
    val displayTimeBase: TrailTimeBase?
)

internal fun resolveTrailDisplayPoseSeed(
    isReplay: Boolean,
    snapshot: DisplayPoseSnapshot?
): TrailDisplayPoseSeed {
    val displayTimeBase = resolveDisplayPoseTrailTimeBase(snapshot)
    val modeMatches = if (isReplay) {
        displayTimeBase == TrailTimeBase.REPLAY_IGC
    } else {
        displayTimeBase == TrailTimeBase.LIVE_MONOTONIC || displayTimeBase == TrailTimeBase.LIVE_WALL
    }
    if (snapshot == null || displayTimeBase == null || !modeMatches) {
        return TrailDisplayPoseSeed(
            displayLocation = null,
            displayTimeMillis = null,
            displayTimeBase = null
        )
    }
    return TrailDisplayPoseSeed(
        displayLocation = snapshot.location,
        displayTimeMillis = snapshot.timestampMs,
        displayTimeBase = displayTimeBase
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
            snapshot = locationManager.getDisplayPoseSnapshot()
        )
        snailTrailManager.updateFromTrailUpdate(
            update = trailUpdateResult,
            settings = trailSettings,
            currentZoom = currentZoom,
            displayLocation = displayPoseSeed.displayLocation,
            displayTimeMillis = displayPoseSeed.displayTimeMillis,
            displayTimeBase = displayPoseSeed.displayTimeBase
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
