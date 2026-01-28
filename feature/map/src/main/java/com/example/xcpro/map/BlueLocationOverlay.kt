package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import com.example.xcpro.core.common.logging.AppLogger
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.xcpro.common.orientation.MapOrientationMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.example.ui1.icons.Sailplane
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

    /**
     * Initialize the blue location overlay on the map
     */
    fun initialize() {
        try {
            val style = map.style ?: return
            AppLogger.d(TAG, "Initializing aircraft location overlay")

            // Create triangle icon bitmap
            val triangleIcon = createSailplaneIconBitmap()

            // Add icon to style
            style.addImage(ICON_ID, triangleIcon)

            // Create GeoJSON source for location position
            val source = GeoJsonSource(SOURCE_ID)
            style.addSource(source)

            // Create symbol layer for location icon
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

    /**
     * Update location position and bearing
     * @param location GPS location
     * @param gpsTrack GPS track direction (direction of movement)
     * @param headingDeg Aircraft heading (degrees)
     * @param mapBearing Map bearing applied to the camera (screen angle)
     * @param orientationMode Current map orientation mode
     */
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
            // CRASH FIX: Validate coordinates before creating GeoJSON
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

            // Update the icon position to the actual GPS location
            // This keeps the triangle at the pilot's real position, not camera center
            val point = Point.fromLngLat(location.longitude, location.latitude)
            val feature = Feature.fromGeometry(point)
            source.setGeoJson(feature)

            // Update icon rotation based on orientation mode
            // Icon rotation is heading relative to screen angle (map bearing).
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

    /**
     * Normalize angle to -180 to 180 range for relative rotation display
     */
    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360
        if (normalized > 180) normalized -= 360
        if (normalized < -180) normalized += 360
        return normalized
    }

    /**
     * Validate coordinate pair to prevent MapLibre crashes
     */
    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        // Check for NaN and Infinity
        if (!lat.isFinite() || !lon.isFinite()) {
            return false
        }

        // Check latitude bounds (-90 to 90)
        if (lat < -90.0 || lat > 90.0) {
            return false
        }

        // Check longitude bounds (-180 to 180)
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

    /**
     * Show or hide the location overlay
     */
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

    /**
     * Remove the location overlay from the map
     */
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

    /**
     * Move location layer to the top of the style stack so it is never obscured.
     */
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

    /**
     * Create bitmap representation of 3D navigation arrow for pilots
     * Sharp angular design with SLIGHTLY rounded corners, black outline, 3-tone blue shading
     */
    private fun createSailplaneIconBitmap(): Bitmap {
        // Create bitmap with transparent background
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = ICON_SIZE_PX / 2f
        val centerY = ICON_SIZE_PX / 2f

        // Sharp arrow points with SLIGHT rounding (5% radius for subtle softness)
        val topPoint = centerY - ICON_SIZE_PX * 0.40f       // Sharp tip
        val leftPoint = centerX - ICON_SIZE_PX * 0.32f      // Left wing
        val leftPointY = centerY + ICON_SIZE_PX * 0.28f
        val rightPoint = centerX + ICON_SIZE_PX * 0.32f     // Right wing
        val rightPointY = centerY + ICON_SIZE_PX * 0.28f
        val bottomPoint = centerY + ICON_SIZE_PX * 0.12f    // Bottom indent

        val cornerRadius = ICON_SIZE_PX * 0.05f  // SLIGHT rounding only

        // LEFT FACET (light blue/cyan - shadow side)
        val leftFacet = Path().apply {
            moveTo(centerX - ICON_SIZE_PX * 0.01f, topPoint + cornerRadius)

            // Top corner - slightly rounded
            quadTo(centerX, topPoint, centerX, topPoint + cornerRadius * 0.5f)

            // Straight edge to left point
            lineTo(leftPoint + cornerRadius, leftPointY - cornerRadius * 0.3f)

            // Left wing corner - slightly rounded
            quadTo(leftPoint, leftPointY, leftPoint + cornerRadius * 0.8f, leftPointY + cornerRadius * 0.3f)

            // Edge to bottom
            lineTo(centerX - ICON_SIZE_PX * 0.01f, bottomPoint - cornerRadius * 0.5f)

            // Small bottom rounding
            quadTo(centerX, bottomPoint, centerX, bottomPoint)

            close()
        }

        // CENTER FACET (medium blue - transition)
        val centerFacet = Path().apply {
            moveTo(centerX, topPoint + cornerRadius * 0.5f)

            // Slight top rounding
            quadTo(centerX, topPoint, centerX + ICON_SIZE_PX * 0.01f, topPoint + cornerRadius)

            // Edge to bottom
            lineTo(centerX + ICON_SIZE_PX * 0.01f, bottomPoint - cornerRadius * 0.5f)

            // Bottom
            quadTo(centerX, bottomPoint, centerX, bottomPoint)
            lineTo(centerX - ICON_SIZE_PX * 0.01f, bottomPoint - cornerRadius * 0.5f)

            close()
        }

        // RIGHT FACET (dark blue - lit side)
        val rightFacet = Path().apply {
            moveTo(centerX + ICON_SIZE_PX * 0.01f, topPoint + cornerRadius)

            // Top corner - slightly rounded
            quadTo(centerX, topPoint, centerX, topPoint + cornerRadius * 0.5f)

            // Bottom edge
            lineTo(centerX + ICON_SIZE_PX * 0.01f, bottomPoint - cornerRadius * 0.5f)

            // Small bottom rounding
            quadTo(centerX, bottomPoint, rightPoint - cornerRadius * 0.8f, rightPointY + cornerRadius * 0.3f)

            // Right wing corner - slightly rounded
            quadTo(rightPoint, rightPointY, rightPoint - cornerRadius, rightPointY - cornerRadius * 0.3f)

            // Back to top
            lineTo(centerX + ICON_SIZE_PX * 0.01f, topPoint + cornerRadius)

            close()
        }

        // OUTLINE PATH (for black border)
        val outlinePath = Path().apply {
            moveTo(centerX, topPoint + cornerRadius * 0.3f)

            // Top - slightly rounded
            quadTo(centerX, topPoint, centerX - ICON_SIZE_PX * 0.015f, topPoint + cornerRadius * 0.8f)

            // Left edge to wing
            lineTo(leftPoint + cornerRadius * 0.7f, leftPointY - cornerRadius * 0.5f)

            // Left wing corner - slightly rounded
            quadTo(leftPoint - cornerRadius * 0.2f, leftPointY,
                   leftPoint + cornerRadius * 0.6f, leftPointY + cornerRadius * 0.6f)

            // To bottom
            lineTo(centerX - ICON_SIZE_PX * 0.02f, bottomPoint - cornerRadius * 0.3f)
            quadTo(centerX, bottomPoint + cornerRadius * 0.2f,
                   centerX + ICON_SIZE_PX * 0.02f, bottomPoint - cornerRadius * 0.3f)

            // To right wing
            lineTo(rightPoint - cornerRadius * 0.6f, rightPointY + cornerRadius * 0.6f)

            // Right wing corner - slightly rounded
            quadTo(rightPoint + cornerRadius * 0.2f, rightPointY,
                   rightPoint - cornerRadius * 0.7f, rightPointY - cornerRadius * 0.5f)

            // Back to top
            lineTo(centerX + ICON_SIZE_PX * 0.015f, topPoint + cornerRadius * 0.8f)
            quadTo(centerX, topPoint, centerX, topPoint + cornerRadius * 0.3f)

            close()
        }

        // Paints - 3-tone blue shading like navigation apps
        val leftPaint = Paint().apply {
            color = "#5DADE2".toColorInt()  // Light cyan/blue
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val centerPaint = Paint().apply {
            color = "#3498DB".toColorInt()  // Medium blue
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val rightPaint = Paint().apply {
            color = "#2874A6".toColorInt()  // Dark blue
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val outlinePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 5f  // Thick black outline for visibility
        }

        // Draw: black outline first, then facets for 3D effect
        canvas.drawPath(outlinePath, outlinePaint)
        canvas.drawPath(leftFacet, leftPaint)
        canvas.drawPath(centerFacet, centerPaint)
        canvas.drawPath(rightFacet, rightPaint)

        return bitmap
    }
}
