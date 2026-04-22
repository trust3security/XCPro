// Role: Resolve fallback snail-trail style ranges when no raw render cache exists.
// Invariants: Display-only helper; does not mutate trail state or map sources.
package com.trust3.xcpro.map.trail

import android.content.Context

internal class SnailTrailStyleCacheResolver(
    private val context: Context
) {
    fun resolve(
        settings: TrailSettings,
        points: List<RenderPoint>
    ): SnailTrailStyleCache {
        val density = context.resources.displayMetrics.density
        val valueRange = when {
            points.isEmpty() && settings.type == TrailType.ALTITUDE ->
                ALTITUDE_DEFAULT_MIN_ALTITUDE to ALTITUDE_DEFAULT_MAX_ALTITUDE
            settings.type == TrailType.ALTITUDE ->
                SnailTrailMath.altitudeRange(points)
            else ->
                SnailTrailMath.varioRange(points)
        }
        val scaledWidths = SnailTrailMath.buildWidths(settings.scalingEnabled, density)
        return SnailTrailStyleCache(
            type = settings.type,
            valueMin = valueRange.first,
            valueMax = valueRange.second,
            useScaledLines = settings.scalingEnabled,
            scaledWidths = scaledWidths,
            minWidth = SnailTrailMath.minWidth(density)
        )
    }

    fun resolve(
        settings: TrailSettings,
        point: TrailPoint
    ): SnailTrailStyleCache {
        return resolve(
            settings = settings,
            points = listOf(
                RenderPoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    altitudeMeters = point.altitudeMeters,
                    varioMs = point.varioMs,
                    timestampMillis = point.timestampMillis
                )
            )
        )
    }

    private companion object {
        private const val ALTITUDE_DEFAULT_MIN_ALTITUDE = 500.0
        private const val ALTITUDE_DEFAULT_MAX_ALTITUDE = 1000.0
    }
}
