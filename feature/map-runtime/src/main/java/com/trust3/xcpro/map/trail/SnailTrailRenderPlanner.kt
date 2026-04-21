// Role: Build a render plan for the trail without MapLibre dependencies.
// Invariants: Outputs reflect filtering, distance thinning, and width scaling rules.
package com.trust3.xcpro.map.trail

import kotlin.math.min

internal class SnailTrailRenderPlanner(
    private val metersPerPixelProvider: MetersPerPixelProvider
) {

    data class Input(
        val points: List<TrailPoint>,
        val settings: TrailSettings,
        val currentLocation: TrailGeoPoint,
        val currentTimeMillis: Long,
        val isCircling: Boolean,
        val isTurnSmoothing: Boolean,
        val currentZoom: Float,
        val isReplay: Boolean,
        val useRenderFrameSync: Boolean,
        val density: Float
    )

    data class Plan(
        val minTimeMillis: Long?,
        val renderPoints: List<RenderPoint>,
        val metersPerPixel: Double,
        val minDistanceMeters: Double,
        val skipBoundsCheck: Boolean,
        val styleCache: SnailTrailStyleCache
    )

    fun plan(input: Input): Plan? {
        if (input.settings.length == TrailLength.OFF) return null
        if (!TrailGeo.isValidCoordinate(input.currentLocation.latitude, input.currentLocation.longitude)) return null

        val minTime = SnailTrailMath.minTimeMillis(input.settings.length, input.currentTimeMillis)
        val renderPoints = SnailTrailMath.filterPoints(
            points = input.points,
            minTimeMillis = minTime,
            currentTimeMillis = input.currentTimeMillis,
            isCircling = input.isCircling,
            settings = input.settings
        )
        if (renderPoints.isEmpty()) return null

        val metersPerPixel = metersPerPixelProvider.metersPerPixel(
            input.currentLocation.latitude,
            input.currentZoom
        )
        val distanceFactor = when {
            input.isReplay -> REPLAY_DISTANCE_FACTOR
            input.isTurnSmoothing -> LIVE_TURN_DISTANCE_FACTOR
            input.isCircling -> LIVE_CIRCLING_DISTANCE_FACTOR
            else -> LIVE_DISTANCE_FACTOR
        }
        val rawMinDistance = metersPerPixel * distanceFactor
        val minDistanceMeters = if (input.isReplay) {
            min(rawMinDistance, REPLAY_MAX_DISTANCE_METERS)
        } else {
            rawMinDistance
        }
        val sim2FullTrail = input.isReplay && input.useRenderFrameSync
        val filtered = SnailTrailMath.applyDistanceFilter(
            renderPoints,
            if (sim2FullTrail) 0.0 else minDistanceMeters
        )
        val latestPoint = renderPoints.lastOrNull()
        val filteredWithTail = if (
            latestPoint != null &&
            filtered.isNotEmpty() &&
            filtered.last().timestampMillis < latestPoint.timestampMillis
        ) {
            val extended = ArrayList<RenderPoint>(filtered.size + 1)
            extended.addAll(filtered)
            extended.add(latestPoint)
            extended
        } else {
            filtered
        }
        if (filteredWithTail.isEmpty()) return null

        val (valueMin, valueMax) = if (input.settings.type == TrailType.ALTITUDE) {
            SnailTrailMath.altitudeRange(filteredWithTail)
        } else {
            SnailTrailMath.varioRange(filteredWithTail)
        }

        val minWidth = SnailTrailMath.minWidth(input.density)
        val scaledWidths = SnailTrailMath.buildWidths(input.settings.scalingEnabled, input.density)
        val useScaledLines = input.settings.scalingEnabled &&
            metersPerPixel <= MAX_METERS_PER_PIXEL_FOR_SCALING
        val styleCache = SnailTrailStyleCache(
            type = input.settings.type,
            valueMin = valueMin,
            valueMax = valueMax,
            useScaledLines = useScaledLines,
            scaledWidths = scaledWidths,
            minWidth = minWidth
        )

        return Plan(
            minTimeMillis = minTime,
            renderPoints = filteredWithTail,
            metersPerPixel = metersPerPixel,
            minDistanceMeters = minDistanceMeters,
            skipBoundsCheck = sim2FullTrail,
            styleCache = styleCache
        )
    }

    private companion object {
        private const val REPLAY_DISTANCE_FACTOR = 1.0
        private const val LIVE_DISTANCE_FACTOR = 3.0
        private const val LIVE_TURN_DISTANCE_FACTOR = 0.8
        private const val LIVE_CIRCLING_DISTANCE_FACTOR = 1.0
        private const val REPLAY_MAX_DISTANCE_METERS = 30.0
        private const val MAX_METERS_PER_PIXEL_FOR_SCALING = 6000.0
    }
}
