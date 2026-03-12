package com.example.xcpro.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.core.common.logging.AppLogger
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng

/**
 * Compose camera effects for MapScreen integration.
 */
object MapCameraEffects {

    /**
     * Animated zoom effect with smooth transitions.
     */
    @Composable
    fun AnimatedZoomEffect(
        cameraManager: MapCameraManager,
        targetZoom: Float?,
        targetLatLng: MapPoint?
    ) {
        val animatedZoom by animateFloatAsState(
            targetValue = targetZoom ?: MapCameraManager.INITIAL_ZOOM.toFloat(),
            animationSpec = tween(durationMillis = 300),
            label = "zoom_animation"
        )

        DisposableEffect(animatedZoom, targetLatLng) {
            cameraManager.mapLibreMapOrNull()?.let { map ->
                try {
                    val latLng = targetLatLng?.let { LatLng(it.latitude, it.longitude) }
                        ?: LatLng(MapCameraManager.INITIAL_LATITUDE, MapCameraManager.INITIAL_LONGITUDE)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, animatedZoom.toDouble()))
                    AppLogger.d(
                        "MapCameraEffects",
                        "Camera moved to lat=${latLng.latitude}, lon=${latLng.longitude}, zoom=$animatedZoom"
                    )
                } catch (e: Exception) {
                    AppLogger.e("MapCameraEffects", "Error moving camera: ${e.message}")
                }
            }
            onDispose { }
        }
    }

    /**
     * Orientation bearing effect for camera rotation.
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
            AppLogger.d(
                "MapCameraEffects",
                "Orientation updated: mode=$orientationMode, source=$bearingSource, bearing=$bearing"
            )
            onDispose { }
        }
    }

    /**
     * Combined camera effects for easy integration.
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
