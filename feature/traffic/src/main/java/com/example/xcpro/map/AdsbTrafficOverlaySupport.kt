package com.example.xcpro.map

import android.graphics.Color
import com.example.xcpro.common.units.UnitsPreferences
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
import org.maplibre.geojson.FeatureCollection

internal fun createAdsbIconOutlineLayer(currentIconSizePx: Int): SymbolLayer =
    SymbolLayer(ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID, ADSB_TRAFFIC_SOURCE_ID)
        .withProperties(
            iconImage(Expression.get(AdsbGeoJsonMapper.PROP_ICON_ID)),
            iconSize(adsbIconScaleForPx(currentIconSizePx) * ADSB_TRAFFIC_OUTLINE_ICON_SCALE_MULTIPLIER),
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

internal fun createAdsbIconLayer(currentIconSizePx: Int): SymbolLayer =
    SymbolLayer(ADSB_TRAFFIC_ICON_LAYER_ID, ADSB_TRAFFIC_SOURCE_ID)
        .withProperties(
            iconImage(Expression.get(AdsbGeoJsonMapper.PROP_ICON_ID)),
            iconSize(adsbIconScaleForPx(currentIconSizePx)),
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

internal fun createAdsbTopLabelLayer(currentIconSizePx: Int): SymbolLayer =
    SymbolLayer(ADSB_TRAFFIC_TOP_LABEL_LAYER_ID, ADSB_TRAFFIC_SOURCE_ID)
        .withProperties(
            textField(Expression.get(AdsbGeoJsonMapper.PROP_LABEL_TOP)),
            textFont(ADSB_TRAFFIC_LABEL_FONT_STACK),
            textSize(ADSB_TRAFFIC_LABEL_TEXT_SIZE_SP),
            textColor(ADSB_TRAFFIC_LABEL_TEXT_COLOR),
            textOffset(arrayOf(0f, adsbTopLabelOffsetYForPx(currentIconSizePx))),
            textAnchor("center"),
            textAllowOverlap(true),
            textIgnorePlacement(true),
            textOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA))
        )

internal fun createAdsbBottomLabelLayer(currentIconSizePx: Int): SymbolLayer =
    SymbolLayer(ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID, ADSB_TRAFFIC_SOURCE_ID)
        .withProperties(
            textField(Expression.get(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM)),
            textFont(ADSB_TRAFFIC_LABEL_FONT_STACK),
            textSize(ADSB_TRAFFIC_LABEL_TEXT_SIZE_SP),
            textColor(ADSB_TRAFFIC_LABEL_TEXT_COLOR),
            textOffset(arrayOf(0f, adsbBottomLabelOffsetYForPx(currentIconSizePx))),
            textAnchor("center"),
            textAllowOverlap(true),
            textIgnorePlacement(true),
            textOpacity(Expression.get(AdsbGeoJsonMapper.PROP_ALPHA))
        )

internal fun applyAdsbIconSizeToStyle(style: Style, iconSizePx: Int) {
    val outlineLayer = style.getLayer(ADSB_TRAFFIC_ICON_OUTLINE_LAYER_ID) as? SymbolLayer
    outlineLayer?.setProperties(
        iconSize(adsbIconScaleForPx(iconSizePx) * ADSB_TRAFFIC_OUTLINE_ICON_SCALE_MULTIPLIER)
    )

    val iconLayer = style.getLayer(ADSB_TRAFFIC_ICON_LAYER_ID) as? SymbolLayer
    iconLayer?.setProperties(iconSize(adsbIconScaleForPx(iconSizePx)))

    val topLabelLayer = style.getLayer(ADSB_TRAFFIC_TOP_LABEL_LAYER_ID) as? SymbolLayer
    topLabelLayer?.setProperties(textOffset(arrayOf(0f, adsbTopLabelOffsetYForPx(iconSizePx))))

    val bottomLabelLayer = style.getLayer(ADSB_TRAFFIC_BOTTOM_LABEL_LAYER_ID) as? SymbolLayer
    bottomLabelLayer?.setProperties(textOffset(arrayOf(0f, adsbBottomLabelOffsetYForPx(iconSizePx))))
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
    iconSizePx.toFloat() / ADSB_ICON_SIZE_DEFAULT_PX.toFloat()

internal fun adsbTopLabelOffsetYForPx(iconSizePx: Int): Float =
    -ADSB_TRAFFIC_LABEL_TEXT_OFFSET_BASE_Y * adsbIconScaleForPx(iconSizePx)

internal fun adsbBottomLabelOffsetYForPx(iconSizePx: Int): Float =
    ADSB_TRAFFIC_LABEL_TEXT_OFFSET_BASE_Y * adsbIconScaleForPx(iconSizePx)

internal fun renderAdsbTrafficFrame(
    source: GeoJsonSource,
    nowMonoMs: Long,
    frameSnapshot: AdsbDisplayMotionSmoother.FrameSnapshot,
    ownshipAltitudeMeters: Double?,
    unitsPreferences: UnitsPreferences,
    iconStyleIdOverrides: Map<String, String>,
    emergencyFlashEnabled: Boolean
) {
    source.setGeoJson(
        FeatureCollection.fromFeatures(
            buildAdsbTrafficOverlayFeatures(
                nowMonoMs = nowMonoMs,
                targets = frameSnapshot.targets,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                unitsPreferences = unitsPreferences,
                iconStyleIdOverrides = iconStyleIdOverrides,
                emergencyFlashEnabled = emergencyFlashEnabled,
                maxTargets = ADSB_TRAFFIC_MAX_TARGETS,
                liveAlpha = ADSB_TRAFFIC_LIVE_ALPHA,
                staleAlpha = ADSB_TRAFFIC_STALE_ALPHA
            )
        )
    )
}
