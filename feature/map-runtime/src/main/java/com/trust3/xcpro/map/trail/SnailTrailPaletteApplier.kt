// Role: Apply snail-trail palette changes to MapLibre trail layers.
// Invariants: Owns only layer paint color updates, never trail source data.
package com.trust3.xcpro.map.trail

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor

internal class SnailTrailPaletteApplier(
    private val map: MapLibreMap
) {
    private var currentType: TrailType? = null

    fun updateIfNeeded(type: TrailType) {
        if (currentType == type) return
        val style = map.style ?: return
        val lineLayer = style.getLayerAs<LineLayer>(SnailTrailStyle.LINE_LAYER_ID) ?: return
        val dotLayer = style.getLayerAs<CircleLayer>(SnailTrailStyle.DOT_LAYER_ID) ?: return
        val palette = SnailTrailPalette.colorExpression(type)

        lineLayer.setProperties(lineColor(palette))
        style.getLayerAs<LineLayer>(SnailTrailStyle.TAIL_LAYER_ID)?.setProperties(lineColor(palette))
        style.getLayerAs<LineLayer>(SnailTrailStyle.DISPLAY_LINE_LAYER_ID)?.setProperties(lineColor(palette))
        dotLayer.setProperties(circleColor(palette))
        style.getLayerAs<CircleLayer>(SnailTrailStyle.DISPLAY_DOT_LAYER_ID)?.setProperties(circleColor(palette))
        currentType = type
    }

    fun reset() {
        currentType = null
    }
}
