package com.example.xcpro.ogn

import kotlin.math.abs

private const val KNOTS_TO_MPS = 0.514444
private const val MAX_ABS_VARIO_KTS = 30.0
private const val MAX_ABS_VARIO_MPS = MAX_ABS_VARIO_KTS * KNOTS_TO_MPS

internal const val OGN_TRAIL_ZERO_WIDTH_PX = 2.0f
internal const val OGN_TRAIL_SINK_MIN_WIDTH_PX = 0.8f
internal const val OGN_TRAIL_CLIMB_MAX_WIDTH_PX = 7.5f

fun ognTrailColorIndex(verticalSpeedMps: Double): Int =
    climbRateToSnailColorIndex(verticalSpeedMps)

fun ognTrailWidthPx(verticalSpeedMps: Double): Float {
    if (!verticalSpeedMps.isFinite()) return OGN_TRAIL_ZERO_WIDTH_PX
    val clamped = verticalSpeedMps.coerceIn(-MAX_ABS_VARIO_MPS, MAX_ABS_VARIO_MPS)
    return if (clamped <= 0.0) {
        val t = (abs(clamped) / MAX_ABS_VARIO_MPS).toFloat()
        lerp(
            start = OGN_TRAIL_ZERO_WIDTH_PX,
            end = OGN_TRAIL_SINK_MIN_WIDTH_PX,
            t = t
        )
    } else {
        val t = (clamped / MAX_ABS_VARIO_MPS).toFloat()
        lerp(
            start = OGN_TRAIL_ZERO_WIDTH_PX,
            end = OGN_TRAIL_CLIMB_MAX_WIDTH_PX,
            t = t
        )
    }
}

private fun lerp(start: Float, end: Float, t: Float): Float =
    start + (end - start) * t.coerceIn(0f, 1f)
