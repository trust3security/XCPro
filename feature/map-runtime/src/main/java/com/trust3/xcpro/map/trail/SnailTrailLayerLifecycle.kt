// Role: Install and remove MapLibre sources/layers used by the snail trail.
// Invariants: Owns layer lifecycle only; does not render feature data.
package com.trust3.xcpro.map.trail

import com.trust3.xcpro.map.BlueLocationOverlay
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

internal class SnailTrailLayerLifecycle(
    private val map: MapLibreMap
) {
    fun install(): Boolean {
        val style = map.style ?: return false
        style.addSource(GeoJsonSource(SnailTrailStyle.LINE_SOURCE_ID))
        style.addSource(GeoJsonSource(SnailTrailStyle.DOT_SOURCE_ID))
        style.addSource(GeoJsonSource(SnailTrailStyle.TAIL_SOURCE_ID))
        style.addSource(GeoJsonSource(SnailTrailStyle.DISPLAY_LINE_SOURCE_ID))
        style.addSource(GeoJsonSource(SnailTrailStyle.DISPLAY_DOT_SOURCE_ID))
        style.addSource(GeoJsonSource(SnailTrailStyle.DISPLAY_CONNECTOR_SOURCE_ID))

        val lineLayer = buildLineLayer(SnailTrailStyle.LINE_LAYER_ID, SnailTrailStyle.LINE_SOURCE_ID)
        val displayLineLayer =
            buildLineLayer(SnailTrailStyle.DISPLAY_LINE_LAYER_ID, SnailTrailStyle.DISPLAY_LINE_SOURCE_ID)
        val displayConnectorLayer =
            buildLineLayer(SnailTrailStyle.DISPLAY_CONNECTOR_LAYER_ID, SnailTrailStyle.DISPLAY_CONNECTOR_SOURCE_ID)
        val tailLayer = buildLineLayer(SnailTrailStyle.TAIL_LAYER_ID, SnailTrailStyle.TAIL_SOURCE_ID)
        val dotLayer = buildDotLayer(SnailTrailStyle.DOT_LAYER_ID, SnailTrailStyle.DOT_SOURCE_ID)
        val displayDotLayer =
            buildDotLayer(SnailTrailStyle.DISPLAY_DOT_LAYER_ID, SnailTrailStyle.DISPLAY_DOT_SOURCE_ID)

        if (style.getLayer(BlueLocationOverlay.LAYER_ID) != null) {
            style.addLayerBelow(lineLayer, BlueLocationOverlay.LAYER_ID)
            style.addLayerAbove(displayLineLayer, SnailTrailStyle.LINE_LAYER_ID)
            style.addLayerAbove(displayConnectorLayer, SnailTrailStyle.DISPLAY_LINE_LAYER_ID)
            style.addLayerAbove(tailLayer, SnailTrailStyle.DISPLAY_CONNECTOR_LAYER_ID)
            style.addLayerAbove(dotLayer, SnailTrailStyle.TAIL_LAYER_ID)
            style.addLayerAbove(displayDotLayer, SnailTrailStyle.DOT_LAYER_ID)
        } else {
            style.addLayer(lineLayer)
            style.addLayerAbove(displayLineLayer, SnailTrailStyle.LINE_LAYER_ID)
            style.addLayerAbove(displayConnectorLayer, SnailTrailStyle.DISPLAY_LINE_LAYER_ID)
            style.addLayerAbove(tailLayer, SnailTrailStyle.DISPLAY_CONNECTOR_LAYER_ID)
            style.addLayerAbove(dotLayer, SnailTrailStyle.TAIL_LAYER_ID)
            style.addLayerAbove(displayDotLayer, SnailTrailStyle.DOT_LAYER_ID)
        }
        return true
    }

    fun remove(): Boolean {
        val style = map.style ?: return false
        style.removeLayer(SnailTrailStyle.DISPLAY_DOT_LAYER_ID)
        style.removeLayer(SnailTrailStyle.DOT_LAYER_ID)
        style.removeLayer(SnailTrailStyle.TAIL_LAYER_ID)
        style.removeLayer(SnailTrailStyle.DISPLAY_CONNECTOR_LAYER_ID)
        style.removeLayer(SnailTrailStyle.DISPLAY_LINE_LAYER_ID)
        style.removeLayer(SnailTrailStyle.LINE_LAYER_ID)
        style.removeSource(SnailTrailStyle.DISPLAY_DOT_SOURCE_ID)
        style.removeSource(SnailTrailStyle.DOT_SOURCE_ID)
        style.removeSource(SnailTrailStyle.TAIL_SOURCE_ID)
        style.removeSource(SnailTrailStyle.DISPLAY_CONNECTOR_SOURCE_ID)
        style.removeSource(SnailTrailStyle.DISPLAY_LINE_SOURCE_ID)
        style.removeSource(SnailTrailStyle.LINE_SOURCE_ID)
        return true
    }

    private fun buildLineLayer(layerId: String, sourceId: String): LineLayer =
        LineLayer(layerId, sourceId).withProperties(
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
            lineWidth(Expression.get(SnailTrailStyle.PROP_WIDTH)),
            lineOpacity(1.0f)
        )

    private fun buildDotLayer(layerId: String, sourceId: String): CircleLayer =
        CircleLayer(layerId, sourceId).withProperties(
            circleRadius(Expression.get(SnailTrailStyle.PROP_RADIUS)),
            circleOpacity(1.0f)
        )
}
