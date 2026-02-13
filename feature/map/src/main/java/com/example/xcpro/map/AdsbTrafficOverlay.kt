package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.SystemClock
import android.view.Choreographer
import androidx.core.content.ContextCompat
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.clampAdsbIconSizePx
import com.example.xcpro.adsb.ui.AdsbAircraftIcon
import com.example.xcpro.adsb.ui.emergencyStyleImageId
import com.example.xcpro.core.common.logging.AppLogger
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconKeepUpright
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

class AdsbTrafficOverlay(
    private val context: Context,
    private val map: MapLibreMap,
    initialIconSizePx: Int = ADSB_ICON_SIZE_DEFAULT_PX
) {
    private var currentIconSizePx: Int = clampAdsbIconSizePx(initialIconSizePx)
    private val motionSmoother = AdsbDisplayMotionSmoother()
    private var frameScheduled = false
    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        frameScheduled = false
        val nowMonoMs = frameTimeNanos / 1_000_000L
        renderFrame(nowMonoMs)
        if (motionSmoother.hasActiveAnimations(nowMonoMs) && map.style != null) {
            scheduleFrameLoop()
        }
    }

    fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            ensureStyleImages(style)
            if (style.getLayer(ICON_LAYER_ID) == null) {
                val iconLayer = SymbolLayer(ICON_LAYER_ID, SOURCE_ID)
                    .withProperties(
                        iconImage(Expression.get(AdsbGeoJsonMapper.PROP_ICON_ID)),
                        iconSize(iconScaleForPx(currentIconSizePx)),
                        iconRotate(
                            Expression.coalesce(
                                Expression.get(AdsbGeoJsonMapper.PROP_TRACK_DEG),
                                Expression.literal(0.0)
                            )
                        ),
                        iconRotationAlignment("map"),
                        iconKeepUpright(false),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconAnchor("center"),
                        iconOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA))
                    )
                val anchorId = BlueLocationOverlay.LAYER_ID
                if (style.getLayer(anchorId) != null) {
                    style.addLayerBelow(iconLayer, anchorId)
                } else {
                    style.addLayer(iconLayer)
                }
            }

            if (style.getLayer(LABEL_LAYER_ID) == null) {
                val labelLayer = SymbolLayer(LABEL_LAYER_ID, SOURCE_ID)
                    .withProperties(
                        textField(Expression.get(AdsbGeoJsonMapper.PROP_LABEL)),
                        textSize(LABEL_TEXT_SIZE_SP),
                        textColor(LABEL_TEXT_COLOR),
                        textHaloColor(LABEL_HALO_COLOR),
                        textHaloWidth(LABEL_HALO_WIDTH_DP),
                        textOffset(arrayOf(labelOffsetXForPx(currentIconSizePx), 0f)),
                        textAnchor("left"),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                        textOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA))
                    )
                if (style.getLayer(ICON_LAYER_ID) != null) {
                    style.addLayerAbove(labelLayer, ICON_LAYER_ID)
                } else {
                    style.addLayer(labelLayer)
                }
            }
            applyIconSizeToStyle()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize ADS-B overlay: ${t.message}", t)
        }
    }

    fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampAdsbIconSizePx(iconSizePx)
        if (clamped == currentIconSizePx) return
        currentIconSizePx = clamped
        applyIconSizeToStyle()
    }

    fun render(targets: List<AdsbTrafficUiModel>) {
        initialize()
        val nowMonoMs = nowMonoMs()
        val changed = motionSmoother.onTargets(targets, nowMonoMs)
        val hasAnimation = motionSmoother.hasActiveAnimations(nowMonoMs)
        if (!changed && !hasAnimation) {
            return
        }
        renderFrame(nowMonoMs)
        if (hasAnimation) {
            scheduleFrameLoop()
        } else {
            stopFrameLoop()
        }
    }

    fun findTargetAt(tap: LatLng): Icao24? {
        val style = map.style ?: return null
        if (style.getSource(SOURCE_ID) == null) return null
        val screenPoint = map.projection.toScreenLocation(tap)
        val features = runCatching {
            map.queryRenderedFeatures(
                screenPoint,
                ICON_LAYER_ID,
                LABEL_LAYER_ID
            )
        }.getOrNull().orEmpty()

        for (feature in features) {
            if (!feature.hasProperty(AdsbGeoJsonMapper.PROP_ICAO24)) continue
            val rawId = runCatching {
                feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICAO24)
            }.getOrNull()
            val id = Icao24.from(rawId) ?: continue
            return id
        }
        return null
    }

    fun clear() {
        stopFrameLoop()
        motionSmoother.clear()
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    fun cleanup() {
        stopFrameLoop()
        motionSmoother.clear()
        val style = map.style ?: return
        try {
            style.removeLayer(LABEL_LAYER_ID)
            style.removeLayer(ICON_LAYER_ID)
            style.removeSource(SOURCE_ID)
            AdsbAircraftIcon.values().forEach { icon ->
                style.removeImage(icon.styleImageId)
                style.removeImage(icon.emergencyStyleImageId())
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup ADS-B overlay: ${t.message}")
        }
    }

    private fun ensureStyleImages(style: Style) {
        AdsbAircraftIcon.values().forEach { icon ->
            val normalImage = runCatching { style.getImage(icon.styleImageId) }.getOrNull()
            val emergencyId = icon.emergencyStyleImageId()
            val emergencyImage = runCatching { style.getImage(emergencyId) }.getOrNull()
            if (normalImage != null && emergencyImage != null) return@forEach

            val baseBitmap = drawableToBitmap(icon.resId) ?: return@forEach
            if (normalImage == null) {
                style.addImage(icon.styleImageId, baseBitmap)
            }
            if (emergencyImage == null) {
                val emergencyBitmap = tintBitmap(baseBitmap, EMERGENCY_ICON_COLOR)
                style.addImage(emergencyId, emergencyBitmap)
            }
        }
    }

    private fun tintBitmap(source: Bitmap, tintColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return bitmap
    }

    private fun drawableToBitmap(drawableId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(
            ICON_BITMAP_BASE_SIZE_PX,
            ICON_BITMAP_BASE_SIZE_PX,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, ICON_BITMAP_BASE_SIZE_PX, ICON_BITMAP_BASE_SIZE_PX)
        drawable.draw(canvas)
        return bitmap
    }

    private fun applyIconSizeToStyle() {
        val style = map.style ?: return
        val iconLayer = style.getLayer(ICON_LAYER_ID) as? SymbolLayer
        iconLayer?.setProperties(iconSize(iconScaleForPx(currentIconSizePx)))

        val labelLayer = style.getLayer(LABEL_LAYER_ID) as? SymbolLayer
        labelLayer?.setProperties(textOffset(arrayOf(labelOffsetXForPx(currentIconSizePx), 0f)))
    }

    private fun renderFrame(nowMonoMs: Long) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(buildFeatures(nowMonoMs)))
    }

    private fun buildFeatures(nowMonoMs: Long): Array<Feature> {
        val features = ArrayList<Feature>(MAX_TARGETS)
        for (target in motionSmoother.frame(nowMonoMs)) {
            if (features.size >= MAX_TARGETS) break
            val feature = AdsbGeoJsonMapper.toFeature(target) ?: continue
            feature.addNumberProperty(
                AdsbGeoJsonMapper.PROP_ALPHA,
                if (target.isStale) STALE_ALPHA else LIVE_ALPHA
            )
            features.add(feature)
        }
        return features.toTypedArray()
    }

    private fun nowMonoMs(): Long = SystemClock.elapsedRealtime()

    private fun scheduleFrameLoop() {
        if (frameScheduled) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrameLoop() {
        if (!frameScheduled) return
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameScheduled = false
    }

    private fun iconScaleForPx(iconSizePx: Int): Float =
        iconSizePx.toFloat() / ICON_BITMAP_BASE_SIZE_PX.toFloat()

    private fun labelOffsetXForPx(iconSizePx: Int): Float =
        LABEL_TEXT_OFFSET_BASE_X * iconScaleForPx(iconSizePx)

    private companion object {
        private const val TAG = "AdsbTrafficOverlay"

        private const val SOURCE_ID = "adsb-traffic-source"
        private const val ICON_LAYER_ID = "adsb-traffic-icon-layer"
        private const val LABEL_LAYER_ID = "adsb-traffic-label-layer"

        private const val MAX_TARGETS = 120
        private const val LIVE_ALPHA = 0.90
        private const val STALE_ALPHA = 0.45

        private const val ICON_BITMAP_BASE_SIZE_PX = ADSB_ICON_SIZE_DEFAULT_PX

        private const val LABEL_TEXT_SIZE_SP = 11f
        private const val LABEL_HALO_WIDTH_DP = 1.1f
        private const val LABEL_TEXT_OFFSET_BASE_X = 0.9f
        private const val LABEL_TEXT_COLOR = "#FFF3D4"
        private const val LABEL_HALO_COLOR = "#2B1204"
        private val EMERGENCY_ICON_COLOR: Int = Color.parseColor("#E53935")
    }
}
