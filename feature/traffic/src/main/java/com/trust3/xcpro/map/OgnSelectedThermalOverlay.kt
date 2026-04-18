package com.trust3.xcpro.map

import android.graphics.Color
import com.trust3.xcpro.adsb.AdsbGeoMath
import com.trust3.xcpro.core.common.logging.AppLogger
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val SELECTED_THERMAL_OVERLAY_TAG = "OgnSelectedThermalOverlay"

private const val SELECTED_THERMAL_SOURCE_ID = "ogn-selected-thermal-source"
private const val HULL_FILL_LAYER_ID = "ogn-selected-thermal-hull-fill-layer"
private const val HULL_BORDER_LAYER_ID = "ogn-selected-thermal-hull-border-layer"
private const val LOOP_CASING_LAYER_ID = "ogn-selected-thermal-loop-casing-layer"
private const val LOOP_LAYER_ID = "ogn-selected-thermal-loop-layer"
private const val DRIFT_LAYER_ID = "ogn-selected-thermal-drift-layer"
private const val DRIFT_ARROW_LAYER_ID = "ogn-selected-thermal-drift-arrow-layer"
private const val MARKER_LAYER_ID = "ogn-selected-thermal-marker-layer"

private const val OGN_GLIDER_TRAIL_LAYER_ID = "ogn-glider-trail-line-layer"
private const val OGN_THERMAL_CIRCLE_LAYER_ID = "ogn-thermal-circle-layer"

private const val PROP_KIND = "kind"
private const val PROP_COLOR_INDEX = "color_index"
private const val PROP_MARKER_KIND = "marker_kind"

private const val KIND_HULL = "hull"
private const val KIND_LOOP_CASING = "loop_casing"
private const val KIND_LOOP = "loop"
private const val KIND_DRIFT = "drift"
private const val KIND_DRIFT_ARROW = "drift_arrow"
private const val KIND_MARKER = "marker"

private const val MARKER_START = "start"
private const val MARKER_LATEST = "latest"

private const val DEFAULT_COLOR = "#FFF4B0"
private const val LOOP_CASING_COLOR = "#08131F"
private const val DRIFT_COLOR = "#E5E7EB"
private const val MARKER_DEFAULT_COLOR = "#E5E7EB"
private const val MARKER_START_COLOR = "#22C55E"
private const val MARKER_LATEST_COLOR = "#F97316"
private const val MARKER_STROKE_COLOR = "#0B1E2D"

private const val HULL_FILL_OPACITY = 0.06f
private const val HULL_BORDER_WIDTH_DP = 1.2f
private const val HULL_BORDER_OPACITY = 0.45f
private const val LOOP_CASING_WIDTH_DP = 7.0f
private const val LOOP_CASING_OPACITY = 0.82f
private const val LOOP_WIDTH_DP = 4.5f
private const val LOOP_OPACITY = 0.98f
private const val DRIFT_WIDTH_DP = 1.8f
private const val DRIFT_OPACITY = 0.72f
private const val DRIFT_DASH_LENGTH_DP = 2f
private const val DRIFT_GAP_LENGTH_DP = 2f
private const val DRIFT_ARROW_WIDTH_DP = 2.4f
private const val DRIFT_ARROW_OPACITY = 0.90f
private const val MARKER_DEFAULT_RADIUS_DP = 4.0f
private const val MARKER_START_RADIUS_DP = 4.2f
private const val MARKER_LATEST_RADIUS_DP = 6.0f
private const val MARKER_OPACITY = 0.98f
private const val MARKER_STROKE_WIDTH_DP = 1.4f

private const val DRIFT_ARROW_HALF_ANGLE_DEG = 24.0
private const val DRIFT_ARROW_MAX_LENGTH_METERS = 120.0
private const val DRIFT_ARROW_MIN_LENGTH_METERS = 28.0
private const val DRIFT_ARROW_LENGTH_SCALE = 0.35
private const val EARTH_RADIUS_METERS = 6_371_000.0

