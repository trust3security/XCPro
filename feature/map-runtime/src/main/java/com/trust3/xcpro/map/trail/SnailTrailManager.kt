// Role: Coordinate trail rendering based on processed trail updates.
// Invariants: Rendering occurs only when a map style is ready.
package com.trust3.xcpro.map.trail

import android.content.Context
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.trail.domain.TrailTimeBase
import com.trust3.xcpro.map.trail.domain.TrailUpdateResult
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class SnailTrailManager(
    private val context: Context,
    private val runtimeState: SnailTrailRuntimeState,
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
    private val displayTrailStore = SnailTrailDisplayStore()

    fun initialize(map: MapLibreMap) {
        val overlay = SnailTrailOverlay(context, map, runtimeState.mapView, featureFlags)
        overlay.initialize()
        runtimeState.snailTrailOverlay = overlay
        runtimeState.blueLocationOverlay?.bringToFront()
        renderLast()
    }

    fun onMapStyleChanged(map: MapLibreMap?) {
        runtimeState.snailTrailOverlay?.cleanup()
        runtimeState.snailTrailOverlay = null
        map?.let { initialize(it) }
        renderLast()
    }

    fun updateFromTrailUpdate(
        update: TrailUpdateResult?,
        settings: TrailSettings,
        currentZoom: Float,
        displayLocation: LatLng? = null,
        displayTimeMillis: Long? = null
    ) {
        val overlay = runtimeState.snailTrailOverlay
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
        displayTrailStore.updateRawState(
            rawPoints = renderState.points,
            rawTimeBase = renderState.timeBase,
            isReplay = isReplay,
            trailLength = settings.length
        )
        if (overlay != null && !shouldRenderRawTrail()) {
            overlay.clearRawTrail()
        }
        if (overlay != null && (!featureFlags.useDisplayPoseSnailTrail || isReplay)) {
            displayTrailStore.clear()
            overlay.clearDisplayTrail()
        }
        lastContext = RenderContext(
            currentLocation = renderLocation,
            currentTimeMillis = renderTime,
            windSpeedMs = renderState.windSpeedMs,
            windDirectionFromDeg = renderState.windDirectionFromDeg,
            isCircling = renderState.isCircling,
            isTurnSmoothing = renderState.isTurnSmoothing,
            currentZoom = currentZoom,
            timeBase = renderState.timeBase
        )
        refreshTailAnchor(lastContext)

        if (overlay != null && (update.requiresFullRender || settingsChanged || zoomChanged)) {
            if (shouldRenderRawTrail()) {
                render(overlay)
            }
            renderDisplayTrail(overlay)
        }
    }

    fun updateDisplayPose(
        displayLocation: LatLng?,
        displayTimeMillis: Long?,
        displayTimeBase: TrailTimeBase?,
        frameId: Long? = null
    ) {
        val overlay = runtimeState.snailTrailOverlay ?: return
        val context = lastContext ?: return
        if (displayTimeBase == null || displayTimeBase != context.timeBase) return
        val location = displayLocation
            ?.takeIf { TrailGeo.isValidCoordinate(it.latitude, it.longitude) }
            ?: return
        val time = displayTimeMillis?.takeIf { it > 0L } ?: context.currentTimeMillis
        if (time <= 0L) return
        if (time < context.currentTimeMillis) return
        if (frameId != null && lastRenderPoseFrameId == frameId) return

        val prevLocation = lastRenderPoseLocation
        val minStepMs = if (featureFlags.useRenderFrameSync) {
            0L
        } else {
            DISPLAY_RENDER_MIN_STEP_MS
        }
        val minDistanceM = if (featureFlags.useRenderFrameSync) {
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

        if (featureFlags.useDisplayPoseSnailTrail && lastIsReplay != true) {
            val accepted = displayTrailStore.appendDisplayPose(
                location = TrailGeoPoint(location.latitude, location.longitude),
                timestampMillis = time,
                poseTimeBase = context.timeBase,
                trailLength = lastSettings.length,
                frameId = frameId,
                minStepMillis = minStepMs,
                minDistanceMeters = minDistanceM
            )
            if (accepted) {
                renderDisplayTrail(overlay)
            }
        } else {
            displayTrailStore.clear()
            overlay.clearDisplayTrail()
        }

        if (shouldRenderRawTrail()) {
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
        } else {
            overlay.clearTail()
        }
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
        displayTrailStore.clear()
    }

    private fun renderLast() {
        val overlay = runtimeState.snailTrailOverlay ?: return
        if (shouldRenderRawTrail()) {
            render(overlay)
        }
        renderDisplayTrail(overlay)
    }

    private fun shouldRenderRawTrail(): Boolean {
        return false
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
            isTurnSmoothing = context.isTurnSmoothing,
            currentZoom = context.currentZoom,
            isReplay = isReplay,
            frameId = frameId
        )
    }

    private fun refreshTailAnchor(context: RenderContext?) {
        if (context == null) {
            return
        }
        if (lastPoints.isEmpty()) {
            return
        }

        val renderPoints = if (context.currentTimeMillis > 0L) {
            val firstTimestamp = lastPoints.firstOrNull()?.timestampMillis
            if (firstTimestamp != null && context.currentTimeMillis >= firstTimestamp) {
                val filtered = lastPoints.filter { it.timestampMillis <= context.currentTimeMillis }
                filtered.ifEmpty { lastPoints }
            } else {
                lastPoints
            }
        } else {
            lastPoints
        }
        lastRawTailPoint = renderPoints.lastOrNull() ?: lastPoints.lastOrNull()
    }

    private fun renderDisplayTrail(overlay: SnailTrailOverlay) {
        val context = lastContext ?: return
        if (!featureFlags.useDisplayPoseSnailTrail || lastIsReplay == true) {
            displayTrailStore.clear()
            overlay.clearDisplayTrail()
            return
        }
        if (lastSettings.length == TrailLength.OFF) {
            displayTrailStore.clear()
            overlay.clearDisplayTrail()
            return
        }
        if (context.timeBase == TrailTimeBase.REPLAY_IGC) {
            displayTrailStore.clear()
            overlay.clearDisplayTrail()
            return
        }
        if (!TrailGeo.isValidCoordinate(context.currentLocation.latitude, context.currentLocation.longitude)) {
            displayTrailStore.clear()
            overlay.clearDisplayTrail()
            return
        }
        displayTrailStore.applyRetention(lastSettings.length, context.currentTimeMillis)
        overlay.renderDisplayTrail(
            points = displayTrailStore.snapshot(),
            settings = lastSettings
        )
    }

    private data class RenderContext(
        val currentLocation: LatLng,
        val currentTimeMillis: Long,
        val windSpeedMs: Double,
        val windDirectionFromDeg: Double,
        val isCircling: Boolean,
        val isTurnSmoothing: Boolean,
        val currentZoom: Float,
        val timeBase: TrailTimeBase
    )

    private companion object {
        private const val DISPLAY_RENDER_MIN_STEP_MS = 100L
        private const val DISPLAY_RENDER_MIN_DISTANCE_M = 0.5
    }
}
