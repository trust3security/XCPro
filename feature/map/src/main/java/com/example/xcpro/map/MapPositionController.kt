package com.example.xcpro.map

import android.os.SystemClock
import android.util.Log
import org.maplibre.android.geometry.LatLng
import kotlin.math.abs
import kotlin.math.sign

/**
 * Single owner for applying accepted (already gated) location samples
 * to both camera and icon. Shared gate, bearing clamp, offset averaging.
 */
class MapPositionController(
    private val mapState: MapScreenState,
    private val maxBearingStepDegProvider: () -> Double = { 5.0 },
    private val headingSmoothingEnabledProvider: () -> Boolean = { true },
    offsetHistorySize: Int = 30,
    iconRotationConfig: IconRotationConfig = IconRotationConfig.defaults()
) {
    companion object {
        private const val TAG = "MapPositionController"
        private const val OVERLAY_LOG_INTERVAL_MS = 1_000L
    }

    private var lastTrackBearing: Double = 0.0
    private val offsetHistory = OffsetHistory(offsetHistorySize)
    private var lastPadding: IntArray? = null
    private var lastOverlayLogMs: Long = 0L
    private val verboseLogging = com.example.xcpro.map.BuildConfig.DEBUG
    private val iconHeadingSmoother = IconHeadingSmoother(iconRotationConfig)

    /**
     * Represents map padding to smooth (top, bottom) in pixels.
     * x = top padding px, y = bottom padding px.
     */
    data class Offset(val x: Float, val y: Float)

    fun rememberOffset(offset: Offset) {
        offsetHistory.add(offset)
    }

    fun averagedOffset(): Offset = offsetHistory.average() ?: Offset(0f, 0f)

    fun updateOverlay(
        location: LatLng,
        trackBearing: Double,
        headingDeg: Double,
        headingValid: Boolean,
        bearingAccuracyDeg: Double?,
        speedAccuracyMs: Double?,
        mapBearing: Double,
        orientationMode: com.example.xcpro.common.orientation.MapOrientationMode,
        speedMs: Double,
        nowMs: Long,
        frameId: Long? = null
    ) {
        val clampedBearing = clampBearingStep(trackBearing)
        val iconHeading = if (headingSmoothingEnabledProvider()) {
            iconHeadingSmoother.update(
                IconHeadingSmoother.IconHeadingInput(
                    headingDeg = headingDeg,
                    headingValid = headingValid,
                    trackDeg = clampedBearing,
                    bearingAccuracyDeg = bearingAccuracyDeg,
                    speedAccuracyMs = speedAccuracyMs,
                    mapBearing = mapBearing,
                    orientationMode = orientationMode,
                    speedMs = speedMs,
                    nowMs = nowMs
                )
            )
        } else {
            headingDeg
        }
        if (verboseLogging) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastOverlayLogMs >= OVERLAY_LOG_INTERVAL_MS) {
                lastOverlayLogMs = now
                Log.d(
                    TAG,
                    "overlayUpdate loc=${location.latitude},${location.longitude} " +
                        "track=${"%.1f".format(trackBearing)} clamped=${"%.1f".format(clampedBearing)} " +
                        "heading=${"%.1f".format(headingDeg)} valid=$headingValid " +
                        "icon=${"%.1f".format(iconHeading)} mapBearing=${"%.1f".format(mapBearing)} " +
                        "orientation=$orientationMode speed=${"%.2f".format(speedMs)} frame=${frameId ?: -1}"
                )
            }
        }

        mapState.blueLocationOverlay?.updateLocation(
            location,
            clampedBearing,
            iconHeading,
            mapBearing,
            orientationMode
        )
        mapState.blueLocationOverlay?.setVisible(true)
    }

    fun updateCamera(
        camera: MapCameraController,
        location: LatLng,
        cameraBearing: Double?,
        padding: IntArray?,
        animationMs: Int?
    ) {
        val currentPosition = camera.cameraPosition
        val newCameraPosition = MapCameraPositionSnapshot(
            target = location,
            zoom = currentPosition.zoom,
            bearing = cameraBearing ?: currentPosition.bearing,
            tilt = currentPosition.tilt
        )

        if (verboseLogging) {
            val fromTarget = currentPosition.target
            val toTarget = newCameraPosition.target
            Log.d(
                TAG,
                "cameraMove from lat=${formatCoord(fromTarget?.latitude)},lon=${formatCoord(fromTarget?.longitude)}, " +
                    "zoom=${"%.2f".format(currentPosition.zoom)} bearing=${"%.1f".format(currentPosition.bearing)} " +
                    "to lat=${formatCoord(toTarget?.latitude)},lon=${formatCoord(toTarget?.longitude)}, " +
                    "zoom=${"%.2f".format(newCameraPosition.zoom)} bearing=${"%.1f".format(newCameraPosition.bearing)}"
            )
        }

        if (animationMs != null && animationMs > 0) {
            camera.animateCamera(newCameraPosition, animationMs)
        } else {
            camera.moveCamera(newCameraPosition)
        }

        padding?.let {
            if (!it.contentEquals(lastPadding)) {
                camera.setPadding(it[0], it[1], it[2], it[3])
                lastPadding = it
                if (verboseLogging) {
                    Log.d(TAG, "mapPadding updated to ${it.contentToString()}")
                }
            } else if (verboseLogging) {
                Log.d(TAG, "mapPadding unchanged ${it.contentToString()}")
            }
        }
    }

    private fun formatCoord(value: Double?): String =
        value?.let { "%.6f".format(it) } ?: "null"

    fun clampBearingStep(newBearing: Double): Double {
        val maxStep = maxBearingStepDegProvider()
        if (!maxStep.isFinite() || maxStep <= 0.0 || maxStep >= 180.0) {
            lastTrackBearing = normalize(newBearing)
            return lastTrackBearing
        }
        val newNorm = normalize(newBearing)
        val prevNorm = normalize(lastTrackBearing)
        var delta = newNorm - prevNorm
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        val limited = if (abs(delta) > maxStep) {
            prevNorm + sign(delta) * maxStep
        } else {
            newNorm
        }
        lastTrackBearing = normalize(limited)
        return lastTrackBearing
    }

    private fun normalize(bearing: Double): Double {
        var b = bearing % 360.0
        if (b < 0) b += 360.0
        return b
    }

    private class OffsetHistory(private val capacity: Int) {
        private val buffer = ArrayList<Offset>(capacity)
        private var index = 0
        fun add(offset: Offset) {
            if (buffer.size < capacity) buffer.add(offset) else buffer[index] = offset
            index = (index + 1) % capacity
        }

        fun average(): Offset? {
            if (buffer.isEmpty()) return null
            var sx = 0f
            var sy = 0f
            buffer.forEach { sx += it.x; sy += it.y }
            val c = buffer.size.toFloat()
            return Offset(sx / c, sy / c)
        }
    }
}
