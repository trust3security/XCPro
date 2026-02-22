package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OgnAircraftIcon
import com.example.xcpro.ogn.OgnSubscriptionPolicy
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnViewportBounds
import com.example.xcpro.ogn.clampOgnIconSizePx
import com.example.xcpro.ogn.iconForOgnAircraftTypeCode
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
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.abs

/**
 * Runtime map overlay for OGN traffic targets.
 * Keeps all MapLibre source/layer state local to the UI runtime.
 */
class OgnTrafficOverlay(
    private val context: Context,
    private val map: MapLibreMap,
    initialIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX
) {
    private var currentIconSizePx: Int = clampOgnIconSizePx(initialIconSizePx)

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
            if (style.getLayer(LABEL_LAYER_ID) == null) {
                val labelLayer = createLabelLayer()
                if (style.getLayer(ICON_LAYER_ID) != null) {
                    style.addLayerAbove(labelLayer, ICON_LAYER_ID)
                } else {
                    style.addLayer(labelLayer)
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

    fun render(targets: List<OgnTrafficTarget>) {
        initialize()
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val nowMonoMs = SystemClock.elapsedRealtime()
        val visibleBounds = map.projection.visibleRegion?.latLngBounds
        val features = ArrayList<Feature>(targets.size.coerceAtMost(MAX_TARGETS))

        for (target in targets) {
            if (features.size >= MAX_TARGETS) break
            if (!isValidCoordinate(target.latitude, target.longitude)) continue
            if (!isInVisibleBounds(target.latitude, target.longitude, visibleBounds)) continue
            val feature = Feature.fromGeometry(
                Point.fromLngLat(target.longitude, target.latitude)
            )
            feature.addStringProperty(PROP_TARGET_ID, target.id)
            feature.addStringProperty(PROP_LABEL, target.displayLabel)
            feature.addStringProperty(
                PROP_ICON_ID,
                iconForOgnAircraftTypeCode(target.identity?.aircraftTypeCode).styleImageId
            )
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
                LABEL_LAYER_ID
            )
        }.getOrNull().orEmpty()

        for (feature in features) {
            if (!feature.hasProperty(PROP_TARGET_ID)) continue
            val id = runCatching { feature.getStringProperty(PROP_TARGET_ID) }.getOrNull()
            val normalized = id?.trim().orEmpty()
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
            style.removeLayer(LABEL_LAYER_ID)
            style.removeLayer(ICON_LAYER_ID)
            style.removeSource(SOURCE_ID)
            OgnAircraftIcon.values().forEach { icon ->
                style.removeImage(icon.styleImageId)
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup OGN overlay: ${t.message}")
        }
    }

    private fun ensureStyleImage(style: Style) {
        OgnAircraftIcon.values().forEach { icon ->
            val existing = runCatching { style.getImage(icon.styleImageId) }.getOrNull()
            if (existing != null) return@forEach
            val bitmap = drawableToBitmap(icon.resId) ?: return@forEach
            style.addImage(icon.styleImageId, bitmap)
        }
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

    private fun createLabelLayer(): SymbolLayer =
        SymbolLayer(LABEL_LAYER_ID, SOURCE_ID)
            .withProperties(
                textField(Expression.get(PROP_LABEL)),
                textSize(LABEL_TEXT_SIZE_SP),
                textColor(LABEL_TEXT_COLOR),
                textHaloColor(LABEL_HALO_COLOR),
                textHaloWidth(LABEL_HALO_WIDTH_DP),
                textOffset(arrayOf(0f, labelOffsetYForPx(currentIconSizePx))),
                textAnchor("top"),
                textAllowOverlap(true),
                textIgnorePlacement(true)
            )

    private fun ensureLayerOrder(style: Style) {
        val anchorId = BlueLocationOverlay.LAYER_ID
        if (style.getLayer(anchorId) == null) return
        if (style.getLayer(ICON_LAYER_ID) == null || style.getLayer(LABEL_LAYER_ID) == null) return

        val layerIds = style.layers.map { it.id }
        val anchorIndex = layerIds.indexOf(anchorId)
        val iconIndex = layerIds.indexOf(ICON_LAYER_ID)
        val labelIndex = layerIds.indexOf(LABEL_LAYER_ID)
        if (anchorIndex < 0 || iconIndex < 0 || labelIndex < 0) return

        val iconNeedsMove = iconIndex <= anchorIndex
        val labelNeedsMove = labelIndex <= iconIndex
        if (!iconNeedsMove && !labelNeedsMove) return

        style.removeLayer(LABEL_LAYER_ID)
        style.removeLayer(ICON_LAYER_ID)
        style.addLayerAbove(createIconLayer(), anchorId)
        style.addLayerAbove(createLabelLayer(), ICON_LAYER_ID)
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
        labelLayer?.setProperties(textOffset(arrayOf(0f, labelOffsetYForPx(currentIconSizePx))))
    }

    private fun iconScaleForPx(iconSizePx: Int): Float =
        iconSizePx.toFloat() / ICON_BITMAP_BASE_SIZE_PX.toFloat()

    private fun labelOffsetYForPx(iconSizePx: Int): Float =
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
        private const val LABEL_LAYER_ID = "ogn-traffic-label-layer"
        private const val DEFAULT_ICON_IMAGE_ID = "ogn_icon_glider"

        private const val PROP_LABEL = "label"
        private const val PROP_ALPHA = "alpha"
        private const val PROP_TRACK_DEG = "track_deg"
        private const val PROP_TARGET_ID = "target_id"
        private const val PROP_ICON_ID = "icon_id"

        private const val MAX_TARGETS = 500
        private const val STALE_VISUAL_AFTER_MS = 60_000L

        private const val LIVE_ALPHA = 0.90
        private const val STALE_ALPHA = 0.45

        private const val ICON_BITMAP_BASE_SIZE_PX = OGN_ICON_SIZE_DEFAULT_PX

        private const val LABEL_TEXT_SIZE_SP = 11f
        private const val LABEL_HALO_WIDTH_DP = 1.1f
        private const val LABEL_TEXT_OFFSET_BASE_Y = 1.1f
        private const val LABEL_TEXT_COLOR = "#EAF4FF"
        private const val LABEL_HALO_COLOR = "#0B1E2D"
    }
}
