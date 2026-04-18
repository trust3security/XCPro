package com.trust3.xcpro.map

import android.graphics.Color
import com.trust3.xcpro.core.common.logging.AppLogger
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class OgnGliderTrailOverlay(
    private val map: MapLibreMap
) : OgnGliderTrailOverlayHandle {
    private var latestRenderedSegments: List<OgnGliderTrailSegment> = emptyList()

    override fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            if (style.getLayer(LAYER_ID) == null) {
                val layer = createLayer()
                when {
                    style.getLayer(OGN_THERMAL_CIRCLE_LAYER_ID) != null -> {
                        style.addLayerBelow(layer, OGN_THERMAL_CIRCLE_LAYER_ID)
                    }

                    style.getLayer(OGN_ICON_LAYER_ID) != null -> {
                        style.addLayerBelow(layer, OGN_ICON_LAYER_ID)
                    }

                    style.getLayer(BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK) != null -> {
                        style.addLayerBelow(layer, BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK)
                    }

                    else -> {
                        style.addLayer(layer)
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize OGN glider trail overlay: ${t.message}", t)
        }
    }

    override fun render(segments: List<OgnGliderTrailSegment>) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return

        try {
            val renderSegments = trimSegmentsForRender(segments)
            if (sameSegmentsByIdentity(latestRenderedSegments, renderSegments)) {
                return
            }
            latestRenderedSegments = renderSegments
            val features = ArrayList<Feature>(renderSegments.size)
            for (segment in renderSegments) {
                if (
                    !isValidOgnThermalCoordinate(segment.startLatitude, segment.startLongitude) ||
                    !isValidOgnThermalCoordinate(segment.endLatitude, segment.endLongitude)
                ) {
                    continue
                }
                val feature = Feature.fromGeometry(
                    LineString.fromLngLats(
                        listOf(
                            Point.fromLngLat(segment.startLongitude, segment.startLatitude),
                            Point.fromLngLat(segment.endLongitude, segment.endLatitude)
                        )
                    )
                )
                feature.addStringProperty(PROP_SEGMENT_ID, segment.id)
                feature.addNumberProperty(PROP_COLOR_INDEX, segment.colorIndex)
                feature.addNumberProperty(PROP_WIDTH_PX, segment.widthPx)
                features.add(feature)
            }
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to render OGN glider trail overlay: ${t.message}", t)
            latestRenderedSegments = emptyList()
            runCatching {
                source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            }.onFailure { clearFailure ->
                AppLogger.w(TAG, "Failed to clear OGN glider trail overlay after render failure: ${clearFailure.message}")
            }
        }
    }

    fun clear() {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        latestRenderedSegments = emptyList()
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    override fun cleanup() {
        val style = map.style ?: return
        latestRenderedSegments = emptyList()
        try {
            style.removeLayer(LAYER_ID)
            style.removeSource(SOURCE_ID)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup OGN glider trail overlay: ${t.message}")
        }
    }

    private fun createLayer(): LineLayer {
        val colorStops = snailColorHexStops().mapIndexed { index, hex ->
            Expression.stop(index, Expression.color(Color.parseColor(hex)))
        }.toTypedArray()
        val colorExpression = Expression.match(
            Expression.get(PROP_COLOR_INDEX),
            Expression.color(Color.parseColor(DEFAULT_COLOR)),
            *colorStops
        )

        return LineLayer(LAYER_ID, SOURCE_ID)
            .withProperties(
                lineColor(colorExpression),
                lineWidth(Expression.get(PROP_WIDTH_PX)),
                lineOpacity(LINE_OPACITY),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
    }

    companion object {
        private const val TAG = "OgnGliderTrailOverlay"

        private const val SOURCE_ID = "ogn-glider-trail-source"
        private const val LAYER_ID = "ogn-glider-trail-line-layer"

        private const val OGN_ICON_LAYER_ID = "ogn-traffic-icon-layer"
        private const val OGN_THERMAL_CIRCLE_LAYER_ID = "ogn-thermal-circle-layer"

        private const val PROP_SEGMENT_ID = "segment_id"
        private const val PROP_COLOR_INDEX = "color_index"
        private const val PROP_WIDTH_PX = "width_px"

        private const val LINE_OPACITY = 0.92f
        private const val DEFAULT_COLOR = "#FFF4B0"
        // AI-NOTE: Keep map-side feature creation bounded even if repository history is large.
        private const val MAX_RENDER_SEGMENTS = 12_000

        fun trimSegmentsForRender(
            segments: List<OgnGliderTrailSegment>,
            maxSegments: Int = MAX_RENDER_SEGMENTS
        ): List<OgnGliderTrailSegment> {
            if (maxSegments <= 0) return emptyList()
            return if (segments.size <= maxSegments) {
                segments
            } else {
                segments.takeLast(maxSegments)
            }
        }

        fun sameSegmentsByIdentity(
            previous: List<OgnGliderTrailSegment>,
            current: List<OgnGliderTrailSegment>
        ): Boolean {
            if (previous === current) return true
            if (previous.size != current.size) return false
            for (index in previous.indices) {
                if (previous[index].id != current[index].id) {
                    return false
                }
            }
            return true
        }
    }
}
