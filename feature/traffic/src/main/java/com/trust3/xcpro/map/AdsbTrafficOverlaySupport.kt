package com.trust3.xcpro.map

import android.graphics.Color
import com.trust3.xcpro.adsb.ADSB_ICON_BITMAP_BASE_SIZE_PX
import com.trust3.xcpro.common.units.UnitsPreferences
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
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
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

internal fun createAdsbLeaderLineLayer(): LineLayer =
    LineLayer(ADSB_TRAFFIC_LEADER_LINE_LAYER_ID, ADSB_TRAFFIC_LEADER_LINE_SOURCE_ID)
        .withProperties(
            lineColor(ADSB_TRAFFIC_LEADER_LINE_COLOR),
            lineWidth(ADSB_TRAFFIC_LEADER_LINE_WIDTH_PX),
            lineOpacity(ADSB_TRAFFIC_LEADER_LINE_OPACITY),
            lineCap("round"),
            lineJoin("round")
        )

internal fun createAdsbIconOutlineLayer(
    currentIconSizePx: Int,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
): SymbolLayer =
    SymbolLayer(ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID, ADSB_TRAFFIC_SOURCE_ID)
        .withProperties(
            iconImage(Expression.get(AdsbGeoJsonMapper.PROP_ICON_ID)),
            iconSize(
                adsbRenderedIconScale(
                    iconSizePx = currentIconSizePx,
                    viewportPolicy = viewportPolicy
                ) * ADSB_TRAFFIC_OUTLINE_ICON_SCALE_MULTIPLIER
            ),
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

internal fun createAdsbIconLayer(
    currentIconSizePx: Int,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
): SymbolLayer =
    SymbolLayer(ADSB_TRAFFIC_ICON_LAYER_ID, ADSB_TRAFFIC_SOURCE_ID)
        .withProperties(
            iconImage(Expression.get(AdsbGeoJsonMapper.PROP_ICON_ID)),
            iconSize(adsbRenderedIconScale(iconSizePx = currentIconSizePx, viewportPolicy = viewportPolicy)),
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

internal fun createAdsbTopLabelLayer(
    currentIconSizePx: Int,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
): SymbolLayer =
    SymbolLayer(ADSB_TRAFFIC_TOP_LABEL_LAYER_ID, ADSB_TRAFFIC_SOURCE_ID)
        .withProperties(
            textField(adsbLabelTextExpression(AdsbGeoJsonMapper.PROP_LABEL_TOP, viewportPolicy)),
            textFont(ADSB_TRAFFIC_LABEL_FONT_STACK),
            textSize(ADSB_TRAFFIC_LABEL_TEXT_SIZE_SP),
            textColor(ADSB_TRAFFIC_LABEL_TEXT_COLOR),
            textOffset(
                arrayOf(
                    0f,
                    adsbTopLabelOffsetYForPx(
                        iconSizePx = currentIconSizePx,
                        viewportPolicy = viewportPolicy
                    )
                )
            ),
            textAnchor("center"),
            textAllowOverlap(adsbPriorityLabelsAllowOverlap(viewportPolicy)),
            textIgnorePlacement(adsbPriorityLabelsAllowOverlap(viewportPolicy)),
            textOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA)),
            visibility("visible")
        )

internal fun createAdsbBottomLabelLayer(
    currentIconSizePx: Int,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
): SymbolLayer =
    SymbolLayer(ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID, ADSB_TRAFFIC_SOURCE_ID)
        .withProperties(
            textField(adsbLabelTextExpression(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM, viewportPolicy)),
            textFont(ADSB_TRAFFIC_LABEL_FONT_STACK),
            textSize(ADSB_TRAFFIC_LABEL_TEXT_SIZE_SP),
            textColor(ADSB_TRAFFIC_LABEL_TEXT_COLOR),
            textOffset(
                arrayOf(
                    0f,
                    adsbBottomLabelOffsetYForPx(
                        iconSizePx = currentIconSizePx,
                        viewportPolicy = viewportPolicy
                    )
                )
            ),
            textAnchor("center"),
            textAllowOverlap(adsbPriorityLabelsAllowOverlap(viewportPolicy)),
            textIgnorePlacement(adsbPriorityLabelsAllowOverlap(viewportPolicy)),
            textOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA)),
            visibility("visible")
        )

