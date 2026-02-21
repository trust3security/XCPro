package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.common.orientation.MapOrientationMode
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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
        internal const val LAYER_ID = "aircraft-location-layer"
        private const val ICON_ID = "aircraft-location-icon"
        internal const val ICON_SIZE_PX = 144 // Bitmap size in pixels (3x larger = 300% increase)
    }

    private var currentLocation: LatLng? = null
    private var currentTrack: Double = 0.0
    private var currentHeadingDeg: Double = 0.0
    private var currentMapBearing: Double = 0.0
    private var currentOrientationMode: MapOrientationMode = MapOrientationMode.NORTH_UP
    private var isLayerAdded = false
    private val verboseLogging = com.example.xcpro.map.BuildConfig.DEBUG

    fun initialize() {
        try {
            val style = map.style ?: return
            AppLogger.d(TAG, "Initializing aircraft location overlay")

            val triangleIcon = createSailplaneIconBitmap()

            style.addImage(ICON_ID, triangleIcon)

            val source = GeoJsonSource(SOURCE_ID)
            style.addSource(source)

            val layer = createLocationLayer()

            style.addLayer(layer)
            isLayerAdded = true

            AppLogger.d(TAG, "Sailplane location overlay initialized successfully")

        } catch (e: Exception) {
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
        if (!isLayerAdded) {
            AppLogger.w(TAG, "Overlay not initialized, cannot update location")
            return
        }

        try {
            if (!isValidCoordinate(location.latitude, location.longitude)) {
                AppLogger.w(TAG, "Invalid coordinates - update skipped")
                return
            }

            val previousLocation = currentLocation
            val deltaMeters = previousLocation?.let { distanceMeters(it, location) }
            currentLocation = location
            currentTrack = gpsTrack
            currentHeadingDeg = headingDeg
            currentMapBearing = mapBearing
            currentOrientationMode = orientationMode

            val style = map.style ?: return
            val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
            val layer = style.getLayerAs<SymbolLayer>(LAYER_ID) ?: return

            val point = Point.fromLngLat(location.longitude, location.latitude)
            val feature = Feature.fromGeometry(point)
            source.setGeoJson(feature)

            val iconRotation = normalizeAngle(headingDeg - mapBearing).toFloat()
            layer.setProperties(iconRotate(iconRotation))

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
        if (!isLayerAdded) return

        try {
            val style = map.style ?: return
            val layer = style.getLayerAs<SymbolLayer>(LAYER_ID) ?: return

            layer.setProperties(
                visibility(if (visible) "visible" else "none")
            )

            AppLogger.d(TAG, "Location overlay visibility: $visible")

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error setting location visibility: ${e.message}", e)
        }
    }

    fun cleanup() {
        try {
            val style = map.style ?: return

            if (isLayerAdded) {
                style.removeLayer(LAYER_ID)
                style.removeSource(SOURCE_ID)
                style.removeImage(ICON_ID)
                isLayerAdded = false
                AppLogger.d(TAG, "Location overlay cleaned up")
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error cleaning up location overlay: ${e.message}", e)
        }
    }

    fun bringToFront() {
        if (!isLayerAdded) return
        try {
            val style = map.style ?: return
            val topId = style.layers.lastOrNull()?.id
            if (topId == LAYER_ID) return
            style.removeLayer(LAYER_ID)
            val layer = createLocationLayer()
            style.addLayer(layer)
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
}
