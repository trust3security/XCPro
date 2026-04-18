package com.trust3.xcpro.map.trail

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

internal class SnailTrailTailRenderer(
    private val map: MapLibreMap,
    private val logger: SnailTrailLogger,
    private val tailBuilder: SnailTrailTailBuilder,
    private val metersPerPixelProvider: MapLibreMetersPerPixelProvider,
    private val iconSizePx: Float,
    private val renderSequenceProvider: () -> Long
) {

    fun renderTail(
        lastPoint: TrailPoint?,
        settings: TrailSettings,
        currentLocation: LatLng?,
        currentTimeMillis: Long,
        isCircling: Boolean,
        currentZoom: Float,
        tailCache: SnailTrailStyleCache?,
        frameId: Long?,
        updatePalette: () -> Unit
    ) {
        val style = map.style ?: return
        val tailSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.TAIL_SOURCE_ID) ?: return
        if (settings.length == TrailLength.OFF || currentLocation == null || lastPoint == null) {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            return
        }

        updatePalette()
        val cache = tailCache ?: return
        val minTime = SnailTrailMath.minTimeMillis(settings.length, currentTimeMillis)
        val renderPoints = SnailTrailMath.filterPoints(
            points = listOf(lastPoint),
            minTimeMillis = minTime,
            currentTimeMillis = currentTimeMillis,
            isCircling = isCircling,
            settings = settings
        )
        val renderPoint = renderPoints.firstOrNull()
        if (renderPoint == null) {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            return
        }

        val metersPerPixel = metersPerPixelProvider.metersPerPixel(currentLocation.latitude, currentZoom)
        val segment = tailBuilder.build(
            SnailTrailTailBuilder.Input(
                lastPoint = renderPoint,
                settings = settings,
                currentLocation = TrailGeoPoint(currentLocation.latitude, currentLocation.longitude),
                currentTimeMillis = currentTimeMillis,
                styleCache = cache,
                metersPerPixel = metersPerPixel,
                iconSizePx = iconSizePx
            )
        )
        if (segment == null) {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            return
        }

        val tailFeature = SnailTrailFeatureBuilder.lineFeature(
            segment.start,
            segment.end,
            segment.colorIndex,
            segment.width
        )
        tailSource.setGeoJson(FeatureCollection.fromFeatures(listOf(tailFeature)))
        val logId = frameId ?: renderSequenceProvider()
        logger.logSegment(
            renderId = logId,
            index = 0,
            kind = "line-to-current frame=${frameId ?: -1}",
            start = segment.start,
            end = segment.end,
            colorIndex = segment.colorIndex,
            type = settings.type,
            width = segment.width,
            radius = null
        )
    }

    fun renderTailInternal(
        lastPoint: RenderPoint?,
        settings: TrailSettings,
        currentLocation: LatLng?,
        currentTimeMillis: Long,
        tailCache: SnailTrailStyleCache?,
        frameId: Long?,
        metersPerPixel: Double,
        renderId: Long?
    ) {
        if (lastPoint == null || currentLocation == null) return
        val cache = tailCache ?: return
        val style = map.style ?: return
        val tailSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.TAIL_SOURCE_ID) ?: return
        val segment = tailBuilder.build(
            SnailTrailTailBuilder.Input(
                lastPoint = lastPoint,
                settings = settings,
                currentLocation = TrailGeoPoint(currentLocation.latitude, currentLocation.longitude),
                currentTimeMillis = currentTimeMillis,
                styleCache = cache,
                metersPerPixel = metersPerPixel,
                iconSizePx = iconSizePx
            )
        )
        if (segment == null) {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            return
        }
        val tailFeature = SnailTrailFeatureBuilder.lineFeature(
            segment.start,
            segment.end,
            segment.colorIndex,
            segment.width
        )
        tailSource.setGeoJson(FeatureCollection.fromFeatures(listOf(tailFeature)))
        val logId = frameId ?: renderId ?: renderSequenceProvider()
        logger.logSegment(
            renderId = logId,
            index = 0,
            kind = "line-to-current frame=${frameId ?: -1}",
            start = segment.start,
            end = segment.end,
            colorIndex = segment.colorIndex,
            type = settings.type,
            width = segment.width,
            radius = null
        )
    }
}
