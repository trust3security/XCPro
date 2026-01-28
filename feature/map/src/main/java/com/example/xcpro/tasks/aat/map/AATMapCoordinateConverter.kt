package com.example.xcpro.tasks.aat.map

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import android.graphics.PointF
import kotlin.math.*

/**
 * AAT Map Coordinate Converter - Phase 1 Foundation
 *
 * Handles conversion between screen coordinates and map coordinates for AAT interaction.
 * Essential for accurate double-tap detection and turnpoint positioning.
 *
 * Features:
 * - Screen pixel to map LatLng conversion
 * - Map LatLng to screen pixel conversion
 * - Zoom level consideration
 * - Map bounds validation
 *
 * Usage:
 * - Convert tap events to map coordinates
 * - Position UI elements on map
 * - Validate coordinate bounds
 */
class AATMapCoordinateConverter(
    private val mapLibreMap: MapLibreMap?
) {

    /**
     * Convert screen coordinates to map coordinates
     * @param screenX Screen X coordinate in pixels
     * @param screenY Screen Y coordinate in pixels
     * @return LatLng map coordinates, or null if conversion fails
     */
    fun screenToMap(screenX: Float, screenY: Float): LatLng? {
        return try {
            mapLibreMap?.projection?.fromScreenLocation(PointF(screenX, screenY))
        } catch (e: Exception) {
            println(" AAT: Screen to map conversion failed: ${e.message}")
            null
        }
    }

    /**
     * Convert map coordinates to screen coordinates
     * @param latLng Map coordinates
     * @return Screen coordinates in pixels, or null if conversion fails
     */
    fun mapToScreen(latLng: LatLng): PointF? {
        return try {
            mapLibreMap?.projection?.toScreenLocation(latLng)
        } catch (e: Exception) {
            println(" AAT: Map to screen conversion failed: ${e.message}")
            null
        }
    }

    /**
     * Check if map coordinates are within current visible bounds
     */
    fun isCoordinateVisible(latLng: LatLng): Boolean {
        return try {
            val visibleRegion = mapLibreMap?.projection?.visibleRegion
            visibleRegion?.latLngBounds?.contains(latLng) ?: false
        } catch (e: Exception) {
            println(" AAT: Visibility check failed: ${e.message}")
            false
        }
    }

    /**
     * Get current map zoom level
     */
    fun getCurrentZoom(): Double {
        return mapLibreMap?.cameraPosition?.zoom ?: 1.0
    }

    /**
     * Get map center coordinates
     */
    fun getMapCenter(): LatLng? {
        return mapLibreMap?.cameraPosition?.target
    }

    /**
     * Calculate screen distance between two map points
     */
    fun calculateScreenDistance(point1: LatLng, point2: LatLng): Float? {
        val screen1 = mapToScreen(point1) ?: return null
        val screen2 = mapToScreen(point2) ?: return null

        val dx = screen2.x - screen1.x
        val dy = screen2.y - screen1.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Get meters per pixel at current zoom level
     * Useful for touch tolerance calculations
     */
    fun getMetersPerPixel(): Double {
        val center = getMapCenter() ?: return 0.0
        val zoom = getCurrentZoom()

        // Approximate meters per pixel calculation
        // At zoom level 0, equator is 256 pixels, which equals ~40,075,017 meters
        val equatorMeters = 40075017.0
        val pixelsAtZoom0 = 256.0
        val pixelsAtCurrentZoom = pixelsAtZoom0 * 2.0.pow(zoom)
        val metersPerPixelAtEquator = equatorMeters / pixelsAtCurrentZoom

        // Adjust for latitude (pixels get smaller away from equator)
        val latRadians = Math.toRadians(center.latitude)
        return metersPerPixelAtEquator * cos(latRadians)
    }

    /**
     * Convert pixel tolerance to meters for distance calculations
     */
    fun pixelsToMeters(pixels: Float): Double {
        return pixels * getMetersPerPixel()
    }

    /**
     * Convert meters to pixels for UI element sizing
     */
    fun metersToPixels(meters: Double): Float {
        val metersPerPixel = getMetersPerPixel()
        return if (metersPerPixel > 0) (meters / metersPerPixel).toFloat() else 0f
    }
}

/**
 * Enhanced tap detection with accurate coordinate conversion
 */
data class AATMapTapDetails(
    val screenCoordinates: PointF,
    val mapCoordinates: LatLng,
    val timestamp: Long,
    val zoomLevel: Double,
    val metersPerPixel: Double
) {
    /**
     * Check if this tap is within tolerance of another tap (for double-tap detection)
     */
    fun isNearPreviousTap(previous: AATMapTapDetails, tolerancePixels: Float): Boolean {
        val dx = screenCoordinates.x - previous.screenCoordinates.x
        val dy = screenCoordinates.y - previous.screenCoordinates.y
        val distance = sqrt(dx * dx + dy * dy)
        return distance <= tolerancePixels
    }

    /**
     * Check if this tap is within a time window of another tap
     */
    fun isWithinTimeWindow(previous: AATMapTapDetails, timeoutMs: Long): Boolean {
        return timestamp - previous.timestamp <= timeoutMs
    }
}

/**
 * Factory for creating coordinate converter instances
 */
object AATMapCoordinateConverterFactory {

    /**
     * Create converter instance from MapLibreMap
     */
    fun create(mapLibreMap: MapLibreMap?): AATMapCoordinateConverter {
        return AATMapCoordinateConverter(mapLibreMap)
    }

    /**
     * Create tap details from screen coordinates
     */
    fun createTapDetails(
        screenX: Float,
        screenY: Float,
        converter: AATMapCoordinateConverter
    ): AATMapTapDetails? {
        val mapCoords = converter.screenToMap(screenX, screenY) ?: return null

        return AATMapTapDetails(
            screenCoordinates = PointF(screenX, screenY),
            mapCoordinates = mapCoords,
            timestamp = System.currentTimeMillis(),
            zoomLevel = converter.getCurrentZoom(),
            metersPerPixel = converter.getMetersPerPixel()
        )
    }
}
