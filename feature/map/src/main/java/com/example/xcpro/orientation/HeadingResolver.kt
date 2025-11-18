package com.example.xcpro.orientation

import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.HeadingSolution
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class HeadingResolverInput(
    val compassHeadingDeg: Double?,
    val compassReliable: Boolean,
    val gpsTrackDeg: Double?,
    val groundSpeedMs: Double,
    val hasGpsFix: Boolean,
    val windFromDeg: Double?,
    val windSpeedMs: Double,
    val minTrackSpeedMs: Double
)

/**
 * Reconstructs a heading suitable for HEAD_UP mode using the same fallbacks as XCSoar:
 * 1) Trust the compass when it is reliable.
 * 2) Otherwise derive the aircraft axis from ground track + wind.
 * 3) Otherwise fall back to GPS track if we're moving fast enough.
 * 4) Emit the last known bearing (handled by the controller) when everything else fails.
 */
class HeadingResolver {

    fun resolve(input: HeadingResolverInput): HeadingSolution {
        val compass = input.compassHeadingDeg?.takeIf { it.isFinite() }?.let(::normalizeBearing)
        if (compass != null && input.compassReliable) {
            return HeadingSolution(bearingDeg = compass, source = BearingSource.COMPASS, isValid = true)
        }

        val track = input.gpsTrackDeg?.takeIf { it.isFinite() }?.let(::normalizeBearing)
        val trackAvailable = track != null && input.hasGpsFix
        val trackAboveThreshold = input.groundSpeedMs >= input.minTrackSpeedMs

        if (trackAvailable) {
            val hasWindSolution = input.windFromDeg != null && input.windSpeedMs > 0.1
            if (trackAboveThreshold && hasWindSolution) {
                val heading = resolveFromWind(
                    trackDeg = track!!,
                    groundSpeed = input.groundSpeedMs,
                    windFromDeg = input.windFromDeg!!,
                    windSpeed = input.windSpeedMs
                )
                return HeadingSolution(bearingDeg = heading, source = BearingSource.WIND, isValid = true)
            }

            if (trackAboveThreshold) {
                return HeadingSolution(bearingDeg = track!!, source = BearingSource.TRACK, isValid = true)
            }

            // Still emit bearing so downstream logic can hold last-known smoothly.
            return HeadingSolution(bearingDeg = track!!, source = BearingSource.TRACK, isValid = false)
        }

        return HeadingSolution()
    }

    private fun resolveFromWind(
        trackDeg: Double,
        groundSpeed: Double,
        windFromDeg: Double,
        windSpeed: Double
    ): Double {
        val trackRad = Math.toRadians(trackDeg)
        var eastComponent = sin(trackRad) * groundSpeed
        var northComponent = cos(trackRad) * groundSpeed

        val windToRad = Math.toRadians(normalizeBearing(windFromDeg + 180.0))
        eastComponent += sin(windToRad) * windSpeed
        northComponent += cos(windToRad) * windSpeed

        if (abs(eastComponent) < 1e-3 && abs(northComponent) < 1e-3) {
            return trackDeg
        }

        val headingRad = atan2(eastComponent, northComponent)
        return normalizeBearing(Math.toDegrees(headingRad))
    }

    private fun normalizeBearing(value: Double): Double {
        var result = value % 360.0
        if (result < 0) {
            result += 360.0
        }
        return result
    }
}
