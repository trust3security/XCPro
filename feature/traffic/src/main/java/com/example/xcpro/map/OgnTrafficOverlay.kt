package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.TimeBridge
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

/**
 * Runtime map overlay for OGN traffic targets.
 * Keeps all MapLibre source/layer state local to the UI runtime.
 */
class OgnTrafficOverlay(
    private val context: Context,
    private val map: MapLibreMap,
    initialIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX,
    initialUseSatelliteContrastIcons: Boolean = false
) : OgnTrafficOverlayHandle {
    private var currentIconSizePx: Int = clampOgnIconSizePx(initialIconSizePx)
    private var useSatelliteContrastIcons: Boolean = initialUseSatelliteContrastIcons

    override fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            ensureOgnStyleImages(context, style)
            if (style.getLayer(ICON_LAYER_ID) == null) {
                val iconLayer = createOgnIconLayer(currentIconSizePx)
                val anchorId = BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK
                if (style.getLayer(anchorId) != null) {
                    style.addLayerAbove(iconLayer, anchorId)
                } else {
                    style.addLayer(iconLayer)
                }
            }
            if (style.getLayer(TOP_LABEL_LAYER_ID) == null) {
                val topLayer = createOgnTopLabelLayer(currentIconSizePx)
                if (style.getLayer(ICON_LAYER_ID) != null) {
                    style.addLayerAbove(topLayer, ICON_LAYER_ID)
                } else {
                    style.addLayer(topLayer)
                }
            }
            if (style.getLayer(BOTTOM_LABEL_LAYER_ID) == null) {
                val bottomLayer = createOgnBottomLabelLayer(currentIconSizePx)
                if (style.getLayer(TOP_LABEL_LAYER_ID) != null) {
                    style.addLayerAbove(bottomLayer, TOP_LABEL_LAYER_ID)
                } else if (style.getLayer(ICON_LAYER_ID) != null) {
                    style.addLayerAbove(bottomLayer, ICON_LAYER_ID)
                } else {
                    style.addLayer(bottomLayer)
                }
            }
            ensureOgnLayerOrder(style, currentIconSizePx)
            applyIconSizeToStyle()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize OGN overlay: ${t.message}", t)
        }
    }

    override fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnIconSizePx(iconSizePx)
        if (clamped == currentIconSizePx) return
        currentIconSizePx = clamped
        applyIconSizeToStyle()
    }

    override fun setUseSatelliteContrastIcons(enabled: Boolean) {
        useSatelliteContrastIcons = enabled
    }

    override fun render(
        targets: List<OgnTrafficTarget>,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences
    ) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val nowMonoMs = TimeBridge.nowMonoMs()
        val visibleBounds = map.projection.visibleRegion?.latLngBounds
        renderOgnTrafficFrame(
            source = source,
            nowMonoMs = nowMonoMs,
            targets = targets,
            ownshipAltitudeMeters = ownshipAltitudeMeters,
            visibleBounds = visibleBounds,
            altitudeUnit = altitudeUnit,
            useSatelliteContrastIcons = useSatelliteContrastIcons,
            unitsPreferences = unitsPreferences,
            maxTargets = MAX_TARGETS,
            staleVisualAfterMs = STALE_VISUAL_AFTER_MS,
            liveAlpha = LIVE_ALPHA,
            staleAlpha = STALE_ALPHA
        )
    }

    override fun findTargetAt(tap: LatLng): String? {
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
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
    }

    override fun cleanup() {
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

    override fun bringToFront() {
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
            style.addLayer(createOgnIconLayer(currentIconSizePx))
            style.addLayer(createOgnTopLabelLayer(currentIconSizePx))
            style.addLayer(createOgnBottomLabelLayer(currentIconSizePx))
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to bring OGN overlay to front: ${t.message}")
        }
    }

    private fun applyIconSizeToStyle() {
        applyOgnIconSizeToStyle(
            style = map.style ?: return,
            iconSizePx = currentIconSizePx
        )
    }
}
