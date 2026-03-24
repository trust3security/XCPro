package com.example.xcpro.map

import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * Centralized camera management for MapScreen.
 * Owns camera positioning, zoom animations, orientation updates, and AAT edit zoom.
 */
class MapCameraManager(
    private val cameraSurface: MapCameraSurfacePort,
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions
) : MapCameraRuntimePort {

    companion object {
        private const val TAG = "MapCameraManager"
        const val DOUBLE_TAP_ZOOM_DELTA = 1.0f
        const val INITIAL_ZOOM = 8.0
        const val INITIAL_LATITUDE = 52.52
        const val INITIAL_LONGITUDE = 13.405
        private const val MAX_BEARING_STEP_DEG = 5.0
        private const val AAT_CIRCLE_SCREEN_FRACTION = 0.125
    }

    override val isTrackingLocation: Boolean
        get() = mapStateReader.isTrackingLocation.value

    override val showReturnButton: Boolean
        get() = mapStateReader.showReturnButton.value

    override val targetLatLng: StateFlow<MapPoint?>
        get() = mapStateReader.targetLatLng

    override val targetZoom: StateFlow<Float?>
        get() = mapStateReader.targetZoom

    private var savedCameraPosition: MapCameraPose? = null

    override fun moveTo(target: MapPoint, zoom: Double?) {
        try {
            val currentPose = cameraSurface.cameraPoseOrNull() ?: return
            val targetZoom = clampZoom(
                zoom = zoom ?: currentPose.zoom,
                latitude = target.latitude
            )
            cameraSurface.moveTo(target, targetZoom)
            AppLogger.d(
                TAG,
                "Camera moved to lat=${target.latitude}, lon=${target.longitude}, zoom=$targetZoom"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error moving camera: ${e.message}")
        }
    }

    fun moveToWaypoint(latitude: Double, longitude: Double, zoom: Double = 12.0) {
        moveTo(MapPoint(latitude, longitude), zoom)
    }

    fun handleDoubleTapZoom(tapLocation: MapPoint) {
        try {
            val currentPose = cameraSurface.cameraPoseOrNull() ?: return
            val targetZoom = clampZoom(
                zoom = currentPose.zoom + DOUBLE_TAP_ZOOM_DELTA,
                latitude = tapLocation.latitude
            ).toFloat()
            stateActions.setTarget(tapLocation, targetZoom)
            AppLogger.d(
                TAG,
                "Double tap zoom to: lat=${tapLocation.latitude}, lon=${tapLocation.longitude}, new zoom=$targetZoom"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error handling double tap zoom: ${e.message}")
        }
    }

    override fun updateBearing(
        newBearing: Double,
        orientationMode: MapOrientationMode,
        bearingSource: BearingSource
    ) {
        try {
            val currentPose = cameraSurface.cameraPoseOrNull() ?: return
            val limitedBearing = resolveCameraBearingUpdate(
                currentBearing = currentPose.bearing,
                requestedBearing = newBearing,
                orientationMode = orientationMode,
                maxBearingStepDeg = MAX_BEARING_STEP_DEG
            )
            if (limitedBearing != null) {
                cameraSurface.moveTo(currentPose.copy(bearing = limitedBearing))
                AppLogger.d(
                    TAG,
                    "Bearing updated: mode=$orientationMode, source=$bearingSource, bearing=$limitedBearing (raw=$newBearing)"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error updating camera bearing: ${e.message}")
        }
    }

    private fun clampZoom(zoom: Double, latitude: Double? = null): Double {
        val viewport = cameraSurface.viewportMetricsOrNull() ?: return zoom
        val currentPose = cameraSurface.cameraPoseOrNull() ?: return zoom
        val width = viewport.widthPx
        if (width <= 0) return zoom

        val lat = latitude ?: currentPose.target?.latitude ?: 0.0
        val metersPerPixel = cameraSurface.metersPerPixelAtLatitude(lat) ?: return zoom
        val pixelRatio = viewport.pixelRatio
        val distancePerPixel = if (pixelRatio > 0f) metersPerPixel / pixelRatio else metersPerPixel
        return MapZoomConstraints.clampZoom(
            zoom = zoom,
            widthPx = width,
            currentZoom = currentPose.zoom,
            distancePerPixel = distancePerPixel
        )
    }

    fun getCurrentPosition(): MapCameraPose? {
        return cameraSurface.cameraPoseOrNull()
    }

    override fun applyAnimatedZoom(animatedZoom: Float, targetLatLng: MapPoint?) {
        try {
            val target = targetLatLng ?: MapPoint(INITIAL_LATITUDE, INITIAL_LONGITUDE)
            cameraSurface.moveTo(target, animatedZoom.toDouble())
            AppLogger.d(
                TAG,
                "Animated zoom applied: lat=${target.latitude}, lon=${target.longitude}, zoom=$animatedZoom"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error applying animated zoom: ${e.message}")
        }
    }

    fun getCurrentZoom(): Double? {
        return cameraSurface.cameraPoseOrNull()?.zoom
    }

    fun animateToTarget() {
        try {
            val targetLocation = mapStateReader.targetLatLng.value
                ?: MapPoint(INITIAL_LATITUDE, INITIAL_LONGITUDE)
            val rawZoom = mapStateReader.targetZoom.value ?: INITIAL_ZOOM
            val targetZoom = clampZoom(rawZoom.toDouble(), targetLocation.latitude)
            cameraSurface.moveTo(targetLocation, targetZoom)
            AppLogger.d(
                TAG,
                "Camera animated to target: lat=${targetLocation.latitude}, lon=${targetLocation.longitude}, zoom=$targetZoom"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error animating camera to target: ${e.message}")
        }
    }

    fun resetToInitialPosition() {
        val initialTarget = MapPoint(INITIAL_LATITUDE, INITIAL_LONGITUDE)
        moveTo(initialTarget, INITIAL_ZOOM)
        stateActions.setTarget(
            location = initialTarget,
            zoom = INITIAL_ZOOM.toFloat()
        )
    }

    override fun zoomToAATAreaForEdit(
        turnpointLat: Double,
        turnpointLon: Double,
        turnpointRadiusMeters: Double,
        bottomSheetHeightPx: Int
    ) {
        try {
            savedCameraPosition = cameraSurface.cameraPoseOrNull()
            AppLogger.d(
                TAG,
                "AAT: Saved camera position - lat=${savedCameraPosition?.target?.latitude}, lon=${savedCameraPosition?.target?.longitude}, zoom=${savedCameraPosition?.zoom}"
            )

            val viewport = cameraSurface.viewportMetricsOrNull()
            val screenWidth = viewport?.widthPx ?: 1080
            val screenHeight = viewport?.heightPx ?: 2340
            if (viewport == null) {
                AppLogger.w(
                    TAG,
                    "AAT: Viewport unavailable, using fallback screen size ${screenWidth}x${screenHeight}"
                )
            }

            val availableMapHeight = kotlin.math.max(
                (screenHeight - bottomSheetHeightPx).toDouble(),
                screenHeight * 0.6
            ).toInt()
            val minScreenDimension = kotlin.math.min(screenWidth, availableMapHeight)
            AppLogger.d(
                TAG,
                "AAT: Screen size - width=${screenWidth}px, height=${screenHeight}px, bottomSheet=${bottomSheetHeightPx}px, available=${minScreenDimension}px"
            )

            val turnpointDisplayKm = turnpointRadiusMeters / 1000.0
            val diameterKm = turnpointDisplayKm * 2.0
            val diameterMeters = turnpointRadiusMeters * 2.0

            val circleFraction = when {
                turnpointRadiusMeters <= 10_000.0 -> AAT_CIRCLE_SCREEN_FRACTION
                turnpointRadiusMeters <= 20_000.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.7
                turnpointRadiusMeters <= 30_000.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.5
                turnpointRadiusMeters <= 40_000.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.35
                turnpointRadiusMeters <= 50_000.0 -> AAT_CIRCLE_SCREEN_FRACTION * 0.20
                else -> AAT_CIRCLE_SCREEN_FRACTION * 0.15
            }

            val usableScreenPixels = minScreenDimension * circleFraction
            AppLogger.d(
                TAG,
                "AAT: Radius=${turnpointDisplayKm}km, diameter=${String.format("%.1f", diameterKm)}km, fraction=${String.format("%.1f", circleFraction * 100)}%, usablePixels=${String.format("%.0f", usableScreenPixels)}px"
            )

            val requiredMetersPerPixel = diameterMeters / usableScreenPixels
            val tileSize = 256.0
            val earthCircumferenceMeters = 40075000.0
            val baseZoom = kotlin.math.log2(earthCircumferenceMeters / (tileSize * requiredMetersPerPixel))
            val latitudeRadians = Math.toRadians(turnpointLat)
            val latitudeFactor = kotlin.math.cos(latitudeRadians)
            val adjustedZoom = baseZoom + kotlin.math.log2(latitudeFactor)
            val editZoom = clampZoom(
                zoom = adjustedZoom.coerceIn(8.0, 16.0),
                latitude = turnpointLat
            )

            val turnpointTarget = MapPoint(turnpointLat, turnpointLon)
            stateActions.setTarget(
                location = turnpointTarget,
                zoom = editZoom.toFloat()
            )
            AppLogger.d(
                TAG,
                "AAT: Zooming to turnpoint - radius=${turnpointDisplayKm}km, diameter=${String.format("%.1f", diameterKm)}km, screen=${minScreenDimension}px, calculated zoom=${String.format("%.2f", editZoom)}, at: lat=${turnpointLat}, lon=${turnpointLon}"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "AAT: Error zooming to AAT area: ${e.message}")
        }
    }

    override fun restoreAATCameraPosition() {
        try {
            val saved = savedCameraPosition
            if (saved != null) {
                val target = saved.target
                if (target != null) {
                    val clampedZoom = clampZoom(saved.zoom, target.latitude)
                    stateActions.setTarget(
                        location = target,
                        zoom = clampedZoom.toFloat()
                    )
                    AppLogger.d(
                        TAG,
                        "dYZ_ AAT: Restored camera position - lat=${target.latitude}, lon=${target.longitude}, zoom=$clampedZoom"
                    )
                } else {
                    AppLogger.w(TAG, "dYZ_ AAT: Saved camera target missing")
                }
                savedCameraPosition = null
            } else {
                AppLogger.w(TAG, "dYZ_ AAT: No saved camera position to restore")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "dYZ_ AAT: Error restoring camera position: ${e.message}")
        }
    }

    override fun fitTaskViewport(snapshot: TaskRenderSnapshot) {
        try {
            val plan = TaskViewportPlanner.plan(
                task = snapshot.task,
                viewport = cameraSurface.viewportMetricsOrNull()
            ) ?: return
            val targetZoom = clampZoom(
                zoom = plan.zoom,
                latitude = plan.target.latitude
            )
            stateActions.setTarget(
                location = plan.target,
                zoom = targetZoom.toFloat()
            )
            AppLogger.d(
                TAG,
                "Fit task viewport target=${plan.target.latitude},${plan.target.longitude} zoom=$targetZoom"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fitting task viewport: ${e.message}")
        }
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
