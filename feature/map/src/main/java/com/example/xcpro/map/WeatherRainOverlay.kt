package com.example.xcpro.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.example.xcpro.weather.rain.WeatherRainTileUrlBuilder
import com.example.xcpro.weather.rain.WEATHER_RAIN_MAX_ZOOM
import com.example.xcpro.weather.rain.WEATHER_RAIN_MIN_ZOOM
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.clampWeatherRainOpacity
import com.example.xcpro.weather.rain.weatherRainTileAttributionHtml
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

class WeatherRainOverlay(
    private val map: MapLibreMap
) {
    private var activeFrameTimeEpochSec: Long? = null
    private var transitionAnimator: ValueAnimator? = null
    private val cachedFrames: LinkedHashMap<Long, CachedWeatherFrame> = linkedMapOf()

    fun render(
        frameSelection: WeatherRainFrameSelection,
        opacity: Float,
        transitionDurationMs: Long
    ) {
        val style = map.style ?: return
        val resolvedOpacity = clampWeatherRainOpacity(opacity)
        val resolvedTransitionDurationMs = transitionDurationMs.coerceAtLeast(0L)
        val urlTemplate = WeatherRainTileUrlBuilder.buildUrlTemplate(frameSelection)
        val frameTimeEpochSec = frameSelection.frameTimeEpochSec
        val cachedFrame = ensureCachedFrame(
            style = style,
            frameTimeEpochSec = frameTimeEpochSec,
            urlTemplate = urlTemplate,
            tileSizePx = frameSelection.renderOptions.normalizedTileSizePx
        )
        val previousFrameTimeEpochSec = activeFrameTimeEpochSec
        cancelTransition()

        if (previousFrameTimeEpochSec == null || previousFrameTimeEpochSec == frameTimeEpochSec) {
            setOnlyFrameVisible(
                style = style,
                visibleFrameTimeEpochSec = frameTimeEpochSec,
                opacity = resolvedOpacity
            )
            activeFrameTimeEpochSec = frameTimeEpochSec
            pruneFrameCache(
                style = style,
                keepFrameTimeEpochSec = frameTimeEpochSec
            )
            return
        }

        val previousLayerId = cachedFrames[previousFrameTimeEpochSec]?.layerId
        if (previousLayerId == null) {
            setOnlyFrameVisible(
                style = style,
                visibleFrameTimeEpochSec = frameTimeEpochSec,
                opacity = resolvedOpacity
            )
            activeFrameTimeEpochSec = frameTimeEpochSec
            pruneFrameCache(
                style = style,
                keepFrameTimeEpochSec = frameTimeEpochSec
            )
            return
        }

        setLayerOpacity(
            style = style,
            layerId = previousLayerId,
            opacity = resolvedOpacity
        )
        setLayerOpacity(
            style = style,
            layerId = cachedFrame.layerId,
            opacity = 0f
        )
        activeFrameTimeEpochSec = frameTimeEpochSec
        if (resolvedTransitionDurationMs <= 0L) {
            setLayerOpacity(style, previousLayerId, 0f)
            setLayerOpacity(style, cachedFrame.layerId, resolvedOpacity)
            pruneFrameCache(
                style = style,
                keepFrameTimeEpochSec = frameTimeEpochSec
            )
            return
        }
        startCrossFade(
            previousFrameTimeEpochSec = previousFrameTimeEpochSec,
            previousLayerId = previousLayerId,
            currentFrameTimeEpochSec = frameTimeEpochSec,
            currentLayerId = cachedFrame.layerId,
            targetOpacity = resolvedOpacity,
            durationMs = resolvedTransitionDurationMs
        )
    }

    fun clear() {
        cancelTransition()
        val style = map.style ?: return
        cachedFrames.values.forEach { cachedFrame ->
            removeFrame(style, cachedFrame)
        }
        cachedFrames.clear()
        removeLegacyArtifacts(style)
        activeFrameTimeEpochSec = null
    }

    fun cleanup() = clear()

    private fun startCrossFade(
        previousFrameTimeEpochSec: Long,
        previousLayerId: String,
        currentFrameTimeEpochSec: Long,
        currentLayerId: String,
        targetOpacity: Float,
        durationMs: Long
    ) {
        var wasCanceled = false
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener { valueAnimator ->
                val style = map.style ?: return@addUpdateListener
                val progress = (valueAnimator.animatedValue as? Float) ?: return@addUpdateListener
                val previousOpacity = targetOpacity * (1f - progress)
                val currentOpacity = targetOpacity * progress
                setLayerOpacity(style, previousLayerId, previousOpacity)
                setLayerOpacity(style, currentLayerId, currentOpacity)
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        wasCanceled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (transitionAnimator === animation) {
                            transitionAnimator = null
                        }
                        if (wasCanceled) {
                            return
                        }
                        val style = map.style ?: return
                        setLayerOpacity(style, previousLayerId, 0f)
                        setLayerOpacity(style, currentLayerId, targetOpacity)
                        pruneFrameCache(
                            style = style,
                            keepFrameTimeEpochSec = currentFrameTimeEpochSec,
                            protectedFrameTimeEpochSec = previousFrameTimeEpochSec
                        )
                    }
                }
            )
        }
        transitionAnimator = animator
        animator.start()
    }

    private fun ensureCachedFrame(
        style: Style,
        frameTimeEpochSec: Long,
        urlTemplate: String,
        tileSizePx: Int
    ): CachedWeatherFrame {
        val existing = cachedFrames[frameTimeEpochSec]
        if (
            existing != null &&
            existing.urlTemplate == urlTemplate &&
            style.getSource(existing.sourceId) is RasterSource &&
            style.getLayer(existing.layerId) is RasterLayer
        ) {
            return existing
        }
        val sourceId = sourceIdForFrameTime(frameTimeEpochSec)
        val layerId = layerIdForFrameTime(frameTimeEpochSec)
        removeFrame(
            style = style,
            cachedFrame = CachedWeatherFrame(
                frameTimeEpochSec = frameTimeEpochSec,
                sourceId = sourceId,
                layerId = layerId,
                urlTemplate = urlTemplate
            )
        )
        val tileSet = TileSet("2.2.0", urlTemplate).apply {
            minZoom = WEATHER_RAIN_MIN_ZOOM
            maxZoom = WEATHER_RAIN_MAX_ZOOM
            attribution = weatherRainTileAttributionHtml()
        }
        style.addSource(RasterSource(sourceId, tileSet, tileSizePx))
        val layer = RasterLayer(layerId, sourceId)
            .withProperties(rasterOpacity(0f))
        addLayerBelowAnchors(style, layer)
        val rebuilt = CachedWeatherFrame(
            frameTimeEpochSec = frameTimeEpochSec,
            sourceId = sourceId,
            layerId = layerId,
            urlTemplate = urlTemplate
        )
        cachedFrames[frameTimeEpochSec] = rebuilt
        return rebuilt
    }

    private fun addLayerBelowAnchors(style: Style, layer: Layer) {
        for (anchor in WEATHER_ANCHOR_LAYER_IDS) {
            if (style.getLayer(anchor) != null) {
                style.addLayerBelow(layer, anchor)
                return
            }
        }
        style.addLayer(layer)
    }

    private fun pruneFrameCache(
        style: Style,
        keepFrameTimeEpochSec: Long,
        protectedFrameTimeEpochSec: Long? = null
    ) {
        val iterator = cachedFrames.entries.iterator()
        while (cachedFrames.size > WEATHER_RAIN_MAX_CACHED_FRAMES && iterator.hasNext()) {
            val (frameTimeEpochSec, cachedFrame) = iterator.next()
            if (
                frameTimeEpochSec == keepFrameTimeEpochSec ||
                frameTimeEpochSec == activeFrameTimeEpochSec ||
                frameTimeEpochSec == protectedFrameTimeEpochSec
            ) {
                continue
            }
            removeFrame(style, cachedFrame)
            iterator.remove()
        }
    }

    private fun sourceIdForFrameTime(frameTimeEpochSec: Long): String {
        return "$WEATHER_RAIN_SOURCE_ID_PREFIX-$frameTimeEpochSec"
    }

    private fun layerIdForFrameTime(frameTimeEpochSec: Long): String {
        return "$WEATHER_RAIN_LAYER_ID_PREFIX-$frameTimeEpochSec"
    }

    private fun removeFrame(
        style: Style,
        cachedFrame: CachedWeatherFrame
    ) {
        runCatching { style.removeLayer(cachedFrame.layerId) }
        runCatching { style.removeSource(cachedFrame.sourceId) }
    }

    private fun removeLegacyArtifacts(style: Style) {
        runCatching { style.removeLayer(WEATHER_RAIN_LEGACY_LAYER_ID) }
        runCatching { style.removeSource(WEATHER_RAIN_LEGACY_SOURCE_ID) }
    }

    private fun cancelTransition() {
        transitionAnimator?.cancel()
        transitionAnimator = null
    }

    private fun setOnlyFrameVisible(
        style: Style,
        visibleFrameTimeEpochSec: Long,
        opacity: Float
    ) {
        cachedFrames.forEach { (frameTimeEpochSec, cachedFrame) ->
            val nextOpacity = if (frameTimeEpochSec == visibleFrameTimeEpochSec) opacity else 0f
            setLayerOpacity(style, cachedFrame.layerId, nextOpacity)
        }
    }

    private fun setLayerOpacity(
        style: Style,
        layerId: String,
        opacity: Float
    ) {
        val rasterLayer = style.getLayer(layerId) as? RasterLayer ?: return
        rasterLayer.setProperties(rasterOpacity(opacity.coerceIn(0f, 1f)))
    }

    companion object {
        private const val WEATHER_RAIN_MAX_CACHED_FRAMES = 24
        private const val WEATHER_RAIN_SOURCE_ID_PREFIX = "weather-rain-source"
        private const val WEATHER_RAIN_LAYER_ID_PREFIX = "weather-rain-layer"
        private const val WEATHER_RAIN_LEGACY_SOURCE_ID = "weather-rain-source"
        private const val WEATHER_RAIN_LEGACY_LAYER_ID = "weather-rain-layer"
        private val WEATHER_ANCHOR_LAYER_IDS: List<String> = buildList {
            addAll(forecastLayerAnchors())
            addAll(baseOverlayAnchors())
        }

        private fun forecastLayerAnchors(): List<String> = buildList {
            val namespaces = listOf("primary", "secondary", "wind")
            val barbBuckets = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 50, 60)
            for (namespace in namespaces) {
                add("forecast-$namespace-raster-layer")
                add("forecast-$namespace-vector-fill-layer")
                add("forecast-$namespace-wind-symbol-layer")
                for (bucket in barbBuckets) {
                    add("forecast-$namespace-wind-barb-layer-$bucket")
                }
            }
        }

        private fun baseOverlayAnchors(): List<String> = listOf(
            "airspace-layer",
            "racing-course-line",
            "racing-turnpoint-areas-fill",
            "racing-waypoints",
            "aat-task-line",
            "aat-areas-layer",
            "aat-waypoints",
            "adsb-traffic-icon-layer",
            "ogn-thermal-label-layer",
            "ogn-thermal-circle-layer",
            "ogn-traffic-icon-layer",
            BlueLocationOverlay.LAYER_ID
        )
    }

    private data class CachedWeatherFrame(
        val frameTimeEpochSec: Long,
        val sourceId: String,
        val layerId: String,
        val urlTemplate: String
    )
}
