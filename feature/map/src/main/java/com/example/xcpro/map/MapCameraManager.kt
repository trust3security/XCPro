package com.example.xcpro.map

import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.MapOrientationMode
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * Centralized camera management for MapScreen
 * Handles camera positioning, zoom animations, orientation updates, and smooth movements
 */
class MapCameraManager(
    internal val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions
) {
    companion object {
        private const val TAG = "MapCameraManager"
        const val DOUBLE_TAP_ZOOM_DELTA = 1.0f
        const val INITIAL_ZOOM = 8.0
        const val INITIAL_LATITUDE = 52.52
        const val INITIAL_LONGITUDE = 13.405
        private const val MAX_BEARING_STEP_DEG = 5.0

        // AAT Edit Zoom: Fraction of screen the circle diameter should occupy
        // LOWER value = MORE padding, MORE zoomed out (circle appears smaller)
        // HIGHER value = LESS padding, MORE zoomed in (circle fills screen)
        // Range: 0.10 (extreme padding) to 0.80 (aggressive, tight fit)
        private const val AAT_CIRCLE_SCREEN_FRACTION = 0.125  // Extreme zoom out - maximum padding
    }

    
    val isTrackingLocation: Boolean
        get() = mapStateReader.isTrackingLocation.value

    val showReturnButton: Boolean
        get() = mapStateReader.showReturnButton.value

    val targetLatLng: StateFlow<MapStateStore.MapPoint?>
        get() = mapStateReader.targetLatLng

    val targetZoom: StateFlow<Float?>
        get() = mapStateReader.targetZoom

    // AAT Edit Mode: Saved camera position for restore on exit
    private var savedCameraPosition: CameraPosition? = null

    /**
     * Move camera to specific location with zoom
     */
    fun moveTo(latLng: LatLng, zoom: Double? = null) {
        mapState.mapLibreMap?.let { map ->
            try {
                val targetZoom = clampZoom(
                    zoom = zoom ?: map.cameraPosition.zoom,
                    latitude = latLng.latitude
                )
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, targetZoom))
                AppLogger.d(TAG, "Camera moved to lat=${latLng.latitude}, lon=${latLng.longitude}, zoom=$targetZoom")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error moving camera: ${e.message}")
            }
        }
    }

    /**
     * Move camera to waypoint location
     */
    fun moveToWaypoint(latitude: Double, longitude: Double, zoom: Double = 12.0) {
        val latLng = LatLng(latitude, longitude)
        moveTo(latLng, zoom)
    }

    /**
     * Handle double-tap zoom at specific location
     */
    fun handleDoubleTapZoom(tapLatLng: LatLng) {
        mapState.mapLibreMap?.let { map ->
            try {
                val targetZoom = clampZoom(
                    zoom = map.cameraPosition.zoom + DOUBLE_TAP_ZOOM_DELTA,
                    latitude = tapLatLng.latitude
                ).toFloat()
                stateActions.setTarget(toMapPoint(tapLatLng), targetZoom)
                AppLogger.d(TAG, "Double tap zoom to: $tapLatLng, new zoom: $targetZoom")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling double tap zoom: ${e.message}")
            }
        }
    }

    /**
     * Update camera bearing based on orientation
     */
    fun updateBearing(
        newBearing: Double,
        orientationMode: MapOrientationMode,
        bearingSource: BearingSource
    ) {
        mapState.mapLibreMap?.let { map ->
            try {
                val currentPosition = map.cameraPosition
                val limitedBearing = resolveCameraBearingUpdate(
                    currentBearing = currentPosition.bearing,
                    requestedBearing = newBearing,
                    orientationMode = orientationMode,
                    maxBearingStepDeg = MAX_BEARING_STEP_DEG
                )

                if (limitedBearing != null) {

                    val newCameraPosition = CameraPosition.Builder()
                        .target(currentPosition.target)
                        .zoom(currentPosition.zoom)
                        .bearing(limitedBearing)
                        .tilt(currentPosition.tilt)
                        .build()

                    map.moveCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))
                    AppLogger.d(
                        TAG,
                        "Bearing updated: mode=$orientationMode, source=$bearingSource, bearing=$limitedBearing (raw=$newBearing)"
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error updating camera bearing: ${e.message}")
            }
        }
    }

    private fun clampZoom(zoom: Double, latitude: Double? = null): Double {
        val mapView = mapState.mapView ?: return zoom
        val map = mapState.mapLibreMap ?: return zoom
        val width = mapView.width
        if (width <= 0) return zoom
        val lat = latitude ?: map.cameraPosition.target?.latitude ?: 0.0
        val metersPerPixel = map.projection.getMetersPerPixelAtLatitude(lat)
        val pixelRatio = mapView.pixelRatio
        val distancePerPixel = if (pixelRatio > 0f) metersPerPixel / pixelRatio else metersPerPixel
        return MapZoomConstraints.clampZoom(
            zoom = zoom,
            widthPx = width,
            currentZoom = map.cameraPosition.zoom,
            distancePerPixel = distancePerPixel
        )
    }

    /**
     * Get current camera position
     */
    fun getCurrentPosition(): CameraPosition? {
        return mapState.mapLibreMap?.cameraPosition
    }

    /**
     * Get current zoom level
     */
    fun getCurrentZoom(): Double? {
        return mapState.mapLibreMap?.cameraPosition?.zoom
    }

    /**
     * Animate camera to target position with smooth transition
     */
    fun animateToTarget() {
        mapState.mapLibreMap?.let { map ->
            try {
                val targetLatLng = toLatLng(mapStateReader.targetLatLng.value)
                    ?: LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE)
                val rawZoom = mapStateReader.targetZoom.value ?: INITIAL_ZOOM
                val targetZoom = clampZoom(rawZoom.toDouble(), targetLatLng.latitude)

                map.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, targetZoom))
                AppLogger.d(TAG, "Camera animated to target: lat=${targetLatLng.latitude}, lon=${targetLatLng.longitude}, zoom=$targetZoom")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error animating camera to target: ${e.message}")
            }
        }
    }

    /**
     * Reset camera to initial position
     */
    fun resetToInitialPosition() {
        val initialLatLng = LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE)
        moveTo(initialLatLng, INITIAL_ZOOM)
        stateActions.setTarget(
            location = MapStateStore.MapPoint(initialLatLng.latitude, initialLatLng.longitude),
            zoom = INITIAL_ZOOM.toFloat()
        )
    }

    /**
     * Zoom to AAT area for editing
     * Saves current camera position and zooms to turnpoint for precise editing
     * Uses adaptive padding based on circle size - larger circles need more breathing room
     *
     * @param turnpointLat Turnpoint center latitude
     * @param turnpointLon Turnpoint center longitude
     * @param turnpointRadiusMeters Turnpoint radius in meters
     * @param bottomSheetHeightPx Height of bottom sheet in pixels (optional, default 0)
     */
    fun zoomToAATAreaForEdit(
        turnpointLat: Double,
        turnpointLon: Double,
        turnpointRadiusMeters: Double,
        bottomSheetHeightPx: Int = 0
    ) {
        mapState.mapLibreMap?.let { map ->
            try {
                // Save current camera position BEFORE zooming in
                savedCameraPosition = map.cameraPosition
                AppLogger.d(TAG, "AAT: Saved camera position - lat=${savedCameraPosition?.target?.latitude}, lon=${savedCameraPosition?.target?.longitude}, zoom=${savedCameraPosition?.zoom}")

                // Get actual screen dimensions from MapView (not safeContainerSize which may be 0)
                // MapView.width/height are native View dimensions, always available after layout
                val screenWidth = mapState.mapView?.width ?: 1080  // Fallback to common phone screen
                val screenHeight = mapState.mapView?.height ?: 2340

                if (mapState.mapView == null) {
                    AppLogger.w(TAG, "AAT: MapView is null, using fallback screen size ${screenWidth}x${screenHeight}")
                }

                // Account for bottom sheet covering part of the map
                val availableMapHeight = kotlin.math.max(
                    (screenHeight - bottomSheetHeightPx).toDouble(),
                    screenHeight * 0.6
                ).toInt() // At least 60% of screen

                // Use the smaller dimension to ensure circle fits in both orientations
                val minScreenDimension = kotlin.math.min(screenWidth, availableMapHeight)
                AppLogger.d(TAG, "AAT: Screen size - width=${screenWidth}px, height=${screenHeight}px, bottomSheet=${bottomSheetHeightPx}px, available=${minScreenDimension}px")

                // Calculate optimal zoom level to fit entire turnpoint on screen
                // MapLibre zoom relationship:
                // At zoom Z, meters per pixel at equator = (earthCircumference * 1000) / (256 * 2^Z)
                // We want: diameterMeters = metersPerPixel * pixelsAvailable
                //
                // Solving for Z:
                // Z = log2((earthCircumference * 1000) / (256 * metersPerPixel))
                // Z = log2((earthCircumference * 1000) / (256 * diameterMeters / pixelsAvailable))

                val turnpointDisplayKm = turnpointRadiusMeters / 1000.0
                val diameterKm = turnpointDisplayKm * 2.0
                val diameterMeters = turnpointRadiusMeters * 2.0

                // Adaptive fraction: Larger circles need to occupy LESS of screen (more padding)
                // This is because users need to see MORE context around larger circles
                val circleFraction = when {
                    turnpointRadiusMeters <= 10_000.0 -> AAT_CIRCLE_SCREEN_FRACTION  // Small: use base fraction (0.125)
                    turnpointRadiusMeters <= 20_000.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.7  // Medium: 70% of base (0.0875)
                    turnpointRadiusMeters <= 30_000.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.5  // Large: 50% of base (0.0625)
                    turnpointRadiusMeters <= 40_000.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.35  // XL: 35% of base (0.044)
                    turnpointRadiusMeters <= 50_000.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.20  // XXL: 20% of base (0.025)
                    else -> AAT_CIRCLE_SCREEN_FRACTION * 0.15  // Extreme: 15% of base (0.019)
                }

                val usableScreenPixels = minScreenDimension * circleFraction
                AppLogger.d(TAG, "AAT: Radius=${turnpointDisplayKm}km, diameter=${String.format("%.1f", diameterKm)}km, fraction=${String.format("%.1f", circleFraction * 100)}%, usablePixels=${String.format("%.0f", usableScreenPixels)}px")

                // Meters per pixel needed to fit circle in available space
                val requiredMetersPerPixel = diameterMeters / usableScreenPixels

                // MapLibre tile size
                val tileSize = 256.0
                val earthCircumferenceMeters = 40075000.0

                // Calculate zoom level
                // metersPerPixel = earthCircumferenceMeters / (tileSize * 2^zoom)
                // Solving for zoom: zoom = log2(earthCircumferenceMeters / (tileSize * metersPerPixel))
                val baseZoom = kotlin.math.log2(earthCircumferenceMeters / (tileSize * requiredMetersPerPixel))

                // Adjust for latitude (circles appear larger near poles)
                val latitudeRadians = Math.toRadians(turnpointLat)
                val latitudeFactor = kotlin.math.cos(latitudeRadians)
                val adjustedZoom = baseZoom + kotlin.math.log2(latitudeFactor)

                // Clamp zoom between reasonable bounds for AAT editing
                // Lower bound allows more zoom out for larger turnpoints
                val editZoom = clampZoom(
                    zoom = adjustedZoom.coerceIn(8.0, 16.0),
                    latitude = turnpointLat
                )  // Was 11.0 - now allows much more zoom out

                val turnpointLatLng = LatLng(turnpointLat, turnpointLon)

                stateActions.setTarget(
                    location = MapStateStore.MapPoint(turnpointLatLng.latitude, turnpointLatLng.longitude),
                    zoom = editZoom.toFloat()
                )
                AppLogger.d(TAG, "AAT: Zooming to turnpoint - radius=${turnpointDisplayKm}km, diameter=${String.format("%.1f", diameterKm)}km, screen=${minScreenDimension}px, calculated zoom=${String.format("%.2f", editZoom)}, at: $turnpointLatLng")
            } catch (e: Exception) {
                AppLogger.e(TAG, "AAT: Error zooming to AAT area: ${e.message}")
            }
        }
    }

    /**
     * Restore camera position after AAT edit mode exit
     * Returns to the saved position from before entering edit mode
     */
    fun restoreAATCameraPosition() {
        mapState.mapLibreMap?.let { map ->
            try {
                val saved = savedCameraPosition
                if (saved != null) {
                    val target = saved.target
                    if (target != null) {
                        val clampedZoom = clampZoom(saved.zoom, target.latitude)
                        stateActions.setTarget(
                            location = MapStateStore.MapPoint(target.latitude, target.longitude),
                            zoom = clampedZoom.toFloat()
                        )

                        AppLogger.d(TAG, "dYZ_ AAT: Restored camera position - lat=${target.latitude}, lon=${target.longitude}, zoom=$clampedZoom")
                    } else {
                        AppLogger.w(TAG, "dYZ_ AAT: Saved camera target missing")
                    }

                    // Clear saved position
                    savedCameraPosition = null
                } else {
                    AppLogger.w(TAG, "dYZ_ AAT: No saved camera position to restore")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "dYZ_ AAT: Error restoring camera position: ${e.message}")
            }
        }
    }

    private fun toLatLng(point: MapStateStore.MapPoint?): LatLng? {
        return point?.let { LatLng(it.latitude, it.longitude) }
    }

    private fun toMapPoint(latLng: LatLng?): MapStateStore.MapPoint? {
        return latLng?.let { MapStateStore.MapPoint(it.latitude, it.longitude) }
    }
}

