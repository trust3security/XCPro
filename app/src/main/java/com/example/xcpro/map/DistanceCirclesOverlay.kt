package com.example.xcpro.map

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.geojson.Point
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import kotlin.math.*

/**
 * Zoom-adaptive distance circles overlay for gliding navigation.
 * Displays concentric circles centered on glider position with distances
 * that automatically adapt based on map zoom level.
 */
class DistanceCirclesOverlay(
    private val context: Context,
    private val map: MapLibreMap
) {
    companion object {
        private const val TAG = "DistanceCirclesOverlay"
        private const val CIRCLES_SOURCE_ID = "distance-circles-source"
        private const val CIRCLES_LAYER_ID = "distance-circles-layer"
        private const val LABELS_SOURCE_ID = "distance-labels-source"
        private const val LABELS_LAYER_ID = "distance-labels-layer"

        // Minimum movement threshold to trigger circle update (500m)
        private const val MIN_MOVEMENT_THRESHOLD = 0.005 // ~500m in degrees

        // Circle generation parameters
        private const val CIRCLE_POINTS = 64 // Points to approximate circle

        // Target number of circles to display
        private const val TARGET_CIRCLE_COUNT = 6

        // Available distance intervals in kilometers
        private val AVAILABLE_INTERVALS = arrayOf(
            0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 400.0
        )
    }

    private var currentLocation: LatLng? = null
    private var currentZoomLevel: Double = 10.0
    private var isVisible: Boolean = false
    private var isLayerAdded: Boolean = false
    private var lastUpdateLocation: LatLng? = null

    /**
     * Initialize the distance circles overlay on the map
     */
    fun initialize() {
        try {
            val style = map.style ?: return
            Log.d(TAG, "Initializing distance circles overlay")

            // Create GeoJSON source for circle geometries
            val circlesSource = GeoJsonSource(CIRCLES_SOURCE_ID)
            style.addSource(circlesSource)

            // Create line layer for distance rings with dark grey styling
            val circlesLayer = LineLayer(CIRCLES_LAYER_ID, CIRCLES_SOURCE_ID)
                .withProperties(
                    lineWidth(1.5f), // Thin dark grey line
                    lineColor(Color(0xFF404040).toArgb()), // Dark grey
                    lineOpacity(0.9f)
                )

            // Add circles layer below labels but above base map
            addLayerWithPositioning(circlesLayer)

            // Create GeoJSON source for distance labels
            val labelsSource = GeoJsonSource(LABELS_SOURCE_ID)
            style.addSource(labelsSource)

            // Create symbol layer for distance labels
            val labelsLayer = SymbolLayer(LABELS_LAYER_ID, LABELS_SOURCE_ID)
                .withProperties(
                    textField("{distance}"),
                    textSize(12f),
                    textColor(Color.White.toArgb()),
                    textHaloColor(Color.Black.toArgb()),
                    textHaloWidth(1f),
                    textIgnorePlacement(true),
                    textAllowOverlap(false),
                    textAnchor("center"),
                    textPitchAlignment("viewport")
                )

            style.addLayer(labelsLayer)
            isLayerAdded = true

            // Set initial zoom level and update circles
            currentZoomLevel = map.cameraPosition.zoom
            updateCircles()

            Log.d(TAG, "Distance circles overlay initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing distance circles overlay: ${e.message}", e)
        }
    }

    /**
     * Update glider location and refresh circles if needed
     */
    fun updateLocation(location: LatLng) {
        if (!isLayerAdded) return

        val shouldUpdate = currentLocation == null ||
                          lastUpdateLocation == null ||
                          hasMovedSignificantly(location, lastUpdateLocation!!)

        currentLocation = location

        if (shouldUpdate && isVisible) {
            lastUpdateLocation = location
            updateCircles()
            Log.d(TAG, "Location updated: lat=${location.latitude}, lon=${location.longitude}")
        }
    }

    /**
     * Update zoom level and refresh circle intervals
     */
    fun updateZoomLevel(zoomLevel: Double) {
        if (abs(zoomLevel - currentZoomLevel) > 0.5) { // Only update on significant zoom changes
            currentZoomLevel = zoomLevel
            updateCircles()
            Log.d(TAG, "Zoom level updated: $zoomLevel")
        }
    }

    /**
     * Show or hide the distance circles overlay
     */
    fun setVisible(visible: Boolean) {
        // DISABLED: Map-based circles replaced with DistanceCirclesCanvas
        // The circles are now drawn as a fixed screen overlay centered on the aircraft
        // This prevents them from moving with the map

        isVisible = visible

        // Return early to prevent map layer updates
        // The DistanceCirclesCanvas handles all circle rendering now
        return

        /* Disabled map-based implementation:
        if (!isLayerAdded) return

        try {
            val style = map.style ?: return
            val circlesLayer = style.getLayerAs<LineLayer>(CIRCLES_LAYER_ID)
            val labelsLayer = style.getLayerAs<SymbolLayer>(LABELS_LAYER_ID)

            val visibility = if (visible) "visible" else "none"
            circlesLayer?.setProperties(visibility(visibility))
            labelsLayer?.setProperties(visibility(visibility))

            if (visible && currentLocation != null) {
                updateCircles()
            }
        */

        // Still log the visibility state for debugging
        Log.d(TAG, "Distance circles visibility: $visible (using Canvas overlay)")
    }

    /**
     * Clean up the overlay resources
     */
    fun cleanup() {
        try {
            val style = map.style ?: return

            if (isLayerAdded) {
                style.removeLayer(LABELS_LAYER_ID)
                style.removeLayer(CIRCLES_LAYER_ID)
                style.removeSource(LABELS_SOURCE_ID)
                style.removeSource(CIRCLES_SOURCE_ID)
                isLayerAdded = false
                Log.d(TAG, "Distance circles overlay cleaned up")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up overlay: ${e.message}", e)
        }
    }

    /**
     * Calculate maximum visible distance from center to screen edge
     */
    private fun calculateMaxScreenDistance(): Double {
        try {
            val visibleRegion = map.projection.visibleRegion
            val center = LatLng(
                (visibleRegion.latLngBounds.latitudeNorth + visibleRegion.latLngBounds.latitudeSouth) / 2,
                (visibleRegion.latLngBounds.longitudeEast + visibleRegion.latLngBounds.longitudeWest) / 2
            )

            // Calculate distance to each corner and take the maximum
            val corners = listOf(
                LatLng(visibleRegion.latLngBounds.latitudeNorth, visibleRegion.latLngBounds.longitudeEast),
                LatLng(visibleRegion.latLngBounds.latitudeNorth, visibleRegion.latLngBounds.longitudeWest),
                LatLng(visibleRegion.latLngBounds.latitudeSouth, visibleRegion.latLngBounds.longitudeEast),
                LatLng(visibleRegion.latLngBounds.latitudeSouth, visibleRegion.latLngBounds.longitudeWest)
            )

            val maxDistance = corners.maxOfOrNull { corner ->
                calculateDistance(center, corner)
            } ?: 10.0 // Fallback to 10km

            Log.d(TAG, "Calculated max screen distance: ${maxDistance}km")
            return maxDistance

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating screen distance, using fallback: ${e.message}")
            return 10.0 // Fallback distance
        }
    }

    /**
     * Find optimal distance interval for screen-adaptive circles
     */
    private fun findOptimalInterval(maxScreenDistance: Double): Double {
        val targetInterval = maxScreenDistance / TARGET_CIRCLE_COUNT

        // Find the closest available interval
        return AVAILABLE_INTERVALS.minByOrNull { interval ->
            abs(interval - targetInterval)
        } ?: 1.0
    }

    /**
     * Generate list of circle distances based on optimal interval
     */
    private fun calculateOptimalDistances(): List<Double> {
        val maxScreenDistance = calculateMaxScreenDistance()
        val optimalInterval = findOptimalInterval(maxScreenDistance)

        // Generate circle distances: 1x, 2x, 3x, 4x, 5x, 6x interval
        val distances = (1..TARGET_CIRCLE_COUNT).map { multiplier ->
            optimalInterval * multiplier
        }

        Log.d(TAG, "Optimal interval: ${optimalInterval}km, distances: $distances")
        return distances
    }

    /**
     * Calculate distance between two LatLng points in kilometers
     */
    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLngRad = Math.toRadians(point2.longitude - point1.longitude)

        val a = sin(deltaLatRad / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(deltaLngRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Update circle geometries and labels based on current location and screen bounds
     */
    private fun updateCircles() {
        if (!isLayerAdded || !isVisible || currentLocation == null) return

        try {
            val style = map.style ?: return
            val circlesSource = style.getSourceAs<GeoJsonSource>(CIRCLES_SOURCE_ID) ?: return
            val labelsSource = style.getSourceAs<GeoJsonSource>(LABELS_SOURCE_ID) ?: return

            // Calculate optimal distances for current screen bounds
            val distances = calculateOptimalDistances()
            val location = currentLocation!!

            // Generate circle features
            val circleFeatures = distances.map { distanceKm ->
                generateCircleFeature(location, distanceKm)
            }

            // Generate label features (positioned at north of each circle)
            val labelFeatures = distances.map { distanceKm ->
                generateLabelFeature(location, distanceKm)
            }

            // Update sources
            circlesSource.setGeoJson(FeatureCollection.fromFeatures(circleFeatures))
            labelsSource.setGeoJson(FeatureCollection.fromFeatures(labelFeatures))

            Log.d(TAG, "Updated circles with screen-adaptive distances: $distances")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating circles: ${e.message}", e)
        }
    }

    /**
     * Generate a circular LineString feature for the given distance
     */
    private fun generateCircleFeature(center: LatLng, radiusKm: Double): Feature {
        val points = mutableListOf<Point>()

        // Convert radius to approximate degrees (rough conversion)
        val radiusDegrees = radiusKm / 111.32 // ~111.32 km per degree at equator

        for (i in 0..CIRCLE_POINTS) {
            val angle = 2.0 * PI * i / CIRCLE_POINTS
            val lat = center.latitude + radiusDegrees * cos(angle)
            val lng = center.longitude + radiusDegrees * sin(angle) / cos(Math.toRadians(center.latitude))
            points.add(Point.fromLngLat(lng, lat))
        }

        // Create LineString from points (this creates a continuous circular line)
        val lineString = LineString.fromLngLats(points)

        return Feature.fromGeometry(lineString)
    }

    /**
     * Generate a label feature positioned north of the circle
     */
    private fun generateLabelFeature(center: LatLng, radiusKm: Double): Feature {
        val radiusDegrees = radiusKm / 111.32

        // Position label at north edge of circle
        val labelLat = center.latitude + radiusDegrees
        val labelLng = center.longitude

        val point = Point.fromLngLat(labelLng, labelLat)
        val feature = Feature.fromGeometry(point)

        // Add distance label as property
        val labelText = if (radiusKm >= 1.0) {
            "${radiusKm.toInt()}km"
        } else {
            "${(radiusKm * 1000).toInt()}m"
        }
        feature.addStringProperty("distance", labelText)

        return feature
    }

    /**
     * Check if location has moved significantly enough to warrant circle update
     */
    private fun hasMovedSignificantly(newLocation: LatLng, lastLocation: LatLng): Boolean {
        val latDiff = abs(newLocation.latitude - lastLocation.latitude)
        val lngDiff = abs(newLocation.longitude - lastLocation.longitude)
        return latDiff > MIN_MOVEMENT_THRESHOLD || lngDiff > MIN_MOVEMENT_THRESHOLD
    }

    /**
     * Add layer with proper positioning below labels but above base map
     */
    private fun addLayerWithPositioning(layer: LineLayer) {
        try {
            val style = map.style ?: return

            // Try to add below label layers for better visibility
            val labelLayers = listOf(
                "place-label", "poi-label", "road-label", "water-label",
                "place_label", "poi_label", "road_label", "water_label"
            )

            var addedBelow = false
            for (labelLayer in labelLayers) {
                if (style.getLayer(labelLayer) != null) {
                    style.addLayerBelow(layer, labelLayer)
                    addedBelow = true
                    Log.d(TAG, "Added circles layer below: $labelLayer")
                    break
                }
            }

            if (!addedBelow) {
                style.addLayer(layer)
                Log.d(TAG, "Added circles layer on top")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error positioning layer, adding on top: ${e.message}")
            try {
                map.style?.addLayer(layer)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to add layer at all: ${e2.message}")
            }
        }
    }
}