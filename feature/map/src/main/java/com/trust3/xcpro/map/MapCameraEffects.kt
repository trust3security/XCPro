package com.trust3.xcpro.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.common.orientation.BearingSource
import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.core.common.logging.AppLogger

/**
 * Compose camera effects for MapScreen integration.
 */
object MapCameraEffects {

    /**
     * Animated zoom effect with smooth transitions.
     */
    @Composable
    fun AnimatedZoomEffect(
        cameraManager: MapCameraRuntimePort,
        targetZoom: Float?,
        targetLatLng: MapPoint?
    ) {
        val animatedZoom by animateFloatAsState(
            targetValue = targetZoom ?: 8.0f,
            animationSpec = tween(durationMillis = 300),
            label = "zoom_animation"
        )

        DisposableEffect(animatedZoom, targetLatLng) {
            cameraManager.applyAnimatedZoom(animatedZoom, targetLatLng)
            AppLogger.d("MapCameraEffects", "Animated zoom dispatched at zoom=$animatedZoom")
            onDispose { }
        }
    }

    /**
     * Orientation bearing effect for camera rotation.
     */
    @Composable
    fun OrientationBearingEffect(
        cameraManager: MapCameraRuntimePort,
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
        cameraManager: MapCameraRuntimePort,
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
