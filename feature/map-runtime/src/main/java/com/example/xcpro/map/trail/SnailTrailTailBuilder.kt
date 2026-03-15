// Role: Build the tail segment for the most recent trail point without MapLibre dependencies.
// Invariants: Tail length and clipping rules match prior overlay behavior.
package com.example.xcpro.map.trail

internal interface TailClipper {
    fun clipToClearance(start: RenderPoint, end: TrailGeoPoint, clearancePx: Float): TrailGeoPoint?
}

internal class SnailTrailTailBuilder(
    private val tailClipper: TailClipper
) {

    data class Input(
        val lastPoint: RenderPoint,
        val settings: TrailSettings,
        val currentLocation: TrailGeoPoint,
        val currentTimeMillis: Long,
        val styleCache: SnailTrailStyleCache,
        val metersPerPixel: Double,
        val iconSizePx: Float
    )

    fun build(input: Input): SnailTrailLineSegment? {
        val trackBearing = TrailGeo.bearingDegrees(
            input.lastPoint.latitude,
            input.lastPoint.longitude,
            input.currentLocation.latitude,
            input.currentLocation.longitude
        )
        val tailOffsetMeters = if (input.metersPerPixel.isFinite() && input.metersPerPixel > 0.0) {
            input.iconSizePx * TAIL_OFFSET_FRACTION * input.metersPerPixel
        } else {
            0.0
        }
        val tailLocation = if (trackBearing.isFinite() && tailOffsetMeters > 0.0) {
            val tailBearing = (trackBearing + 180.0) % 360.0
            val (lat, lon) = TrailGeo.destinationPoint(
                input.currentLocation.latitude,
                input.currentLocation.longitude,
                tailBearing,
                tailOffsetMeters
            )
            if (TrailGeo.isValidCoordinate(lat, lon)) {
                TrailGeoPoint(lat, lon)
            } else {
                input.currentLocation
            }
        } else {
            input.currentLocation
        }
        val distToAnchor = TrailGeo.distanceMeters(
            input.lastPoint.latitude,
            input.lastPoint.longitude,
            tailLocation.latitude,
            tailLocation.longitude
        )
        if (distToAnchor < MIN_CURRENT_SEGMENT_METERS) {
            return null
        }
        val clearancePx = input.iconSizePx * ICON_CLEARANCE_FRACTION
        val clipped = tailClipper.clipToClearance(input.lastPoint, tailLocation, clearancePx)
            ?: return null
        val colorIndex = if (input.settings.type == TrailType.ALTITUDE) {
            SnailTrailMath.altitudeColorIndex(
                input.lastPoint.altitudeMeters,
                input.styleCache.valueMin,
                input.styleCache.valueMax
            )
        } else {
            SnailTrailMath.varioColorIndex(
                input.lastPoint.varioMs,
                input.styleCache.valueMin,
                input.styleCache.valueMax
            )
        }
        val width = if (input.styleCache.useScaledLines) {
            input.styleCache.scaledWidths[colorIndex]
        } else {
            input.styleCache.minWidth
        }
        val currentPoint = RenderPoint(
            latitude = clipped.latitude,
            longitude = clipped.longitude,
            altitudeMeters = input.lastPoint.altitudeMeters,
            varioMs = input.lastPoint.varioMs,
            timestampMillis = input.currentTimeMillis
        )
        return SnailTrailLineSegment(
            start = input.lastPoint,
            end = currentPoint,
            colorIndex = colorIndex,
            width = width
        )
    }

    internal companion object {
        internal const val MIN_CURRENT_SEGMENT_METERS = 0.5
        internal const val ICON_CLEARANCE_FRACTION = 0f
        internal const val TAIL_OFFSET_FRACTION = 0.12f
    }
}