class OgnSelectedThermalOverlay(
    private val map: MapLibreMap
) : OgnSelectedThermalOverlayHandle {

    override fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SELECTED_THERMAL_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SELECTED_THERMAL_SOURCE_ID))
            }
            if (style.getLayer(HULL_FILL_LAYER_ID) == null) {
                val layer = createHullFillLayer()
                when {
                    style.getLayer(OGN_GLIDER_TRAIL_LAYER_ID) != null ->
                        style.addLayerBelow(layer, OGN_GLIDER_TRAIL_LAYER_ID)
                    style.getLayer(OGN_THERMAL_CIRCLE_LAYER_ID) != null ->
                        style.addLayerBelow(layer, OGN_THERMAL_CIRCLE_LAYER_ID)
                    else -> style.addLayer(layer)
                }
            }
            if (style.getLayer(HULL_BORDER_LAYER_ID) == null) {
                val layer = createHullBorderLayer()
                when {
                    style.getLayer(HULL_FILL_LAYER_ID) != null ->
                        style.addLayerAbove(layer, HULL_FILL_LAYER_ID)
                    else -> style.addLayer(layer)
                }
            }
            if (style.getLayer(LOOP_CASING_LAYER_ID) == null) {
                val layer = createLoopCasingLayer()
                when {
                    style.getLayer(HULL_BORDER_LAYER_ID) != null ->
                        style.addLayerAbove(layer, HULL_BORDER_LAYER_ID)
                    style.getLayer(OGN_GLIDER_TRAIL_LAYER_ID) != null ->
                        style.addLayerAbove(layer, OGN_GLIDER_TRAIL_LAYER_ID)
                    else -> style.addLayer(layer)
                }
            }
            if (style.getLayer(LOOP_LAYER_ID) == null) {
                val layer = createLoopLayer()
                when {
                    style.getLayer(LOOP_CASING_LAYER_ID) != null ->
                        style.addLayerAbove(layer, LOOP_CASING_LAYER_ID)
                    style.getLayer(OGN_GLIDER_TRAIL_LAYER_ID) != null ->
                        style.addLayerAbove(layer, OGN_GLIDER_TRAIL_LAYER_ID)
                    else -> style.addLayer(layer)
                }
            }
            if (style.getLayer(DRIFT_LAYER_ID) == null) {
                val layer = createDriftLayer()
                when {
                    style.getLayer(LOOP_LAYER_ID) != null ->
                        style.addLayerAbove(layer, LOOP_LAYER_ID)
                    else -> style.addLayer(layer)
                }
            }
            if (style.getLayer(DRIFT_ARROW_LAYER_ID) == null) {
                val layer = createDriftArrowLayer()
                when {
                    style.getLayer(DRIFT_LAYER_ID) != null ->
                        style.addLayerAbove(layer, DRIFT_LAYER_ID)
                    style.getLayer(LOOP_LAYER_ID) != null ->
                        style.addLayerAbove(layer, LOOP_LAYER_ID)
                    else -> style.addLayer(layer)
                }
            }
            if (style.getLayer(MARKER_LAYER_ID) == null) {
                val layer = createMarkerLayer()
                when {
                    style.getLayer(OGN_THERMAL_CIRCLE_LAYER_ID) != null ->
                        style.addLayerAbove(layer, OGN_THERMAL_CIRCLE_LAYER_ID)
                    style.getLayer(DRIFT_ARROW_LAYER_ID) != null ->
                        style.addLayerAbove(layer, DRIFT_ARROW_LAYER_ID)
                    style.getLayer(DRIFT_LAYER_ID) != null ->
                        style.addLayerAbove(layer, DRIFT_LAYER_ID)
                    else -> style.addLayer(layer)
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(SELECTED_THERMAL_OVERLAY_TAG, "Failed to initialize selected thermal overlay: ${t.message}", t)
        }
    }

    override fun render(context: SelectedOgnThermalOverlayContext?) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SELECTED_THERMAL_SOURCE_ID) ?: return
        try {
            source.setGeoJson(FeatureCollection.fromFeatures(buildSelectedThermalOverlayFeatures(context)))
        } catch (t: Throwable) {
            AppLogger.e(SELECTED_THERMAL_OVERLAY_TAG, "Failed to render selected thermal overlay: ${t.message}", t)
        }
    }

    override fun cleanup() {
        val style = map.style ?: return
        try {
            style.removeLayer(MARKER_LAYER_ID)
            style.removeLayer(DRIFT_ARROW_LAYER_ID)
            style.removeLayer(DRIFT_LAYER_ID)
            style.removeLayer(LOOP_LAYER_ID)
            style.removeLayer(LOOP_CASING_LAYER_ID)
            style.removeLayer(HULL_BORDER_LAYER_ID)
            style.removeLayer(HULL_FILL_LAYER_ID)
            style.removeSource(SELECTED_THERMAL_SOURCE_ID)
        } catch (t: Throwable) {
            AppLogger.w(SELECTED_THERMAL_OVERLAY_TAG, "Failed to cleanup selected thermal overlay: ${t.message}")
        }
    }

    private fun createHullFillLayer(): FillLayer =
        FillLayer(HULL_FILL_LAYER_ID, SELECTED_THERMAL_SOURCE_ID)
            .withFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_HULL)))
            .withProperties(
                fillColor(colorExpression()),
                fillOpacity(HULL_FILL_OPACITY)
            )

    private fun createHullBorderLayer(): LineLayer =
        LineLayer(HULL_BORDER_LAYER_ID, SELECTED_THERMAL_SOURCE_ID)
            .withFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_HULL)))
            .withProperties(
                lineColor(colorExpression()),
                lineWidth(HULL_BORDER_WIDTH_DP),
                lineOpacity(HULL_BORDER_OPACITY),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )

    private fun createLoopCasingLayer(): LineLayer =
        LineLayer(LOOP_CASING_LAYER_ID, SELECTED_THERMAL_SOURCE_ID)
            .withFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_LOOP_CASING)))
            .withProperties(
                lineColor(LOOP_CASING_COLOR),
                lineWidth(LOOP_CASING_WIDTH_DP),
                lineOpacity(LOOP_CASING_OPACITY),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )

    private fun createLoopLayer(): LineLayer =
        LineLayer(LOOP_LAYER_ID, SELECTED_THERMAL_SOURCE_ID)
            .withFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_LOOP)))
            .withProperties(
                lineColor(colorExpression()),
                lineWidth(LOOP_WIDTH_DP),
                lineOpacity(LOOP_OPACITY),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )

    private fun createDriftLayer(): LineLayer =
        LineLayer(DRIFT_LAYER_ID, SELECTED_THERMAL_SOURCE_ID)
            .withFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_DRIFT)))
            .withProperties(
                lineColor(DRIFT_COLOR),
                lineWidth(DRIFT_WIDTH_DP),
                lineOpacity(DRIFT_OPACITY),
                lineDasharray(arrayOf(DRIFT_DASH_LENGTH_DP, DRIFT_GAP_LENGTH_DP)),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )

    private fun createDriftArrowLayer(): LineLayer =
        LineLayer(DRIFT_ARROW_LAYER_ID, SELECTED_THERMAL_SOURCE_ID)
            .withFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_DRIFT_ARROW)))
            .withProperties(
                lineColor(DRIFT_COLOR),
                lineWidth(DRIFT_ARROW_WIDTH_DP),
                lineOpacity(DRIFT_ARROW_OPACITY),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )

    private fun createMarkerLayer(): CircleLayer {
        val markerColor = Expression.match(
            Expression.get(PROP_MARKER_KIND),
            Expression.color(Color.parseColor(MARKER_DEFAULT_COLOR)),
            Expression.stop(MARKER_START, Expression.color(Color.parseColor(MARKER_START_COLOR))),
            Expression.stop(MARKER_LATEST, Expression.color(Color.parseColor(MARKER_LATEST_COLOR)))
        )
        val markerRadius = Expression.match(
            Expression.get(PROP_MARKER_KIND),
            Expression.literal(MARKER_DEFAULT_RADIUS_DP),
            Expression.stop(MARKER_START, Expression.literal(MARKER_START_RADIUS_DP)),
            Expression.stop(MARKER_LATEST, Expression.literal(MARKER_LATEST_RADIUS_DP))
        )
        return CircleLayer(MARKER_LAYER_ID, SELECTED_THERMAL_SOURCE_ID)
            .withFilter(Expression.eq(Expression.get(PROP_KIND), Expression.literal(KIND_MARKER)))
            .withProperties(
                circleColor(markerColor),
                circleRadius(markerRadius),
                circleOpacity(MARKER_OPACITY),
                circleStrokeColor(MARKER_STROKE_COLOR),
                circleStrokeWidth(MARKER_STROKE_WIDTH_DP)
            )
    }

    private fun colorExpression(): Expression {
        val colorStops = snailColorHexStops().mapIndexed { index, hex ->
            Expression.stop(index, Expression.color(Color.parseColor(hex)))
        }.toTypedArray()
        return Expression.match(
            Expression.get(PROP_COLOR_INDEX),
            Expression.color(Color.parseColor(DEFAULT_COLOR)),
            *colorStops
        )
    }
}

