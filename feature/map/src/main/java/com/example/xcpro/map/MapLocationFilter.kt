package com.example.xcpro.map

import android.graphics.PointF
import android.util.Log
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

/**
 * Centralized gating/smoothing for aircraft location updates.
 *
 * Ignore updates that move less than a pixel (default 0.5 px) so both camera and
 * icon advance together without jitter.
 */
class MapLocationFilter(
    private val config: Config = Config(),
    private val projector: ScreenProjector = MapLibreProjector()
    ) {

    data class Config(
        val thresholdPx: Float = 0.5f,
        val historySize: Int = 30
    )

    data class Stats(
        val accepted: Int,
        val rejected: Int,
        val lastMovePxSquared: Float
    )

    private val offsetHistory = OffsetHistory(config.historySize)
    private var lastAcceptedScreenPoint: PointF? = null
    private var accepted = 0
    private var rejected = 0
    private var lastMovePxSq: Float = Float.NaN

    fun accept(location: LatLng, map: MapLibreMap): Boolean {
        val screenPoint = projector.toScreenPoint(map, location)
        if (screenPoint == null) {
            // Projection failed; allow update to avoid freezing.
            Log.w(TAG, "Projection failed; accepting location to avoid stall")
            accepted++
            return true
        }

        val previousAccepted = lastAcceptedScreenPoint?.let { PointF(it.x, it.y) }

        // First sample always accepted to seed state.
        if (previousAccepted == null) {
            accepted++
            lastAcceptedScreenPoint = PointF(screenPoint.x, screenPoint.y)
            lastMovePxSq = Float.POSITIVE_INFINITY
            return true
        }

        val dx = screenPoint.x - previousAccepted.x
        val dy = screenPoint.y - previousAccepted.y
        val distSq = dx * dx + dy * dy
        lastMovePxSq = distSq

        val passes = distSq > config.thresholdPx * config.thresholdPx
        if (passes) {
            accepted++
            lastAcceptedScreenPoint = PointF(screenPoint.x, screenPoint.y)
        } else {
            rejected++
        }
        if (com.example.xcpro.map.BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "accept=$passes distSq=${"%.3f".format(distSq)} thresholdSq=${"%.3f".format(config.thresholdPx * config.thresholdPx)} " +
                    "screen=(${screenPoint.x.roundToInt()},${screenPoint.y.roundToInt()}) " +
                    "prev=(${previousAccepted.x.roundToInt()},${previousAccepted.y.roundToInt()}) " +
                    "lat=${location.latitude},lon=${location.longitude} accepted=$accepted rejected=$rejected"
            )
        }
        return passes
    }

    fun resetTo(location: LatLng, map: MapLibreMap) {
        val screenPoint = projector.toScreenPoint(map, location) ?: return
        lastAcceptedScreenPoint = PointF(screenPoint.x, screenPoint.y)
        lastMovePxSq = Float.NaN
        if (com.example.xcpro.map.BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "resetTo screen=(${screenPoint.x.roundToInt()},${screenPoint.y.roundToInt()}) " +
                    "lat=${location.latitude},lon=${location.longitude}"
            )
        }
    }

    fun rememberOffset(offset: PointF) {
        offsetHistory.add(offset)
    }

    fun averagedOffset(): PointF = offsetHistory.average() ?: PointF(0f, 0f)

    fun stats(): Stats = Stats(accepted, rejected, lastMovePxSq)

    private class OffsetHistory(private val capacity: Int) {
        private val buffer = ArrayList<PointF>(capacity)
        private var index = 0

        fun add(point: PointF) {
            if (buffer.size < capacity) {
                buffer.add(point)
            } else {
                buffer[index] = point
            }
            index = (index + 1) % capacity
        }

        fun average(): PointF? {
            if (buffer.isEmpty()) return null
            var sumX = 0f
            var sumY = 0f
            buffer.forEach {
                sumX += it.x
                sumY += it.y
            }
            val count = buffer.size.toFloat()
            return PointF(sumX / count, sumY / count)
        }
    }

    companion object {
        private const val TAG = "MapLocationFilter"
    }
}

/**
 * Projection adapter to make testing easier.
 */
interface ScreenProjector {
    fun toScreenPoint(map: MapLibreMap, latLng: LatLng): PointF?
}

class MapLibreProjector : ScreenProjector {
    override fun toScreenPoint(map: MapLibreMap, latLng: LatLng): PointF? =
        try {
            val p = map.projection?.toScreenLocation(latLng)
            p?.let { PointF(it.x, it.y) }
        } catch (e: Exception) {
            Log.w("MapLibreProjector", "Projection error: ${e.message}")
            null
        }
}
