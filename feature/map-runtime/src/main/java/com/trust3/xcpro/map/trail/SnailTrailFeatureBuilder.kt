package com.trust3.xcpro.map.trail

import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

internal object SnailTrailFeatureBuilder {
    fun lineFeature(start: RenderPoint, end: RenderPoint, colorIndex: Int, width: Float): Feature {
        val line = LineString.fromLngLats(
            listOf(
                Point.fromLngLat(start.longitude, start.latitude),
                Point.fromLngLat(end.longitude, end.latitude)
            )
        )
        return Feature.fromGeometry(line).apply {
            addNumberProperty(SnailTrailStyle.PROP_COLOR_INDEX, colorIndex)
            addNumberProperty(SnailTrailStyle.PROP_WIDTH, width.toDouble())
        }
    }

    fun dotFeature(start: RenderPoint, end: RenderPoint, colorIndex: Int, radius: Float): Feature {
        val midLat = (start.latitude + end.latitude) / 2.0
        val midLon = (start.longitude + end.longitude) / 2.0
        val point = Point.fromLngLat(midLon, midLat)
        return Feature.fromGeometry(point).apply {
            addNumberProperty(SnailTrailStyle.PROP_COLOR_INDEX, colorIndex)
            addNumberProperty(SnailTrailStyle.PROP_RADIUS, radius.toDouble())
        }
    }
}
