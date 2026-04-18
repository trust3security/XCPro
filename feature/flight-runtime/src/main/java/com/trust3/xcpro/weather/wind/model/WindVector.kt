package com.trust3.xcpro.weather.wind.model

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Represents a horizontal wind vector. Components are expressed as the
 * velocity of the airmass (i.e. the direction *towards* which the wind blows).
 */
data class WindVector(
    val east: Double,
    val north: Double
) {

    val speed: Double = hypot(east, north)

    /**
     * Direction the wind is blowing **towards** (mathematical azimuth, radians, 0 = North).
     */
    val directionToRad: Double = normalizeRadians(atan2(east, north))

    /**
     * Direction the wind is coming **from** (meteorological bearing, radians).
     */
    val directionFromRad: Double = normalizeRadians(directionToRad + PI)

    val directionToDeg: Double = Math.toDegrees(directionToRad)
    val directionFromDeg: Double = Math.toDegrees(directionFromRad)

    companion object {
        fun fromSpeedAndBearing(speed: Double, directionFromRad: Double): WindVector {
            val directionTo = normalizeRadians(directionFromRad - PI)
            val east = sin(directionTo) * speed
            val north = cos(directionTo) * speed
            return WindVector(east, north)
        }
    }
}

fun normalizeRadians(value: Double): Double {
    var angle = value
    while (angle <= -PI) angle += 2 * PI
    while (angle > PI) angle -= 2 * PI
    return angle
}
