package com.example.xcpro.map

import android.graphics.PointF
import android.util.Log
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Centralized gating/smoothing for aircraft location updates.
 *
 * Mirrors XCSoar's SetLocationLazy: ignore updates that move less than a pixel
 * (default 0.5 px) so both camera and icon advance together without jitter.
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
    private var lastScreenPoint: PointF? = null
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

        val previous = lastScreenPoint?.let { PointF(it.x, it.y) }
        lastScreenPoint = PointF(screenPoint.x, screenPoint.y)

        // First sample always accepted to seed state.
        if (previous == null) {
            accepted++
            lastMovePxSq = Float.POSITIVE_INFINITY
            return true
        }

        val dx = screenPoint.x - previous.x
        val dy = screenPoint.y - previous.y
        val distSq = dx * dx + dy * dy
        lastMovePxSq = distSq

        val passes = distSq > config.thresholdPx * config.thresholdPx
        if (passes) accepted++ else rejected++
        return passes
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
