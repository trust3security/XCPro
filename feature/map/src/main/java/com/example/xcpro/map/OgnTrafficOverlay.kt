package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OgnAircraftIcon
import com.example.xcpro.ogn.OgnSubscriptionPolicy
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnViewportBounds
import com.example.xcpro.ogn.clampOgnIconSizePx
import com.example.xcpro.ogn.iconForOgnAircraftIdentity
import kotlin.math.abs
import org.maplibre.android.geometry.LatLngBounds
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
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Runtime map overlay for OGN traffic targets.
 * Keeps all MapLibre source/layer state local to the UI runtime.
 */
class OgnTrafficOverlay(
    private val context: Context,
    private val map: MapLibreMap,
    initialIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX,
    initialUseSatelliteContrastIcons: Boolean = false
) {
    private var currentIconSizePx: Int = clampOgnIconSizePx(initialIconSizePx)
    private var useSatelliteContrastIcons: Boolean = initialUseSatelliteContrastIcons

    fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            ensureStyleImage(style)
            if (style.getLayer(ICON_LAYER_ID) == null) {
                val iconLayer = createIconLayer()
                val anchorId = BlueLocationOverlay.LAYER_ID
                if (style.getLayer(anchorId) != null) {
                    style.addLayerAbove(iconLayer, anchorId)
                } else {
                    style.addLayer(iconLayer)
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
            ensureLayerOrder(style)
            applyIconSizeToStyle()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize OGN overlay: ${t.message}", t)
        }
    }

    fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnIconSizePx(iconSizePx)
        if (clamped == currentIconSizePx) return
        currentIconSizePx = clamped
        applyIconSizeToStyle()
    }

    fun setUseSatelliteContrastIcons(enabled: Boolean) {
        useSatelliteContrastIcons = enabled
    }

    fun render(
        targets: List<OgnTrafficTarget>,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
        unitsPreferences: UnitsPreferences = UnitsPreferences()
    ) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val nowMonoMs = TimeBridge.nowMonoMs()
        val visibleBounds = map.projection.visibleRegion?.latLngBounds
        val features = ArrayList<Feature>(targets.size.coerceAtMost(MAX_TARGETS))

        for (target in targets) {
            if (features.size >= MAX_TARGETS) break
            if (!isValidCoordinate(target.latitude, target.longitude)) continue
            if (!isInVisibleBounds(target.latitude, target.longitude, visibleBounds)) continue
            val feature = Feature.fromGeometry(
                Point.fromLngLat(target.longitude, target.latitude)
            )
            feature.addStringProperty(PROP_TARGET_KEY, target.canonicalKey)
            feature.addStringProperty(PROP_TARGET_ID, target.id)

            val icon = iconForOgnAircraftIdentity(
                aircraftTypeCode = target.identity?.aircraftTypeCode,
                competitionNumber = target.identity?.competitionNumber
            )
            val secondaryLabel = OgnIdentifierDistanceLabelMapper.map(
                competitionId = target.identity?.competitionNumber,
                registration = target.identity?.registration,
                distanceMeters = target.distanceMeters,
                unitsPreferences = unitsPreferences
            )
            val mapping = OgnRelativeAltitudeFeatureMapper.map(
                OgnRelativeAltitudeFeatureMapperInput(
                    targetAltitudeMeters = target.altitudeMeters,
                    ownshipAltitudeMeters = ownshipAltitudeMeters,
                    altitudeUnit = altitudeUnit,
                    icon = icon,
                    defaultIconStyleImageId = resolveStyleImageId(icon),
                    gliderAboveIconStyleImageId = RELATIVE_GLIDER_ABOVE_ICON_IMAGE_ID,
                    gliderBelowIconStyleImageId = RELATIVE_GLIDER_BELOW_ICON_IMAGE_ID,
                    gliderNearIconStyleImageId = RELATIVE_GLIDER_NEAR_ICON_IMAGE_ID,
                    secondaryLabelText = secondaryLabel.text
                )
            )
            feature.addStringProperty(PROP_ICON_ID, mapping.iconStyleImageId)
            feature.addStringProperty(PROP_TOP_LABEL, mapping.topLabel)
            feature.addStringProperty(PROP_BOTTOM_LABEL, mapping.bottomLabel)
            feature.addNumberProperty(
                PROP_ALPHA,
                if (target.isStale(nowMonoMs, STALE_VISUAL_AFTER_MS)) STALE_ALPHA else LIVE_ALPHA
            )
            if (target.trackDegrees?.isFinite() == true) {
                feature.addNumberProperty(PROP_TRACK_DEG, target.trackDegrees)
            }
            features.add(feature)
        }

        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun findTargetAt(tap: org.maplibre.android.geometry.LatLng): String? {
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
            val key = when {
                feature.hasProperty(PROP_TARGET_KEY) ->
                    runCatching { feature.getStringProperty(PROP_TARGET_KEY) }.getOrNull()
                feature.hasProperty(PROP_TARGET_ID) ->
                    runCatching { feature.getStringProperty(PROP_TARGET_ID) }.getOrNull()
                else -> null
            }
            val normalized = key?.trim().orEmpty()
            if (normalized.isNotEmpty()) return normalized
        }
        return null
    }

    fun clear() {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    fun cleanup() {
        val style = map.style ?: return
        try {
            style.removeLayer(BOTTOM_LABEL_LAYER_ID)
            style.removeLayer(TOP_LABEL_LAYER_ID)
            style.removeLayer(ICON_LAYER_ID)
            style.removeSource(SOURCE_ID)
            OgnAircraftIcon.values().forEach { icon ->
                style.removeImage(icon.styleImageId)
            }
            style.removeImage(SATELLITE_GLIDER_ICON_IMAGE_ID)
            style.removeImage(RELATIVE_GLIDER_ABOVE_ICON_IMAGE_ID)
            style.removeImage(RELATIVE_GLIDER_BELOW_ICON_IMAGE_ID)
            style.removeImage(RELATIVE_GLIDER_NEAR_ICON_IMAGE_ID)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup OGN overlay: ${t.message}")
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
            style.addLayer(createIconLayer())
            style.addLayer(createTopLabelLayer())
            style.addLayer(createBottomLabelLayer())
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to bring OGN overlay to front: ${t.message}")
        }
    }

    private fun ensureStyleImage(style: Style) {
        OgnAircraftIcon.values().forEach { icon ->
            val existing = runCatching { style.getImage(icon.styleImageId) }.getOrNull()
            if (existing != null) return@forEach
            val bitmap = drawableToBitmap(icon.resId) ?: return@forEach
            style.addImage(icon.styleImageId, bitmap)
        }
        ensureSatelliteGliderStyleImage(style)
        ensureRelativeGliderStyleImages(style)
    }

    private fun ensureRelativeGliderStyleImages(style: Style) {
        ensureTintedStyleImage(
            style = style,
            imageId = RELATIVE_GLIDER_ABOVE_ICON_IMAGE_ID,
            drawableId = OgnAircraftIcon.Glider.resId,
            tintColor = RELATIVE_GLIDER_ABOVE_TINT
        )
        ensureTintedStyleImage(
            style = style,
            imageId = RELATIVE_GLIDER_BELOW_ICON_IMAGE_ID,
            drawableId = OgnAircraftIcon.Glider.resId,
            tintColor = RELATIVE_GLIDER_BELOW_TINT
        )
        ensureTintedStyleImage(
            style = style,
            imageId = RELATIVE_GLIDER_NEAR_ICON_IMAGE_ID,
            drawableId = OgnAircraftIcon.Glider.resId,
            tintColor = RELATIVE_GLIDER_NEAR_TINT
        )
    }

    private fun ensureTintedStyleImage(
        style: Style,
        imageId: String,
        drawableId: Int,
        tintColor: Int
    ) {
        val existing = runCatching { style.getImage(imageId) }.getOrNull()
        if (existing != null) return
        val bitmap = drawableToBitmap(
            drawableId = drawableId,
            tintColor = tintColor
        ) ?: return
        style.addImage(imageId, bitmap)
    }

    private fun createIconLayer(): SymbolLayer =
        SymbolLayer(ICON_LAYER_ID, SOURCE_ID)
            .withProperties(
                iconImage(
                    Expression.coalesce(
                        Expression.get(PROP_ICON_ID),
                        Expression.literal(DEFAULT_ICON_IMAGE_ID)
                    )
                ),
                iconSize(iconScaleForPx(currentIconSizePx)),
                iconRotate(
                    Expression.coalesce(
                        Expression.get(PROP_TRACK_DEG),
                        Expression.literal(0.0)
                    )
                ),
                iconRotationAlignment("map"),
                iconKeepUpright(false),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconAnchor("center"),
                iconOpacity(Expression.get(PROP_ALPHA))
            )

    private fun createTopLabelLayer(): SymbolLayer =
        SymbolLayer(TOP_LABEL_LAYER_ID, SOURCE_ID)
            .withProperties(
                textField(Expression.get(PROP_TOP_LABEL)),
                textFont(LABEL_FONT_STACK),
                textSize(labelTextSizeForPx(currentIconSizePx)),
                textColor(LABEL_TEXT_COLOR),
                textOffset(arrayOf(0f, topLabelOffsetYForPx(currentIconSizePx))),
                textAnchor("center"),
                textAllowOverlap(true),
                textIgnorePlacement(true),
                textOpacity(Expression.get(PROP_ALPHA))
            )

    private fun createBottomLabelLayer(): SymbolLayer =
        SymbolLayer(BOTTOM_LABEL_LAYER_ID, SOURCE_ID)
            .withProperties(
                textField(Expression.get(PROP_BOTTOM_LABEL)),
                textFont(LABEL_FONT_STACK),
                textSize(labelTextSizeForPx(currentIconSizePx)),
                textColor(LABEL_TEXT_COLOR),
                textOffset(arrayOf(0f, bottomLabelOffsetYForPx(currentIconSizePx))),
                textAnchor("center"),
                textAllowOverlap(true),
                textIgnorePlacement(true),
                textOpacity(Expression.get(PROP_ALPHA))
            )

    private fun ensureLayerOrder(style: Style) {
        val anchorId = BlueLocationOverlay.LAYER_ID
        if (style.getLayer(anchorId) == null) return
        if (style.getLayer(ICON_LAYER_ID) == null ||
            style.getLayer(TOP_LABEL_LAYER_ID) == null ||
            style.getLayer(BOTTOM_LABEL_LAYER_ID) == null
        ) {
            return
        }

        val layerIds = style.layers.map { it.id }
        val anchorIndex = layerIds.indexOf(anchorId)
        val iconIndex = layerIds.indexOf(ICON_LAYER_ID)
        val topIndex = layerIds.indexOf(TOP_LABEL_LAYER_ID)
        val bottomIndex = layerIds.indexOf(BOTTOM_LABEL_LAYER_ID)
        if (anchorIndex < 0 || iconIndex < 0 || topIndex < 0 || bottomIndex < 0) return

        val iconNeedsMove = iconIndex <= anchorIndex
        val topNeedsMove = topIndex <= iconIndex
        val bottomNeedsMove = bottomIndex <= topIndex
        if (!iconNeedsMove && !topNeedsMove && !bottomNeedsMove) return

        style.removeLayer(BOTTOM_LABEL_LAYER_ID)
        style.removeLayer(TOP_LABEL_LAYER_ID)
        style.removeLayer(ICON_LAYER_ID)
        style.addLayerAbove(createIconLayer(), anchorId)
        style.addLayerAbove(createTopLabelLayer(), ICON_LAYER_ID)
        style.addLayerAbove(createBottomLabelLayer(), TOP_LABEL_LAYER_ID)
    }

    private fun ensureSatelliteGliderStyleImage(style: Style) {
        val existing = runCatching { style.getImage(SATELLITE_GLIDER_ICON_IMAGE_ID) }.getOrNull()
        if (existing != null) return
        val bitmap = drawableToBitmap(
            drawableId = OgnAircraftIcon.Glider.resId,
            tintColor = Color.WHITE
        ) ?: return
        style.addImage(SATELLITE_GLIDER_ICON_IMAGE_ID, bitmap)
    }

    private fun resolveStyleImageId(icon: OgnAircraftIcon): String {
        if (useSatelliteContrastIcons && icon == OgnAircraftIcon.Glider) {
            return SATELLITE_GLIDER_ICON_IMAGE_ID
        }
        return icon.styleImageId
    }

    private fun drawableToBitmap(drawableId: Int, tintColor: Int? = null): Bitmap? {
        val baseDrawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val drawable = baseDrawable.mutate()
        if (tintColor != null) {
            DrawableCompat.setTint(drawable, tintColor)
        }
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

        val labelSize = labelTextSizeForPx(currentIconSizePx)
        val topLabelLayer = style.getLayer(TOP_LABEL_LAYER_ID) as? SymbolLayer
        topLabelLayer?.setProperties(
            textSize(labelSize),
            textOffset(arrayOf(0f, topLabelOffsetYForPx(currentIconSizePx)))
        )

        val bottomLabelLayer = style.getLayer(BOTTOM_LABEL_LAYER_ID) as? SymbolLayer
        bottomLabelLayer?.setProperties(
            textSize(labelSize),
            textOffset(arrayOf(0f, bottomLabelOffsetYForPx(currentIconSizePx)))
        )
    }

    private fun iconScaleForPx(iconSizePx: Int): Float =
        iconSizePx.toFloat() / ICON_BITMAP_BASE_SIZE_PX.toFloat()

    private fun labelTextSizeForPx(iconSizePx: Int): Float {
        val scaled = LABEL_TEXT_SIZE_BASE_SP * iconScaleForPx(iconSizePx)
        return scaled.coerceIn(MIN_LABEL_TEXT_SIZE_SP, MAX_LABEL_TEXT_SIZE_SP)
    }

    private fun topLabelOffsetYForPx(iconSizePx: Int): Float =
        -LABEL_TEXT_OFFSET_BASE_Y * iconScaleForPx(iconSizePx)

    private fun bottomLabelOffsetYForPx(iconSizePx: Int): Float =
        LABEL_TEXT_OFFSET_BASE_Y * iconScaleForPx(iconSizePx)

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (abs(latitude) > 90.0) return false
        if (abs(longitude) > 180.0) return false
        return true
    }

    private fun isInVisibleBounds(
        latitude: Double,
        longitude: Double,
        bounds: LatLngBounds?
    ): Boolean {
        if (bounds == null) return true
        return OgnSubscriptionPolicy.isInViewport(
            latitude = latitude,
            longitude = longitude,
            bounds = OgnViewportBounds(
                northLat = bounds.latitudeNorth,
                southLat = bounds.latitudeSouth,
                eastLon = bounds.longitudeEast,
                westLon = bounds.longitudeWest
            )
        )
    }

    private companion object {
        private const val TAG = "OgnTrafficOverlay"

        private const val SOURCE_ID = "ogn-traffic-source"
        private const val ICON_LAYER_ID = "ogn-traffic-icon-layer"
        private const val TOP_LABEL_LAYER_ID = "ogn-traffic-label-top-layer"
        private const val BOTTOM_LABEL_LAYER_ID = "ogn-traffic-label-bottom-layer"
        private const val DEFAULT_ICON_IMAGE_ID = "ogn_icon_unknown"
        private const val SATELLITE_GLIDER_ICON_IMAGE_ID = "ogn_icon_glider_satellite"
        private const val RELATIVE_GLIDER_ABOVE_ICON_IMAGE_ID = "ogn_icon_glider_rel_above"
        private const val RELATIVE_GLIDER_BELOW_ICON_IMAGE_ID = "ogn_icon_glider_rel_below"
        private const val RELATIVE_GLIDER_NEAR_ICON_IMAGE_ID = "ogn_icon_glider_rel_near"

        private const val PROP_TOP_LABEL = "label_top"
        private const val PROP_BOTTOM_LABEL = "label_bottom"
        private const val PROP_ALPHA = "alpha"
        private const val PROP_TRACK_DEG = "track_deg"
        private const val PROP_TARGET_ID = "target_id"
        private const val PROP_TARGET_KEY = "target_key"
        private const val PROP_ICON_ID = "icon_id"

        private const val MAX_TARGETS = 500
        private const val STALE_VISUAL_AFTER_MS = 60_000L

        private const val LIVE_ALPHA = 0.90
        private const val STALE_ALPHA = 0.45

        private const val ICON_BITMAP_BASE_SIZE_PX = OGN_ICON_SIZE_DEFAULT_PX

        private const val LABEL_TEXT_SIZE_BASE_SP = 13f
        private const val MIN_LABEL_TEXT_SIZE_SP = 12f
        private const val MAX_LABEL_TEXT_SIZE_SP = 17f
        private const val LABEL_TEXT_OFFSET_BASE_Y = 1.6f
        private const val LABEL_TEXT_COLOR = "#000000"
        private val LABEL_FONT_STACK = arrayOf(
            "Open Sans Semibold",
            "Noto Sans Medium",
            "Open Sans Regular",
            "Arial Unicode MS Regular"
        )
        private val RELATIVE_GLIDER_ABOVE_TINT = Color.parseColor("#1B5E20")
        private val RELATIVE_GLIDER_BELOW_TINT = Color.parseColor("#0D47A1")
        private val RELATIVE_GLIDER_NEAR_TINT = Color.parseColor("#101010")
    }
}