internal fun applyAdsbViewportPolicyToStyle(
    style: Style,
    iconSizePx: Int,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
) {
    val outlineLayer = style.getLayer(ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID) as? SymbolLayer
    outlineLayer?.setProperties(
        iconSize(
            adsbRenderedIconScale(
                iconSizePx = iconSizePx,
                viewportPolicy = viewportPolicy
            ) * ADSB_TRAFFIC_OUTLINE_ICON_SCALE_MULTIPLIER
        )
    )

    val iconLayer = style.getLayer(ADSB_TRAFFIC_ICON_LAYER_ID) as? SymbolLayer
    iconLayer?.setProperties(
        iconSize(adsbRenderedIconScale(iconSizePx = iconSizePx, viewportPolicy = viewportPolicy))
    )

    val topLabelLayer = style.getLayer(ADSB_TRAFFIC_TOP_LABEL_LAYER_ID) as? SymbolLayer
    topLabelLayer?.setProperties(
        textField(adsbLabelTextExpression(AdsbGeoJsonMapper.PROP_LABEL_TOP, viewportPolicy)),
        textOffset(
            arrayOf(
                0f,
                adsbTopLabelOffsetYForPx(iconSizePx = iconSizePx, viewportPolicy = viewportPolicy)
            )
        ),
        textAllowOverlap(adsbPriorityLabelsAllowOverlap(viewportPolicy)),
        textIgnorePlacement(adsbPriorityLabelsAllowOverlap(viewportPolicy)),
        visibility("visible")
    )

    val bottomLabelLayer = style.getLayer(ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID) as? SymbolLayer
    bottomLabelLayer?.setProperties(
        textField(adsbLabelTextExpression(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM, viewportPolicy)),
        textOffset(
            arrayOf(
                0f,
                adsbBottomLabelOffsetYForPx(
                    iconSizePx = iconSizePx,
                    viewportPolicy = viewportPolicy
                )
            )
        ),
        textAllowOverlap(adsbPriorityLabelsAllowOverlap(viewportPolicy)),
        textIgnorePlacement(adsbPriorityLabelsAllowOverlap(viewportPolicy)),
        visibility("visible")
    )
}

internal fun resolveAdsbViewportRangeMeters(map: MapLibreMap): Double? {
    val center = map.cameraPosition.target ?: return null
    val visibleRegion = map.projection.visibleRegion ?: return null
    val topLeft = visibleRegion.farLeft ?: return null
    val topRight = visibleRegion.farRight ?: return null
    val bottomLeft = visibleRegion.nearLeft ?: return null
    val bottomRight = visibleRegion.nearRight ?: return null
    return resolveAdsbViewportRangeMeters(
        center = center,
        topLeft = topLeft,
        topRight = topRight,
        bottomLeft = bottomLeft,
        bottomRight = bottomRight
    )
}

internal fun resolveAdsbViewportRangeMeters(
    center: LatLng,
    topLeft: LatLng,
    topRight: LatLng,
    bottomLeft: LatLng,
    bottomRight: LatLng
): Double? {
    if (!center.hasFiniteCoordinates() ||
        !topLeft.hasFiniteCoordinates() ||
        !topRight.hasFiniteCoordinates() ||
        !bottomLeft.hasFiniteCoordinates() ||
        !bottomRight.hasFiniteCoordinates()
    ) {
        return null
    }
    val edgeMidpoints = listOf(
        midpoint(topLeft, topRight),
        midpoint(bottomLeft, bottomRight),
        midpoint(topLeft, bottomLeft),
        midpoint(topRight, bottomRight)
    )
    return edgeMidpoints
        .map { edgePoint ->
            haversineMeters(
                lat1 = center.latitude,
                lon1 = center.longitude,
                lat2 = edgePoint.latitude,
                lon2 = edgePoint.longitude
            )
        }
        .filter { it.isFinite() && it > 0.0 }
        .maxOrNull()
}

internal fun hasActiveAdsbVisualAnimation(
    frameSnapshot: AdsbDisplayMotionSmoother.FrameSnapshot,
    emergencyFlashEnabled: Boolean
): Boolean =
    frameSnapshot.hasActiveAnimations ||
        (
            emergencyFlashEnabled &&
                frameSnapshot.targets.any { target ->
                    !target.isPositionStale && target.proximityTier == AdsbProximityTier.EMERGENCY
                }
        )

internal fun adsbIconScaleForPx(iconSizePx: Int): Float =
    iconSizePx.toFloat() / ADSB_ICON_BITMAP_BASE_SIZE_PX.toFloat()

internal fun adsbRenderedIconScale(
    iconSizePx: Int,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
): Float = adsbIconScaleForPx(iconSizePx) * viewportPolicy.iconScaleMultiplier

