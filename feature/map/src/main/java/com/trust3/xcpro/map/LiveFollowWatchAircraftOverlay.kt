package com.trust3.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import kotlin.math.abs
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconKeepUpright
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

internal data class LiveFollowWatchAircraftOverlayState(
    val shareCode: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val trackDeg: Double?
)

internal class LiveFollowWatchAircraftOverlay(
    private val map: MapLibreMap,
    private val context: Context
) {
    companion object {
        private const val SOURCE_ID = "livefollow-watch-aircraft-source"
        internal const val LAYER_ID = LIVE_FOLLOW_WATCH_AIRCRAFT_LAYER_ID
        private const val ICON_ID = "livefollow-watch-aircraft-icon"
        private const val LOCATION_EPSILON = 1e-7
        private const val ROTATION_EPSILON_DEG = 0.05f
    }

    private var boundStyle: Style? = null
    private var lastRenderedState: LiveFollowWatchAircraftOverlayState? = null
    private var lastRenderedRotation: Float? = null
    private var lastRenderedVisible: Boolean? = null
    private var currentViewportZoom: Float =
        map.cameraPosition.zoom.toFloat().takeIf { it.isFinite() } ?: LIVE_FOLLOW_WATCH_AIRCRAFT_CLOSE_ZOOM_THRESHOLD
    private var currentIconScale: Float = resolveLiveFollowWatchAircraftScale(currentViewportZoom)

    fun render(state: LiveFollowWatchAircraftOverlayState?) {
        if (state == null) {
            setVisible(false)
            return
        }
        if (!state.latitudeDeg.isFinite() || !state.longitudeDeg.isFinite()) {
            setVisible(false)
            return
        }
        val style = map.style ?: return
        ensureRuntimeObjects(style)
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val layer = style.getLayerAs<SymbolLayer>(LAYER_ID) ?: return

        if (shouldUpdateLocation(state)) {
            source.setGeoJson(
                Feature.fromGeometry(
                    Point.fromLngLat(
                        state.longitudeDeg,
                        state.latitudeDeg
                    )
                )
            )
            lastRenderedState = state
        }

        val rotation = resolveLiveFollowWatchAircraftRotation(state.trackDeg)
        if (shouldUpdateRotation(rotation)) {
            layer.setProperties(iconRotate(rotation))
            lastRenderedRotation = rotation
        }

        applyVisibility(layer, visible = true)
        boundStyle = style
    }

    fun setViewportZoom(zoomLevel: Float) {
        val normalizedZoom = zoomLevel.takeIf { it.isFinite() } ?: return
        currentViewportZoom = normalizedZoom
        val resolvedScale = resolveLiveFollowWatchAircraftScale(normalizedZoom)
        if (resolvedScale == currentIconScale && boundStyle === map.style) {
            return
        }
        currentIconScale = resolvedScale
        applyIconScaleToStyle()
    }

    fun reapplyCurrentStyle() {
        val style = map.style ?: return
        ensureRuntimeObjects(style)
        val state = lastRenderedState
        if (state != null) {
            render(state)
            return
        }
        setVisible(false)
    }

    fun cleanup() {
        val style = map.style ?: return
        runCatching { style.removeLayer(LAYER_ID) }
        runCatching { style.removeSource(SOURCE_ID) }
        runCatching { style.removeImage(ICON_ID) }
        lastRenderedState = null
        lastRenderedRotation = null
        lastRenderedVisible = null
        boundStyle = null
    }

    private fun shouldUpdateLocation(
        state: LiveFollowWatchAircraftOverlayState
    ): Boolean {
        val previous = lastRenderedState ?: return true
        if (boundStyle !== map.style) return true
        return previous.shareCode != state.shareCode ||
            abs(previous.latitudeDeg - state.latitudeDeg) >= LOCATION_EPSILON ||
            abs(previous.longitudeDeg - state.longitudeDeg) >= LOCATION_EPSILON
    }

    private fun shouldUpdateRotation(rotation: Float): Boolean {
        val previous = lastRenderedRotation ?: return true
        if (boundStyle !== map.style) return true
        return abs(previous - rotation) >= ROTATION_EPSILON_DEG
    }

    private fun setVisible(visible: Boolean) {
        val style = map.style ?: return
        ensureRuntimeObjects(style)
        val layer = style.getLayerAs<SymbolLayer>(LAYER_ID) ?: return
        applyVisibility(layer, visible)
        if (!visible) {
            lastRenderedState = null
            lastRenderedRotation = null
        }
        boundStyle = style
    }

    private fun applyVisibility(
        layer: SymbolLayer,
        visible: Boolean
    ) {
        if (lastRenderedVisible == visible && boundStyle === map.style) {
            return
        }
        layer.setProperties(visibility(if (visible) "visible" else "none"))
        lastRenderedVisible = visible
    }

    private fun applyIconScaleToStyle() {
        val style = map.style ?: return
        ensureRuntimeObjects(style)
        val layer = style.getLayerAs<SymbolLayer>(LAYER_ID) ?: return
        layer.setProperties(iconSize(currentIconScale))
        boundStyle = style
    }

    private fun ensureRuntimeObjects(style: Style) {
        val hasIcon = runCatching { style.getImage(ICON_ID) }.getOrNull() != null
        if (!hasIcon) {
            style.addImage(ICON_ID, createWatchAircraftBitmap())
        }
        if (style.getSourceAs<GeoJsonSource>(SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(SOURCE_ID))
        }
        if (style.getLayerAs<SymbolLayer>(LAYER_ID) == null) {
            val layer = SymbolLayer(LAYER_ID, SOURCE_ID)
                .withProperties(
                    iconImage(ICON_ID),
                    iconSize(currentIconScale),
                    iconRotate(0f),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconRotationAlignment("map"),
                    iconKeepUpright(false),
                    iconAnchor("center"),
                    iconOffset(arrayOf(0f, 0f)),
                    visibility("none")
                )
            if (style.getLayer(BlueLocationOverlay.LAYER_ID) != null) {
                style.addLayerAbove(layer, BlueLocationOverlay.LAYER_ID)
            } else {
                style.addLayer(layer)
            }
        }
    }

    private fun createWatchAircraftBitmap(): Bitmap {
        return createLiveFollowWatchAircraftBitmap(context)
    }
}