internal fun resolveCameraBearingUpdate(
    currentBearing: Double,
    requestedBearing: Double,
    orientationMode: MapOrientationMode,
    maxBearingStepDeg: Double,
    minBearingChangeDeg: Double = 2.0
): Double? {
    val normalizedCurrent = normalizeBearingDegrees(currentBearing)
    val targetBearing = if (orientationMode == MapOrientationMode.NORTH_UP) {
        0.0
    } else {
        requestedBearing
    }
    val normalizedTarget = normalizeBearingDegrees(targetBearing)
    val delta = shortestDeltaDegrees(normalizedCurrent, normalizedTarget)
    val limitedBearing = if (abs(delta) > maxBearingStepDeg) {
        normalizeBearingDegrees(normalizedCurrent + sign(delta) * maxBearingStepDeg)
    } else {
        normalizedTarget
    }
    return if (abs(shortestDeltaDegrees(normalizedCurrent, limitedBearing)) > minBearingChangeDeg) {
        limitedBearing
    } else {
        null
    }
}

private fun normalizeBearingDegrees(bearing: Double): Double {
    var normalized = bearing % 360.0
    if (normalized < 0.0) {
        normalized += 360.0
    }
    return normalized
}

private fun shortestDeltaDegrees(from: Double, to: Double): Double {
    var delta = (to - from) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta < -180.0) delta += 360.0
    return delta
}
