package com.example.xcpro.xcprov1.filters

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Runtime helper for horizontal wind estimation.
 */
class WindStateModel {

    data class WindVector(
        val windX: Double,
        val windY: Double,
        val speed: Double,
        val directionDeg: Double
    )

    private var estimateX = 0.0
    private var estimateY = 0.0

    fun update(
        groundSpeed: Double,
        groundTrackRad: Double?,
        trueAirspeed: Double,
        airBearingRad: Double?,
        alpha: Double = 0.1
    ): WindVector {
        if (groundTrackRad == null || airBearingRad == null) {
            return toVector()
        }

        val groundX = groundSpeed * cos(groundTrackRad)
        val groundY = groundSpeed * sin(groundTrackRad)

        val airX = trueAirspeed * cos(airBearingRad)
        val airY = trueAirspeed * sin(airBearingRad)

        val wx = groundX - airX
        val wy = groundY - airY

        estimateX = (1.0 - alpha) * estimateX + alpha * wx
        estimateY = (1.0 - alpha) * estimateY + alpha * wy

        return toVector()
    }

    fun set(estimateX: Double, estimateY: Double) {
        this.estimateX = estimateX
        this.estimateY = estimateY
    }

    fun get(): WindVector = toVector()

    fun reset() {
        estimateX = 0.0
        estimateY = 0.0
    }

    private fun toVector(): WindVector {
        val speed = sqrt(estimateX.pow(2.0) + estimateY.pow(2.0))
        val directionRad = atan2(estimateY, estimateX) + Math.PI // Coming FROM
        val deg = Math.toDegrees(directionRad)
        return WindVector(
            windX = estimateX,
            windY = estimateY,
            speed = speed,
            directionDeg = (deg + 360.0) % 360.0
        )
    }
}