internal val LIVE_FOLLOW_WATCH_AIRCRAFT_DRAWABLE_RES_ID: Int = R.drawable.ic_adsb_glider
internal const val LIVE_FOLLOW_WATCH_AIRCRAFT_LAYER_ID = "livefollow-watch-aircraft-layer"
internal const val LIVE_FOLLOW_WATCH_AIRCRAFT_ICON_SIZE_PX: Int = 120
internal const val LIVE_FOLLOW_WATCH_AIRCRAFT_CLOSE_ZOOM_THRESHOLD: Float = 10.5f
internal const val LIVE_FOLLOW_WATCH_AIRCRAFT_CLOSE_ICON_SCALE: Float = 1.60f

internal fun createLiveFollowWatchAircraftBitmap(
    context: Context,
    iconSizePx: Int = LIVE_FOLLOW_WATCH_AIRCRAFT_ICON_SIZE_PX
): Bitmap {
    val drawable = ContextCompat.getDrawable(context, LIVE_FOLLOW_WATCH_AIRCRAFT_DRAWABLE_RES_ID)
        ?: error("Missing drawable id: $LIVE_FOLLOW_WATCH_AIRCRAFT_DRAWABLE_RES_ID")
    val bitmap = Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, iconSizePx, iconSizePx)
    drawable.draw(canvas)
    return bitmap
}

internal fun resolveLiveFollowWatchAircraftRotation(trackDeg: Double?): Float {
    val rawTrack = trackDeg ?: 0.0
    var normalized = rawTrack % 360.0
    if (normalized < 0.0) {
        normalized += 360.0
    }
    return normalized.toFloat()
}

internal fun resolveLiveFollowWatchAircraftScale(zoomLevel: Float): Float {
    val zoom = zoomLevel.takeIf { it.isFinite() } ?: LIVE_FOLLOW_WATCH_AIRCRAFT_CLOSE_ZOOM_THRESHOLD
    return when {
        zoom >= LIVE_FOLLOW_WATCH_AIRCRAFT_CLOSE_ZOOM_THRESHOLD -> 1.60f
        zoom >= 9.25f -> 1.30f
        zoom >= 8.25f -> 1.00f
        else -> 0.80f
    }
}
