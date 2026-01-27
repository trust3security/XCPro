package com.example.xcpro.orientation

import kotlin.math.abs

internal fun normalizeBearing(value: Double): Double {
    if (!value.isFinite()) {
        return 0.0
    }
    var result = value % 360.0
    if (result < 0) {
        result += 360.0
    }
    return result
}

internal fun shortestDeltaDegrees(from: Double, to: Double): Double {
    var delta = (to - from) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta < -180.0) delta += 360.0
    return delta
}