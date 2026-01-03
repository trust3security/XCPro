package com.example.xcpro.map

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.MapOrientationMode
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sign

/**
 * Centralized camera management for MapScreen
 * Handles camera positioning, zoom animations, orientation updates, and smooth movements
 */
class MapCameraManager(
    internal val mapState: MapScreenState,
    private val mapStateStore: MapStateStore,
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
        get() = mapStateStore.isTrackingLocation.value

    val showReturnButton: Boolean
        get() = mapStateStore.showReturnButton.value

    val targetLatLng: StateFlow<MapStateStore.MapPoint?>
        get() = mapStateStore.targetLatLng

    val targetZoom: StateFlow<Float?>
        get() = mapStateStore.targetZoom

    // Camera state
    var lastCameraBearing by mutableStateOf(0.0)
        private set

    // AAT Edit Mode: Saved camera position for restore on exit
    private var savedCameraPosition: CameraPosition? = null

    /**
     * Move camera to specific location with zoom
     */
    fun moveTo(latLng: LatLng, zoom: Double? = null) {
        mapState.mapLibreMap?.let { map ->
            try {
                val targetZoom = zoom ?: map.cameraPosition.zoom
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, targetZoom))
                Log.d(TAG, "Camera moved to lat=${latLng.latitude}, lon=${latLng.longitude}, zoom=$targetZoom")
            } catch (e: Exception) {
                Log.e(TAG, "Error moving camera: ${e.message}")
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
                val targetZoom = (map.cameraPosition.zoom + DOUBLE_TAP_ZOOM_DELTA).toFloat()
                stateActions.setTarget(toMapPoint(tapLatLng), targetZoom)
                Log.d(TAG, "Double tap zoom to: $tapLatLng, new zoom: $targetZoom")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling double tap zoom: ${e.message}")
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
                val targetBearing = if (orientationMode == MapOrientationMode.NORTH_UP) {
                    0.0
                } else {
                    newBearing
                }

                // Clamp rotation step to smooth jitter (XCSoar-like)
                val delta = shortestDeltaDegrees(lastCameraBearing, targetBearing)
                val limitedBearing = if (kotlin.math.abs(delta) > MAX_BEARING_STEP_DEG) {
                    lastCameraBearing + sign(delta) * MAX_BEARING_STEP_DEG
                } else {
                    targetBearing
                }

                val bearingChanged = kotlin.math.abs(shortestDeltaDegrees(lastCameraBearing, limitedBearing)) > 2.0

                if (bearingChanged) {
                    val currentPosition = map.cameraPosition

                    val newCameraPosition = CameraPosition.Builder()
                        .target(currentPosition.target)
                        .zoom(currentPosition.zoom)
                        .bearing(limitedBearing)
                        .tilt(currentPosition.tilt)
                        .build()

                    map.moveCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition))
                    lastCameraBearing = limitedBearing
                    Log.d(
                        TAG,
                        "Bearing updated: mode=$orientationMode, source=$bearingSource, bearing=$limitedBearing (raw=$newBearing)"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating camera bearing: ${e.message}")
            }
        }
    }

    private fun shortestDeltaDegrees(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180) delta -= 360.0
        if (delta < -180) delta += 360.0
        return delta
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
                val targetLatLng = toLatLng(mapStateStore.targetLatLng.value)
                    ?: LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE)
                val targetZoom = mapStateStore.targetZoom.value ?: INITIAL_ZOOM

                map.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, targetZoom.toDouble()))
                Log.d(TAG, "Camera animated to target: lat=${targetLatLng.latitude}, lon=${targetLatLng.longitude}, zoom=$targetZoom")
            } catch (e: Exception) {
                Log.e(TAG, "Error animating camera to target: ${e.message}")
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
     * @param turnpointRadiusKm Turnpoint radius in kilometers (e.g., 5, 10, 20)
     * @param bottomSheetHeightPx Height of bottom sheet in pixels (optional, default 0)
     */
    fun zoomToAATAreaForEdit(
        turnpointLat: Double,
        turnpointLon: Double,
        turnpointRadiusKm: Double,
        bottomSheetHeightPx: Int = 0
    ) {
        mapState.mapLibreMap?.let { map ->
            try {
                // Save current camera position BEFORE zooming in
                savedCameraPosition = map.cameraPosition
                Log.d(TAG, "🎯 AAT: Saved camera position - lat=${savedCameraPosition?.target?.latitude}, lon=${savedCameraPosition?.target?.longitude}, zoom=${savedCameraPosition?.zoom}")

                // Get actual screen dimensions from MapView (not safeContainerSize which may be 0)
                // MapView.width/height are native View dimensions, always available after layout
                val screenWidth = mapState.mapView?.width ?: 1080  // Fallback to common phone screen
                val screenHeight = mapState.mapView?.height ?: 2340

                if (mapState.mapView == null) {
                    Log.w(TAG, "⚠️ AAT: MapView is null, using fallback screen size ${screenWidth}×${screenHeight}")
                }

                // Account for bottom sheet covering part of the map
                val availableMapHeight = kotlin.math.max(
                    (screenHeight - bottomSheetHeightPx).toDouble(),
                    screenHeight * 0.6
                ).toInt() // At least 60% of screen

                // Use the smaller dimension to ensure circle fits in both orientations
                val minScreenDimension = kotlin.math.min(screenWidth, availableMapHeight)

                Log.d(TAG, "🎯 AAT: Screen size - width=${screenWidth}px, height=${screenHeight}px, bottomSheet=${bottomSheetHeightPx}px, available=${minScreenDimension}px")

                // Calculate optimal zoom level to fit entire turnpoint on screen
                // MapLibre zoom relationship:
                // At zoom Z, meters per pixel at equator = (earthCircumference * 1000) / (256 * 2^Z)
                // We want: diameterMeters = metersPerPixel * pixelsAvailable
                //
                // Solving for Z:
                // Z = log2((earthCircumference * 1000) / (256 * metersPerPixel))
                // Z = log2((earthCircumference * 1000) / (256 * diameterMeters / pixelsAvailable))

                val diameterKm = turnpointRadiusKm * 2.0
                val diameterMeters = diameterKm * 1000.0

                // Adaptive fraction: Larger circles need to occupy LESS of screen (more padding)
                // This is because users need to see MORE context around larger circles
                val circleFraction = when {
                    turnpointRadiusKm <= 10.0 -> AAT_CIRCLE_SCREEN_FRACTION  // Small: use base fraction (0.125)
                    turnpointRadiusKm <= 20.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.7  // Medium: 70% of base (0.0875)
                    turnpointRadiusKm <= 30.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.5  // Large: 50% of base (0.0625)
                    turnpointRadiusKm <= 40.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.35  // XL: 35% of base (0.044)
                    turnpointRadiusKm <= 50.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.20  // XXL: 20% of base (0.025)
                    else -> AAT_CIRCLE_SCREEN_FRACTION * 0.15  // Extreme: 15% of base (0.019)
                }

                val usableScreenPixels = minScreenDimension * circleFraction

                Log.d(TAG, "🎯 AAT: Radius=${turnpointRadiusKm}km, diameter=${String.format("%.1f", diameterKm)}km → fraction=${String.format("%.1f", circleFraction * 100)}% → usablePixels=${String.format("%.0f", usableScreenPixels)}px")

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
                val editZoom = adjustedZoom.coerceIn(8.0, 16.0)  // Was 11.0 - now allows much more zoom out

                val turnpointLatLng = LatLng(turnpointLat, turnpointLon)

                stateActions.setTarget(
                    location = MapStateStore.MapPoint(turnpointLatLng.latitude, turnpointLatLng.longitude),
                    zoom = editZoom.toFloat()
                )

                Log.d(TAG, "🎯 AAT: Zooming to turnpoint - radius=${turnpointRadiusKm}km, diameter=${String.format("%.1f", diameterKm)}km, screen=${minScreenDimension}px, calculated zoom=${String.format("%.2f", editZoom)}, at: $turnpointLatLng")
            } catch (e: Exception) {
                Log.e(TAG, "🎯 AAT: Error zooming to AAT area: ${e.message}")
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
                        stateActions.setTarget(
                            location = MapStateStore.MapPoint(target.latitude, target.longitude),
                            zoom = saved.zoom.toFloat()
                        )

                        Log.d(TAG, "dYZ_ AAT: Restored camera position - lat=${target.latitude}, lon=${target.longitude}, zoom=${saved.zoom}")
                    } else {
                        Log.w(TAG, "dYZ_ AAT: Saved camera target missing")
                    }

                    // Clear saved position
                    savedCameraPosition = null
                } else {
                    Log.w(TAG, "dYZ_ AAT: No saved camera position to restore")
                }
            } catch (e: Exception) {
                Log.e(TAG, "dYZ_ AAT: Error restoring camera position: ${e.message}")
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

/**
 * Compose camera effects for MapScreen integration
 */
object MapCameraEffects {

    /**
     * Animated zoom effect with smooth transitions
     */
    @Composable
    fun AnimatedZoomEffect(
        cameraManager: MapCameraManager,
        targetZoom: Float?,
        targetLatLng: MapStateStore.MapPoint?
    ) {
        val animatedZoom by animateFloatAsState(
            targetValue = targetZoom ?: MapCameraManager.INITIAL_ZOOM.toFloat(),
            animationSpec = tween(durationMillis = 300),
            label = "zoom_animation"
        )

        DisposableEffect(animatedZoom, targetLatLng) {
            cameraManager.mapState.mapLibreMap?.let { map ->
                try {
                    val latLng = targetLatLng?.let { LatLng(it.latitude, it.longitude) }
                        ?: LatLng(MapCameraManager.INITIAL_LATITUDE, MapCameraManager.INITIAL_LONGITUDE)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, animatedZoom.toDouble()))
                    Log.d("MapCameraEffects", "Camera moved to lat=${latLng.latitude}, lon=${latLng.longitude}, zoom=$animatedZoom")
                } catch (e: Exception) {
                    Log.e("MapCameraEffects", "Error moving camera: ${e.message}")
                }
            }
            onDispose { }
        }
    }

    /**
     * Orientation bearing effect for camera rotation
     */
    @Composable
    fun OrientationBearingEffect(
        cameraManager: MapCameraManager,
        bearing: Double,
        orientationMode: MapOrientationMode,
        bearingSource: BearingSource,
        replayPlaying: Boolean = false
    ) {
        // If map tracking is active or replay is playing, bearing is applied with position in MapPositionController.
        if (replayPlaying || (cameraManager.isTrackingLocation && !cameraManager.showReturnButton)) {
            return
        }
        DisposableEffect(bearing, orientationMode, bearingSource) {
            cameraManager.updateBearing(bearing, orientationMode, bearingSource)
            Log.d(
                "MapCameraEffects",
                "Orientation updated: mode=$orientationMode, source=$bearingSource, bearing=$bearing"
            )
            onDispose { }
        }
    }

    /**
     * Combined camera effects for easy integration
     */
    @Composable
    fun AllCameraEffects(
        cameraManager: MapCameraManager,
        bearing: Double,
        orientationMode: MapOrientationMode,
        bearingSource: BearingSource,
        replayPlaying: Boolean = false
    ) {
        val targetZoom by cameraManager.targetZoom.collectAsStateWithLifecycle()
        val targetLatLng by cameraManager.targetLatLng.collectAsStateWithLifecycle()

        AnimatedZoomEffect(
            cameraManager = cameraManager,
            targetZoom = targetZoom,
            targetLatLng = targetLatLng
        )

        OrientationBearingEffect(
            cameraManager = cameraManager,
            bearing = bearing,
            orientationMode = orientationMode,
            bearingSource = bearingSource,
            replayPlaying = replayPlaying
        )
    }
}
