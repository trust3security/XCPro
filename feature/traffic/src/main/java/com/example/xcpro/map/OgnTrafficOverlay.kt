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
    private val packedGroupLabelControl = TrafficPackedGroupLabelControl()
    private val selectedGroupFanoutLayout = TrafficSelectedGroupFanoutLayout()
    private var currentIconSizePx: Int = clampOgnRenderedIconSizePx(initialIconSizePx)
    private var currentViewportZoom: Float =
        map.cameraPosition.zoom.toFloat().takeIf { it.isFinite() } ?: OGN_TRAFFIC_CLOSE_ZOOM_THRESHOLD
    private var currentViewportPolicy: OgnTrafficViewportDeclutterPolicy =
        resolveOgnTrafficViewportDeclutterPolicy(currentViewportZoom)
    private var useSatelliteContrastIcons: Boolean = initialUseSatelliteContrastIcons

    override fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            if (style.getSource(LEADER_LINE_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(LEADER_LINE_SOURCE_ID))
            }
            ensureOgnStyleImages(context, style)
            if (style.getLayer(LEADER_LINE_LAYER_ID) == null) {
                val leaderLineLayer = createOgnLeaderLineLayer()
                val anchorId = BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK
                when {
                    style.getLayer(ICON_LAYER_ID) != null -> style.addLayerBelow(leaderLineLayer, ICON_LAYER_ID)
                    style.getLayer(anchorId) != null -> style.addLayerAbove(leaderLineLayer, anchorId)
                    else -> style.addLayer(leaderLineLayer)
                }
            }
            if (style.getLayer(ICON_LAYER_ID) == null) {
                val iconLayer = createOgnIconLayer(currentIconSizePx)
                if (style.getLayer(LEADER_LINE_LAYER_ID) != null) {
                    style.addLayerAbove(iconLayer, LEADER_LINE_LAYER_ID)
                } else if (style.getLayer(BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK) != null) {
                    style.addLayerAbove(iconLayer, BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK)
                } else {
                    style.addLayer(iconLayer)
                }
            }
            if (style.getLayer(TOP_LABEL_LAYER_ID) == null) {
                val topLayer = createOgnTopLabelLayer(
                    renderedIconSizePx = currentIconSizePx,
                    labelsVisible = currentViewportPolicy.labelsVisible
                )
                if (style.getLayer(ICON_LAYER_ID) != null) {
                    style.addLayerAbove(topLayer, ICON_LAYER_ID)
                } else {
                    style.addLayer(topLayer)
                }
            }
            if (style.getLayer(BOTTOM_LABEL_LAYER_ID) == null) {
                val bottomLayer = createOgnBottomLabelLayer(
                    renderedIconSizePx = currentIconSizePx,
                    labelsVisible = currentViewportPolicy.labelsVisible
                )
                if (style.getLayer(TOP_LABEL_LAYER_ID) != null) {
                    style.addLayerAbove(bottomLayer, TOP_LABEL_LAYER_ID)
                } else if (style.getLayer(ICON_LAYER_ID) != null) {
                    style.addLayerAbove(bottomLayer, ICON_LAYER_ID)
                } else {
                    style.addLayer(bottomLayer)
                }
            }
            ensureOgnLayerOrder(
                style = style,
                renderedIconSizePx = currentIconSizePx,
                labelsVisible = currentViewportPolicy.labelsVisible
            )
            applyViewportPolicyToStyle()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize OGN overlay: ${t.message}", t)
        }
    }

    override fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnRenderedIconSizePx(iconSizePx)
        if (clamped == currentIconSizePx) return
        currentIconSizePx = clamped
        applyViewportPolicyToStyle()
    }

    override fun setViewportZoom(zoomLevel: Float) {
        val normalizedZoom = zoomLevel.takeIf { it.isFinite() } ?: return
        val resolvedPolicy = resolveOgnTrafficViewportDeclutterPolicy(normalizedZoom)
        if (currentViewportZoom == normalizedZoom && currentViewportPolicy == resolvedPolicy) {
            return
        }
        currentViewportZoom = normalizedZoom
        currentViewportPolicy = resolvedPolicy
        applyViewportPolicyToStyle()
    }

    override fun setUseSatelliteContrastIcons(enabled: Boolean) {
        useSatelliteContrastIcons = enabled
    }

    override fun render(
        targets: List<OgnTrafficTarget>,
        selectedTargetKey: String?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences
    ) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val leaderLineSource = style.getSourceAs<GeoJsonSource>(LEADER_LINE_SOURCE_ID) ?: return
        val nowMonoMs = TimeBridge.nowMonoMs()
        val visibleBounds = map.projection.visibleRegion?.latLngBounds
        val fullLabelKeys = resolveFullLabelKeys(
            targets = targets,
            selectedTargetKey = selectedTargetKey
        )
        val displayCoordinatesByKey = resolveDisplayCoordinatesByKey(
            targets = targets,
            selectedTargetKey = selectedTargetKey
        )
        renderOgnTrafficFrame(
            source = source,
            leaderLineSource = leaderLineSource,
            nowMonoMs = nowMonoMs,
            targets = targets,
            fullLabelKeys = fullLabelKeys,
            displayCoordinatesByKey = displayCoordinatesByKey,
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
            val targetKey = resolveOgnTrafficTargetKey(feature)
            if (targetKey != null) return targetKey
        }
        return null
    }

    fun clear() {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        val leaderLineSource = style.getSourceAs<GeoJsonSource>(LEADER_LINE_SOURCE_ID) ?: return
        leaderLineSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
    }

    override fun cleanup() {
        val style = map.style ?: return
        try {
            style.removeLayer(BOTTOM_LABEL_LAYER_ID)
            style.removeLayer(TOP_LABEL_LAYER_ID)
            style.removeLayer(ICON_LAYER_ID)
            style.removeLayer(LEADER_LINE_LAYER_ID)
            style.removeSource(SOURCE_ID)
            style.removeSource(LEADER_LINE_SOURCE_ID)
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
        if (
            style.getLayer(LEADER_LINE_LAYER_ID) == null ||
            style.getLayer(ICON_LAYER_ID) == null ||
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
            style.removeLayer(LEADER_LINE_LAYER_ID)
            style.addLayer(createOgnLeaderLineLayer())
            style.addLayer(createOgnIconLayer(currentIconSizePx))
            style.addLayer(
                createOgnTopLabelLayer(
                    renderedIconSizePx = currentIconSizePx,
                    labelsVisible = currentViewportPolicy.labelsVisible
                )
            )
            style.addLayer(
                createOgnBottomLabelLayer(
                    renderedIconSizePx = currentIconSizePx,
                    labelsVisible = currentViewportPolicy.labelsVisible
                )
            )
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to bring OGN overlay to front: ${t.message}")
        }
    }

    private fun applyViewportPolicyToStyle() {
        applyOgnViewportPolicyToStyle(
            style = map.style ?: return,
            renderedIconSizePx = currentIconSizePx,
            labelsVisible = currentViewportPolicy.labelsVisible
        )
    }

    private fun resolveFullLabelKeys(
        targets: List<OgnTrafficTarget>,
        selectedTargetKey: String?
    ): Set<String> {
        if (targets.isEmpty()) return emptySet()
        val priorityRankByKey = rankOgnTargetsForPackedGroupLabels(
            targets = targets,
            selectedTargetKey = selectedTargetKey
        )
        val collisionSizePx = resolvePackedGroupCollisionSizePx()
        val seeds = targets.map { target ->
            TrafficPackedGroupLabelSeed(
                key = target.canonicalKey,
                latitude = target.latitude,
                longitude = target.longitude,
                collisionWidthPx = collisionSizePx,
                collisionHeightPx = collisionSizePx,
                priorityRank = priorityRankByKey.getValue(target.canonicalKey)
            )
        }
        return packedGroupLabelControl.resolveFullLabelKeys(
            map = map,
            seeds = seeds
        )
    }

    private fun resolveDisplayCoordinatesByKey(
        targets: List<OgnTrafficTarget>,
        selectedTargetKey: String?
    ): Map<String, TrafficDisplayCoordinate> {
        if (selectedTargetKey.isNullOrBlank() || currentViewportZoom < OGN_TRAFFIC_CLOSE_ZOOM_THRESHOLD) {
            return emptyMap()
        }
        val collisionSizePx = resolvePackedGroupCollisionSizePx()
        val seeds = targets.map { target ->
            TrafficPackedGroupLabelSeed(
                key = target.canonicalKey,
                latitude = target.latitude,
                longitude = target.longitude,
                collisionWidthPx = collisionSizePx,
                collisionHeightPx = collisionSizePx,
                priorityRank = 0
            )
        }
        return selectedGroupFanoutLayout.resolveDisplayCoordinatesByKey(
            map = map,
            seeds = seeds,
            selectedTargetKey = selectedTargetKey
        )
    }

    private fun resolvePackedGroupCollisionSizePx(): Float {
        val density = context.resources.displayMetrics.density
        val minimumCollisionSizePx = PACKED_GROUP_COLLISION_SIZE_DP * density
        return maxOf(currentIconSizePx.toFloat(), minimumCollisionSizePx)
    }

    private companion object {
        private const val PACKED_GROUP_COLLISION_SIZE_DP = 40f
    }
}
