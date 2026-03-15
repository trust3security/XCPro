package com.example.xcpro.tasks.aat.map

import android.graphics.PointF
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class AATMapCoordinateConverter(
    private val mapLibreMap: MapLibreMap?
) {

    fun screenToMap(screenX: Float, screenY: Float): LatLng? {
        return try {
            mapLibreMap?.projection?.fromScreenLocation(PointF(screenX, screenY))
        } catch (_: Exception) {
            null
        }
    }

    fun mapToScreen(latLng: LatLng): PointF? {
        return try {
            mapLibreMap?.projection?.toScreenLocation(latLng)
        } catch (_: Exception) {
            null
        }
    }

    fun isCoordinateVisible(latLng: LatLng): Boolean {
        return try {
            val visibleRegion = mapLibreMap?.projection?.visibleRegion
            visibleRegion?.latLngBounds?.contains(latLng) ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun getCurrentZoom(): Double {
        return mapLibreMap?.cameraPosition?.zoom ?: 1.0
    }

    fun getMapCenter(): LatLng? {
        return mapLibreMap?.cameraPosition?.target
    }

    fun calculateScreenDistance(point1: LatLng, point2: LatLng): Float? {
        val screen1 = mapToScreen(point1) ?: return null
        val screen2 = mapToScreen(point2) ?: return null
        val dx = screen2.x - screen1.x
        val dy = screen2.y - screen1.y
        return sqrt(dx * dx + dy * dy)
    }

    fun getMetersPerPixel(): Double {
        val center = getMapCenter() ?: return 0.0
        val zoom = getCurrentZoom()
        val equatorMeters = 40075017.0
        val pixelsAtZoom0 = 256.0
        val pixelsAtCurrentZoom = pixelsAtZoom0 * 2.0.pow(zoom)
        val metersPerPixelAtEquator = equatorMeters / pixelsAtCurrentZoom
        val latRadians = Math.toRadians(center.latitude)
        return metersPerPixelAtEquator * cos(latRadians)
    }

    fun pixelsToMeters(pixels: Float): Double {
        return pixels * getMetersPerPixel()
    }

    fun metersToPixels(meters: Double): Float {
        val metersPerPixel = getMetersPerPixel()
        return if (metersPerPixel > 0) (meters / metersPerPixel).toFloat() else 0f
    }
}

data class AATMapTapDetails(
    val screenCoordinates: PointF,
    val mapCoordinates: LatLng,
    val timestamp: Long,
    val zoomLevel: Double,
    val metersPerPixel: Double
) {
    fun isNearPreviousTap(previous: AATMapTapDetails, tolerancePixels: Float): Boolean {
        val dx = screenCoordinates.x - previous.screenCoordinates.x
        val dy = screenCoordinates.y - previous.screenCoordinates.y
        val distance = sqrt(dx * dx + dy * dy)
        return distance <= tolerancePixels
    }

    fun isWithinTimeWindow(previous: AATMapTapDetails, timeoutMs: Long): Boolean {
        return timestamp - previous.timestamp <= timeoutMs
    }
}

object AATMapCoordinateConverterFactory {

    fun create(mapLibreMap: MapLibreMap?): AATMapCoordinateConverter {
        return AATMapCoordinateConverter(mapLibreMap)
    }

    fun createTapDetails(
        screenX: Float,
        screenY: Float,
        converter: AATMapCoordinateConverter,
        timestampMs: Long
    ): AATMapTapDetails? {
        val mapCoords = converter.screenToMap(screenX, screenY) ?: return null
        return AATMapTapDetails(
            screenCoordinates = PointF(screenX, screenY),
            mapCoordinates = mapCoords,
            timestamp = timestampMs,
            zoomLevel = converter.getCurrentZoom(),
            metersPerPixel = converter.getMetersPerPixel()
        )
    }
}