internal fun buildSelectedThermalOverlayFeatures(
    context: SelectedOgnThermalOverlayContext?
): List<Feature> {
    if (context == null) return emptyList()
    val features = ArrayList<Feature>(context.highlightedSegments.size * 2 + 6)
    addHullFeature(features, context)
    addLoopFeatures(features, context)
    addDriftFeatures(features, context.startPoint, context.latestPoint)
    addMarkerFeature(features, context.startPoint, MARKER_START)
    addMarkerFeature(features, context.latestPoint, MARKER_LATEST)
    return features
}

private fun addHullFeature(
    features: MutableList<Feature>,
    context: SelectedOgnThermalOverlayContext
) {
    if (context.occupancyHullPoints.size < 3) return
    val hullPoints = context.occupancyHullPoints.map { point ->
        Point.fromLngLat(point.longitude, point.latitude)
    }
    val closedHull = if (hullPoints.first() == hullPoints.last()) hullPoints else hullPoints + hullPoints.first()
    features += Feature.fromGeometry(Polygon.fromLngLats(listOf(closedHull))).apply {
        addStringProperty(PROP_KIND, KIND_HULL)
        addNumberProperty(PROP_COLOR_INDEX, context.snailColorIndex)
    }
}

private fun addLoopFeatures(
    features: MutableList<Feature>,
    context: SelectedOgnThermalOverlayContext
) {
    for (segment in context.highlightedSegments) {
        if (
            !isValidOgnThermalCoordinate(segment.startLatitude, segment.startLongitude) ||
            !isValidOgnThermalCoordinate(segment.endLatitude, segment.endLongitude)
        ) {
            continue
        }
        val geometry = LineString.fromLngLats(
            listOf(
                Point.fromLngLat(segment.startLongitude, segment.startLatitude),
                Point.fromLngLat(segment.endLongitude, segment.endLatitude)
            )
        )
        features += Feature.fromGeometry(geometry).apply {
            addStringProperty(PROP_KIND, KIND_LOOP_CASING)
            addNumberProperty(PROP_COLOR_INDEX, context.snailColorIndex)
        }
        features += Feature.fromGeometry(geometry).apply {
            addStringProperty(PROP_KIND, KIND_LOOP)
            addNumberProperty(PROP_COLOR_INDEX, context.snailColorIndex)
        }
    }
}

