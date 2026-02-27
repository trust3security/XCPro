package com.example.xcpro.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
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
    private val nowUtcMsProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val animationHandler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null

    private var activeFrameCount: Int = 1
    private var activeFrameIndex: Int = 0
    private var activeSatellite: Boolean = false
    private var activeRadar: Boolean = false
    private var activeLightning: Boolean = false

    fun render(config: SkySightSatelliteRenderConfig) {
        val style = map.style ?: return
        val hasAnyLayerEnabled =
            config.showSatelliteImagery || config.showRadar || config.showLightning
        if (!config.enabled || !hasAnyLayerEnabled) {
            clear()
            return
        }

        val frameCount = clampSkySightSatelliteHistoryFrames(config.historyFrameCount)
        val baseFrameEpochSec = resolveBaseFrameEpochSec(config.referenceTimeUtcMs)
        val frameEpochs = buildFrameEpochs(
            baseFrameEpochSec = baseFrameEpochSec,
            frameCount = frameCount
        )

        stopAnimation()
        rebuildSourcesAndLayers(style, frameEpochs, config)

        activeFrameCount = frameCount
        activeFrameIndex = activeFrameIndex.coerceIn(0, frameCount - 1)
        activeSatellite = config.showSatelliteImagery
        activeRadar = config.showRadar
        activeLightning = config.showLightning

        if (config.animate && frameCount > 1) {
            startAnimation()
        } else {
            activeFrameIndex = 0
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
                activeFrameIndex = (activeFrameIndex + 1) % activeFrameCount
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

    private fun resolveBaseFrameEpochSec(referenceTimeUtcMs: Long?): Long {
        val nowUtcMs = nowUtcMsProvider()
        val latestAvailableUtcMs = nowUtcMs - SKY_SIGHT_SOURCE_DELAY_MS
        val candidateUtcMs = referenceTimeUtcMs ?: latestAvailableUtcMs
        val clampedUtcMs = minOf(candidateUtcMs, latestAvailableUtcMs)
        val frameStepMs = FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES * 60_000L
        val steppedUtcMs = (clampedUtcMs / frameStepMs) * frameStepMs
        return (steppedUtcMs / 1_000L).coerceAtLeast(0L)
    }

    private fun buildFrameEpochs(baseFrameEpochSec: Long, frameCount: Int): List<Long> {
        val frameStepSec = FORECAST_SKYSIGHT_SATELLITE_FRAME_STEP_MINUTES * 60L
        return (0 until frameCount).map { index ->
            (baseFrameEpochSec - (index * frameStepSec)).coerceAtLeast(0L)
        }
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
        for (anchor in SKY_SIGHT_ANCHOR_LAYER_IDS) {
            if (style.getLayer(anchor) != null) {
                style.addLayerBelow(layer, anchor)
                return
            }
        }
        style.addLayer(layer)
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

        private val SKY_SIGHT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy/MM/dd/HHmm", Locale.US)
            .withZone(ZoneOffset.UTC)

        private val SKY_SIGHT_ANCHOR_LAYER_IDS: List<String> = listOf(
            "forecast-primary-raster-layer",
            "forecast-primary-vector-fill-layer",
            "forecast-secondary-raster-layer",
            "forecast-secondary-vector-fill-layer",
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
            BlueLocationOverlay.LAYER_ID
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
