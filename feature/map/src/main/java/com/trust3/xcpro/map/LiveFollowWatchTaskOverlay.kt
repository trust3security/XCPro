package com.trust3.xcpro.map

import com.trust3.xcpro.livefollow.model.LiveFollowTaskPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal data class LiveFollowWatchTaskOverlayState(
    val shareCode: String,
    val points: List<LiveFollowTaskPoint>
)

internal class LiveFollowWatchTaskOverlay(
    private val map: MapLibreMap
) {
    private var boundStyle: Style? = null
    private var lastRenderedState: LiveFollowWatchTaskOverlayState? = null
    private var lastRenderedVisible: Boolean? = null

    fun render(state: LiveFollowWatchTaskOverlayState?) {
        if (state == null || state.points.size < 2) {
            setVisible(false)
            return
        }
        val style = map.style ?: return
        ensureRuntimeObjects(style)
        if (boundStyle !== style || lastRenderedState != state) {
            style.getSourceAs<GeoJsonSource>(CYLINDER_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(cylinderFeatures(state.points)))
            style.getSourceAs<GeoJsonSource>(LEG_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(legFeatures(state.points)))
            lastRenderedState = state
        }
        applyVisibility(style, visible = true)
        boundStyle = style
    }

    fun cleanup() {
        val style = map.style ?: return
        runCatching { style.removeLayer(LEG_LAYER_ID) }
        runCatching { style.removeLayer(CYLINDER_OUTLINE_LAYER_ID) }
        runCatching { style.removeLayer(CYLINDER_FILL_LAYER_ID) }
        runCatching { style.removeSource(LEG_SOURCE_ID) }
        runCatching { style.removeSource(CYLINDER_SOURCE_ID) }
        lastRenderedState = null
        lastRenderedVisible = null
        boundStyle = null
    }

    private fun setVisible(visible: Boolean) {
        val style = map.style ?: return
        ensureRuntimeObjects(style)
        applyVisibility(style, visible)
        if (!visible) {
            lastRenderedState = null
        }
        boundStyle = style
    }

    private fun applyVisibility(
        style: Style,
        visible: Boolean
    ) {
        if (lastRenderedVisible == visible && boundStyle === style) {
            return
        }
        val visibilityValue = if (visible) "visible" else "none"
        style.getLayerAs<FillLayer>(CYLINDER_FILL_LAYER_ID)
            ?.setProperties(visibility(visibilityValue))
        style.getLayerAs<LineLayer>(CYLINDER_OUTLINE_LAYER_ID)
            ?.setProperties(visibility(visibilityValue))
        style.getLayerAs<LineLayer>(LEG_LAYER_ID)
            ?.setProperties(visibility(visibilityValue))
        lastRenderedVisible = visible
    }

    private fun ensureRuntimeObjects(style: Style) {
        if (style.getSourceAs<GeoJsonSource>(CYLINDER_SOURCE_ID) == null) {
            style.addSource(emptyFeatureSource(CYLINDER_SOURCE_ID))
        }
        if (style.getSourceAs<GeoJsonSource>(LEG_SOURCE_ID) == null) {
            style.addSource(emptyFeatureSource(LEG_SOURCE_ID))
        }
        if (style.getLayerAs<FillLayer>(CYLINDER_FILL_LAYER_ID) == null) {
            addTaskLayer(
                style = style,
                layer = FillLayer(CYLINDER_FILL_LAYER_ID, CYLINDER_SOURCE_ID).withProperties(
                    fillColor(TASK_COLOR_HEX),
                    fillOpacity(TASK_FILL_OPACITY),
                    visibility("none")
                )
            )
        }
        if (style.getLayerAs<LineLayer>(CYLINDER_OUTLINE_LAYER_ID) == null) {
            addTaskLayer(
                style = style,
                layer = LineLayer(CYLINDER_OUTLINE_LAYER_ID, CYLINDER_SOURCE_ID).withProperties(
                    lineColor(TASK_COLOR_HEX),
                    lineOpacity(TASK_OUTLINE_OPACITY),
                    lineWidth(TASK_OUTLINE_WIDTH),
                    visibility("none")
                )
            )
        }
        if (style.getLayerAs<LineLayer>(LEG_LAYER_ID) == null) {
            addTaskLayer(
                style = style,
                layer = LineLayer(LEG_LAYER_ID, LEG_SOURCE_ID).withProperties(
                    lineColor(TASK_COLOR_HEX),
                    lineOpacity(TASK_LEG_OPACITY),
                    lineWidth(TASK_LEG_WIDTH),
                    visibility("none")
                )
            )
        }
    }

    private fun addTaskLayer(
        style: Style,
        layer: org.maplibre.android.style.layers.Layer
    ) {
        when {
            style.getLayer(LiveFollowWatchAircraftOverlay.LAYER_ID) != null -> {
                style.addLayerBelow(layer, LiveFollowWatchAircraftOverlay.LAYER_ID)
            }

            style.getLayer(BlueLocationOverlay.LAYER_ID) != null -> {
                style.addLayerAbove(layer, BlueLocationOverlay.LAYER_ID)
            }

            else -> style.addLayer(layer)
        }
    }

    private fun emptyFeatureSource(sourceId: String): GeoJsonSource {
        return GeoJsonSource(
            sourceId,
            FeatureCollection.fromFeatures(emptyList())
        )
    }

    private fun cylinderFeatures(
        points: List<LiveFollowTaskPoint>
    ): List<Feature> {
        return points.mapNotNull { point ->
            val radiusMeters = point.radiusMeters?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            Feature.fromGeometry(
                circlePolygon(
                    latitudeDeg = point.latitudeDeg,
                    longitudeDeg = point.longitudeDeg,
                    radiusMeters = radiusMeters
                )
            )
        }
    }

    private fun legFeatures(
        points: List<LiveFollowTaskPoint>
    ): List<Feature> {
        if (points.size < 2) {
            return emptyList()
        }
        val linePoints = points.map { point ->
            Point.fromLngLat(point.longitudeDeg, point.latitudeDeg)
        }
        return listOf(Feature.fromGeometry(LineString.fromLngLats(linePoints)))
    }

    private companion object {
        private const val CYLINDER_SOURCE_ID = "livefollow-watch-task-cylinders-source"
        private const val CYLINDER_FILL_LAYER_ID = "livefollow-watch-task-cylinders-fill"
        private const val CYLINDER_OUTLINE_LAYER_ID = "livefollow-watch-task-cylinders-outline"
        private const val LEG_SOURCE_ID = "livefollow-watch-task-legs-source"
        private const val LEG_LAYER_ID = "livefollow-watch-task-legs-layer"
        private const val TASK_COLOR_HEX = "#1A7D76"
        private const val TASK_FILL_OPACITY = 0.10f
        private const val TASK_OUTLINE_OPACITY = 0.55f
        private const val TASK_OUTLINE_WIDTH = 1.5f
        private const val TASK_LEG_OPACITY = 0.75f
        private const val TASK_LEG_WIDTH = 2.25f
        private const val CIRCLE_SEGMENTS = 64
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        private fun circlePolygon(
            latitudeDeg: Double,
            longitudeDeg: Double,
            radiusMeters: Double
        ): Polygon {
            val points = (0..CIRCLE_SEGMENTS).map { segment ->
                val bearingDeg = segment * (360.0 / CIRCLE_SEGMENTS)
                destinationPoint(
                    latitudeDeg = latitudeDeg,
                    longitudeDeg = longitudeDeg,
                    distanceMeters = radiusMeters,
                    bearingDeg = bearingDeg
                )
            }
            return Polygon.fromLngLats(listOf(points))
        }

        private fun destinationPoint(
            latitudeDeg: Double,
            longitudeDeg: Double,
            distanceMeters: Double,
            bearingDeg: Double
        ): Point {
            val angularDistance = distanceMeters / EARTH_RADIUS_METERS
            val bearingRad = Math.toRadians(bearingDeg)
            val latitudeRad = Math.toRadians(latitudeDeg)
            val longitudeRad = Math.toRadians(longitudeDeg)

            val destinationLatitudeRad = asin(
                sin(latitudeRad) * cos(angularDistance) +
                    cos(latitudeRad) * sin(angularDistance) * cos(bearingRad)
            )
            val destinationLongitudeRad = longitudeRad + atan2(
                sin(bearingRad) * sin(angularDistance) * cos(latitudeRad),
                cos(angularDistance) - sin(latitudeRad) * sin(destinationLatitudeRad)
            )
            return Point.fromLngLat(
                Math.toDegrees(destinationLongitudeRad),
                Math.toDegrees(destinationLatitudeRad)
            )
        }
    }
}
