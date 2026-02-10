package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.ui.AdsbIconIds
import com.example.xcpro.adsb.ui.classifyAdsbAircraftKind
import com.example.xcpro.adsb.ui.toAdsbIconId
import com.example.xcpro.core.common.logging.AppLogger
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconKeepUpright
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
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
import org.maplibre.geojson.Point
import java.util.Locale

class AdsbTrafficOverlay(
    private val context: Context,
    private val map: MapLibreMap
) {

    fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            if (style.getLayer(ICON_LAYER_ID) == null) {
                registerStyleImages(style)
                val iconLayer = SymbolLayer(ICON_LAYER_ID, SOURCE_ID)
                    .withProperties(
                        iconImage(Expression.get(PROP_ICON_ID)),
                        iconSize(ICON_SIZE),
                        iconRotate(Expression.get(PROP_TRACK_DEG)),
                        iconRotationAlignment("map"),
                        iconKeepUpright(false),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconAnchor("center"),
                        iconOpacity(Expression.get(PROP_ALPHA))
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
                        textField(Expression.get(PROP_LABEL)),
                        textSize(LABEL_TEXT_SIZE_SP),
                        textColor(LABEL_TEXT_COLOR),
                        textHaloColor(LABEL_HALO_COLOR),
                        textHaloWidth(LABEL_HALO_WIDTH_DP),
                        textOffset(arrayOf(0f, LABEL_TEXT_OFFSET_Y)),
                        textAnchor("top"),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                        textOpacity(Expression.get(PROP_ALPHA))
                    )
                if (style.getLayer(ICON_LAYER_ID) != null) {
                    style.addLayerAbove(labelLayer, ICON_LAYER_ID)
                } else {
                    style.addLayer(labelLayer)
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize ADS-B overlay: ${t.message}", t)
        }
    }

    fun render(targets: List<AdsbTrafficUiModel>) {
        initialize()
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val features = ArrayList<Feature>(targets.size.coerceAtMost(MAX_TARGETS))
        for (target in targets) {
            if (features.size >= MAX_TARGETS) break
            if (!target.lat.isFinite() || !target.lon.isFinite()) continue
            val feature = Feature.fromGeometry(
                Point.fromLngLat(target.lon, target.lat)
            )
            val callsign = target.callsign?.trim()?.takeIf { it.isNotBlank() }
            feature.addStringProperty(PROP_ICAO24, target.id.raw)
            feature.addStringProperty(PROP_LABEL, callsign ?: target.id.raw.uppercase(Locale.US))
            feature.addNumberProperty(PROP_TRACK_DEG, target.trackDeg ?: 0.0)
            feature.addNumberProperty(
                PROP_ALPHA,
                if (target.isStale) STALE_ALPHA else LIVE_ALPHA
            )
            feature.addStringProperty(
                PROP_ICON_ID,
                classifyAdsbAircraftKind(
                    category = target.category,
                    speedMps = target.speedMps
                ).toAdsbIconId()
            )
            target.altitudeM?.let { feature.addNumberProperty(PROP_ALT_M, it) }
            target.speedMps?.let { feature.addNumberProperty(PROP_SPEED_MPS, it) }
            target.climbMps?.let { feature.addNumberProperty(PROP_VS_MPS, it) }
            target.category?.let { feature.addNumberProperty(PROP_CATEGORY, it) }
            feature.addNumberProperty(PROP_AGE_SEC, target.ageSec)
            features.add(feature)
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
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
            if (!feature.hasProperty(PROP_ICAO24)) continue
            val rawId = runCatching { feature.getStringProperty(PROP_ICAO24) }.getOrNull()
            val id = Icao24.from(rawId) ?: continue
            return id
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
            style.removeImage(AdsbIconIds.SMALL_SINGLE_ENGINE)
            style.removeImage(AdsbIconIds.SMALL_JET)
            style.removeImage(AdsbIconIds.LARGE_JET)
            style.removeImage(AdsbIconIds.HELICOPTER)
            style.removeImage(AdsbIconIds.GLIDER)
            style.removeImage(AdsbIconIds.UNKNOWN)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup ADS-B overlay: ${t.message}")
        }
    }

    private companion object {
        private const val TAG = "AdsbTrafficOverlay"

        private const val SOURCE_ID = "adsb-traffic-source"
        private const val ICON_LAYER_ID = "adsb-traffic-icon-layer"
        private const val LABEL_LAYER_ID = "adsb-traffic-label-layer"
        private const val PROP_ICAO24 = "icao24"
        private const val PROP_LABEL = "label"
        private const val PROP_ICON_ID = "icon_id"
        private const val PROP_TRACK_DEG = "track_deg"
        private const val PROP_ALPHA = "alpha"
        private const val PROP_ALT_M = "alt_m"
        private const val PROP_SPEED_MPS = "speed_mps"
        private const val PROP_VS_MPS = "vs_mps"
        private const val PROP_AGE_SEC = "age_s"
        private const val PROP_CATEGORY = "category"

        private const val MAX_TARGETS = 120
        private const val LIVE_ALPHA = 0.90
        private const val STALE_ALPHA = 0.45

        private const val ICON_SIZE = 0.05f

        private const val LABEL_TEXT_SIZE_SP = 11f
        private const val LABEL_HALO_WIDTH_DP = 1.1f
        private const val LABEL_TEXT_OFFSET_Y = 1.1f
        private const val LABEL_TEXT_COLOR = "#FFF3D4"
        private const val LABEL_HALO_COLOR = "#2B1204"
        private const val DEFAULT_ICON_SIZE_PX = 24
    }

    private fun registerStyleImages(style: Style) {
        addStyleImage(
            style = style,
            imageId = AdsbIconIds.SMALL_SINGLE_ENGINE,
            drawableId = R.drawable.ic_adsb_small_single_engine
        )
        addStyleImage(
            style = style,
            imageId = AdsbIconIds.SMALL_JET,
            drawableId = R.drawable.ic_adsb_small_jet
        )
        addStyleImage(
            style = style,
            imageId = AdsbIconIds.LARGE_JET,
            drawableId = R.drawable.ic_adsb_large_jet
        )
        addStyleImage(
            style = style,
            imageId = AdsbIconIds.HELICOPTER,
            drawableId = R.drawable.ic_adsb_helicopter
        )
        addStyleImage(
            style = style,
            imageId = AdsbIconIds.GLIDER,
            drawableId = R.drawable.ic_adsb_glider
        )
        addStyleImage(
            style = style,
            imageId = AdsbIconIds.UNKNOWN,
            drawableId = R.drawable.ic_adsb_unknown
        )
    }

    private fun addStyleImage(
        style: Style,
        imageId: String,
        @DrawableRes drawableId: Int
    ) {
        val bitmap = drawableToBitmap(drawableId)
        if (bitmap != null) {
            style.addImage(imageId, bitmap)
        }
    }

    private fun drawableToBitmap(@DrawableRes drawableId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_ICON_SIZE_PX
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_ICON_SIZE_PX
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

}
