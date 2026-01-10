package com.example.xcpro.map

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.sign

/**
 * Single owner for applying accepted (already gated) location samples
 * to both camera and icon. Mirrors XCSoar smoothness: shared gate,
 * bearing clamp, offset averaging.
 */
class MapPositionController(
    private val mapState: MapScreenState,
    private val maxBearingStepDeg: Double = 5.0,
    offsetHistorySize: Int = 30
) {
    private var lastIconBearing: Double = 0.0
    private val offsetHistory = OffsetHistory(offsetHistorySize)
    private var lastPadding: IntArray? = null

    /**
     * Represents map padding to smooth (top, bottom) in pixels.
     * x = top padding px, y = bottom padding px.
     */
    data class Offset(val x: Float, val y: Float)

    fun rememberOffset(offset: Offset) {
        offsetHistory.add(offset)
    }

    fun averagedOffset(): Offset = offsetHistory.average() ?: Offset(0f, 0f)

    fun applyAcceptedSample(
        map: MapLibreMap,
        location: LatLng,
        trackBearing: Double,
        headingDeg: Double,
        mapBearing: Double,
        orientationMode: com.example.xcpro.common.orientation.MapOrientationMode,
        shouldTrackCamera: Boolean = true,
        padding: IntArray? = null,
        cameraBearing: Double? = null
    ) {
        val clampedBearing = clampBearingStep(trackBearing)

        mapState.blueLocationOverlay?.updateLocation(
            location,
            clampedBearing,
            headingDeg,
            mapBearing,
            orientationMode
        )
        mapState.blueLocationOverlay?.setVisible(true)

        // AI-NOTE: Only move the camera when tracking is enabled so user pans aren't overridden.
        if (shouldTrackCamera) {
            val currentPosition = map.cameraPosition
            val newCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                .target(location)
                .zoom(currentPosition.zoom)
                .bearing(cameraBearing ?: currentPosition.bearing)
                .tilt(currentPosition.tilt)
                .build()

            map.moveCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(newCameraPosition)
            )
            padding?.let {
                if (!it.contentEquals(lastPadding)) {
                    map.setPadding(it[0], it[1], it[2], it[3])
                    lastPadding = it
                }
            }
        }
    }

    fun clampBearingStep(newBearing: Double): Double {
        val newNorm = normalize(newBearing)
        val prevNorm = normalize(lastIconBearing)
        var delta = newNorm - prevNorm
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        val limited = if (abs(delta) > maxBearingStepDeg) {
            prevNorm + sign(delta) * maxBearingStepDeg
        } else {
            newNorm
        }
        lastIconBearing = normalize(limited)
        return lastIconBearing
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
