package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import org.maplibre.android.geometry.LatLngBounds
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
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer

internal fun ensureOgnStyleImages(context: Context, style: Style) {
    OgnAircraftIcon.values().forEach { icon ->
        val existing = runCatching { style.getImage(icon.styleImageId) }.getOrNull()
        if (existing != null) return@forEach
        val bitmap = drawableToBitmap(context = context, drawableId = icon.resId) ?: return@forEach
        style.addImage(icon.styleImageId, bitmap)
    }
    ensureOgnSatelliteGliderStyleImage(style, context)
    ensureOgnRelativeGliderStyleImages(style, context)
    ensureOgnClusterStyleImage(style)
}

internal fun createOgnIconLayer(renderedIconSizePx: Int): SymbolLayer =
    SymbolLayer(ICON_LAYER_ID, SOURCE_ID)
        .withProperties(
            iconImage(
                Expression.coalesce(
                    Expression.get(PROP_ICON_ID),
                    Expression.literal(DEFAULT_ICON_IMAGE_ID)
                )
            ),
            iconSize(ognIconScaleForPx(renderedIconSizePx)),
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

internal fun createOgnTopLabelLayer(
    renderedIconSizePx: Int,
    labelsVisible: Boolean
): SymbolLayer =
    SymbolLayer(TOP_LABEL_LAYER_ID, SOURCE_ID)
        .withProperties(
            textField(Expression.get(PROP_TOP_LABEL)),
            textFont(LABEL_FONT_STACK),
            textSize(ognLabelTextSizeForPx(renderedIconSizePx)),
            textColor(LABEL_TEXT_COLOR),
            textOffset(arrayOf(0f, ognTopLabelOffsetYForPx(renderedIconSizePx))),
            textAnchor("center"),
            textAllowOverlap(true),
            textIgnorePlacement(true),
            textOpacity(Expression.get(PROP_ALPHA)),
            visibility(if (labelsVisible) "visible" else "none")
        )

internal fun createOgnClusterCountLayer(renderedIconSizePx: Int): SymbolLayer =
    SymbolLayer(CLUSTER_COUNT_LAYER_ID, SOURCE_ID)
        .withProperties(
            textField(Expression.get(PROP_CLUSTER_COUNT_LABEL)),
            textFont(LABEL_FONT_STACK),
            textSize(ognClusterCountTextSizeForPx(renderedIconSizePx)),
            textColor(CLUSTER_COUNT_TEXT_COLOR),
            textHaloColor(CLUSTER_COUNT_TEXT_HALO_COLOR),
            textHaloWidth(CLUSTER_COUNT_TEXT_HALO_WIDTH_DP),
            textOffset(ognClusterCountOffsetForPx(renderedIconSizePx)),
            textAnchor("center"),
            textAllowOverlap(true),
            textIgnorePlacement(true),
            textOpacity(Expression.get(PROP_ALPHA))
        )

internal fun createOgnBottomLabelLayer(
    renderedIconSizePx: Int,
    labelsVisible: Boolean
): SymbolLayer =
    SymbolLayer(BOTTOM_LABEL_LAYER_ID, SOURCE_ID)
        .withProperties(
            textField(Expression.get(PROP_BOTTOM_LABEL)),
            textFont(LABEL_FONT_STACK),
            textSize(ognLabelTextSizeForPx(renderedIconSizePx)),
            textColor(LABEL_TEXT_COLOR),
            textOffset(arrayOf(0f, ognBottomLabelOffsetYForPx(renderedIconSizePx))),
            textAnchor("center"),
            textAllowOverlap(true),
            textIgnorePlacement(true),
            textOpacity(Expression.get(PROP_ALPHA)),
            visibility(if (labelsVisible) "visible" else "none")
        )

internal fun ensureOgnLayerOrder(
    style: Style,
    renderedIconSizePx: Int,
    labelsVisible: Boolean
) {
    val anchorId = BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK
    if (style.getLayer(anchorId) == null) return
    if (style.getLayer(ICON_LAYER_ID) == null ||
        style.getLayer(CLUSTER_COUNT_LAYER_ID) == null ||
        style.getLayer(TOP_LABEL_LAYER_ID) == null ||
        style.getLayer(BOTTOM_LABEL_LAYER_ID) == null
    ) {
        return
    }

    val layerIds = style.layers.map { it.id }
    val anchorIndex = layerIds.indexOf(anchorId)
    val iconIndex = layerIds.indexOf(ICON_LAYER_ID)
    val clusterCountIndex = layerIds.indexOf(CLUSTER_COUNT_LAYER_ID)
    val topIndex = layerIds.indexOf(TOP_LABEL_LAYER_ID)
    val bottomIndex = layerIds.indexOf(BOTTOM_LABEL_LAYER_ID)
    if (anchorIndex < 0 || iconIndex < 0 || clusterCountIndex < 0 || topIndex < 0 || bottomIndex < 0) {
        return
    }

    val iconNeedsMove = iconIndex <= anchorIndex
    val clusterCountNeedsMove = clusterCountIndex <= iconIndex
    val topNeedsMove = topIndex <= clusterCountIndex
    val bottomNeedsMove = bottomIndex <= topIndex
    if (!iconNeedsMove && !clusterCountNeedsMove && !topNeedsMove && !bottomNeedsMove) return

    style.removeLayer(BOTTOM_LABEL_LAYER_ID)
    style.removeLayer(TOP_LABEL_LAYER_ID)
    style.removeLayer(CLUSTER_COUNT_LAYER_ID)
    style.removeLayer(ICON_LAYER_ID)
    style.addLayerAbove(createOgnIconLayer(renderedIconSizePx), anchorId)
    style.addLayerAbove(createOgnClusterCountLayer(renderedIconSizePx), ICON_LAYER_ID)
    style.addLayerAbove(
        createOgnTopLabelLayer(
            renderedIconSizePx = renderedIconSizePx,
            labelsVisible = labelsVisible
        ),
        CLUSTER_COUNT_LAYER_ID
    )
    style.addLayerAbove(
        createOgnBottomLabelLayer(
            renderedIconSizePx = renderedIconSizePx,
            labelsVisible = labelsVisible
        ),
        TOP_LABEL_LAYER_ID
    )
}

internal fun applyOgnViewportPolicyToStyle(
    style: Style,
    renderedIconSizePx: Int,
    labelsVisible: Boolean
) {
    val iconLayer = style.getLayer(ICON_LAYER_ID) as? SymbolLayer
    iconLayer?.setProperties(iconSize(ognIconScaleForPx(renderedIconSizePx)))

    val clusterCountLayer = style.getLayer(CLUSTER_COUNT_LAYER_ID) as? SymbolLayer
    clusterCountLayer?.setProperties(
        textSize(ognClusterCountTextSizeForPx(renderedIconSizePx)),
        textOffset(ognClusterCountOffsetForPx(renderedIconSizePx))
    )

    val labelSize = ognLabelTextSizeForPx(renderedIconSizePx)
    val topLabelLayer = style.getLayer(TOP_LABEL_LAYER_ID) as? SymbolLayer
    topLabelLayer?.setProperties(
        textSize(labelSize),
        textOffset(arrayOf(0f, ognTopLabelOffsetYForPx(renderedIconSizePx))),
        visibility(if (labelsVisible) "visible" else "none")
    )

    val bottomLabelLayer = style.getLayer(BOTTOM_LABEL_LAYER_ID) as? SymbolLayer
    bottomLabelLayer?.setProperties(
        textSize(labelSize),
        textOffset(arrayOf(0f, ognBottomLabelOffsetYForPx(renderedIconSizePx))),
        visibility(if (labelsVisible) "visible" else "none")
    )
}

internal fun isValidOgnCoordinate(latitude: Double, longitude: Double): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    if (kotlin.math.abs(latitude) > 90.0) return false
    if (kotlin.math.abs(longitude) > 180.0) return false
    return true
}

