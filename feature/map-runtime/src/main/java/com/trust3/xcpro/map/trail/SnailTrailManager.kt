// Role: Coordinate trail rendering based on processed trail updates.
// Invariants: Rendering occurs only when a map style is ready.
package com.trust3.xcpro.map.trail

import android.content.Context
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.trail.domain.TrailTimeBase
import com.trust3.xcpro.map.trail.domain.TrailUpdateResult
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

class SnailTrailManager(
    private val context: Context,
    private val runtimeState: SnailTrailRuntimeState,
    private val featureFlags: MapFeatureFlags
) {
    private var lastSettings: TrailSettings = TrailSettings()
    private var lastContext: RenderContext? = null
    private var lastIsReplay: Boolean? = null
    private var lastPoints: List<TrailPoint> = emptyList()
    private var lastRawTailPoint: TrailPoint? = null
    private var lastRenderPoseFrameId: Long? = null

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
        displayTimeMillis: Long? = null,
        displayTimeBase: TrailTimeBase? = null
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
        val displayTime = displayTimeMillis?.takeIf { it > 0L }
        val validDisplayLocation = displayLocation?.takeIf {
            TrailGeo.isValidCoordinate(it.latitude, it.longitude)
        }
        val sameTimeBaseDisplayTime = displayTime?.takeIf {
            displayTimeBase == renderState.timeBase
        }
        val freshDisplayLocation = if (
            sameTimeBaseDisplayTime != null &&
            sameTimeBaseDisplayTime >= renderState.currentTimeMillis
        ) {
            validDisplayLocation
        } else {
            null
        }
        val previousSameTimeBaseContext = lastContext?.takeIf {
            sameTimeBaseDisplayTime != null && it.timeBase == renderState.timeBase
        }
        val renderLocation = when {
            freshDisplayLocation != null -> freshDisplayLocation
            previousSameTimeBaseContext != null -> previousSameTimeBaseContext.currentLocation
            else -> baseLocation
        }
        val renderTime = when {
            freshDisplayLocation != null -> sameTimeBaseDisplayTime ?: renderState.currentTimeMillis
            previousSameTimeBaseContext != null -> previousSameTimeBaseContext.currentTimeMillis
            else -> renderState.currentTimeMillis
        }

        val zoomChanged = lastContext?.currentZoom?.let { it != currentZoom } ?: false

        lastPoints = renderState.points
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

        if (frameId != null) {
            lastRenderPoseFrameId = frameId
        }
        val updatedContext = context.copy(
            currentLocation = location,
            currentTimeMillis = time
        )
        val previousTailPoint = lastRawTailPoint
        lastContext = updatedContext
        refreshTailAnchor(updatedContext)

        if (shouldRenderRawTrail()) {
            if (lastRawTailPoint != previousTailPoint) {
                render(overlay, frameId)
            } else {
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
        lastRawTailPoint = null
        lastRenderPoseFrameId = null
    }

    private fun renderLast() {
        val overlay = runtimeState.snailTrailOverlay ?: return
        if (shouldRenderRawTrail()) {
            render(overlay)
        }
    }

    private fun shouldRenderRawTrail(): Boolean {
        return true
    }

    private fun render(overlay: SnailTrailOverlay, frameId: Long? = null) {
        val context = lastContext ?: return
        val isReplay = lastIsReplay ?: false
        val renderPoints = resolveRenderPoints(context, isReplay)
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

        val renderPoints = resolveRenderPoints(context, lastIsReplay ?: false)
        lastRawTailPoint = renderPoints.lastOrNull() ?: lastPoints.lastOrNull()
    }

    private fun resolveRenderPoints(
        context: RenderContext,
        isReplay: Boolean
    ): List<TrailPoint> {
        val timeFiltered = filterPointsAtDisplayTime(lastPoints, context.currentTimeMillis)
        if (isReplay) {
            return timeFiltered
        }
        return clampLivePointsToDisplayPose(timeFiltered, context.currentLocation)
    }

    private fun filterPointsAtDisplayTime(
        points: List<TrailPoint>,
        currentTimeMillis: Long
    ): List<TrailPoint> {
        if (currentTimeMillis <= 0L) {
            return points
        }
        val firstTimestamp = points.firstOrNull()?.timestampMillis
        if (firstTimestamp == null || currentTimeMillis < firstTimestamp) {
            return points
        }
        val filtered = points.filter { it.timestampMillis <= currentTimeMillis }
        return filtered.ifEmpty { points }
    }

    private fun clampLivePointsToDisplayPose(
        points: List<TrailPoint>,
        displayLocation: LatLng
    ): List<TrailPoint> {
        if (points.size < 2) {
            return points
        }
        if (!TrailGeo.isValidCoordinate(displayLocation.latitude, displayLocation.longitude)) {
            return points
        }

        val projection = nearestSegmentProjection(points, displayLocation) ?: return points
        val anchorIndex = if (
            projection.t >= SEGMENT_END_SNAP_T ||
            projection.distanceToEndMeters <= DISPLAY_POSE_POINT_SNAP_METERS
        ) {
            projection.segmentStartIndex + 1
        } else {
            projection.segmentStartIndex
        }
        return points.take((anchorIndex + 1).coerceIn(1, points.size))
    }

    private fun nearestSegmentProjection(
        points: List<TrailPoint>,
        location: LatLng
    ): SegmentProjection? {
        var best: SegmentProjection? = null
        for (index in 0 until points.lastIndex) {
            val projection = projectOnSegment(
                start = points[index],
                end = points[index + 1],
                location = location,
                segmentStartIndex = index
            ) ?: continue
            val currentBest = best
            if (
                currentBest == null ||
                projection.distanceMeters < currentBest.distanceMeters - PROJECTION_EPSILON_METERS ||
                (
                    abs(projection.distanceMeters - currentBest.distanceMeters) <= PROJECTION_EPSILON_METERS &&
                        projection.segmentStartIndex > currentBest.segmentStartIndex
                    )
            ) {
                best = projection
            }
        }
        return best
    }

    private fun projectOnSegment(
        start: TrailPoint,
        end: TrailPoint,
        location: LatLng,
        segmentStartIndex: Int
    ): SegmentProjection? {
        val originLatitude = start.latitude
        val endX = longitudeDeltaMeters(end.longitude - start.longitude, originLatitude)
        val endY = latitudeDeltaMeters(end.latitude - start.latitude)
        val locX = longitudeDeltaMeters(location.longitude - start.longitude, originLatitude)
        val locY = latitudeDeltaMeters(location.latitude - start.latitude)
        val lenSq = endX * endX + endY * endY
        if (!lenSq.isFinite() || lenSq <= 0.0) {
            return null
        }

        val unclampedT = ((locX * endX) + (locY * endY)) / lenSq
        val t = unclampedT.coerceIn(0.0, 1.0)
        val projectedX = endX * t
        val projectedY = endY * t
        val dx = locX - projectedX
        val dy = locY - projectedY
        val distance = sqrt(dx * dx + dy * dy)
        val endDx = locX - endX
        val endDy = locY - endY
        val distanceToEnd = sqrt(endDx * endDx + endDy * endDy)
        if (!distance.isFinite() || !distanceToEnd.isFinite()) {
            return null
        }
        return SegmentProjection(
            segmentStartIndex = segmentStartIndex,
            t = t,
            distanceMeters = distance,
            distanceToEndMeters = distanceToEnd
        )
    }

    private fun latitudeDeltaMeters(deltaLatitudeDeg: Double): Double =
        Math.toRadians(deltaLatitudeDeg) * EARTH_RADIUS_METERS

    private fun longitudeDeltaMeters(deltaLongitudeDeg: Double, originLatitudeDeg: Double): Double =
        Math.toRadians(deltaLongitudeDeg) * EARTH_RADIUS_METERS * cos(Math.toRadians(originLatitudeDeg))

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

    private data class SegmentProjection(
        val segmentStartIndex: Int,
        val t: Double,
        val distanceMeters: Double,
        val distanceToEndMeters: Double
    )

    private companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val DISPLAY_POSE_POINT_SNAP_METERS = 0.75
        private const val SEGMENT_END_SNAP_T = 0.995
        private const val PROJECTION_EPSILON_METERS = 0.01
    }

}
