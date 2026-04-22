// Role: Render the recent display-pose snail trail layer.
// Invariants: Uses existing trail styling rules and only writes display trail sources.
package com.trust3.xcpro.map.trail

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

internal class SnailTrailDisplayTrailRenderer(
    private val map: MapLibreMap,
    private val mapView: MapView?
) {
    fun render(
        points: List<RenderPoint>,
        settings: TrailSettings,
        styleCache: SnailTrailStyleCache?
    ) {
        if (settings.length == TrailLength.OFF || points.size < MIN_RENDER_POINTS || styleCache == null) {
            clear()
            return
        }
        val style = map.style ?: return
        val bounds = ScreenBounds(mapView)
        val segmentPlan = SnailTrailSegmentBuilder(
            object : SnailTrailSegmentBuilder.BoundsChecker {
                override fun isInside(point: RenderPoint): Boolean {
                    return bounds.isInside(map, point.latitude, point.longitude)
                }
            }
        ).build(
            points = points,
            settings = settings,
            styleCache = styleCache,
            skipBoundsCheck = false,
            includeLogs = false
        )

        val lineFeatures = ArrayList<Feature>(segmentPlan.lineSegments.size)
        for (segment in segmentPlan.lineSegments) {
            lineFeatures.add(
                SnailTrailFeatureBuilder.lineFeature(segment.start, segment.end, segment.colorIndex, segment.width)
            )
        }
        val dotFeatures = ArrayList<Feature>(segmentPlan.dotSegments.size)
        for (segment in segmentPlan.dotSegments) {
            dotFeatures.add(
                SnailTrailFeatureBuilder.dotFeature(segment.start, segment.end, segment.colorIndex, segment.radius)
            )
        }

        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DISPLAY_LINE_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(lineFeatures))
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DISPLAY_DOT_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(dotFeatures))
    }

    fun clear() {
        val style = map.style ?: return
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DISPLAY_LINE_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DISPLAY_DOT_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    private companion object {
        private const val MIN_RENDER_POINTS = 2
    }
}
