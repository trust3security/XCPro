package com.example.xcpro.map.trail.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

internal data class TrailWindSample(
    val speedMs: Double,
    val directionFromDeg: Double
)

/**
 * Exponential smoother for replay wind samples.
 */
internal class TrailWindSmoother(
    private val tauMs: Long,
    private val minValidSpeedMs: Double
) {
    private var lastTimeMs: Long? = null
    private var vx: Double = 0.0
    private var vy: Double = 0.0

    fun reset() {
        lastTimeMs = null
        vx = 0.0
        vy = 0.0
    }

    fun update(speedMs: Double, directionFromDeg: Double, timestampMs: Long): TrailWindSample {
        if (timestampMs <= 0L) {
            return TrailWindSample(speedMs, directionFromDeg)
        }
        val valid = speedMs.isFinite() &&
            directionFromDeg.isFinite() &&
            speedMs > minValidSpeedMs
        val windToDeg = if (valid) (directionFromDeg + 180.0) % 360.0 else 0.0
        val windToRad = Math.toRadians(windToDeg)
        val targetVx = if (valid) speedMs * sin(windToRad) else 0.0
        val targetVy = if (valid) speedMs * cos(windToRad) else 0.0

        val last = lastTimeMs
        if (last == null) {
            vx = 0.0
            vy = 0.0
            lastTimeMs = timestampMs
        } else {
            val dtMs = (timestampMs - last).coerceAtLeast(0L)
            val alpha = if (tauMs > 0L) {
                1.0 - exp(-dtMs.toDouble() / tauMs.toDouble())
            } else {
                1.0
            }
            vx += (targetVx - vx) * alpha
            vy += (targetVy - vy) * alpha
            lastTimeMs = timestampMs
        }

        val speed = hypot(vx, vy)
        if (speed <= 1e-3) {
            return TrailWindSample(0.0, directionFromDeg.takeIf { it.isFinite() } ?: 0.0)
        }
        val windTo = (Math.toDegrees(atan2(vx, vy)) + 360.0) % 360.0
        val windFrom = (windTo + 180.0) % 360.0
        return TrailWindSample(speed, windFrom)
    }
}