internal fun isOgnInVisibleBounds(
    latitude: Double,
    longitude: Double,
    bounds: LatLngBounds?
): Boolean {
    if (bounds == null) return true
    return isInViewport(
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

internal fun resolveOgnStyleImageId(
    icon: OgnAircraftIcon,
    useSatelliteContrastIcons: Boolean
): String {
    if (useSatelliteContrastIcons && icon == OgnAircraftIcon.Glider) {
        return SATELLITE_GLIDER_ICON_IMAGE_ID
    }
    return icon.styleImageId
}

internal fun drawableToBitmap(context: Context, drawableId: Int, tintColor: Int? = null): Bitmap? {
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

private fun ensureOgnRelativeGliderStyleImages(style: Style, context: Context) {
    ensureOgnTintedStyleImage(
        style = style,
        context = context,
        imageId = RELATIVE_GLIDER_ABOVE_ICON_IMAGE_ID,
        drawableId = OgnAircraftIcon.Glider.resId,
        tintColor = RELATIVE_GLIDER_ABOVE_TINT
    )
    ensureOgnTintedStyleImage(
        style = style,
        context = context,
        imageId = RELATIVE_GLIDER_BELOW_ICON_IMAGE_ID,
        drawableId = OgnAircraftIcon.Glider.resId,
        tintColor = RELATIVE_GLIDER_BELOW_TINT
    )
    ensureOgnTintedStyleImage(
        style = style,
        context = context,
        imageId = RELATIVE_GLIDER_NEAR_ICON_IMAGE_ID,
        drawableId = OgnAircraftIcon.Glider.resId,
        tintColor = RELATIVE_GLIDER_NEAR_TINT
    )
}

private fun ensureOgnTintedStyleImage(
    style: Style,
    context: Context,
    imageId: String,
    drawableId: Int,
    tintColor: Int
) {
    val existing = runCatching { style.getImage(imageId) }.getOrNull()
    if (existing != null) return
    val bitmap = drawableToBitmap(context = context, drawableId = drawableId, tintColor = tintColor) ?: return
    style.addImage(imageId, bitmap)
}

private fun ensureOgnSatelliteGliderStyleImage(style: Style, context: Context) {
    val existing = runCatching { style.getImage(SATELLITE_GLIDER_ICON_IMAGE_ID) }.getOrNull()
    if (existing != null) return
    val bitmap = drawableToBitmap(
        context = context,
        drawableId = OgnAircraftIcon.Glider.resId,
        tintColor = Color.WHITE
    ) ?: return
    style.addImage(SATELLITE_GLIDER_ICON_IMAGE_ID, bitmap)
}

private fun ensureOgnClusterStyleImage(style: Style) {
    val existing = runCatching { style.getImage(CLUSTER_ICON_IMAGE_ID) }.getOrNull()
    if (existing != null) return
    style.addImage(CLUSTER_ICON_IMAGE_ID, createOgnClusterBitmap())
}

private fun createOgnClusterBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(
        ICON_BITMAP_BASE_SIZE_PX,
        ICON_BITMAP_BASE_SIZE_PX,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF4C95D")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#202020")
        style = Paint.Style.STROKE
        strokeWidth = (ICON_BITMAP_BASE_SIZE_PX / 14f).coerceAtLeast(4f)
    }
    val center = ICON_BITMAP_BASE_SIZE_PX / 2f
    val radius = ICON_BITMAP_BASE_SIZE_PX * 0.34f
    canvas.drawCircle(center, center, radius, fillPaint)
    canvas.drawCircle(center, center, radius, strokePaint)
    return bitmap
}

internal fun ognIconScaleForPx(iconSizePx: Int): Float =
    iconSizePx.toFloat() / ICON_BITMAP_BASE_SIZE_PX.toFloat()

internal fun ognLabelTextSizeForPx(iconSizePx: Int): Float {
    val scaled = LABEL_TEXT_SIZE_BASE_SP * ognIconScaleForPx(iconSizePx)
    return scaled.coerceIn(MIN_LABEL_TEXT_SIZE_SP, MAX_LABEL_TEXT_SIZE_SP)
}

internal fun ognClusterCountTextSizeForPx(iconSizePx: Int): Float {
    val scaled = (LABEL_TEXT_SIZE_BASE_SP * 0.9f) * ognIconScaleForPx(iconSizePx)
    return scaled.coerceIn(10f, 16f)
}

internal fun ognClusterCountOffsetForPx(iconSizePx: Int): Array<Float> {
    val scale = ognIconScaleForPx(iconSizePx)
    val offset = 0.6f + (0.45f * scale)
    return arrayOf(offset, -offset)
}

internal fun ognTopLabelOffsetYForPx(iconSizePx: Int): Float =
    -LABEL_TEXT_OFFSET_BASE_Y * ognIconScaleForPx(iconSizePx)

internal fun ognBottomLabelOffsetYForPx(iconSizePx: Int): Float =
    LABEL_TEXT_OFFSET_BASE_Y * ognIconScaleForPx(iconSizePx)
