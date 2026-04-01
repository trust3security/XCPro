package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.example.xcpro.map.DisplayPoseSnapshot
import com.example.xcpro.map.MapLocationRuntimePort
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import kotlinx.coroutines.isActive
import org.maplibre.android.geometry.LatLng

internal data class TrailDisplayPoseSeed(
    val displayLocation: LatLng?,
    val displayTimeMillis: Long?
)

internal enum class DisplayPoseUpdateMode {
    OFF,
    FRAME_LOOP,
    FRAME_LISTENER
}

internal fun resolveTrailDisplayPoseSeed(
    suppressLiveGps: Boolean,
    displayLocation: LatLng?,
    displayTimeMillis: Long?
): TrailDisplayPoseSeed = if (suppressLiveGps) {
    TrailDisplayPoseSeed(
        displayLocation = displayLocation,
        displayTimeMillis = displayTimeMillis
    )
} else {
    TrailDisplayPoseSeed(
        displayLocation = null,
        displayTimeMillis = null
    )
}

internal fun resolveDisplayPoseUpdateMode(
    suppressLiveGps: Boolean,
    useRenderFrameSync: Boolean
): DisplayPoseUpdateMode = when {
    !suppressLiveGps -> DisplayPoseUpdateMode.OFF
    useRenderFrameSync -> DisplayPoseUpdateMode.FRAME_LISTENER
    else -> DisplayPoseUpdateMode.FRAME_LOOP
}

internal fun forwardDisplayPoseSnapshot(
    snailTrailManager: SnailTrailManager,
    snapshot: DisplayPoseSnapshot?
) {
    snailTrailManager.updateDisplayPose(
        displayLocation = snapshot?.location,
        displayTimeMillis = snapshot?.timestampMs,
        frameId = snapshot?.frameId
    )
}

@Composable
internal fun MapScreenTrailRuntimeEffects(
    snailTrailManager: SnailTrailManager,
    locationManager: MapLocationRuntimePort,
    featureFlags: MapFeatureFlags,
    trailUpdateResult: TrailUpdateResult?,
    trailSettings: TrailSettings,
    currentZoom: Float,
    suppressLiveGps: Boolean
) {
    LaunchedEffect(
        trailUpdateResult,
        trailSettings,
        suppressLiveGps
    ) {
        val displayPoseSeed = if (suppressLiveGps) {
            resolveTrailDisplayPoseSeed(
                suppressLiveGps = true,
                displayLocation = locationManager.getDisplayPoseLocation(),
                displayTimeMillis = locationManager.getDisplayPoseTimestampMs()
            )
        } else {
            resolveTrailDisplayPoseSeed(
                suppressLiveGps = false,
                displayLocation = null,
                displayTimeMillis = null
            )
        }
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

    LaunchedEffect(suppressLiveGps) {
        if (
            resolveDisplayPoseUpdateMode(
                suppressLiveGps = suppressLiveGps,
                useRenderFrameSync = featureFlags.useRenderFrameSync
            ) != DisplayPoseUpdateMode.FRAME_LOOP
        ) {
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos { }
            forwardDisplayPoseSnapshot(
                snailTrailManager = snailTrailManager,
                snapshot = locationManager.getDisplayPoseSnapshot()
            )
        }
    }

    DisposableEffect(suppressLiveGps) {
        when (
            resolveDisplayPoseUpdateMode(
                suppressLiveGps = suppressLiveGps,
                useRenderFrameSync = featureFlags.useRenderFrameSync
            )
        ) {
            DisplayPoseUpdateMode.FRAME_LISTENER -> {
                val listener: (DisplayPoseSnapshot) -> Unit = { snapshot ->
                    forwardDisplayPoseSnapshot(
                        snailTrailManager = snailTrailManager,
                        snapshot = snapshot
                    )
                }
                locationManager.setDisplayPoseFrameListener(listener)
                onDispose { locationManager.setDisplayPoseFrameListener(null) }
            }

            DisplayPoseUpdateMode.FRAME_LOOP,
            DisplayPoseUpdateMode.OFF -> onDispose {
                locationManager.setDisplayPoseFrameListener(null)
            }
        }
    }
}
