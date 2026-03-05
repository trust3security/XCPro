package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Choreographer
import androidx.core.content.ContextCompat
import com.example.xcpro.adsb.ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.AdsbProximityTier
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.clampAdsbIconSizePx
import com.example.xcpro.adsb.ui.AdsbAircraftIcon
import com.example.xcpro.adsb.ui.emergencyStyleImageId
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.TimeBridge
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconColor
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
import org.maplibre.android.style.layers.PropertyFactory.textFont
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
    private var emergencyFlashEnabled: Boolean = ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
    private var currentOwnshipAltitudeMeters: Double? = null
    private var currentUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private val motionSmoother = AdsbDisplayMotionSmoother()
    private var frameScheduled = false
    private var lastRenderedFrameMonoMs: Long = Long.MIN_VALUE
    private val frameCallback = Choreographer.FrameCallback frame@{ _ ->
        frameScheduled = false
        // Use one monotonic clock source for both immediate and choreographer frames.
        val nowMonoMs = nowMonoMs()
        val frameSnapshot = motionSmoother.snapshot(nowMonoMs)
        val hasVisualAnimation = hasActiveVisualAnimation(frameSnapshot)
        if (!hasVisualAnimation) {
            return@frame
        }
        if (!shouldRenderAnimationFrame(nowMonoMs)) {
            if (map.style != null && hasVisualAnimation) {
                scheduleFrameLoop()
            }
            return@frame
        }
        renderFrame(nowMonoMs, frameSnapshot)
        lastRenderedFrameMonoMs = nowMonoMs
        if (map.style != null && hasVisualAnimation) {
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
            if (style.getLayer(ICON_OUTLINE_LAYER_ID) == null) {
                val outlineLayer = createIconOutlineLayer()
                val anchorId = BlueLocationOverlay.LAYER_ID
                if (style.getLayer(anchorId) != null) {
                    style.addLayerBelow(outlineLayer, anchorId)
                } else {
                    style.addLayer(outlineLayer)
                }
            }
            if (style.getLayer(ICON_LAYER_ID) == null) {
                val iconLayer = createIconLayer()
                if (style.getLayer(ICON_OUTLINE_LAYER_ID) != null) {
                    style.addLayerAbove(iconLayer, ICON_OUTLINE_LAYER_ID)
                } else {
                    val anchorId = BlueLocationOverlay.LAYER_ID
                    if (style.getLayer(anchorId) != null) {
                        style.addLayerBelow(iconLayer, anchorId)
                    } else {
                        style.addLayer(iconLayer)
                    }
                }
            }

            if (style.getLayer(TOP_LABEL_LAYER_ID) == null) {
                val topLayer = createTopLabelLayer()
                if (style.getLayer(ICON_LAYER_ID) != null) {
                    style.addLayerAbove(topLayer, ICON_LAYER_ID)
                } else {
                    style.addLayer(topLayer)
                }
            }
            if (style.getLayer(BOTTOM_LABEL_LAYER_ID) == null) {
                val bottomLayer = createBottomLabelLayer()
                if (style.getLayer(TOP_LABEL_LAYER_ID) != null) {
                    style.addLayerAbove(bottomLayer, TOP_LABEL_LAYER_ID)
                } else if (style.getLayer(ICON_LAYER_ID) != null) {
                    style.addLayerAbove(bottomLayer, ICON_LAYER_ID)
                } else {
                    style.addLayer(bottomLayer)
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

    fun setEmergencyFlashEnabled(enabled: Boolean) {
        if (emergencyFlashEnabled == enabled) return
        emergencyFlashEnabled = enabled
        val nowMonoMs = nowMonoMs()
        val frameSnapshot = motionSmoother.snapshot(nowMonoMs)
        renderFrame(nowMonoMs, frameSnapshot)
        lastRenderedFrameMonoMs = nowMonoMs
        if (hasActiveVisualAnimation(frameSnapshot)) {
            scheduleFrameLoop()
        } else {
            stopFrameLoop()
        }
    }

    fun render(
        targets: List<AdsbTrafficUiModel>,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences
    ) {
        initialize()
        val normalizedOwnshipAltitude = ownshipAltitudeMeters?.takeIf { it.isFinite() }
        val contextChanged =
            currentOwnshipAltitudeMeters != normalizedOwnshipAltitude ||
                currentUnitsPreferences != unitsPreferences
        currentOwnshipAltitudeMeters = normalizedOwnshipAltitude
        currentUnitsPreferences = unitsPreferences
        val nowMonoMs = nowMonoMs()
        val changed = motionSmoother.onTargets(targets, nowMonoMs)
        val frameSnapshot = motionSmoother.snapshot(nowMonoMs)
        val hasVisualAnimation = hasActiveVisualAnimation(frameSnapshot)
        if (!changed && !hasVisualAnimation && !contextChanged) {
            return
        }
        renderFrame(nowMonoMs, frameSnapshot)
        lastRenderedFrameMonoMs = nowMonoMs
        if (hasVisualAnimation) {
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
                TOP_LABEL_LAYER_ID,
                BOTTOM_LABEL_LAYER_ID
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
        lastRenderedFrameMonoMs = Long.MIN_VALUE
        currentOwnshipAltitudeMeters = null
        currentUnitsPreferences = UnitsPreferences()
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    fun cleanup() {
        stopFrameLoop()
        motionSmoother.clear()
        lastRenderedFrameMonoMs = Long.MIN_VALUE
        currentOwnshipAltitudeMeters = null
        currentUnitsPreferences = UnitsPreferences()
        val style = map.style ?: return
        try {
            style.removeLayer(BOTTOM_LABEL_LAYER_ID)
            style.removeLayer(TOP_LABEL_LAYER_ID)
            style.removeLayer(ICON_LAYER_ID)
            style.removeLayer(ICON_OUTLINE_LAYER_ID)
            style.removeSource(SOURCE_ID)
            AdsbAircraftIcon.values().forEach { icon ->
                style.removeImage(icon.styleImageId)
                style.removeImage(icon.emergencyStyleImageId())
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup ADS-B overlay: ${t.message}")
        }
    }

    fun bringToFront() {
        val style = map.style ?: return
        if (style.getLayer(ICON_LAYER_ID) == null ||
            style.getLayer(TOP_LABEL_LAYER_ID) == null ||
            style.getLayer(BOTTOM_LABEL_LAYER_ID) == null
        ) {
            return
        }
        try {
            val topLayerId = style.layers.lastOrNull()?.id
            if (topLayerId == BOTTOM_LABEL_LAYER_ID) return

            style.removeLayer(BOTTOM_LABEL_LAYER_ID)
            style.removeLayer(TOP_LABEL_LAYER_ID)
            style.removeLayer(ICON_LAYER_ID)
            style.removeLayer(ICON_OUTLINE_LAYER_ID)
            style.addLayer(createIconOutlineLayer())
            style.addLayer(createIconLayer())
            style.addLayer(createTopLabelLayer())
            style.addLayer(createBottomLabelLayer())
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to bring ADS-B overlay to front: ${t.message}")
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
                style.addImage(icon.styleImageId, baseBitmap, true)
            }
            if (emergencyImage == null) {
                val emergencyBitmap = tintBitmap(baseBitmap, EMERGENCY_ICON_COLOR)
                style.addImage(emergencyId, emergencyBitmap, true)
            }
        }
    }

    private fun createIconOutlineLayer(): SymbolLayer =
        SymbolLayer(ICON_OUTLINE_LAYER_ID, SOURCE_ID)
            .withProperties(
                iconImage(Expression.get(AdsbGeoJsonMapper.PROP_ICON_ID)),
                iconSize(iconScaleForPx(currentIconSizePx) * OUTLINE_ICON_SCALE_MULTIPLIER),
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
                iconColor(Color.BLACK),
                iconOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA))
            )

    private fun createIconLayer(): SymbolLayer =
        SymbolLayer(ICON_LAYER_ID, SOURCE_ID)
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
                iconColor(AdsbProximityColorPolicy.expression()),
                iconOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA))
            )

    private fun createTopLabelLayer(): SymbolLayer =
        SymbolLayer(TOP_LABEL_LAYER_ID, SOURCE_ID)
            .withProperties(
                textField(Expression.get(AdsbGeoJsonMapper.PROP_LABEL_TOP)),
                textFont(LABEL_FONT_STACK),
                textSize(LABEL_TEXT_SIZE_SP),
                textColor(LABEL_TEXT_COLOR),
                textOffset(arrayOf(0f, topLabelOffsetYForPx(currentIconSizePx))),
                textAnchor("center"),
                textAllowOverlap(true),
                textIgnorePlacement(true),
                textOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA))
            )

    private fun createBottomLabelLayer(): SymbolLayer =
        SymbolLayer(BOTTOM_LABEL_LAYER_ID, SOURCE_ID)
            .withProperties(
                textField(Expression.get(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM)),
                textFont(LABEL_FONT_STACK),
                textSize(LABEL_TEXT_SIZE_SP),
                textColor(LABEL_TEXT_COLOR),
                textOffset(arrayOf(0f, bottomLabelOffsetYForPx(currentIconSizePx))),
                textAnchor("center"),
                textAllowOverlap(true),
                textIgnorePlacement(true),
                textOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA))
            )

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
        val outlineLayer = style.getLayer(ICON_OUTLINE_LAYER_ID) as? SymbolLayer
        outlineLayer?.setProperties(
            iconSize(iconScaleForPx(currentIconSizePx) * OUTLINE_ICON_SCALE_MULTIPLIER)
        )

        val iconLayer = style.getLayer(ICON_LAYER_ID) as? SymbolLayer
        iconLayer?.setProperties(iconSize(iconScaleForPx(currentIconSizePx)))

        val topLabelLayer = style.getLayer(TOP_LABEL_LAYER_ID) as? SymbolLayer
        topLabelLayer?.setProperties(textOffset(arrayOf(0f, topLabelOffsetYForPx(currentIconSizePx))))

        val bottomLabelLayer = style.getLayer(BOTTOM_LABEL_LAYER_ID) as? SymbolLayer
        bottomLabelLayer?.setProperties(textOffset(arrayOf(0f, bottomLabelOffsetYForPx(currentIconSizePx))))
    }

    private fun renderFrame(
        nowMonoMs: Long,
        frameSnapshot: AdsbDisplayMotionSmoother.FrameSnapshot
    ) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                buildFeatures(
                    nowMonoMs = nowMonoMs,
                    targets = frameSnapshot.targets
                )
            )
        )
    }

    private fun buildFeatures(
        nowMonoMs: Long,
        targets: List<AdsbTrafficUiModel>
    ): Array<Feature> {
        val features = ArrayList<Feature>(MAX_TARGETS)
        for (target in targets) {
            if (features.size >= MAX_TARGETS) break
            val feature = AdsbGeoJsonMapper.toFeature(
                target = target,
                ownshipAltitudeMeters = currentOwnshipAltitudeMeters,
                unitsPreferences = currentUnitsPreferences
            ) ?: continue
            feature.addNumberProperty(
                AdsbGeoJsonMapper.PROP_ALPHA,
                AdsbEmergencyFlashPolicy.alphaForTarget(
                    target = target,
                    nowMonoMs = nowMonoMs,
                    liveAlpha = LIVE_ALPHA,
                    staleAlpha = STALE_ALPHA,
                    emergencyFlashEnabled = emergencyFlashEnabled
                )
            )
            features.add(feature)
        }
        return features.toTypedArray()
    }

    private fun nowMonoMs(): Long = TimeBridge.nowMonoMs()

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

    private fun shouldRenderAnimationFrame(nowMonoMs: Long): Boolean {
        val lastRendered = lastRenderedFrameMonoMs
        if (lastRendered == Long.MIN_VALUE) return true
        return nowMonoMs - lastRendered >= ANIMATION_FRAME_INTERVAL_MS
    }

    private fun hasActiveVisualAnimation(
        frameSnapshot: AdsbDisplayMotionSmoother.FrameSnapshot
    ): Boolean =
        frameSnapshot.hasActiveAnimations ||
            (
                emergencyFlashEnabled &&
                    frameSnapshot.targets.any { target ->
                        !target.isStale && target.proximityTier == AdsbProximityTier.EMERGENCY
                    }
                )

    private fun iconScaleForPx(iconSizePx: Int): Float =
        iconSizePx.toFloat() / ICON_BITMAP_BASE_SIZE_PX.toFloat()

    private fun topLabelOffsetYForPx(iconSizePx: Int): Float =
        -LABEL_TEXT_OFFSET_BASE_Y * iconScaleForPx(iconSizePx)

    private fun bottomLabelOffsetYForPx(iconSizePx: Int): Float =
        LABEL_TEXT_OFFSET_BASE_Y * iconScaleForPx(iconSizePx)

    private companion object {
        private const val TAG = "AdsbTrafficOverlay"

        private const val SOURCE_ID = "adsb-traffic-source"
        private const val ICON_OUTLINE_LAYER_ID = "adsb-traffic-icon-outline-layer"
        private const val ICON_LAYER_ID = "adsb-traffic-icon-layer"
        private const val TOP_LABEL_LAYER_ID = "adsb-traffic-top-label-layer"
        private const val BOTTOM_LABEL_LAYER_ID = "adsb-traffic-bottom-label-layer"

        private const val MAX_TARGETS = 120
        private const val LIVE_ALPHA = 0.90
        private const val STALE_ALPHA = 0.45

        private const val ICON_BITMAP_BASE_SIZE_PX = ADSB_ICON_SIZE_DEFAULT_PX

        private const val LABEL_TEXT_SIZE_SP = 13f
        private const val LABEL_TEXT_OFFSET_BASE_Y = 1.7f
        private const val LABEL_TEXT_COLOR = "#000000"
        private const val OUTLINE_ICON_SCALE_MULTIPLIER = 1.14f
        private val LABEL_FONT_STACK = arrayOf(
            "Open Sans Semibold",
            "Noto Sans Medium",
            "Open Sans Regular",
            "Arial Unicode MS Regular"
        )
        private const val ANIMATION_FRAME_INTERVAL_MS = 66L
        private val EMERGENCY_ICON_COLOR: Int = Color.parseColor(AdsbProximityColorPolicy.EMERGENCY_HEX)
    }
}