private fun addDriftFeatures(
    features: MutableList<Feature>,
    startPoint: OgnThermalPoint?,
    latestPoint: OgnThermalPoint?
) {
    if (startPoint == null || latestPoint == null || startPoint == latestPoint) return
    val driftDistanceMeters = AdsbGeoMath.haversineMeters(
        startPoint.latitude,
        startPoint.longitude,
        latestPoint.latitude,
        latestPoint.longitude
    ).takeIf { it.isFinite() && it > 0.0 } ?: return
    val driftBearingDeg = AdsbGeoMath.bearingDegrees(
        startPoint.latitude,
        startPoint.longitude,
        latestPoint.latitude,
        latestPoint.longitude
    ).takeIf { it.isFinite() } ?: return
    features += Feature.fromGeometry(
        LineString.fromLngLats(
            listOf(
                Point.fromLngLat(startPoint.longitude, startPoint.latitude),
                Point.fromLngLat(latestPoint.longitude, latestPoint.latitude)
            )
        )
    ).apply {
        addStringProperty(PROP_KIND, KIND_DRIFT)
    }
    buildDriftArrowFeature(latestPoint, driftBearingDeg, driftDistanceMeters)?.let(features::add)
}

private fun buildDriftArrowFeature(
    latestPoint: OgnThermalPoint,
    driftBearingDeg: Double,
    driftDistanceMeters: Double
): Feature? {
    val arrowLengthMeters = min(
        DRIFT_ARROW_MAX_LENGTH_METERS,
        max(DRIFT_ARROW_MIN_LENGTH_METERS, driftDistanceMeters * DRIFT_ARROW_LENGTH_SCALE)
    ).takeIf { it < driftDistanceMeters } ?: return null
    val leftWing = destinationPoint(
        latitudeDeg = latestPoint.latitude,
        longitudeDeg = latestPoint.longitude,
        distanceMeters = arrowLengthMeters,
        bearingDeg = driftBearingDeg + 180.0 - DRIFT_ARROW_HALF_ANGLE_DEG
    )
    val rightWing = destinationPoint(
        latitudeDeg = latestPoint.latitude,
        longitudeDeg = latestPoint.longitude,
        distanceMeters = arrowLengthMeters,
        bearingDeg = driftBearingDeg + 180.0 + DRIFT_ARROW_HALF_ANGLE_DEG
    )
    return Feature.fromGeometry(
        LineString.fromLngLats(
            listOf(
                leftWing,
                Point.fromLngLat(latestPoint.longitude, latestPoint.latitude),
                rightWing
            )
        )
    ).apply {
        addStringProperty(PROP_KIND, KIND_DRIFT_ARROW)
    }
}

private fun addMarkerFeature(
    features: MutableList<Feature>,
    point: OgnThermalPoint?,
    markerKind: String
) {
    if (point == null) return
    if (!isValidOgnThermalCoordinate(point.latitude, point.longitude)) return
    features += Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
        addStringProperty(PROP_KIND, KIND_MARKER)
        addStringProperty(PROP_MARKER_KIND, markerKind)
    }
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