internal fun adsbPackedGroupCollisionSizePx(
    iconSizePx: Int,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy,
    density: Float
): Float {
    val minimumCollisionSizePx = ADSB_TRAFFIC_PACKED_GROUP_COLLISION_SIZE_DP * density
    val renderedOutlineSizePx =
        iconSizePx.toFloat() *
            viewportPolicy.iconScaleMultiplier *
            ADSB_TRAFFIC_OUTLINE_ICON_SCALE_MULTIPLIER
    return maxOf(renderedOutlineSizePx, minimumCollisionSizePx)
}

internal fun adsbTopLabelOffsetYForPx(
    iconSizePx: Int,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
): Float = -ADSB_TRAFFIC_LABEL_TEXT_OFFSET_BASE_Y * adsbRenderedIconScale(iconSizePx, viewportPolicy)

internal fun adsbBottomLabelOffsetYForPx(
    iconSizePx: Int,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
): Float = ADSB_TRAFFIC_LABEL_TEXT_OFFSET_BASE_Y * adsbRenderedIconScale(iconSizePx, viewportPolicy)

internal fun renderAdsbTrafficFrame(
    source: GeoJsonSource,
    leaderLineSource: GeoJsonSource,
    nowMonoMs: Long,
    frameSnapshot: AdsbDisplayMotionSmoother.FrameSnapshot,
    fullLabelKeys: Set<String>,
    displayCoordinatesByKey: Map<String, TrafficDisplayCoordinate>,
    ownshipAltitudeMeters: Double?,
    unitsPreferences: UnitsPreferences,
    iconStyleIdOverrides: Map<String, String>,
    emergencyFlashEnabled: Boolean,
    maxTargets: Int
) {
    source.setGeoJson(
        FeatureCollection.fromFeatures(
            buildAdsbTrafficOverlayFeatures(
                nowMonoMs = nowMonoMs,
                targets = frameSnapshot.targets,
                fullLabelKeys = fullLabelKeys,
                displayCoordinatesByKey = displayCoordinatesByKey,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                unitsPreferences = unitsPreferences,
                iconStyleIdOverrides = iconStyleIdOverrides,
                emergencyFlashEnabled = emergencyFlashEnabled,
                maxTargets = maxTargets,
                liveAlpha = ADSB_TRAFFIC_LIVE_ALPHA,
                staleAlpha = ADSB_TRAFFIC_STALE_ALPHA
            )
        )
    )
    leaderLineSource.setGeoJson(
        FeatureCollection.fromFeatures(
            buildAdsbTrafficLeaderLineFeatures(
                targets = frameSnapshot.targets,
                displayCoordinatesByKey = displayCoordinatesByKey,
                maxTargets = maxTargets
            )
        )
    )
}

private fun adsbLabelTextExpression(
    labelProperty: String,
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
): Expression {
    val labelExpression = Expression.get(labelProperty)
    if (viewportPolicy.showAllLabels) {
        return labelExpression
    }
    return Expression.switchCase(
        Expression.lte(
            Expression.coalesce(
                Expression.get(AdsbGeoJsonMapper.PROP_DISTANCE_M),
                Expression.literal(viewportPolicy.closeTrafficLabelDistanceMeters + 1.0)
            ),
            Expression.literal(viewportPolicy.closeTrafficLabelDistanceMeters)
        ),
        labelExpression,
        Expression.literal("")
    )
}

private fun adsbPriorityLabelsAllowOverlap(
    viewportPolicy: AdsbTrafficViewportDeclutterPolicy
): Boolean = !viewportPolicy.showAllLabels

private fun midpoint(first: LatLng, second: LatLng): LatLng = LatLng(
    (first.latitude + second.latitude) / 2.0,
    midpointLongitude(first.longitude, second.longitude)
)

private fun midpointLongitude(firstLongitude: Double, secondLongitude: Double): Double {
    val delta = ((secondLongitude - firstLongitude + 540.0) % 360.0) - 180.0
    return normalizeLongitude(firstLongitude + delta / 2.0)
}

private fun normalizeLongitude(longitude: Double): Double {
    var normalized = longitude
    while (normalized > 180.0) normalized -= 360.0
    while (normalized < -180.0) normalized += 360.0
    return normalized
}

private fun LatLng.hasFiniteCoordinates(): Boolean =
    latitude.isFinite() && longitude.isFinite()

private const val ADSB_TRAFFIC_LEADER_LINE_COLOR = "#202020"
private const val ADSB_TRAFFIC_LEADER_LINE_WIDTH_PX = 1.4f
private const val ADSB_TRAFFIC_LEADER_LINE_OPACITY = 0.32f
