// Role: Coordinate trail rendering based on processed trail updates.
// Invariants: Rendering occurs only when a map style is ready.
package com.example.xcpro.map.trail

import android.content.Context
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.trail.domain.TrailUpdateResult
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class SnailTrailManager(
    private val context: Context,
    private val mapState: MapScreenState,
    private val featureFlags: MapFeatureFlags
) {
    private var lastSettings: TrailSettings = TrailSettings()
    private var lastContext: RenderContext? = null
    private var lastIsReplay: Boolean? = null
    private var lastPoints: List<TrailPoint> = emptyList()
    private var lastRenderPoseTimeMs: Long? = null
    private var lastRenderPoseLocation: LatLng? = null
    private var lastRawTailPoint: TrailPoint? = null
    private var lastRenderPoseFrameId: Long? = null

    fun initialize(map: MapLibreMap) {
        val overlay = SnailTrailOverlay(context, map, mapState.mapView, featureFlags)
        overlay.initialize()
        mapState.snailTrailOverlay = overlay
        mapState.blueLocationOverlay?.bringToFront()
        renderLast()
    }

    fun onMapStyleChanged(map: MapLibreMap?) {
        mapState.snailTrailOverlay?.cleanup()
        mapState.snailTrailOverlay = null
        map?.let { initialize(it) }
        renderLast()
    }

    internal fun updateFromTrailUpdate(
        update: TrailUpdateResult?,
        settings: TrailSettings,
        currentZoom: Float,
        displayLocation: LatLng? = null,
        displayTimeMillis: Long? = null
    ) {
        val overlay = mapState.snailTrailOverlay
        if (update == null) {
            clearRenderState(overlay)
            return
        }

        if (update.modeChanged || update.storeReset) {
            clearRenderState(overlay)
        }

        val renderState = update.renderState
        val isReplay = renderState.isReplay
        lastIsReplay = isReplay

        val settingsChanged = settings != lastSettings
        lastSettings = settings

        val baseLocation = LatLng(
            renderState.currentLocation.latitude,
            renderState.currentLocation.longitude
        )
        val overrideLocation = if (isReplay) {
            displayLocation?.takeIf {
                TrailGeo.isValidCoordinate(it.latitude, it.longitude)
            }
        } else {
            null
        }
        val renderLocation = overrideLocation ?: baseLocation
        val renderTime = if (isReplay) {
            displayTimeMillis?.takeIf { it > 0L } ?: renderState.currentTimeMillis
        } else {
            renderState.currentTimeMillis
        }

        val zoomChanged = lastContext?.currentZoom?.let { it != currentZoom } ?: false

        lastPoints = renderState.points
        lastContext = RenderContext(
            currentLocation = renderLocation,
            currentTimeMillis = renderTime,
            windSpeedMs = renderState.windSpeedMs,
            windDirectionFromDeg = renderState.windDirectionFromDeg,
            isCircling = renderState.isCircling,
            currentZoom = currentZoom
        )

        if (overlay != null && (update.sampleAdded || settingsChanged || zoomChanged || update.modeChanged || update.storeReset)) {
            render(overlay)
        }
    }

    fun updateDisplayPose(
        displayLocation: LatLng?,
        displayTimeMillis: Long?,
        frameId: Long? = null
    ) {
        if (lastIsReplay != true) return
        val overlay = mapState.snailTrailOverlay ?: return
        val context = lastContext ?: return
        val location = displayLocation
            ?.takeIf { TrailGeo.isValidCoordinate(it.latitude, it.longitude) }
            ?: return
        val time = displayTimeMillis?.takeIf { it > 0L } ?: context.currentTimeMillis
        if (time <= 0L) return
        if (time < context.currentTimeMillis) return
        if (frameId != null && lastRenderPoseFrameId == frameId) return

        val prevLocation = lastRenderPoseLocation
        val minStepMs = if (featureFlags.useRenderFrameSync && lastIsReplay == true) {
            0L
        } else {
            DISPLAY_RENDER_MIN_STEP_MS
        }
        val minDistanceM = if (featureFlags.useRenderFrameSync && lastIsReplay == true) {
            0.0
        } else {
            DISPLAY_RENDER_MIN_DISTANCE_M
        }
        val movedEnough = if (prevLocation == null) {
            true
        } else {
            TrailGeo.distanceMeters(
                prevLocation.latitude,
                prevLocation.longitude,
                location.latitude,
                location.longitude
            ) >= minDistanceM
        }
        val prevTime = lastRenderPoseTimeMs ?: 0L
        val dt = time - prevTime
        if (dt < minStepMs && !movedEnough) return

        lastRenderPoseTimeMs = time
        lastRenderPoseLocation = location
        if (frameId != null) {
            lastRenderPoseFrameId = frameId
        }
        lastContext = context.copy(
            currentLocation = location,
            currentTimeMillis = time
        )
        overlay.renderTail(
            lastPoint = lastRawTailPoint,
            settings = lastSettings,
            currentLocation = location,
            currentTimeMillis = time,
            windSpeedMs = context.windSpeedMs,
            windDirectionFromDeg = context.windDirectionFromDeg,
            isCircling = context.isCircling,
            currentZoom = context.currentZoom,
            isReplay = lastIsReplay ?: false,
            frameId = frameId
        )
    }

    fun onSettingsChanged(settings: TrailSettings) {
        lastSettings = settings
        renderLast()
    }

    fun onZoomChanged(currentZoom: Float) {
        val existing = lastContext ?: return
        lastContext = existing.copy(currentZoom = currentZoom)
        renderLast()
    }

    private fun clearRenderState(overlay: SnailTrailOverlay?) {
        overlay?.clear()
        lastIsReplay = null
        lastPoints = emptyList()
        lastContext = null
        lastRenderPoseTimeMs = null
        lastRenderPoseLocation = null
        lastRawTailPoint = null
        lastRenderPoseFrameId = null
    }

    private fun renderLast() {
        val overlay = mapState.snailTrailOverlay ?: return
        render(overlay)
    }

    private fun render(overlay: SnailTrailOverlay, frameId: Long? = null) {
        val context = lastContext ?: return
        val isReplay = lastIsReplay ?: false
        val allPoints = lastPoints
        val renderPoints = if (context.currentTimeMillis > 0L) {
            val firstTimestamp = allPoints.firstOrNull()?.timestampMillis
            if (firstTimestamp != null && context.currentTimeMillis >= firstTimestamp) {
                val filtered = allPoints.filter { it.timestampMillis <= context.currentTimeMillis }
                if (filtered.isNotEmpty()) filtered else allPoints
            } else {
                allPoints
            }
        } else {
            allPoints
        }
        lastRawTailPoint = renderPoints.lastOrNull()
        overlay.render(
            points = renderPoints,
            settings = lastSettings,
            currentLocation = context.currentLocation,
            currentTimeMillis = context.currentTimeMillis,
            windSpeedMs = context.windSpeedMs,
            windDirectionFromDeg = context.windDirectionFromDeg,
            isCircling = context.isCircling,
            currentZoom = context.currentZoom,
            isReplay = isReplay,
            frameId = frameId
        )
    }

    private data class RenderContext(
        val currentLocation: LatLng,
        val currentTimeMillis: Long,
        val windSpeedMs: Double,
        val windDirectionFromDeg: Double,
        val isCircling: Boolean,
        val currentZoom: Float
    )

    private companion object {
        private const val DISPLAY_RENDER_MIN_STEP_MS = 100L
        private const val DISPLAY_RENDER_MIN_DISTANCE_M = 0.5
    }
}
