package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.runtime.BuildConfig as RuntimeBuildConfig
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.geojson.Point
import org.maplibre.geojson.Feature
import kotlin.math.*

/**
 * Sailplane location overlay showing current user position.
 * Uses sailplane icon that rotates based on bearing.
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
        private const val LOCATION_EPSILON = 1e-7
        private const val ROTATION_EPSILON_DEG = 0.05f
    }

    private var currentLocation: LatLng? = null
    private var currentTrack: Double = 0.0
    private var currentHeadingDeg: Double = 0.0
    private var currentMapBearing: Double = 0.0
    private var currentOrientationMode: MapOrientationMode = MapOrientationMode.NORTH_UP
    private var isLayerAdded = false
    private var boundStyle: Style? = null
    private var lastRenderedLocation: LatLng? = null
    private var lastRenderedIconRotation: Float? = null
    private var lastRenderedVisible: Boolean? = null
    private val verboseLogging = RuntimeBuildConfig.DEBUG

    fun initialize() {
        try {
            val style = map.style ?: run {
                clearRuntimeCache()
                return
            }
            AppLogger.d(TAG, "Initializing aircraft location overlay")

            ensureRuntimeObjects(style)
            isLayerAdded = hasRuntimeObjects(style)
            boundStyle = if (isLayerAdded) style else null
            lastRenderedVisible = if (isLayerAdded) true else null
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

    private fun createLocationLayer(): SymbolLayer =
        SymbolLayer(LAYER_ID, SOURCE_ID)
            .withProperties(
                iconImage(ICON_ID),
                iconSize(1.0f),
                iconRotate(0f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconRotationAlignment("viewport"),
                iconAnchor("center"),
                iconOffset(arrayOf(0f, 0f))
            )

    fun updateLocation(
        location: LatLng,
        gpsTrack: Double,
        headingDeg: Double = 0.0,
        mapBearing: Double = 0.0,
        orientationMode: MapOrientationMode = MapOrientationMode.NORTH_UP
    ) {
        try {
            if (!isValidCoordinate(location.latitude, location.longitude)) {
                AppLogger.w(TAG, "Invalid coordinates - update skipped")
                return
            }

            val style = map.style ?: run {
                clearRuntimeCache()
                return
            }
            val styleChanged = boundStyle !== style
            val iconRotation = normalizeAngle(headingDeg - mapBearing).toFloat()

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
            val deltaMeters = previousLocation?.let { distanceMeters(it, location) }

            var source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
            var layer = style.getLayerAs<SymbolLayer>(LAYER_ID)
            if (source == null || layer == null) {
                ensureRuntimeObjects(style)
                source = style.getSourceAs(SOURCE_ID)
                layer = style.getLayerAs(LAYER_ID)
            }
            if (source == null || layer == null) {
                clearRuntimeCache()
                AppLogger.w(TAG, "Overlay source/layer missing after recovery attempt")
                return
            }

            if (styleChanged || lastRenderedLocation == null || !sameLocation(lastRenderedLocation, location)) {
                val point = Point.fromLngLat(location.longitude, location.latitude)
                val feature = Feature.fromGeometry(point)
                source.setGeoJson(feature)
                lastRenderedLocation = location
            }
            if (styleChanged || !sameRotation(lastRenderedIconRotation, iconRotation)) {
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

    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360
        if (normalized > 180) normalized -= 360
        if (normalized < -180) normalized += 360
        return normalized
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (!lat.isFinite() || !lon.isFinite()) {
            return false
        }

        if (lat < -90.0 || lat > 90.0) {
            return false
        }

        if (lon < -180.0 || lon > 180.0) {
            return false
        }

        return true
    }

    private fun distanceMeters(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude,
            results
        )
        return results[0]
    }

    fun setVisible(visible: Boolean) {
        try {
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
            applyVisibility(layer, visible = visible, force = styleChanged)
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
            val layer = createLocationLayer()
            style.addLayer(layer)
            isLayerAdded = true
            boundStyle = style
            AppLogger.d(TAG, "Re-added aircraft overlay to keep it on top")
            currentLocation?.let {
                updateLocation(it, currentTrack, currentHeadingDeg, currentMapBearing, currentOrientationMode)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error bringing aircraft overlay to front: ${e.message}", e)
        }
    }

    private fun createSailplaneIconBitmap(): Bitmap {
        return SailplaneIconBitmapFactory.create(ICON_SIZE_PX)
    }

    private fun ensureOverlayReadyForUpdate(style: Style, styleChanged: Boolean): Boolean {
        if (styleChanged || !isLayerAdded) {
            if (AppLogger.rateLimit(TAG, "overlay_recovery", 2_000L)) {
                AppLogger.w(TAG, "Overlay runtime missing, attempting self-heal")
            }
            ensureRuntimeObjects(style)
            boundStyle = style
        }
        isLayerAdded = hasRuntimeObjects(style)
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
            sameLocation(renderedLocation, location) &&
            sameRotation(renderedRotation, iconRotation)
    }

    private fun sameLocation(first: LatLng?, second: LatLng): Boolean {
        val location = first ?: return false
        return abs(location.latitude - second.latitude) < LOCATION_EPSILON &&
            abs(location.longitude - second.longitude) < LOCATION_EPSILON
    }

    private fun sameRotation(first: Float?, second: Float): Boolean {
        val rotation = first ?: return false
        return abs(rotation - second) < ROTATION_EPSILON_DEG
    }

    private fun applyVisibility(layer: SymbolLayer, visible: Boolean, force: Boolean) {
        if (!force && lastRenderedVisible == visible) {
            return
        }
        layer.setProperties(visibility(if (visible) "visible" else "none"))
        lastRenderedVisible = visible
        AppLogger.d(TAG, "Location overlay visibility: $visible")
    }

    private fun clearRuntimeCache() {
        isLayerAdded = false
        boundStyle = null
        lastRenderedLocation = null
        lastRenderedIconRotation = null
        lastRenderedVisible = null
    }

    private fun ensureRuntimeObjects(style: Style) {
        val hasIcon = runCatching { style.getImage(ICON_ID) }.getOrNull() != null
        if (!hasIcon) {
            style.addImage(ICON_ID, createSailplaneIconBitmap())
        }
        if (style.getSourceAs<GeoJsonSource>(SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(SOURCE_ID))
        }
        if (style.getLayerAs<SymbolLayer>(LAYER_ID) == null) {
            style.addLayer(createLocationLayer())
        }
    }

    private fun hasRuntimeObjects(style: Style): Boolean {
        val hasIcon = runCatching { style.getImage(ICON_ID) }.getOrNull() != null
        return hasIcon &&
            style.getSourceAs<GeoJsonSource>(SOURCE_ID) != null &&
            style.getLayerAs<SymbolLayer>(LAYER_ID) != null
    }
}
