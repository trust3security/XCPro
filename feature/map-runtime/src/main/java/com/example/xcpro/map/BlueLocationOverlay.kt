package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.runtime.BuildConfig as RuntimeBuildConfig
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Sailplane location overlay showing current user position.
 */
class BlueLocationOverlay(
    private val context: Context,
    private val map: MapLibreMap
) {
    companion object {
        private const val TAG = "SailplaneLocationOverlay"
        private const val SOURCE_ID = "aircraft-location-source"
        const val LAYER_ID = MAP_BLUE_LOCATION_LAYER_ID
        private const val ICON_ID = "aircraft-location-icon"
        const val ICON_SIZE_PX = 144 // Bitmap size in pixels (3x larger = 300% increase)
    }

    private var currentLocation: LatLng? = null
    private var currentTrack: Double = 0.0
    private var currentHeadingDeg: Double = 0.0
    private var currentMapBearing: Double = 0.0
    private var currentOrientationMode: MapOrientationMode = MapOrientationMode.NORTH_UP
    private var currentViewportMetrics: MapCameraViewportMetrics? = null
    private var currentDistancePerPixelMeters: Double? = null
    private var currentIconScale: Float = resolveBlueLocationViewportScalePolicy(null).iconScaleMultiplier
    private var desiredVisible: Boolean = true
    private var isLayerAdded = false
    private var boundStyle: Style? = null
    private var lastRenderedLocation: LatLng? = null
    private var lastRenderedIconRotation: Float? = null
    private var lastRenderedIconScale: Float? = null
    private var lastRenderedVisible: Boolean? = null
    private val verboseLogging = RuntimeBuildConfig.DEBUG

    fun initialize() {
        try {
            val style = map.style ?: run {
                clearRuntimeCache()
                return
            }
            AppLogger.d(TAG, "Initializing aircraft location overlay")

            ensureBlueLocationRuntimeObjects(style, ICON_ID, SOURCE_ID, LAYER_ID, currentIconScale)
            isLayerAdded = hasBlueLocationRuntimeObjects(style, ICON_ID, SOURCE_ID, LAYER_ID)
            boundStyle = if (isLayerAdded) style else null
            if (isLayerAdded) {
                reapplyCurrentStateToStyle(style, force = true)
            }
            if (isLayerAdded) {
                AppLogger.d(TAG, "Sailplane location overlay initialized successfully")
            } else {
                AppLogger.w(TAG, "Sailplane location overlay initialize incomplete")
            }
        } catch (e: Exception) {
            clearRuntimeCache()
            AppLogger.e(TAG, "Error initializing blue location overlay: ${e.message}", e)
        }
    }

    fun setViewportMetrics(
        metrics: MapCameraViewportMetrics?,
        distancePerPixelMeters: Double? = currentDistancePerPixelMeters
    ) {
        currentViewportMetrics = metrics
        currentDistancePerPixelMeters = distancePerPixelMeters
        val style = map.style ?: return
        val styleChanged = boundStyle !== style
        if (!ensureOverlayReadyForUpdate(style, styleChanged)) {
            return
        }
        val layer = style.getLayerAs<SymbolLayer>(LAYER_ID) ?: run {
            clearRuntimeCache()
            return
        }
        val location = currentLocation ?: return
        applyResolvedIconScale(
            layer = layer,
            location = location,
            force = styleChanged
        )
        isLayerAdded = true
        boundStyle = style
    }

    fun updateLocation(
        location: LatLng,
        gpsTrack: Double,
        headingDeg: Double = 0.0,
        mapBearing: Double = 0.0,
        orientationMode: MapOrientationMode = MapOrientationMode.NORTH_UP
    ) {
        try {
            if (!isValidBlueLocationCoordinate(location.latitude, location.longitude)) {
                AppLogger.w(TAG, "Invalid coordinates - update skipped")
                return
            }

            val style = map.style ?: run {
                clearRuntimeCache()
                return
            }
            val styleChanged = boundStyle !== style
            val iconRotation = normalizeBlueLocationAngle(headingDeg - mapBearing).toFloat()
            currentLocation = location
            currentTrack = gpsTrack
            currentHeadingDeg = headingDeg
            currentMapBearing = mapBearing
            currentOrientationMode = orientationMode

            if (isSteadyStateNoOp(location, iconRotation, styleChanged)) {
                return
            }
            if (!ensureOverlayReadyForUpdate(style, styleChanged)) {
                AppLogger.w(TAG, "Overlay runtime missing, cannot update location")
                return
            }

            val previousLocation = lastRenderedLocation
            val deltaMeters = previousLocation?.let { calculateBlueLocationDistanceMeters(it, location) }

            var source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
            var layer = style.getLayerAs<SymbolLayer>(LAYER_ID)
            if (source == null || layer == null) {
                ensureBlueLocationRuntimeObjects(style, ICON_ID, SOURCE_ID, LAYER_ID, currentIconScale)
                source = style.getSourceAs(SOURCE_ID)
                layer = style.getLayerAs(LAYER_ID)
            }
            if (source == null || layer == null) {
                clearRuntimeCache()
                AppLogger.w(TAG, "Overlay source/layer missing after recovery attempt")
                return
            }
            applyResolvedIconScale(
                layer = layer,
                location = location,
                force = styleChanged
            )
            if (styleChanged || lastRenderedLocation == null || !sameBlueLocation(lastRenderedLocation, location)) {
                updateBlueLocationSource(source, location)
                lastRenderedLocation = location
            }
            if (styleChanged || !sameBlueLocationRotation(lastRenderedIconRotation, iconRotation)) {
                layer.setProperties(iconRotate(iconRotation))
                lastRenderedIconRotation = iconRotation
            }
            applyVisibility(layer, visible = true, force = styleChanged)
            isLayerAdded = true
            boundStyle = style

            if (verboseLogging && AppLogger.rateLimit(TAG, "location_verbose", 1_000L)) {
                val moveLabel = if (deltaMeters == null) "delta=--" else "delta=${"%.1f".format(deltaMeters)}m"
                AppLogger.d(
                    TAG,
                    "Location updated: lat=${location.latitude}, " +
                        "lon=${location.longitude}, track=${gpsTrack.toInt()} deg, " +
                        "heading=${headingDeg.toInt()} deg, map=${mapBearing.toInt()} deg, " +
                        "mode=$orientationMode, iconRotation=${iconRotation.toInt()} deg, " +
                        moveLabel
                )
                if (previousLocation != null) {
                    AppLogger.d(
                        TAG,
                        "Location delta: from=${previousLocation.latitude},${previousLocation.longitude} " +
                            "to=${location.latitude},${location.longitude}"
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error updating location: ${e.message}", e)
        }
    }

    fun setVisible(visible: Boolean) {
        try {
            desiredVisible = visible
            val style = map.style ?: run {
                clearRuntimeCache()
                return
            }
            val styleChanged = boundStyle !== style
            if (!ensureOverlayReadyForUpdate(style, styleChanged)) {
                return
            }
            val layer = style.getLayerAs<SymbolLayer>(LAYER_ID) ?: run {
                clearRuntimeCache()
                return
            }
            applyVisibility(layer, visible = desiredVisible, force = styleChanged)
            isLayerAdded = true
            boundStyle = style
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error setting location visibility: ${e.message}", e)
        }
    }

    fun cleanup() {
        try {
            val style = map.style
            if (style != null && isLayerAdded) {
                style.removeLayer(LAYER_ID)
                style.removeSource(SOURCE_ID)
                style.removeImage(ICON_ID)
                AppLogger.d(TAG, "Location overlay cleaned up")
            }
            clearRuntimeCache()
        } catch (e: Exception) {
            clearRuntimeCache()
            AppLogger.e(TAG, "Error cleaning up location overlay: ${e.message}", e)
        }
    }

    fun bringToFront() {
        val style = map.style ?: run {
            clearRuntimeCache()
            return
        }
        if (!ensureOverlayReadyForUpdate(style, boundStyle !== style)) return
        try {
            val topId = style.layers.lastOrNull()?.id
            if (topId == LAYER_ID) return
            style.removeLayer(LAYER_ID)
            val layer = createBlueLocationLayer(LAYER_ID, SOURCE_ID, ICON_ID, currentIconScale)
            style.addLayer(layer)
            isLayerAdded = true
            boundStyle = style
            AppLogger.d(TAG, "Re-added aircraft overlay to keep it on top")
            reapplyCurrentStateToStyle(style, force = true)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error bringing aircraft overlay to front: ${e.message}", e)
        }
    }
    private fun ensureOverlayReadyForUpdate(style: Style, styleChanged: Boolean): Boolean {
        if (styleChanged || !isLayerAdded) {
            if (AppLogger.rateLimit(TAG, "overlay_recovery", 2_000L)) {
                AppLogger.w(TAG, "Overlay runtime missing, attempting self-heal")
            }
            ensureBlueLocationRuntimeObjects(style, ICON_ID, SOURCE_ID, LAYER_ID, currentIconScale)
            boundStyle = style
        }
        isLayerAdded = hasBlueLocationRuntimeObjects(style, ICON_ID, SOURCE_ID, LAYER_ID)
        return isLayerAdded
    }

    private fun isSteadyStateNoOp(
        location: LatLng,
        iconRotation: Float,
        styleChanged: Boolean
    ): Boolean {
        if (styleChanged || !isLayerAdded) {
            return false
        }
        val renderedLocation = lastRenderedLocation ?: return false
        val renderedRotation = lastRenderedIconRotation ?: return false
        val renderedVisible = lastRenderedVisible ?: return false
        return renderedVisible &&
            sameBlueLocation(renderedLocation, location) &&
            sameBlueLocationRotation(renderedRotation, iconRotation)
    }

    private fun applyResolvedIconScale(
        layer: SymbolLayer,
        location: LatLng,
        force: Boolean
    ) {
        val resolvedScale = resolveCurrentIconScale(location)
        if (!force && sameBlueLocationIconScale(lastRenderedIconScale, resolvedScale)) {
            currentIconScale = resolvedScale
            return
        }
        layer.setProperties(iconSize(resolvedScale))
        currentIconScale = resolvedScale
        lastRenderedIconScale = resolvedScale
    }

    private fun resolveCurrentIconScale(location: LatLng): Float {
        val viewportMetrics = currentViewportMetrics ?: return currentIconScale
        val distancePerPixelMeters = currentDistancePerPixelMeters ?: run {
            val metersPerPixelAtLatitude = runCatching {
                map.projection.getMetersPerPixelAtLatitude(location.latitude)
            }.getOrNull()
            resolveBlueLocationDistancePerPixelMeters(
                metersPerPixelAtLatitude = metersPerPixelAtLatitude,
                viewportMetrics = viewportMetrics
            )
        } ?: return currentIconScale
        val screenPoint = resolveBlueLocationScreenPoint(
            map = map,
            location = location
        ) ?: return currentIconScale
        val visibleRadiusMeters = resolveBlueLocationVisibleRadiusMeters(
            screenX = screenPoint.x,
            screenY = screenPoint.y,
            viewportMetrics = viewportMetrics,
            distancePerPixelMeters = distancePerPixelMeters
        ) ?: return currentIconScale
        return resolveBlueLocationViewportScalePolicy(visibleRadiusMeters).iconScaleMultiplier
    }

    private fun applyVisibility(layer: SymbolLayer, visible: Boolean, force: Boolean) {
        if (!force && lastRenderedVisible == visible) {
            return
        }
        layer.setProperties(visibility(if (visible) "visible" else "none"))
        lastRenderedVisible = visible
        AppLogger.d(TAG, "Location overlay visibility: $visible")
    }

    private fun reapplyCurrentStateToStyle(style: Style, force: Boolean) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
        val layer = style.getLayerAs<SymbolLayer>(LAYER_ID)
        if (source == null || layer == null) {
            clearRuntimeCache()
            return
        }
        currentLocation?.let { location ->
            updateBlueLocationSource(source, location)
            lastRenderedLocation = location
            applyResolvedIconScale(layer = layer, location = location, force = force)
        }

        val iconRotation = normalizeBlueLocationAngle(currentHeadingDeg - currentMapBearing).toFloat()
        layer.setProperties(iconRotate(iconRotation))
        lastRenderedIconRotation = iconRotation
        applyVisibility(layer, visible = desiredVisible, force = force)
        isLayerAdded = true
        boundStyle = style
    }

    private fun clearRuntimeCache() {
        isLayerAdded = false
        boundStyle = null
        lastRenderedLocation = null
        lastRenderedIconRotation = null
        lastRenderedIconScale = null
        lastRenderedVisible = null
    }
}
