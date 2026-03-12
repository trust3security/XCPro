package com.example.xcpro.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX
import com.example.xcpro.forecast.clampSkySightSatelliteHistoryFrames
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource

class SkySightSatelliteOverlay(
    private val map: MapLibreMap,
    private val nowUtcMsProvider: () -> Long = { TimeBridge.nowWallMs() }
) {
    private val animationHandler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null

    private var activeFrameCount: Int = 1
    private var activeFrameIndex: Int = 0
    private var activeSatellite: Boolean = false
    private var activeRadar: Boolean = false
    private var activeLightning: Boolean = false
    private var lastRenderedFrameEpochs: List<Long> = emptyList()
    private var lastRenderedSatellite: Boolean = false
    private var lastRenderedRadar: Boolean = false
    private var lastRenderedLightning: Boolean = false
    private var lastStyleIdentity: Int? = null

    fun render(config: SkySightSatelliteRenderConfig) {
        val style = map.style ?: return
        val hasAnyLayerEnabled =
            config.showSatelliteImagery || config.showRadar || config.showLightning
        if (!config.enabled || !hasAnyLayerEnabled) {
            clear()
            return
        }

        val frameCount = clampSkySightSatelliteHistoryFrames(config.historyFrameCount)
        val baseFrameEpochSec = resolveBaseFrameEpochSec(
            referenceTimeUtcMs = config.referenceTimeUtcMs,
            frameCount = frameCount
        )
        val frameEpochs = buildFrameEpochs(
            baseFrameEpochSec = baseFrameEpochSec,
            frameCount = frameCount
        )
        val styleIdentity = System.identityHashCode(style)

        stopAnimation()
        if (
            shouldRebuildSourcesAndLayers(
                style = style,
                frameEpochs = frameEpochs,
                showSatelliteImagery = config.showSatelliteImagery,
                showRadar = config.showRadar,
                showLightning = config.showLightning,
                styleIdentity = styleIdentity
            )
        ) {
            rebuildSourcesAndLayers(style, frameEpochs, config)
            lastRenderedFrameEpochs = frameEpochs
            lastRenderedSatellite = config.showSatelliteImagery
            lastRenderedRadar = config.showRadar
            lastRenderedLightning = config.showLightning
            lastStyleIdentity = styleIdentity
        }

        activeFrameCount = frameCount
        activeFrameIndex = resolveInitialFrameIndex(
            animate = config.animate,
            frameCount = frameCount
        )
        activeSatellite = config.showSatelliteImagery
        activeRadar = config.showRadar
        activeLightning = config.showLightning

        if (config.animate && frameCount > 1) {
            startAnimation()
        }
        applyFrameOpacities(style)
    }

    fun clear() {
        stopAnimation()
        activeFrameCount = 1
        activeFrameIndex = 0
        activeSatellite = false
        activeRadar = false
        activeLightning = false
        lastRenderedFrameEpochs = emptyList()
        lastRenderedSatellite = false
        lastRenderedRadar = false
        lastRenderedLightning = false
        lastStyleIdentity = null
        val style = map.style ?: return
        removeAllKnownSourcesAndLayers(style)
    }

    fun cleanup() = clear()

    private fun startAnimation() {
        if (animationRunnable != null) return
        val runnable = object : Runnable {
            override fun run() {
                val style = map.style ?: run {
                    stopAnimation()
                    return
                }
                if (activeFrameCount <= 1) {
                    stopAnimation()
                    return
                }
                activeFrameIndex = nextFrameIndex(
                    currentFrameIndex = activeFrameIndex,
                    frameCount = activeFrameCount
                )
                applyFrameOpacities(style)
                animationHandler.postDelayed(this, SKY_SIGHT_ANIMATION_INTERVAL_MS)
            }
        }
        animationRunnable = runnable
        animationHandler.postDelayed(runnable, SKY_SIGHT_ANIMATION_INTERVAL_MS)
    }

    private fun stopAnimation() {
        animationRunnable?.let { runnable ->
            animationHandler.removeCallbacks(runnable)
        }
        animationRunnable = null
    }

    private fun rebuildSourcesAndLayers(
        style: Style,
        frameEpochs: List<Long>,
        config: SkySightSatelliteRenderConfig
    ) {
        removeAllKnownSourcesAndLayers(style)
        ensureLightningIcon(style)

        frameEpochs.forEachIndexed { index, frameEpochSec ->
            if (config.showSatelliteImagery) {
                val tileSet = TileSet(
                    "2.2.0",
                    buildSatelliteTileTemplate(frameEpochSec)
                ).apply {
                    minZoom = SKY_SIGHT_MIN_ZOOM
                    maxZoom = SKY_SIGHT_SATELLITE_MAX_ZOOM
                    attribution = SKY_SIGHT_ATTRIBUTION
                }
                style.addSource(
                    RasterSource(
                        satelliteSourceId(index),
                        tileSet,
                        SKY_SIGHT_TILE_SIZE_PX
                    )
                )
                val layer = RasterLayer(
                    satelliteLayerId(index),
                    satelliteSourceId(index)
                ).withProperties(rasterOpacity(0f))
                addLayerBelowAnchors(style, layer)
            }

            if (config.showRadar) {
                val tileSet = TileSet(
                    "2.2.0",
                    buildRadarTileTemplate(frameEpochSec)
                ).apply {
                    minZoom = SKY_SIGHT_MIN_ZOOM
                    maxZoom = SKY_SIGHT_RADAR_MAX_ZOOM
                    attribution = SKY_SIGHT_ATTRIBUTION
                }
                style.addSource(
                    RasterSource(
                        radarSourceId(index),
                        tileSet,
                        SKY_SIGHT_TILE_SIZE_PX
                    )
                )
                val layer = RasterLayer(
                    radarLayerId(index),
                    radarSourceId(index)
                ).withProperties(rasterOpacity(0f))
                addLayerBelowAnchors(style, layer)
            }

            if (config.showLightning) {
                val tileSet = TileSet(
                    "2.2.0",
                    buildLightningTileTemplate(frameEpochSec)
                ).apply {
                    minZoom = SKY_SIGHT_MIN_ZOOM
                    maxZoom = SKY_SIGHT_LIGHTNING_MAX_ZOOM
                    attribution = SKY_SIGHT_ATTRIBUTION
                }
                style.addSource(
                    VectorSource(
                        lightningSourceId(index),
                        tileSet
                    )
                )
                val layer = SymbolLayer(
                    lightningLayerId(index),
                    lightningSourceId(index)
                ).apply {
                    setSourceLayer(SKY_SIGHT_LIGHTNING_SOURCE_LAYER)
                }.withProperties(
                    iconImage(SKY_SIGHT_LIGHTNING_ICON_ID),
                    iconSize(SKY_SIGHT_LIGHTNING_ICON_SIZE),
                    iconAllowOverlap(false),
                    iconIgnorePlacement(false),
                    iconOpacity(0f)
                )
                addLayerBelowAnchors(style, layer)
            }
        }
    }

    private fun shouldRebuildSourcesAndLayers(
        style: Style,
        frameEpochs: List<Long>,
        showSatelliteImagery: Boolean,
        showRadar: Boolean,
        showLightning: Boolean,
        styleIdentity: Int
    ): Boolean {
        if (lastStyleIdentity != styleIdentity) return true
        if (lastRenderedFrameEpochs != frameEpochs) return true
        if (lastRenderedSatellite != showSatelliteImagery) return true
        if (lastRenderedRadar != showRadar) return true
        if (lastRenderedLightning != showLightning) return true
        if (showSatelliteImagery && style.getLayer(satelliteLayerId(0)) == null) return true
        if (showRadar && style.getLayer(radarLayerId(0)) == null) return true
        if (showLightning && style.getLayer(lightningLayerId(0)) == null) return true
        return false
    }

    private fun applyFrameOpacities(style: Style) {
        for (index in 0 until FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX) {
            val visible = index < activeFrameCount && index == activeFrameIndex
            val satelliteOpacity = if (visible && activeSatellite) SKY_SIGHT_SATELLITE_OPACITY else 0f
            val radarOpacity = if (visible && activeRadar) SKY_SIGHT_RADAR_OPACITY else 0f
            val lightningOpacity = if (visible && activeLightning) SKY_SIGHT_LIGHTNING_OPACITY else 0f

            setRasterLayerOpacity(style, satelliteLayerId(index), satelliteOpacity)
            setRasterLayerOpacity(style, radarLayerId(index), radarOpacity)
            setSymbolLayerOpacity(style, lightningLayerId(index), lightningOpacity)
        }
    }

    private fun setRasterLayerOpacity(style: Style, layerId: String, opacity: Float) {
        val layer = style.getLayer(layerId) as? RasterLayer ?: return
        layer.setProperties(rasterOpacity(opacity.coerceIn(0f, 1f)))
    }

    private fun setSymbolLayerOpacity(style: Style, layerId: String, opacity: Float) {
        val layer = style.getLayer(layerId) as? SymbolLayer ?: return
        layer.setProperties(iconOpacity(opacity.coerceIn(0f, 1f)))
    }

    private fun ensureLightningIcon(style: Style) {
        val existing = runCatching { style.getImage(SKY_SIGHT_LIGHTNING_ICON_ID) }.getOrNull()
        if (existing != null) return
        style.addImage(SKY_SIGHT_LIGHTNING_ICON_ID, createLightningBitmap())
    }

    private fun createLightningBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(
            SKY_SIGHT_LIGHTNING_ICON_SIZE_PX,
            SKY_SIGHT_LIGHTNING_ICON_SIZE_PX,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = SKY_SIGHT_LIGHTNING_FILL_COLOR
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = SKY_SIGHT_LIGHTNING_STROKE_WIDTH_PX
            color = SKY_SIGHT_LIGHTNING_STROKE_COLOR
        }
        val path = Path().apply {
            moveTo(30f, 4f)
            lineTo(12f, 34f)
            lineTo(26f, 34f)
            lineTo(20f, 60f)
            lineTo(50f, 26f)
            lineTo(34f, 26f)
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        return bitmap
    }

    private fun removeAllKnownSourcesAndLayers(style: Style) {
        for (index in 0 until FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX) {
            runCatching { style.removeLayer(satelliteLayerId(index)) }
            runCatching { style.removeLayer(radarLayerId(index)) }
            runCatching { style.removeLayer(lightningLayerId(index)) }
            runCatching { style.removeSource(satelliteSourceId(index)) }
            runCatching { style.removeSource(radarSourceId(index)) }
            runCatching { style.removeSource(lightningSourceId(index)) }
        }
    }

    private fun resolveBaseFrameEpochSec(referenceTimeUtcMs: Long?, frameCount: Int): Long {
        val safeFrameCount = clampSkySightSatelliteHistoryFrames(frameCount)
            .coerceAtLeast(1)
        val nowUtcMs = nowUtcMsProvider()
        val latestAvailableUtcMs = (nowUtcMs - SKY_SIGHT_SOURCE_DELAY_MS).coerceAtLeast(0L)
        val frameStepMs = FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES * 60_000L
        val latestSteppedUtcMs = (latestAvailableUtcMs / frameStepMs) * frameStepMs
        val maxHistorySpanMs = (FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_MAX - 1) * frameStepMs
        val renderedHistorySpanMs = (safeFrameCount - 1) * frameStepMs
        val earliestRenderableReferenceUtcMs = (
            latestSteppedUtcMs - maxHistorySpanMs + renderedHistorySpanMs
            ).coerceAtLeast(0L)
        val candidateUtcMs = referenceTimeUtcMs ?: latestAvailableUtcMs
        val clampedUtcMs = candidateUtcMs.coerceIn(
            minimumValue = earliestRenderableReferenceUtcMs,
            maximumValue = latestSteppedUtcMs
        )
        val steppedUtcMs = (clampedUtcMs / frameStepMs) * frameStepMs
        return (steppedUtcMs / 1_000L).coerceAtLeast(0L)
    }

    private fun buildFrameEpochs(baseFrameEpochSec: Long, frameCount: Int): List<Long> {
        val safeFrameCount = clampSkySightSatelliteHistoryFrames(frameCount)
            .coerceAtLeast(1)
        val frameStepSec = FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES * 60L
        return (0 until safeFrameCount).map { index ->
            val offset = (safeFrameCount - 1 - index) * frameStepSec
            (baseFrameEpochSec - offset).coerceAtLeast(0L)
        }
    }

    private fun resolveInitialFrameIndex(animate: Boolean, frameCount: Int): Int {
        val safeFrameCount = clampSkySightSatelliteHistoryFrames(frameCount)
            .coerceAtLeast(1)
        return if (animate && safeFrameCount > 1) {
            0
        } else {
            safeFrameCount - 1
        }
    }

    private fun nextFrameIndex(currentFrameIndex: Int, frameCount: Int): Int {
        val safeFrameCount = clampSkySightSatelliteHistoryFrames(frameCount)
            .coerceAtLeast(1)
        val safeCurrent = currentFrameIndex.coerceIn(0, safeFrameCount - 1)
        return (safeCurrent + 1) % safeFrameCount
    }

    private fun buildSatelliteTileTemplate(epochSec: Long): String {
        val formattedTime = SKY_SIGHT_TIME_FORMATTER.format(Instant.ofEpochSecond(epochSec))
        return "https://satellite.skysight.io/tiles/{z}/{x}/{y}@2x?date=$formattedTime&mtg=true"
    }

    private fun buildRadarTileTemplate(epochSec: Long): String {
        val formattedTime = SKY_SIGHT_TIME_FORMATTER.format(Instant.ofEpochSecond(epochSec))
        return "https://satellite.skysight.io/radar/{z}/{x}/{y}@2x?date=$formattedTime"
    }

    private fun buildLightningTileTemplate(epochSec: Long): String {
        val formattedTime = SKY_SIGHT_TIME_FORMATTER.format(Instant.ofEpochSecond(epochSec))
        return "https://satellite.skysight.io/lightning/{z}/{x}/{y}@2x?date=$formattedTime"
    }

    private fun addLayerBelowAnchors(style: Style, layer: Layer) {
        val forecastWindBarbAnchor = lowestLayerIdWithPrefix(
            style = style,
            prefix = FORECAST_WIND_BARB_LAYER_PREFIX
        )
        if (forecastWindBarbAnchor != null) {
            style.addLayerBelow(layer, forecastWindBarbAnchor)
            return
        }
        val weatherRainFrameAnchor = lowestLayerIdWithPrefix(
            style = style,
            prefix = WEATHER_RAIN_FRAME_LAYER_PREFIX
        )
        if (weatherRainFrameAnchor != null) {
            style.addLayerBelow(layer, weatherRainFrameAnchor)
            return
        }
        for (anchor in SKY_SIGHT_ANCHOR_LAYER_IDS) {
            if (style.getLayer(anchor) != null) {
                style.addLayerBelow(layer, anchor)
                return
            }
        }
        style.addLayer(layer)
    }

    private fun lowestLayerIdWithPrefix(style: Style, prefix: String): String? {
        val layers = runCatching { style.layers.toList() }.getOrElse { emptyList() }
        return layers.firstOrNull { layer -> layer.id.startsWith(prefix) }?.id
    }

    private fun satelliteSourceId(index: Int): String = "skysight-sat-source-$index"
    private fun satelliteLayerId(index: Int): String = "skysight-sat-layer-$index"
    private fun radarSourceId(index: Int): String = "skysight-radar-source-$index"
    private fun radarLayerId(index: Int): String = "skysight-radar-layer-$index"
    private fun lightningSourceId(index: Int): String = "skysight-lightning-source-$index"
    private fun lightningLayerId(index: Int): String = "skysight-lightning-layer-$index"

    companion object {
        private const val SKY_SIGHT_SOURCE_DELAY_MS = 15 * 60_000L
        private const val SKY_SIGHT_ANIMATION_INTERVAL_MS = 800L
        private const val SKY_SIGHT_TILE_SIZE_PX = 512
        private const val SKY_SIGHT_MIN_ZOOM = 0f
        private const val SKY_SIGHT_SATELLITE_MAX_ZOOM = 8f
        private const val SKY_SIGHT_RADAR_MAX_ZOOM = 8f
        private const val SKY_SIGHT_LIGHTNING_MAX_ZOOM = 4f
        private const val SKY_SIGHT_SATELLITE_OPACITY = 1f
        private const val SKY_SIGHT_RADAR_OPACITY = 0.4f
        private const val SKY_SIGHT_LIGHTNING_OPACITY = 1f
        private const val SKY_SIGHT_LIGHTNING_ICON_ID = "skysight-lightning-icon"
        private const val SKY_SIGHT_LIGHTNING_SOURCE_LAYER = "lightning"
        private const val SKY_SIGHT_LIGHTNING_ICON_SIZE = 1f
        private const val SKY_SIGHT_LIGHTNING_ICON_SIZE_PX = 64
        private const val SKY_SIGHT_LIGHTNING_FILL_COLOR = -11180
        private const val SKY_SIGHT_LIGHTNING_STROKE_COLOR = -16777216
        private const val SKY_SIGHT_LIGHTNING_STROKE_WIDTH_PX = 3f
        private const val SKY_SIGHT_ATTRIBUTION = "SkySight satellite"
        private const val FORECAST_WIND_BARB_LAYER_PREFIX = "forecast-wind-wind-barb-layer-"
        private const val WEATHER_RAIN_FRAME_LAYER_PREFIX = "weather-rain-layer-"

        private val SKY_SIGHT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy/MM/dd/HHmm", Locale.US)
            .withZone(ZoneOffset.UTC)

        private val SKY_SIGHT_ANCHOR_LAYER_IDS: List<String> = listOf(
            "forecast-primary-raster-layer",
            "forecast-primary-vector-fill-layer",
            "forecast-wind-raster-layer",
            "forecast-wind-symbol-layer",
            "weather-rain-layer",
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
            MAP_BLUE_LOCATION_LAYER_ID
        )
    }
}

data class SkySightSatelliteRenderConfig(
    val enabled: Boolean,
    val showSatelliteImagery: Boolean,
    val showRadar: Boolean,
    val showLightning: Boolean,
    val animate: Boolean,
    val historyFrameCount: Int,
    val referenceTimeUtcMs: Long?
)
