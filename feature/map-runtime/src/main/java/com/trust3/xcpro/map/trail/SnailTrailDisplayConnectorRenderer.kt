// Role: Render the transient display-pose connector from stored trail to aircraft pose.
// Invariants: Writes only the display connector source and never mutates trail storage.
package com.trust3.xcpro.map.trail

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

internal class SnailTrailDisplayConnectorRenderer(
    private val map: MapLibreMap,
    private val tailBuilder: SnailTrailTailBuilder,
    private val metersPerPixelProvider: MetersPerPixelProvider,
    private val iconSizePx: Float
) {
    fun render(
        lastPoint: RenderPoint?,
        settings: TrailSettings,
        currentLocation: LatLng?,
        currentTimeMillis: Long,
        currentZoom: Float,
        styleCache: SnailTrailStyleCache?
    ) {
        if (settings.length == TrailLength.OFF || lastPoint == null || currentLocation == null || styleCache == null) {
            clear()
            return
        }
        if (!TrailGeo.isValidCoordinate(currentLocation.latitude, currentLocation.longitude)) {
            clear()
            return
        }
        val style = map.style ?: return
        val segment = tailBuilder.build(
            SnailTrailTailBuilder.Input(
                lastPoint = lastPoint,
                settings = settings,
                currentLocation = TrailGeoPoint(currentLocation.latitude, currentLocation.longitude),
                currentTimeMillis = currentTimeMillis,
                styleCache = styleCache,
                metersPerPixel = metersPerPixelProvider.metersPerPixel(currentLocation.latitude, currentZoom),
                iconSizePx = iconSizePx
            )
        )
        val feature = segment?.let {
            SnailTrailFeatureBuilder.lineFeature(it.start, it.end, it.colorIndex, it.width)
        }
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DISPLAY_CONNECTOR_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(feature)))
    }

    fun clear() {
        val style = map.style ?: return
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DISPLAY_CONNECTOR_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }
}
